package com.example.rutautpnative.ui.screens

import android.graphics.Bitmap
import android.view.accessibility.AccessibilityManager
import android.content.Context
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.data.*
import com.example.rutautpnative.data.gtfs.TransporteApp
import com.example.rutautpnative.data.imagenes.*
import com.example.rutautpnative.data.negocios.*
import com.example.rutautpnative.data.notificaciones.NotificacionesService
import com.example.rutautpnative.data.senias.*
import com.example.rutautpnative.model.Negocio
import com.example.rutautpnative.navigation.*
import com.example.rutautpnative.ui.components.BottomNavBar
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilScreen(router: AppRouter) {
    val contexto = LocalContext.current
    val empresa = TematicaEmpresaStore.seleccion
    val scope = rememberCoroutineScope()
    val nombre = "Joaquín Díaz"
    val modoSenias by SeniasPrefs.observarActivo().collectAsState(initial = false)
    val foto by produceState<Bitmap?>(null, ProfileImageStore.revision) { value = ProfileImageStore.cargar(contexto) }
    val carnetGuardado by produceState(false, CarnetImageStore.revision, empresa.id) { value = CarnetImageStore.existe(contexto, empresa.id) }
    val cupones by produceState<List<Negocio>>(emptyList()) {
        CuponesStore.observarIds().collect { ids -> value = NegociosService.todos().filter { it.cupon != null && it.id in ids }.sortedBy { it.nombre } }
    }
    var conexionTransporte by remember { mutableStateOf(false) }
    var idiomas by remember { mutableStateOf(false) }
    var notificaciones by remember { mutableStateOf(false) }
    var datos by remember { mutableStateOf(false) }
    var credenciales by remember { mutableStateOf(false) }
    var respaldos by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(false) }
    var accesibilidad by remember { mutableStateOf(false) }
    var recargar by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf(false) }
    var tarjetas by remember { mutableStateOf(false) }
    var fotoCarnet by remember { mutableStateOf(false) }
    var digital by remember { mutableStateOf(false) }
    var detalleNegocio by remember { mutableStateOf<Negocio?>(null) }
    var ubicacionInfo by remember { mutableStateOf(false) }
    var ampliarUbicacion by remember { mutableStateOf(false) }
    val talkback = (contexto.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true
    val rol = if (empresa == TematicaEmpresa.UTP) L.t("ESTUDIANTE UTP", "UTP STUDENT") else L.t("COLABORADOR · ", "EMPLOYEE · ") + empresa.nombre.uppercase()

    Scaffold(containerColor = AppBackground, contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = { BottomNavBar(router) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            PerfilIdentidad(nombre, rol, foto, carnetGuardado && PreferenciasVisualesPerfilStore.visible(CarnetPerfil.FOTO, empresa)) { datos = true }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FlowRow(horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(L.t("Mi billetera", "My wallet"), style = HeadlineSm, color = OnSurface, modifier = Modifier.semantics { heading() })
                    TextButton(onClick = { credenciales = true }) { Icon(Icons.Filled.Tune, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(L.t("Credenciales", "Credentials")) }
                }
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(PrimaryFill)) { MonederoCard({ recargar = true }, { qr = true }) }
                GrupoPerfil {
                    if (PreferenciasVisualesPerfilStore.visible(CarnetPerfil.FOTO, empresa)) {
                        FilaPerfil(Icons.Filled.Badge, CarnetPerfil.FOTO.titulo(empresa), if (carnetGuardado) L.t("Ver foto", "View photo") else L.t("Añadir foto", "Add photo")) { fotoCarnet = true }
                        SeparadorPerfil()
                    }
                    if (PreferenciasVisualesPerfilStore.visible(CarnetPerfil.DIGITAL, empresa)) {
                        FilaPerfil(Icons.Filled.School, CarnetPerfil.DIGITAL.titulo(empresa), L.t("Muestra · sin validez", "Sample · not valid")) { digital = true }
                        SeparadorPerfil()
                    }
                    FilaPerfil(Icons.Filled.CreditCard, L.t("Mis tarjetas", "My cards"), L.t("Referencias locales · sin pagos", "Local references · no payments")) { tarjetas = true }
                }
            }
            MisCuponesSection(cupones, { router.navigate(AppScreen.MapaPrincipal) }, { negocio -> scope.launch { CuponesStore.alternarCupon(negocio) } }, { detalleNegocio = it })

            if (TransporteApp.busesUTPDisponibles) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(L.t("Transporte", "Transport"), style = HeadlineSm, color = OnSurface)
                GrupoPerfil { SwitchPerfil(Icons.Filled.DirectionsBus, L.t("Buses de la UTP", "UTP buses"), TransporteApp.busesUTPActivos) { TransporteApp.establecerBusesUTP(it) } }
                Text(if (TransporteApp.rutasUTPPendientes) TransporteApp.mensajePendiente else L.t("Los viajes en curso terminan al cambiar de modo.", "Current trips end when you switch modes."), style = BodySm, color = OnSurfaceVariant)
            }

            SeccionPerfil(L.t("Preferencias", "Preferences")) {
                FilaPerfil(Icons.Filled.Language, L.t("Idioma", "Language"), L.nombreIdioma) { idiomas = true }
                SeparadorPerfil()
                FilaPerfil(Icons.Filled.Notifications, L.t("Notificaciones", "Notifications"), if (!PreferenciasPerfilStore.notificaciones) L.t("Desactivadas", "Off") else if (!NotificacionesService.permitido(contexto)) L.t("Sin permiso", "Permission needed") else L.t("Activadas", "On")) { notificaciones = true }
                SeparadorPerfil()
                FilaPerfil(Icons.Filled.CloudSync, L.t("Conexión de transporte", "Transport connection"),
                    com.example.rutautpnative.data.conexion.ConexionInstalacionStore.revision.let {
                        if (com.example.rutautpnative.data.conexion.ConexionInstalacionStore.receptor() == null) L.t("Sin cuenta en este teléfono", "No account on this phone") else L.t("Cuenta guardada", "Account saved")
                    }) { conexionTransporte = true }
                SeparadorPerfil()
                FilaPerfil(Icons.Filled.CloudOff, L.t("Modo offline", "Offline mode")) { offline = true }
            }
            SeccionPerfil(L.t("Accesibilidad", "Accessibility")) {
                SwitchPerfil(Icons.Filled.SignLanguage, L.t("Modo Señas", "Sign Language Mode"), modoSenias) { activo ->
                    scope.launch { SeniasPrefs.establecerActivo(activo); if (activo) SeniasOverlay.mostrarActivacionDelModo() else SeniasOverlay.cancelarPorNavegacion() }
                }
                SeparadorPerfil()
                FilaPerfil(Icons.Filled.RecordVoiceOver, "TalkBack", if (talkback) L.t("Activado", "On") else L.t("Desactivado", "Off")) { accesibilidad = true }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SeccionPerfil(L.t("Privacidad y datos", "Privacy and data")) {
                    FilaPerfil(Icons.Filled.Person, L.t("Datos personales", "Personal details")) { datos = true }
                    SeparadorPerfil()
                    FilaPerfil(Icons.Filled.Archive, L.t("Respaldos guardados", "Saved backups")) { respaldos = true }
                    SeparadorPerfil()
                    SwitchPerfil(Icons.Filled.LocationOn, L.t("Preferencia de ubicación", "Location preference"), PreferenciasPerfilStore.compartirUbicacion) { PreferenciasPerfilStore.establecerCompartirUbicacion(it); ubicacionInfo = true }
                }
                TextButton(onClick = { ampliarUbicacion = !ampliarUbicacion }) { Text(L.t("Sobre la ubicación", "About location")); Icon(if (ampliarUbicacion) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null) }
                if (ampliarUbicacion) Text(L.t("La preferencia se guarda en este dispositivo. No comparte tu ubicación con otros usuarios ni desactiva el GPS del mapa.", "This preference is saved on this device. It does not share your location with other users or turn off map GPS."), style = BodySm, color = OnSurfaceVariant)
            }
        }
    }
    if (conexionTransporte) ConexionTransporteScreen { conexionTransporte = false }
    if (idiomas) com.example.rutautpnative.ui.components.SelectorIdiomaSheet { idiomas = false }
    if (credenciales) ModalBottomSheet(onDismissRequest = { credenciales = false }, containerColor = AppSurface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(L.t("Credenciales", "Credentials"), style = HeadlineMd)
            Text(empresa.nombre, style = BodyMdMedium)
            CarnetPerfil.entries.forEach { carnet -> SwitchPerfil(if (carnet == CarnetPerfil.FOTO) Icons.Filled.Badge else Icons.Filled.School, CarnetPerfil.entries.first { it == carnet }.titulo(empresa), PreferenciasVisualesPerfilStore.visible(carnet, empresa)) { PreferenciasVisualesPerfilStore.establecer(it, carnet, empresa) } }
            Text(L.t("Elige qué carnés aparecen en tu billetera. Ocultarlos conserva tus fotos.", "Choose which cards appear in your wallet. Hiding cards keeps your photos."), style = BodySm, color = OnSurfaceVariant)
        }
    }
    if (notificaciones) NotificacionesScreen { notificaciones = false }
    if (datos) DatosPersonalesSheet { datos = false }
    if (respaldos) RespaldosRecuperacionSheet { respaldos = false }
    if (offline) OfflineMapSheet { offline = false }
    if (accesibilidad) AyudaAccesibilidadSheet { accesibilidad = false }
    if (recargar) RecargarSaldoSheet { recargar = false }
    if (qr) QRPasajeSheet { qr = false }
    if (tarjetas) MetodosPagoSheet { tarjetas = false }
    if (digital) CarneDigitalScreen(nombre, empresa) { digital = false }
    if (fotoCarnet) VisorFotoCarnet(empresa) { fotoCarnet = false }
    detalleNegocio?.let { NegocioDetailCard(it, com.google.android.gms.maps.model.LatLng(it.latitud, it.longitud), onCerrar = { detalleNegocio = null }) }
    if (ubicacionInfo) AlertDialog(onDismissRequest = { ubicacionInfo = false }, title = { Text(L.t("Preferencia guardada", "Preference saved")) }, text = { Text(L.t("Este ajuste no cambia los permisos de GPS. El mapa y la contribución de viajes se controlan por separado.", "This setting does not change GPS permissions. Map location and trip contributions are controlled separately.")) }, confirmButton = { TextButton(onClick = { ubicacionInfo = false }) { Text(L.t("Entendido", "Got it")) } })
}

