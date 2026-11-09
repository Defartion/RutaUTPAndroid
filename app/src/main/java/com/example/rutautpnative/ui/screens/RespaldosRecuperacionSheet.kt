package com.example.rutautpnative.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.data.*
import com.example.rutautpnative.data.monedero.MonederoStore
import com.example.rutautpnative.data.notificaciones.NotificacionesService
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RespaldosRecuperacionSheet(onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var archivos by remember { mutableStateOf<List<File>>(emptyList()) }
    var exportar by remember { mutableStateOf<File?>(null) }
    var confirmar by remember { mutableStateOf<String?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    val destino = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val archivo = exportar
        if (uri != null && archivo != null) scope.launch {
            mensaje = withContext(Dispatchers.IO) { runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { output -> archivo.inputStream().use { it.copyTo(output) } } ?: error("Destino no disponible")
                L.t("Copia exportada.", "Backup exported.")
            }.getOrElse { L.t("No se pudo exportar la copia.", "Could not export backup.") } }
        }
    }
    LaunchedEffect(Unit) { archivos = withContext(Dispatchers.IO) { RecuperacionPreferencias.listar(ctx) } }
    ModalBottomSheet(onDismissRequest = onCerrar, containerColor = AppSurface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(L.t("Respaldos y recuperación", "Backups and recovery"), style = HeadlineMd)
            Text(L.t("Puedes exportar las copias locales. Restablecer un grupo guarda primero sus datos originales y después lo devuelve a sus valores iniciales.", "You can export local backups. Resetting a group first saves its original data and then returns it to initial values."), style = BodySm)
            archivos.forEach { archivo -> OutlinedButton(onClick = { exportar = archivo; destino.launch(archivo.name) }, enabled = !ocupado) { Text(archivo.name, maxLines = 2) } }
            if (archivos.isEmpty()) Text(L.t("No hay copias de recuperación.", "No recovery backups."), style = BodySm)
            HorizontalDivider()
            listOf("lugares" to L.t("Lugares guardados", "Saved places"), "lineas" to L.t("Líneas guardadas", "Saved routes"), "monedero" to L.t("Monedero demo", "Demo wallet"), "notificaciones" to L.t("Notificaciones", "Notifications")).forEach { (id, titulo) ->
                TextButton(onClick = { confirmar = id }, enabled = !ocupado) { Text(L.t("Restablecer", "Reset") + " · " + titulo, color = AppError) }
            }
            if (ocupado) CircularProgressIndicator()
            mensaje?.let { Text(it, style = BodySm) }
            TextButton(onClick = onCerrar) { Text(L.t("Cerrar", "Close")) }
        }
    }
    confirmar?.let { grupo -> AlertDialog(onDismissRequest = { confirmar = null },
        title = { Text(L.t("¿Restablecer este grupo?", "Reset this group?")) },
        text = { Text(L.t("Se guardará una copia de los datos actuales antes de sustituirlos. Las demás preferencias se conservan.", "A copy of current data will be saved before replacement. Other preferences are preserved.")) },
        confirmButton = { TextButton(onClick = {
            confirmar = null; ocupado = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching {
                    when (grupo) {
                        "lugares" -> LugaresStore.reiniciarConRespaldo()
                        "lineas" -> LineasGuardadasStore.reiniciarConRespaldo()
                        "monedero" -> MonederoStore.reiniciarConRespaldo()
                        else -> NotificacionesService.reiniciarConRespaldo()
                    }
                }.isSuccess }
                archivos = withContext(Dispatchers.IO) { RecuperacionPreferencias.listar(ctx) }
                mensaje = if (ok) L.t("Datos restablecidos y copia conservada.", "Data reset and backup preserved.") else L.t("No se completó el restablecimiento. Revisa la copia y vuelve a intentarlo.", "Reset did not complete. Check the backup and try again.")
                ocupado = false
            }
        }) { Text(L.t("Guardar copia y restablecer", "Back up and reset")) } },
        dismissButton = { TextButton(onClick = { confirmar = null }) { Text(L.t("Cancelar", "Cancel")) } }) }
}
