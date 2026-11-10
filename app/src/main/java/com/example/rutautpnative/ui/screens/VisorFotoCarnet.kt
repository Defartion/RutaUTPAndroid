package com.example.rutautpnative.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.rutautpnative.data.imagenes.CarnetImageStore
import com.example.rutautpnative.ui.components.EditorEncuadre
import com.example.rutautpnative.ui.components.rememberSelectorFoto
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*

@Composable
fun VisorFotoCarnet(empresa: TematicaEmpresa, onCerrar: () -> Unit) {
    val contexto = LocalContext.current
    val foto by produceState<Bitmap?>(null, empresa.id, CarnetImageStore.revision) { value = CarnetImageStore.cargar(contexto, empresa.id) }
    var encuadrar by remember { mutableStateOf<Bitmap?>(null) }
    var fallo by remember { mutableStateOf(false) }
    val elegir = rememberSelectorFoto(L.t("Foto del carné", "ID card photo")) { if (it != null) encuadrar = it }
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = AppBackground) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(L.t("Foto del carné", "ID card photo"), style = HeadlineMd, color = OnSurface, modifier = Modifier.weight(1f))
                    IconButton(onClick = onCerrar) { Icon(Icons.Filled.Close, L.t("Cerrar", "Close")) }
                }
                Text(empresa.nombre, style = BodyMdMedium, color = OnSurfaceVariant)
                val imagen = foto
                if (imagen != null) Image(imagen.asImageBitmap(), L.t("Foto guardada del carné", "Saved ID card photo"), modifier = Modifier.fillMaxWidth().weight(1f), contentScale = ContentScale.Fit)
                else Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Filled.Badge, null, tint = OnSurfaceVariant, modifier = Modifier.size(56.dp))
                        Text(L.t("Añade una foto de tu carné", "Add a photo of your ID card"), color = OnSurface)
                    }
                }
                if (fallo) Text(L.t("No se pudo guardar la foto. Vuelve a intentarlo.", "Could not save the photo. Try again."), color = AppError)
                Button(onClick = elegir, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.AddAPhoto, null); Spacer(Modifier.width(8.dp)); Text(if (imagen == null) L.t("Añadir foto", "Add photo") else L.t("Cambiar foto", "Change photo")) }
                if (imagen != null) OutlinedButton(onClick = { encuadrar = imagen }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Crop, null); Spacer(Modifier.width(8.dp)); Text(L.t("Ajustar encuadre", "Adjust crop")) }
            }
        }
    }
    encuadrar?.let { bitmap -> EditorEncuadre(bitmap, circular = false, onGuardar = { recorte -> fallo = !CarnetImageStore.guardar(contexto, empresa.id, recorte); encuadrar = null }, onCancelar = { encuadrar = null }) }
}
