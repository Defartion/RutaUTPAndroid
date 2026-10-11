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
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.rutautpnative.data.imagenes.CarnetImageStore
import com.example.rutautpnative.ui.components.CodigoBarras
import com.example.rutautpnative.ui.components.EditorEncuadre
import com.example.rutautpnative.ui.components.rememberSelectorFoto
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import com.example.rutautpnative.ui.theme.TematicaEmpresa

//----Carné Digital (port de CarneDigitalView)----
// La foto PERSISTE por empresa (CarnetImageStore: carnet-<empresa>.jpg),
// separada de la foto de perfil; el editor de encuadre recorta antes de
// guardar y puede reajustar la guardada. "Carné guardado" confirma el
// almacenamiento, no una verificación universitaria.
private const val CODIGO_UTP = "1234567" // dato de ejemplo, igual que en iOS

@Composable
fun CarneDigitalScreen(
    nombre: String,
    empresa: TematicaEmpresa,
    onCerrar: () -> Unit
) {
    val contexto = LocalContext.current

    // Foto del carné persistida por empresa (fuente unica).
    var foto by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(CarnetImageStore.revision, empresa.id) {
        foto = CarnetImageStore.cargar(contexto, empresa.id)
    }

    var bitmapParaEncuadre by remember { mutableStateOf<Bitmap?>(null) }
    val abrirSelectorFoto = rememberSelectorFoto(titulo = L.t("Foto del carné", "ID card photo")) {
        bitmapParaEncuadre = it
    }

    val esUtp = empresa == TematicaEmpresa.UTP
    val codigo = if (esUtp) CODIGO_UTP else "DEMO-EMPRESA"

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
                    Text(
                        if (esUtp) L.t("Carné Digital", "Digital ID Card")
                        else L.t("Carnet de empresa", "Company Card"),
                        style = HeadlineLg, color = AppPrimary
                    )
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
                                        bitmap = foto!!.asImageBitmap(),
                                        contentDescription = L.t("Foto del carné", "ID card photo"),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(inicialesDe(nombre), style = HeadlineMd, color = Color.White)
                                }
                            }
                            // Insignia de cámara: camara o galeria -> editor.
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable { abrirSelectorFoto() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.AddAPhoto, L.t("Cambiar foto del carné", "Change ID card photo"), tint = AppPrimary, modifier = Modifier.size(13.dp))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        // "Ajustar encuadre": recortar de nuevo la foto guardada
                        // (iOS idem; cancelar conserva la actual).
                        if (foto != null) {
                            TextButton(onClick = { bitmapParaEncuadre = foto }) {
                                Icon(Icons.Filled.Crop, null, tint = AppPrimary, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(L.t("Ajustar encuadre", "Adjust frame"), style = BodyXs, color = AppPrimary)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(nombre, style = HeadlineMd, color = OnSurface)
                        Text(
                            if (esUtp) L.t("Estudiante UTP", "UTP Student")
                            else L.t("Colaborador", "Employee"),
                            style = BodySm, color = OnSurfaceVariant
                        )
                        Text(L.t("Identidad no verificada", "Identity not verified"), style = BodyXs, color = OnSurfaceVariant)
                        Spacer(Modifier.height(18.dp))

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                        Spacer(Modifier.height(16.dp))

                        //----Código (UTP o demo de empresa)----
                        Text(
                            if (esUtp) L.t("CÓDIGO UTP", "UTP CODE") else L.t("CÓDIGO DE EMPRESA", "COMPANY CODE"),
                            style = LabelCapsMd, color = OnSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            codigo,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp,
                            letterSpacing = 6.sp,
                            color = OnSurface
                        )
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                        Spacer(Modifier.height(16.dp))

                        //----Código de barras Code 128 (generado local)----
                        Text(L.t("Ingresa al campus mostrando este código", "Enter campus by showing this code"), style = BodySm, color = OnSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        BoxWithConstraints {
                            val anchoDp = maxWidth - 48.dp
                            val altoDp = 84.dp
                            val anchoPx = with(LocalDensity.current) { anchoDp.roundToPx() }
                            val altoPx = with(LocalDensity.current) { altoDp.roundToPx() }
                            val barcode = remember(anchoPx, altoPx, codigo) {
                                CodigoBarras.code128(codigo, anchoPx, altoPx)
                            }
                            barcode?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = L.t("Código de barras del carné", "ID card barcode"),
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
                            Text(L.t("ÚLTIMO CICLO MATRICULADO", "LAST ENROLLED TERM"), style = LabelCapsMd, color = Color.White, letterSpacing = 2.sp)
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
                            L.t(
                                "Recuerda que compartir tus credenciales de identificación es una infracción muy grave que conlleva la máxima sanción bajo el Reglamento de Disciplina.",
                                "Please be advised that sharing your identification credentials is a serious disciplinary offense, subject to the maximum penalty under the Disciplinary Regulations."
                            ),
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
                    Text(L.t("Cerrar", "Close"), style = BodyMdMedium, color = Color.White)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    //----Editor de encuadre rectangular (carné)----
    bitmapParaEncuadre?.let { bitmap ->
        EditorEncuadre(
            bitmap = bitmap,
            circular = false,
            onGuardar = { recorte ->
                CarnetImageStore.guardar(contexto, empresa.id, recorte)
                bitmapParaEncuadre = null
            },
            onCancelar = { bitmapParaEncuadre = null }
        )
    }
}

private fun inicialesDe(nombre: String): String =
    nombre.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("")
