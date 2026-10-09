package com.example.rutautpnative.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DepartureBoard
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rutautpnative.data.geo.PolylineMatching
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.ParaderoGTFS
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.data.ubicacion.LocationService
import com.example.rutautpnative.ui.components.formatoDistancia
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.screens.mapa.PulsingUserMarker
import com.example.rutautpnative.ui.theme.*import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil

//----Navegacion activa (puerto del NavegacionRutaView.swift + ViewModel)----
// Sigue al USUARIO viajando sobre el recorrido REAL de la linea (aqui no se
// rastrea al bus: el que viaja es el usuario). Reglas del iOS:
//   - Proyeccion sobre el shape con umbral 60 m; fueraDeRuta(m) con metros al trazado.
//   - Fixes filtrados: precision <= 65 m y antiguedad < 30 s.
//   - Anti-jitter: el progreso NO retrocede salvo caida > 0.03 (re-embarque atras).
//   - Final: restante <= 25 m y a <= 35 m del ultimo punto.
//   - Cerca del destino: < 180 m restantes.
//   - Minutos restantes = duracion GTFS x (1 - progreso).
//   - Demo ~72 s: tick 80 ms, +1/900 por tick, interpolando sobre el shape.
//
// El panel es oscuro (#141414, como el iOS que fuerza .dark en su ventana);
// los negocios demo ya no viven aqui (pertenecen a TrackingDemo, Fase 10:
// ver NegociosDemoComponents.kt).

private enum class EstadoNav { ESPERANDO_GPS, SIN_PERMISO, EN_RUTA, FUERA_RUTA, CERCA_DESTINO, FINALIZADO }

// Paleta oscura: importada de PaletaOscura.kt (compartida, antes duplicada).
private val PanelOscuroTexto = TextoOscuro
private val PanelOscuroTextoVariante = TextoOscuroVariante

private class NavegacionVM(val ruta: RutaGTFS) {

    val shape = PolylineMatching.decimate(ruta.shape, 240)
    val acumulados = PolylineMatching.distanciasAcumuladas(shape)
    val totalM = acumulados.lastOrNull() ?: 0.0

    // Paraderos proyectados sobre el shape (umbral 80 m, como el iOS), por fraccion.
    data class ParaderoProy(val paradero: ParaderoGTFS, val fraccion: Double)

    val paraderosProy: List<ParaderoProy> = ruta.paraderos.mapNotNull { p ->
        val proy = PolylineMatching.proyectarEnShape(p.coordinate, shape) ?: return@mapNotNull null
        if (proy.distanciaM <= 80.0) ParaderoProy(p, proy.fraccion) else null
    }.sortedBy { it.fraccion }

    //----Estado observable----
    var estado by mutableStateOf(EstadoNav.ESPERANDO_GPS)
    var posicion by mutableStateOf<LatLng?>(null)
        private set
    var progreso by mutableStateOf(0.0)
        private set
    var metrosFuera by mutableStateOf(0.0)
        private set
    var demoActivo by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var gpsJob: Job? = null
    private var demoJob: Job? = null

    //----GPS real----
    fun iniciarGPS() {
        if (gpsJob?.isActive == true) return
        gpsJob = scope.launch {
            LocationService.currentLocation().collect { loc -> procesarFix(loc) }
        }
    }

    private fun procesarFix(loc: android.location.Location) {
        if (demoActivo) return   // el modo demo manda mientras este activo
        // Filtros del iOS: precision y frescura del fix.
        if (!loc.hasAccuracy() || loc.accuracy > 65f) return
        val antiguedadMs = System.currentTimeMillis() - loc.time
        if (antiguedadMs > 30_000) return
        aplicarPosicion(LatLng(loc.latitude, loc.longitude))
    }

    /// Nucleo: proyecta la posicion sobre el shape y actualiza estados.
    private fun aplicarPosicion(pos: LatLng) {
        posicion = pos
        val proy = PolylineMatching.proyectarEnShape(pos, shape)
        if (proy == null || proy.distanciaM > UMBRAL_RUTA_M) {
            estado = EstadoNav.FUERA_RUTA
            metrosFuera = proy?.distanciaM ?: 999.0
            return   // fuera de ruta: el progreso queda congelado
        }
        metrosFuera = 0.0
        val nuevo = proy.fraccion
        // Anti-jitter: pequenos retrocesos (GPS ruidoso) no mueven el progreso;
        // solo una caida > 0.03 (re-embarque atras) lo retrocede.
        if (!(nuevo < progreso && (progreso - nuevo) <= 0.03)) {
            progreso = nuevo
        }

        val restanteM = (1.0 - progreso) * totalM
        val distUltimo = GTFSRepository.distanciaMetros(pos, shape.last())
        estado = when {
            restanteM <= 25.0 && distUltimo <= 35.0 -> EstadoNav.FINALIZADO
            restanteM < 180.0 -> EstadoNav.CERCA_DESTINO
            else -> EstadoNav.EN_RUTA
        }
    }

