package com.example.rutautpnative.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.rutautpnative.data.imagenes.ProfileImageStore
import com.example.rutautpnative.ui.components.*
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CarneDigitalScreen(nombre: String, empresa: TematicaEmpresa, onCerrar: () -> Unit) {
    val contexto = LocalContext.current
    val codigo = if (empresa == TematicaEmpresa.UTP) "1234567" else "DEMO-${empresa.id.uppercase()}"
    val foto by produceState<Bitmap?>(null, ProfileImageStore.revision) { value = ProfileImageStore.cargar(contexto) }
    val barras by produceState<Bitmap?>(null, codigo) { value = withContext(Dispatchers.Default) { CodigoBarras.code128(codigo, 1000, 240) } }
    var encuadre by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf(false) }
    val selector = rememberSelectorFoto(L.t("Foto de perfil", "Profile photo")) { if (it != null) encuadre = it }
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = AppSurface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Badge, null, tint = AcentoInterfaz, modifier = Modifier.size(40.dp))
                    Column(Modifier.weight(1f)) { Text(L.t("Carné Digital · muestra", "Digital ID · sample"), style = HeadlineMd, color = OnSurface); if (empresa != TematicaEmpresa.UTP) Text(empresa.nombre, style = BodySm, color = OnSurfaceVariant) }
                    IconButton(onClick = onCerrar) { Icon(Icons.Filled.Close, L.t("Cerrar", "Close")) }
                }
                Surface(shape = RoundedCornerShape(18.dp), color = SuperficieAgrupada) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(Modifier.size(84.dp), contentAlignment = Alignment.BottomEnd) {
                                Box(Modifier.fillMaxSize().clip(CircleShape).background(PrimaryFill), contentAlignment = Alignment.Center) {
                                    foto?.let { Image(it.asImageBitmap(), L.t("Foto de perfil", "Profile photo"), Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                        ?: Text(nombre.split(" ").take(2).mapNotNull { it.firstOrNull() }.joinToString(""), style = HeadlineMd, color = OnPrimaryFill)
                                }
                                FilledTonalIconButton(onClick = selector, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.PhotoCamera, L.t("Cambiar foto del carné", "Change ID photo"), modifier = Modifier.size(18.dp)) }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(nombre, style = HeadlineSm, color = OnSurface); Text(L.t("Identidad no verificada", "Unverified identity"), style = BodyXs, color = OnSurfaceVariant) }
                        }
                        HorizontalDivider(color = OutlineVariant.copy(alpha = .45f))
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(L.t("CÓDIGO DE MUESTRA", "SAMPLE CODE"), style = LabelCapsMd, color = OnSurfaceVariant)
                            Text(codigo, style = HeadlineLg, color = OnSurface)
                        }
                        HorizontalDivider(color = OutlineVariant.copy(alpha = .45f))
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (empresa == TematicaEmpresa.UTP) L.t("Código de demostración. No permite ingresar al campus.", "Demo code. It does not grant campus access.") else L.f("Código de demostración de {0}. No permite acceder a sus instalaciones.", "{0} demo code. It does not grant access to company facilities.", empresa.nombre), style = BodySm, color = OnSurfaceVariant)
                            barras?.let { Image(it.asImageBitmap(), L.t("Código de barras de muestra, sin validez: ", "Sample barcode, not valid: ") + codigo, modifier = Modifier.fillMaxWidth().height(104.dp).background(Color.White).padding(10.dp), contentScale = ContentScale.FillBounds) }
                        }
                        Text(if (empresa == TematicaEmpresa.UTP) L.t("MUESTRA · SIN VALIDEZ UNIVERSITARIA", "SAMPLE · NOT A VALID UNIVERSITY ID") else L.t("MUESTRA · SIN VALIDEZ CORPORATIVA", "SAMPLE · NOT A VALID COMPANY ID"), style = BodyXs, color = Color.White, modifier = Modifier.fillMaxWidth().background(Color.Black).padding(12.dp))
                    }
                }
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFDBA612)) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.WarningAmber, null, tint = Color(0xFF4A3700))
                        Text(L.f("Este carné es una muestra de {0} con un código ficticio. El nombre y la foto no verifican tu identidad. No es una credencial emitida por la institución y no permite acceder a sus instalaciones.", "This {0} ID is a sample with a fictional code. The name and photo do not verify your identity. It is not an institution-issued credential and does not grant access to its facilities.", empresa.nombre), style = BodySm, color = Color(0xFF4A3700))
                    }
                }
                if (error) Text(L.t("No se pudo guardar la foto. Vuelve a intentarlo.", "Could not save the photo. Try again."), color = AppError)
                OutlinedButton(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) { Text(L.t("Cerrar", "Close")) }
            }
        }
    }
    encuadre?.let { EditorEncuadre(it, circular = true, onGuardar = { recorte -> error = !ProfileImageStore.guardar(contexto, recorte); encuadre = null }, onCancelar = { encuadre = null }) }
}