@Composable
private fun GrupoPerfil(contenido: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = SuperficieAgrupada) { Column(Modifier.fillMaxWidth(), content = contenido) }
}

@Composable
private fun SeccionPerfil(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(titulo, style = HeadlineSm, color = OnSurface, modifier = Modifier.semantics { heading() })
        GrupoPerfil(contenido)
    }
}

@Composable
private fun SeparadorPerfil() { HorizontalDivider(Modifier.padding(start = 56.dp), color = OutlineVariant.copy(alpha = .3f)) }

@Composable
private fun FilaPerfil(icono: ImageVector, titulo: String, detalle: String? = null, accion: () -> Unit) {
    Surface(onClick = accion, color = SuperficieAgrupada) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icono, null, tint = AcentoInterfaz, modifier = Modifier.size(23.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(titulo, style = BodyMdMedium, color = OnSurface); detalle?.let { Text(it, style = BodySm, color = OnSurfaceVariant) } }
            Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SwitchPerfil(icono: ImageVector, titulo: String, valor: Boolean, accion: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icono, null, tint = AcentoInterfaz, modifier = Modifier.size(23.dp))
        Text(titulo, style = BodyMdMedium, color = OnSurface, modifier = Modifier.weight(1f))
        Switch(valor, accion)
    }
}