    //----Demo (~72 s)----
    fun iniciarDemo() {
        detenerDemo()
        demoActivo = true
        progreso = 0.0
        demoJob = scope.launch {
            while (isActive) {
                delay(80)
                val nuevo = progreso + 1.0 / 900.0
                if (nuevo >= 1.0) {
                    aplicarPosicion(shape.last())
                    break
                }
                progreso = nuevo
                aplicarPosicion(puntoEnFraccion(nuevo))
            }
        }
    }

    fun detenerDemo() {
        demoJob?.cancel()
        demoJob = null
        demoActivo = false
    }

    /// Interpolacion de la coordenada a una fraccion del shape (busqueda
    /// binaria sobre las distancias acumuladas).
    fun puntoEnFraccion(fraccion: Double): LatLng {
        val objetivo = fraccion.coerceIn(0.0, 1.0) * totalM
        var lo = 0
        var hi = acumulados.size - 1
        while (lo < hi - 1) {
            val mid = (lo + hi) / 2
            if (acumulados[mid] <= objetivo) lo = mid else hi = mid
        }
        val a = acumulados[lo]
        val b = acumulados[lo + 1]
        val f = if (b > a) ((objetivo - a) / (b - a)).coerceIn(0.0, 1.0) else 0.0
        return LatLng(
            shape[lo].latitude + (shape[lo + 1].latitude - shape[lo].latitude) * f,
            shape[lo].longitude + (shape[lo + 1].longitude - shape[lo].longitude) * f
        )
    }

    //----Derivados para la UI----
    val restanteM: Double get() = (1.0 - progreso) * totalM
    val paraderosRestantes: Int get() = paraderosProy.count { it.fraccion > progreso }
    val minutosRestantes: Int
        get() = if (ruta.duracionMin > 0) ceil(ruta.duracionMin * (1.0 - progreso)).toInt() else 0
    val proximoParadero: ParaderoProy? get() = paraderosProy.firstOrNull { it.fraccion > progreso }

    fun detener() {
        gpsJob?.cancel()
        gpsJob = null
        detenerDemo()
        // Cancela el scope entero: sin esto, el CoroutineScope sobrevive a
        // la pantalla y retiene el SupervisorJob (fuga de hilo Main).
        scope.cancel()
    }

    companion object {
        const val UMBRAL_RUTA_M = 60.0
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NavegacionScreen(ruta: RutaGTFS, onFinish: () -> Unit) {
    val context = LocalContext.current
    val vm = remember(ruta.id) { NavegacionVM(ruta) }
    val permisoUbicacion = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    // GPS en cuanto haya permiso; al salir, el conteo de consumidores del
    // LocationService apaga el hardware.
    LaunchedEffect(permisoUbicacion.status.isGranted) {
        if (permisoUbicacion.status.isGranted) {
            vm.estado = EstadoNav.ESPERANDO_GPS
            vm.iniciarGPS()
        } else {
            vm.estado = EstadoNav.SIN_PERMISO
        }
    }
    DisposableEffect(Unit) {
        onDispose { vm.detener() }
    }

    //----Camara----
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(ruta.shape.firstOrNull() ?: GTFSRepository.coordenadaUTP, 15f)
    }
    var seguirUsuario by remember { mutableStateOf(true) }
    var animandoCamara by remember { mutableStateOf(false) }

    // Encuadre inicial de toda la ruta (una sola vez).
    var encuadreInicialPendiente by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        if (encuadreInicialPendiente && ruta.shape.isNotEmpty()) {
            encuadreInicialPendiente = false
            val builder = LatLngBounds.builder()
            ruta.shape.forEach(builder::include)
            animandoCamara = true
            camera.animate(CameraUpdateFactory.newLatLngBounds(builder.build(), 140))
            animandoCamara = false
        }
    }

    // Seguimiento del usuario: se re-activa con el toggle y con cada fix
    // mientras nadie haya hecho pan manual.
    LaunchedEffect(vm.posicion, seguirUsuario) {
        val pos = vm.posicion
        if (seguirUsuario && pos != null) {
            animandoCamara = true
            camera.animate(CameraUpdateFactory.newLatLngZoom(pos, 17f))
            animandoCamara = false
        }
    }
    // Pan manual => se deja de seguir (la animacion propia no cuenta).
    LaunchedEffect(camera.isMoving) {
        if (camera.isMoving && !animandoCamara) seguirUsuario = false
    }

