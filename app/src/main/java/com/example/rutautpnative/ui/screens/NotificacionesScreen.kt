package com.example.rutautpnative.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.data.LugaresStore
import com.example.rutautpnative.data.PreferenciasPerfilStore
import com.example.rutautpnative.data.SedeTrabajoStore
import com.example.rutautpnative.data.notificaciones.*
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificacionesScreen(onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val servicio = NotificacionesService
    val config = servicio.configuracion
    val lugares by LugaresStore.observar().collectAsState(initial = emptyList())
    var permitido by remember { mutableStateOf(servicio.permitido(ctx)) }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permitido = servicio.permitido(ctx); servicio.refrescar()
    }
    var hora by remember { mutableStateOf("%02d:%02d".format((config.recordatorio?.minutoDelDia ?: 420) / 60, (config.recordatorio?.minutoDelDia ?: 420) % 60)) }
    var dias by remember { mutableStateOf(config.recordatorio?.dias ?: setOf(2, 3, 4, 5, 6)) }
    var destino by remember { mutableStateOf(config.recordatorio?.destinoID.orEmpty()) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    val destinos = buildList {
        SedeTrabajoStore.sede?.let { add("sede:${it.id}" to it.nombre) }
        lugares.filter { it.lat != null && it.lon != null }.forEach { add("lugar:${it.id}" to it.nombre) }
    }
    fun guardar(nueva: ConfiguracionNotificaciones) { if (!servicio.guardar(nueva)) mensaje = L.t("No se pudieron guardar los ajustes.", "Could not save settings.") }
    ModalBottomSheet(onDismissRequest = onCerrar, containerColor = AppSurface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(L.t("Notificaciones", "Notifications"), style = HeadlineMd, color = OnSurface)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(L.t("Activar notificaciones", "Enable notifications"), color = OnSurface)
                Switch(PreferenciasPerfilStore.notificaciones, { activo ->
                    PreferenciasPerfilStore.establecerNotificaciones(activo)
                    if (activo && Build.VERSION.SDK_INT >= 33 && !permitido) permiso.launch(Manifest.permission.POST_NOTIFICATIONS)
                    servicio.refrescar()
                })
            }
            Text(if (permitido) L.t("Permitidas por Android", "Allowed by Android") else L.t("Revisa el permiso de notificaciones de Android.", "Check Android notification permission."), style = BodySm)
            TextButton(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }) { Text(L.t("Abrir ajustes de Android", "Open Android settings")) }
            if (servicio.datosInvalidos) Text(L.t("Los ajustes guardados no se pueden leer. Se conservan sin sobrescribirlos.", "Saved settings cannot be read. They are preserved without being overwritten."), color = AppError)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(L.t("Cambios en mis líneas guardadas", "Changes on my saved routes"), modifier = Modifier.weight(1f))
                Switch(PushRutasService.activadas, { activo ->
                    if (activo && Build.VERSION.SDK_INT >= 33 && !permitido) permiso.launch(Manifest.permission.POST_NOTIFICATIONS)
                    PushRutasService.cambiar(activo)
                }, enabled = PushRutasService.disponible)
            }
            Text(PushRutasService.estado, style = BodyXs, color = OnSurfaceVariant)
            Text(L.t("RECORDATORIO DE VIAJE", "TRIP REMINDER"), style = LabelCapsMd)
            OutlinedTextField(hora, { hora = it.take(5) }, label = { Text(L.t("Hora (HH:mm)", "Time (HH:mm)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            DiasNotificaciones(dias) { dias = it }
            Text(L.t("Destino guardado", "Saved destination"), style = BodyMdMedium)
            destinos.forEach { (id, titulo) ->
                Row(Modifier.fillMaxWidth()) {
                    RadioButton(destino == id, { destino = id })
                    TextButton(onClick = { destino = id }) { Text(titulo, color = OnSurface) }
                }
            }
            Button(onClick = {
                val minuto = minutoHora(hora)
                if (minuto == null || dias.isEmpty() || destinos.none { it.first == destino }) mensaje = L.t("Elige una hora, días y destino válidos.", "Choose a valid time, days and destination.")
                else guardar(config.copy(recordatorio = RecordatorioViaje(config.recordatorio?.id ?: java.util.UUID.randomUUID().toString(), minuto, dias, destino)))
            }, enabled = !servicio.datosInvalidos) { Text(L.t("Guardar recordatorio", "Save reminder")) }
            if (config.recordatorio != null) TextButton(onClick = { guardar(config.copy(recordatorio = null)) }) { Text(L.t("Quitar recordatorio", "Remove reminder")) }
            HorizontalDivider()
            Text(L.t("PAUSAS Y SILENCIO", "PAUSES AND QUIET HOURS"), style = LabelCapsMd)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30L, 60L, 180L).forEach { minutos -> OutlinedButton(onClick = { guardar(config.copy(pausaHasta = Instant.now().plusSeconds(minutos * 60).toEpochMilli())) }) { Text("$minutos min") } }
                TextButton(onClick = { guardar(config.copy(pausaHasta = null)) }) { Text(L.t("Reanudar", "Resume")) }
            }
            Text(L.t("Días sin avisos", "Days without notifications"), style = BodySm)
            DiasNotificaciones(config.diasSilencio) { guardar(config.copy(diasSilencio = it)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(L.t("Horario de silencio", "Quiet hours")); Switch(config.silencioHorario, { guardar(config.copy(silencioHorario = it)) })
            }
            if (config.silencioHorario) {
                CampoHora(L.t("Desde", "From"), config.inicioSilencio) { guardar(config.copy(inicioSilencio = it)) }
                CampoHora(L.t("Hasta", "Until"), config.finSilencio) { guardar(config.copy(finSilencio = it)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(L.t("Sonido", "Sound")); Switch(config.sonido, { guardar(config.copy(sonido = it)) })
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(L.t("Avisos diarios", "Daily notifications")); Switch(config.diariosActivos, { guardar(config.copy(diariosActivos = it)) })
            }
            if (config.diariosActivos) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..3).forEach { cantidad -> FilterChip(config.cantidadDiaria == cantidad, { guardar(config.copy(cantidadDiaria = cantidad)) }, label = { Text("$cantidad") }) }
                }
                CampoHora(L.t("Inicio de la franja", "Start of time window"), config.inicioDiario) { guardar(config.copy(inicioDiario = it)) }
                CampoHora(L.t("Fin de la franja", "End of time window"), config.finDiario) { guardar(config.copy(finDiario = it)) }
            }
            Text(L.f("Avisos programados: {0}", "Scheduled notifications: {0}", "${servicio.programadas}"), style = BodySm)
            Text(L.t("Android puede retrasar los avisos para ahorrar batería. Los horarios del catálogo son referencias, no llegadas en vivo.", "Android may delay notifications to save battery. Catalog timetables are references, not live arrivals."), style = BodyXs)
            servicio.error?.let { Text(it, color = AppError) }
            mensaje?.let { Text(it, color = AppError) }
            OutlinedButton(onClick = { servicio.mostrar(ctx, null, null, prueba = true) }, enabled = permitido) { Text(L.t("Enviar notificación de prueba", "Send test notification")) }
            TextButton(onClick = onCerrar) { Text(L.t("Listo", "Done")) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun minutoHora(texto: String): Int? {
    val partes = texto.split(':')
    val h = partes.getOrNull(0)?.toIntOrNull() ?: return null
    val m = partes.getOrNull(1)?.toIntOrNull() ?: return null
    return (h * 60 + m).takeIf { partes.size == 2 && h in 0..23 && m in 0..59 }
}

@Composable
private fun CampoHora(label: String, minuto: Int, onGuardar: (Int) -> Unit) {
    var texto by remember(minuto) { mutableStateOf("%02d:%02d".format(minuto / 60, minuto % 60)) }
    OutlinedTextField(texto, { valor -> texto = valor.take(5); minutoHora(texto)?.let(onGuardar) }, label = { Text(label + " (HH:mm)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiasNotificaciones(elegidos: Set<Int>, onCambiar: (Set<Int>) -> Unit) {
    val nombres = listOf(L.t("D", "Su"), L.t("L", "Mo"), L.t("M", "Tu"), L.t("Mi", "We"), L.t("J", "Th"), L.t("V", "Fr"), L.t("S", "Sa"))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        nombres.forEachIndexed { i, nombre -> FilterChip(i + 1 in elegidos, { onCambiar(if (i + 1 in elegidos) elegidos - (i + 1) else elegidos + (i + 1)) }, label = { Text(nombre) }) }
    }
}
