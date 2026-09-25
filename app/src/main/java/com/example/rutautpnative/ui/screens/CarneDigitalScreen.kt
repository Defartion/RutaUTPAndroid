package com.example.rutautpnative.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.rutautpnative.ui.components.CodigoBarras
import com.example.rutautpnative.ui.components.rememberSelectorFoto
import com.example.rutautpnative.ui.theme.*

//----Carné Digital (port de CarneDigitalView)----
// Foto y nombre: estado de sesión (no se persiste), igual que el resto de Perfil.
private const val CODIGO_UTP = "1234567" // dato de ejemplo, igual que en iOS

@Composable
fun CarneDigitalScreen(
    nombre: String,
    foto: Bitmap?,
    onFotoChange: (Bitmap?) -> Unit,
    onCerrar: () -> Unit
) {
    // Selector cámara/galería compartido (ui/components/SelectorFoto.kt).
    val abrirSelectorFoto = rememberSelectorFoto(titulo = "Foto del carné") { onFotoChange(it) }

    Dialog(
        onDismissRequest = onCerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                //----Encabezado----
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(PrimaryFixed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Badge, null, tint = AppPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Carné Digital", style = HeadlineLg, color = AppPrimary)
                }
                Spacer(Modifier.height(24.dp))

                //----Tarjeta del carné----
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                    ) {
                        // Foto circular 84dp con insignia de cámara.
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(CircleShape)
                                    .background(InversePrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                if (foto != null) {
                                    Image(
                                        bitmap = foto.asImageBitmap(),
                                        contentDescription = "Foto del carné",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(inicialesDe(nombre), style = HeadlineMd, color = Color.White)
                                }
                            }
                            // Insignia de cámara (círculo blanco con borde morado).
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable { abrirSelectorFoto() },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.AddAPhoto, "Cambiar foto", tint = AppPrimary, modifier = Modifier.size(13.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(nombre, style = HeadlineMd, color = OnSurface)
                        Text("Estudiante UTP", style = BodySm, color = OnSurfaceVariant)
                        Spacer(Modifier.height(18.dp))

                        Divider(modifier = Modifier.padding(horizontal = 20.dp))
                        Spacer(Modifier.height(16.dp))

                        //----Código UTP----
                        Text("CÓDIGO UTP", style = LabelCapsMd, color = OnSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            CODIGO_UTP,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp,
                            letterSpacing = 6.sp,
                            color = OnSurface
                        )
                        Spacer(Modifier.height(16.dp))
                        Divider(modifier = Modifier.padding(horizontal = 20.dp))
                        Spacer(Modifier.height(16.dp))

                        //----Código de barras Code 128 (generado local)----
                        Text("Ingresa al campus mostrando este código", style = BodySm, color = OnSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        BoxWithConstraints {
                            val anchoDp = maxWidth - 48.dp
                            val altoDp = 84.dp
                            val anchoPx = with(LocalDensity.current) { anchoDp.roundToPx() }
                            val altoPx = with(LocalDensity.current) { altoDp.roundToPx() }
                            val barcode = remember(anchoPx, altoPx) {
                                CodigoBarras.code128(CODIGO_UTP, anchoPx, altoPx)
                            }
                            barcode?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Código de barras del código UTP",
                                    modifier = Modifier
                                        .width(anchoDp)
                                        .height(altoDp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White)
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))

                        // Barra negra inferior decorativa.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                                .background(Color.Black)
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("ÚLTIMO CICLO MATRICULADO", style = LabelCapsMd, color = Color.White, letterSpacing = 2.sp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                //----Aviso de sanción (mostaza)----
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDBA612)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning, null,
                            tint = Color(0xFF4A3700),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Recuerda que compartir tus credenciales de identificación es una infracción muy grave que conlleva la máxima sanción bajo el Reglamento de Disciplina.",
                            style = BodySm,
                            color = Color(0xFF4A3700)
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))

                //----Cerrar----
                Button(
                    onClick = onCerrar,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
                ) {
                    Text("Cerrar", style = BodyMdMedium, color = Color.White)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // (El diálogo de selector de foto lo incluye rememberSelectorFoto,
    //  compartido en ui/components/SelectorFoto.kt)
}

private fun inicialesDe(nombre: String): String =
    nombre.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("")