    Box(modifier = Modifier.fillMaxSize().background(PanelOscuro)) {

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
        ) {
            // Recorrido oficial.
            Polyline(points = vm.shape, color = ruta.color, width = 12f, zIndex = 2f)

            // Paraderos proyectados (tope 70, inicio y fin siempre), como el iOS.
            val visibles = remember(vm.paraderosProy) {
                if (vm.paraderosProy.size <= 70) vm.paraderosProy
                else {
                    val paso = (vm.paraderosProy.size - 1).toDouble() / 69
                    val intermedios = (0 until 69).map { i -> vm.paraderosProy[(i * paso).toInt().coerceAtMost(vm.paraderosProy.size - 2)] }
                    (intermedios + vm.paraderosProy.last()).distinctBy { it.paradero.id }
                }
            }
            visibles.forEachIndexed { i, pp ->
                val esInicio = i == 0
                val esFin = pp == visibles.last()
                MarkerComposable(
                    state = MarkerState(pp.paradero.coordinate),
                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                    title = pp.paradero.nombre
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (esInicio || esFin) 20.dp else 10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    esFin -> RojoPeligro
                                    esInicio -> VerdeExito
                                    else -> Color(0xFF37474F)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (esInicio) Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        if (esFin) Icon(Icons.Filled.Flag, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
            }

            // Posicion del usuario (real o demo).
            vm.posicion?.let { pos ->
                MarkerComposable(
                    state = MarkerState(pos),
                    title = if (vm.demoActivo) L.t("Tu posición · demo", "Your position · demo") else L.t("Tu posición", "Your position")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (vm.demoActivo) {
                            Text(
                                L.t("DEMO", "DEMO"),
                                style = androidx.compose.ui.text.TextStyle(fontSize = 8.sp, color = Color.White),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF1E88E5))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        PulsingUserMarker()
                    }
                }
            }
        }

        //----Barra superior----
        // El fondo oscuro va ANTES del statusBarsPadding: cubre tambien la
        // zona de la status bar (hora, senal), ahi no debe verse el mapa.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelOscuro)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (vm.demoActivo) L.t("SIMULACIÓN", "SIMULATION") else L.t("TU VIAJE", "YOUR TRIP"),
                    style = LabelCapsMd, color = PanelOscuroTextoVariante
                )
                Text(
                    "${ruta.linea} · ${ruta.empresa}",
                    style = HeadlineSm, color = PanelOscuroTexto, maxLines = 1
                )
            }
            // Chip Demo (verde cuando activo).
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (vm.demoActivo) VerdeExito else Color(0xFF26292C))
                    .clickable { if (vm.demoActivo) vm.detenerDemo() else vm.iniciarDemo() }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(L.t("Demo", "Demo"), style = LabelCapsMd, color = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            // Finalizar (rojo).
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(RojoPeligro)
                    .clickable { onFinish() }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(L.t("Finalizar", "End"), style = LabelCapsMd, color = Color.White)
            }
        }

        //----Panel inferior----
        // El fondo va ANTES del navigationBarsPadding: se extiende detras de
        // los botones del sistema (atras/inicio/recientes) y el contenido
        // queda por encima de ellos, sin taparse.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(PanelOscuro)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            // Instruccion principal por estado.
            val (icono, tintColor, instruccion, subtitulo) = when (vm.estado) {
                EstadoNav.SIN_PERMISO -> Quad(Icons.Filled.LocationOff, RojoPeligro,
                    L.t("Se necesita permiso de ubicación", "Location permission needed"), "")
                EstadoNav.ESPERANDO_GPS -> Quad(Icons.Filled.Schedule, PanelOscuroTextoVariante,
                    L.t("Esperando GPS…", "Waiting for GPS…"),
                    L.t("Muévete al aire libre para mejorar la señal.", "Move outdoors for a better signal."))
                EstadoNav.EN_RUTA -> Quad(Icons.Filled.NearMe, VerdeExito,
                    L.t("Estás en la ruta", "You're on the route"), subtituloProximo(vm))
                EstadoNav.FUERA_RUTA -> Quad(Icons.Filled.LocationOff, Color(0xFFFB8C00),
                    L.t("Fuera de la ruta", "Off the route"),
                    L.t("Estás a ${vm.metrosFuera.toInt()} m del recorrido. Acércate para continuar.", "You're ${vm.metrosFuera.toInt()} m from the route. Get closer to continue."))
                EstadoNav.CERCA_DESTINO -> Quad(Icons.Filled.DepartureBoard, VerdeExito,
                    L.t("Estás cerca de tu destino", "You're close to your destination"), subtituloProximo(vm))
                EstadoNav.FINALIZADO -> Quad(Icons.Filled.CheckCircle, VerdeExito,
                    L.t("Fin del recorrido", "End of the route"), "")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icono, null, tint = tintColor, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(instruccion, style = HeadlineSm, color = PanelOscuroTexto)
            }
            if (subtitulo.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(subtitulo, style = BodySm, color = PanelOscuroTextoVariante)
            }

            // Sin permiso: abrir ajustes del sistema.
            if (vm.estado == EstadoNav.SIN_PERMISO) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { permisoUbicacion.launchPermissionRequest() }) {
                        Text(L.t("Dar permiso", "Grant permission"), color = PanelOscuroTexto)
                    }
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    }) {
                        Text(L.t("Abrir Ajustes", "Open Settings"), color = PanelOscuroTexto)
                    }
                }
            }

            // Hasta <parada fin> + tarifa.
            Spacer(Modifier.height(12.dp))
            Row {
                Text(
                    L.t("Hasta", "To") + " ${ruta.paraderos.lastOrNull()?.nombre ?: ruta.recorrido}",
                    style = BodySm, color = PanelOscuroTexto, modifier = Modifier.weight(1f)
                )
                Text(ruta.precioTexto, style = BodySm, color = PanelOscuroTextoVariante)
            }

            // Barra de progreso con %.
            Spacer(Modifier.height(10.dp))
            val fraccion = animateFloatAsState(
                targetValue = (vm.progreso * 100).toFloat(),
                animationSpec = tween(300), label = "progreso"
            )
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF26292C))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraccion.value / 100f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(ruta.color)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${fraccion.value.toInt()}%",
                    style = LabelCapsSm, color = PanelOscuroTextoVariante
                )
            }

            // Stats: restante / por recorrer / paraderos.
            Spacer(Modifier.height(8.dp))
            Row {
                Text(
                    L.t("Restante: ", "Remaining: ") + "~${vm.minutosRestantes} min",
                    style = BodySm, color = PanelOscuroTexto, modifier = Modifier.weight(1f)
                )
                Text(
                    L.t("Por recorrer: ", "To go: ") + formatoDistancia(vm.restanteM),
                    style = BodySm, color = PanelOscuroTexto, modifier = Modifier.weight(1f)
                )
                Text(
                    L.t("Paraderos: ", "Stops: ") + "${vm.paraderosRestantes}",
                    style = BodySm, color = PanelOscuroTexto
                )
            }

            // Toggle: seguir toda la ruta <-> seguir al usuario.
            Spacer(Modifier.height(10.dp))
            val scopeCamara = rememberCoroutineScope()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF26292C))
                    .clickable {
                        if (seguirUsuario) {
                            // "Ver toda la ruta": encuadra el recorrido completo.
                            seguirUsuario = false
                            scopeCamara.launch {
                                val builder = LatLngBounds.builder()
                                ruta.shape.forEach(builder::include)
                                animandoCamara = true
                                camera.animate(CameraUpdateFactory.newLatLngBounds(builder.build(), 140))
                                animandoCamara = false
                            }
                        } else {
                            seguirUsuario = true   // el LaunchedEffect vuelve a seguir
                        }
                    }
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (seguirUsuario) Icons.Filled.LocationOn else Icons.Filled.MyLocation,
                        null, tint = PanelOscuroTextoVariante, modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (seguirUsuario) L.t("Ver toda la ruta", "View full route")
                        else L.t("Seguir mi ubicación", "Follow my location"),
                        style = BodyMdMedium, color = PanelOscuroTexto
                    )
                }
            }
        }

        //----Alerta de llegada----
        if (vm.estado == EstadoNav.FINALIZADO) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(24.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1D2022))
                        .padding(28.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = VerdeExito, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(14.dp))
                    Text(
                        L.t("Fin del recorrido", "End of the route"),
                        style = HeadlineMd, color = PanelOscuroTexto, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        L.t("Llegaste a", "You arrived at") + " ${ruta.paraderos.lastOrNull()?.nombre ?: ruta.recorrido}",
                        style = BodySm, color = PanelOscuroTextoVariante, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = onFinish,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VerdeExito)
                    ) {
                        Text(L.t("Terminar", "Finish"), style = HeadlineSm, color = Color.White)
                    }
                }
            }
        }
    }
}

private data class Quad(
    val icono: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val titulo: String,
    val subtitulo: String
)

private fun subtituloProximo(vm: NavegacionVM): String {
    val proximo = vm.proximoParadero ?: return L.t("Próxima parada: destino", "Next stop: destination")
    val metros = ((proximo.fraccion - vm.progreso) * vm.totalM).toInt().coerceAtLeast(0)
    return L.t("Próxima parada: ", "Next stop: ") + proximo.paradero.nombre + " · ${formatoDistancia(metros.toDouble())}"
}
