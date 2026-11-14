package com.example.rutautpnative.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.rutautpnative.data.*
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.data.tracking.*
import com.example.rutautpnative.navigation.*
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.*

@Composable
internal fun SaludoSeguridad(fecha: Date? = null, zonaHoraria: TimeZone = TimeZone.getDefault()) {
    val ahora by produceState(fecha ?: Date(), fecha) { if (fecha == null) while (true) { delay(30_000); value = Date() } }
    val hora = Calendar.getInstance(zonaHoraria).apply { time = ahora }.get(Calendar.HOUR_OF_DAY)
    val saludo = when (hora) { in 5..11 -> L.t("Buenos días", "Good morning"); in 12..18 -> L.t("Buenas tardes", "Good afternoon"); else -> L.t("Buenas noches", "Good evening") }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(saludo, style = HeadlineBody, color = OnSurface)
            Text(SimpleDateFormat("EEEE, d MMMM", Locale.forLanguageTag(L.codigo)).apply { timeZone = zonaHoraria }.format(ahora), style = BodySm, color = OnSurfaceVariant)
        }
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = zonaHoraria }.format(ahora), style = HeadlineSm, color = AcentoInterfaz)
    }
}

@Composable
internal fun AyudaRapidaSeguridad(onEmergencia: () -> Unit, onEditarContacto: () -> Unit, onReportar: () -> Unit) {
    val context = LocalContext.current
    val store = PreferenciasPerfilStore
    var llamar by remember { mutableStateOf(false) }
    val principal = ContactoEmergencia.telefonoValido(store.emergenciaNumero)
    val alternativo = ContactoEmergencia.telefonoValido(store.emergenciaAlternativo)
    val textoGrande = LocalDensity.current.fontScale >= 1.5f
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { com.example.rutautpnative.data.senias.SeniasOverlay.ejecutarTrasVerSenia("seguridad.emergencia", onEmergencia) }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = PrimaryFill, contentColor = OnPrimaryFill)) {
            if (LocalDensity.current.fontScale >= 1.8f) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(L.t("Emergencias", "Emergency"), style = BodyMdMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Phone, null); Text("105", style = BodyMdMedium)
                    }
                }
            } else {
                Icon(Icons.Filled.Phone, null); Spacer(Modifier.width(10.dp)); Text(L.t("Emergencias", "Emergency"), modifier = Modifier.weight(1f), style = BodyMdMedium); Text("105", style = BodyMdMedium)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = if (textoGrande) 1 else 2) {
            Card(onClick = { if (principal == null) onEditarContacto() else llamar = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = SuperficieAgrupada)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Person, null, tint = AcentoInterfaz)
                        IconButton(onClick = onEditarContacto, modifier = Modifier.size(32.dp)) { Icon(if (principal == null) Icons.Filled.Add else Icons.Filled.Edit, L.t("Editar contacto de emergencia", "Edit emergency contact"), tint = AcentoInterfaz, modifier = Modifier.size(18.dp)) }
                    }
                    Text(if (principal == null) L.t("Añadir contacto", "Add contact") else L.t("Llamar", "Call"), style = BodyMdMedium, color = OnSurface)
                    Text(store.emergenciaNombre.ifBlank { L.t("Mi contacto", "My contact") }, style = BodyXs, color = OnSurfaceVariant)
                }
            }
            Card(onClick = { com.example.rutautpnative.data.senias.SeniasOverlay.ejecutarTrasVerSenia("seguridad.reportar", onReportar) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = SuperficieAgrupada)) {
                Column(Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 102.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.WarningAmber, null, tint = AcentoInterfaz)
                    Text(L.t("Reportar", "Report"), style = BodyMdMedium, color = OnSurface)
                }
            }
        }
    }
    if (llamar) AlertDialog(onDismissRequest = { llamar = false }, title = { Text(L.t("Llamar a mi contacto", "Call my contact")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(store.emergenciaNombre); Text(principal.orEmpty()); alternativo?.let { numero -> TextButton(onClick = { llamar = false; context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numero"))) }) { Text(L.t("Llamar al otro número", "Call the alternate number")) } } } },
        confirmButton = { TextButton(onClick = { llamar = false; principal?.let { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it"))) } }) { Text(L.t("Llamar", "Call")) } },
        dismissButton = { TextButton(onClick = { llamar = false }) { Text(L.t("Cancelar", "Cancel")) } })
}

@Composable
internal fun AvisosLineasSeguridad(router: AppRouter, rutas: List<RutaGTFS>) {
    val contexto = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val referencias by LineasGuardadasStore.observar().collectAsState(initial = emptyList())
    val invalidas by LineasGuardadasStore.datosInvalidos.collectAsState()
    val activa by PassiveTrackingCoordinator.rutaViajeSeleccionada.collectAsState()
    val iniciada by PassiveTrackingCoordinator.viajeIniciadoEn.collectAsState()
    val ids = (if (invalidas) emptySet() else referencias.map { it.routeId }.toSet()) + if (iniciada != null) setOfNotNull(activa?.routeId) else emptySet()
    val config = remember(com.example.rutautpnative.data.conexion.ConexionInstalacionStore.revision) { MQTTConfiguration.paraReportes() }
    val servicio = remember(config) { config?.let { RouteChangesService(it, contexto) } }
    val listo by (servicio?.listo ?: remember { MutableStateFlow(false) }).collectAsState()
    val alertas by (servicio?.alertas ?: remember { MutableStateFlow(emptyList<RouteChangesService.AlertaCambio>()) }).collectAsState()
    val vigentes = alertas.filter { it.routeId in ids }.sortedWith(compareBy<RouteChangesService.AlertaCambio> { it.routeId != activa?.routeId }.thenBy { it.expiresAt })
    var abrir by remember { mutableStateOf(false) }
    var rutaInicial by remember { mutableStateOf<String?>(null) }
    DisposableEffect(servicio, owner, ids) {
        fun actualizar() { if (ids.isNotEmpty() && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) servicio?.start() else servicio?.stop() }
        val observer = LifecycleEventObserver { _, _ -> actualizar() }
        owner.lifecycle.addObserver(observer); actualizar()
        onDispose { owner.lifecycle.removeObserver(observer); servicio?.stop() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(L.t("Avisos de tus líneas", "Your line alerts"), style = HeadlineSm, color = OnSurface)
        Text(L.t("Tus guardadas y tu viaje activo", "Your saved lines and active trip"), style = BodySm, color = OnSurfaceVariant)
        Surface(shape = RoundedCornerShape(20.dp), color = SuperficieAgrupada) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    invalidas -> Text(L.t("No pudimos leer tus líneas guardadas. Los datos originales se conservan.", "Couldn't read your saved lines. Original data is preserved."), color = AppError)
                    ids.isEmpty() -> { Text(L.t("Empieza con tus líneas", "Start with your lines"), style = BodyMdMedium); Text(L.t("Guarda una línea para ver sus obras, cierres y desvíos aquí.", "Save a line to see its roadworks, closures and detours here."), style = BodySm, color = OnSurfaceVariant); TextButton(onClick = { router.navigate(AppScreen.Guardado) }) { Text(L.t("Elegir líneas", "Choose lines")) } }
                    !listo -> { Text(L.t("Sin información actualizada", "No up-to-date information"), style = BodyMdMedium); Text(L.t("No podemos consultar los avisos ahora. Se actualizarán al conectar.", "We can't check alerts now. They'll update when connected."), style = BodySm, color = OnSurfaceVariant); TextButton(onClick = { servicio?.stop(); servicio?.start() }) { Text(L.t("Actualizar", "Refresh")) } }
                    vigentes.isEmpty() -> { Text(L.t("Sin avisos vigentes", "No current alerts"), style = BodyMdMedium); Text(L.t("No hay avisos confirmados para tus líneas ahora.", "No confirmed alerts for your lines right now."), style = BodySm, color = OnSurfaceVariant) }
                    else -> vigentes.forEach { aviso -> TextButton(onClick = { rutaInicial = aviso.routeId; abrir = true }, modifier = Modifier.fillMaxWidth()) { Text((rutas.firstOrNull { it.id == aviso.routeId }?.lineaConLetra ?: aviso.routeId) + " · " + aviso.place, modifier = Modifier.weight(1f)); Icon(Icons.Filled.ChevronRight, null) } }
                }
            }
        }
    }
    if (abrir) RouteChangesSheet(onDismiss = { abrir = false }, rutaInicial = rutaInicial)
}
