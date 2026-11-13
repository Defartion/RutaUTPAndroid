package com.example.rutautpnative.ui.screens

import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.rutautpnative.ui.components.AlEstarVisible
import com.example.rutautpnative.navigation.ViajesActivos
import com.example.rutautpnative.navigation.TipoViaje
import android.Manifest
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.rutautpnative.data.directions.DirectionsService
import com.example.rutautpnative.data.geo.PolylineMatching
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.ParaderoGTFS
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.data.negocios.NegociosService
import com.example.rutautpnative.data.places.PlacesService
import com.example.rutautpnative.data.routing.TransitPlanner
import com.example.rutautpnative.data.tracking.MQTTTrackingProvider
import com.example.rutautpnative.data.tracking.SimulatedTrackingProvider
import com.example.rutautpnative.data.tracking.TrackingDemoPrefs
import com.example.rutautpnative.data.tracking.TripSession
import com.example.rutautpnative.data.tracking.EstadoViaje
import com.example.rutautpnative.data.tracking.PuntoRecorrido
import com.example.rutautpnative.data.tracking.TrackingProviderFactory
import com.example.rutautpnative.data.tracking.VehiclePosition
import com.example.rutautpnative.data.ubicacion.LocationService
import com.example.rutautpnative.model.Negocio
import com.example.rutautpnative.navigation.AppRouter
import com.example.rutautpnative.navigation.AppScreen
import com.example.rutautpnative.ui.components.formatoDistancia
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.screens.mapa.BusMarker3D
import com.example.rutautpnative.ui.screens.mapa.BusMarkerAncla
import com.example.rutautpnative.ui.screens.mapa.PulsingUserMarker
import com.example.rutautpnative.ui.screens.mapa.indiceFotogramaBus
import com.example.rutautpnative.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
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
import kotlinx.coroutines.ensureActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

//----Pantalla TrackingDemo (puerto del RouteTrackingDemoView.swift)----
// La demo integradora: viaje puerta-a-puerta (caminata -> paradero -> micro ->
// bajada -> caminata) con GPS real o simulado, vehiculos del provider,
// negocios por viewport y sesion de viaje con resumen final.
//
// Entrada: item "Tracking Demo" del drawer (iOS lo marca TEMPORAL).

internal enum class EstadoDemo { ESPERANDO_GPS, SIN_PERMISO, LISTO, EN_RUTA, FUERA_RUTA, CERCA_DESTINO, FINALIZADO }

// Las fases del viaje son las del TransitPlanner (JourneyLeg del iOS): a pie
// a la subida, primer micro, transbordo, segundo micro y caminata final.

//----Ruta combinada del plan (caminata 1 + bus 1 + [transbordo] + caminata 2)----
private class RutaCombinada(val plan: TransitPlanner.Plan) {
    val puntos: List<LatLng> = TransitPlanner.puntosCombinados(plan)
    val acumulados = PolylineMatching.distanciasAcumuladas(puntos)
    val totalM = acumulados.lastOrNull() ?: 0.0

    fun puntoEn(fraccion: Double): LatLng =
        PolylineMatching.puntoEnFraccion(puntos, acumulados, fraccion, totalM)

    fun faseEn(fraccion: Double): TransitPlanner.TramoViaje = plan.tramoEn(fraccion * totalM)
}

// Paleta oscura: importada de PaletaOscura.kt (compartida, antes duplicada).
private val ColorOscuro = PanelOscuro
private val TextoOscuro = com.example.rutautpnative.ui.theme.TextoOscuro
private val TextoOscuroVar = TextoOscuroVariante
private val Verde = VerdeExito
private val Naranja = NaranjaAdvertencia

internal class TrackingDemoVM(
    private val scope: CoroutineScope,
    private val ajustes: AjustesTracking,
    private val cargarRutas: suspend () -> List<RutaGTFS> = { GTFSRepository.rutas() },
    private val caminatas: TransitPlanner.CaminatasReales = TransitPlanner.CaminatasReales { a, b -> TransitPlanner.caminataReal(a, b) }
) {
    //----Fuente de vehiculos: SIEMPRE SIMULADA en la demo (paridad iOS)----
    // El RouteTrackingViewModel del iOS recibe SimulatedTrackingProvider() por
    // defecto: esta pantalla es una DEMO. La flota REAL del broker pertenece
    // al mapa principal (Fase 6), no aqui.
    val esFuenteReal: Boolean = false
    val providerSimulado: SimulatedTrackingProvider = SimulatedTrackingProvider()
    val providerReal: MQTTTrackingProvider? = null

    //----Preferencias del demo (persistidas, claves del iOS)----
    var radioParadero by mutableStateOf(ajustes.radio)
        private set
    var multiplicadorVelocidad by mutableStateOf(ajustes.velocidad)
        private set

    fun cambiarRadio(radio: Double) {
        radioParadero = radio
        ajustes.guardarRadio(radio)
        // Cambiar el radio invalida el ultimo error (el usuario esta probando
        // otro valor; el mensaje ya no corresponde).
        mensajeErrorRuta = null
    }

    fun cambiarVelocidad(mult: Int) {
        multiplicadorVelocidad = mult
        ajustes.guardarVelocidad(mult)
    }

    //----Estado del viaje----
    var estado by mutableStateOf(EstadoDemo.ESPERANDO_GPS)
    var fase by mutableStateOf(TransitPlanner.TramoViaje.CAMINANDO_A_SUBIDA)
    var posicion by mutableStateOf<LatLng?>(null)
    var progreso by mutableStateOf(0.0)
    var recalculando by mutableStateOf(false)
        private set

    var destinoTitulo by mutableStateOf<String?>(null)
    var destino by mutableStateOf<LatLng?>(null)
    var itinerario by mutableStateOf<TransitPlanner.Plan?>(null)
        private set
    private var rutaCombinada: RutaCombinada? = null
    var caminataAproximada by mutableStateOf(false)

    //----Feedback de cálculo (antes: silencio total cuando no había ruta)----
    var calculandoRuta by mutableStateOf(false)
        private set
    var mensajeErrorRuta by mutableStateOf<String?>(null)
        private set

    var sesion: TripSession? = null
        private set

    //----Vehiculos del provider----
    var vehiculos by mutableStateOf<List<VehiclePosition>>(emptyList())
        private set
    var vehiculoSeleccionado by mutableStateOf<VehiclePosition?>(null)

    //----Negocios por viewport----
    var negocios by mutableStateOf<List<Negocio>>(emptyList())
        private set
    var negocioSeleccionado by mutableStateOf<Negocio?>(null)
    private var centroNegocios: LatLng? = null

    //----Paradero cercano (sin viaje)----
    var paraderoCercano by mutableStateOf<ParaderoGTFS?>(null)
    var paraderoCercanoM by mutableStateOf(0.0)

    //----GPS / demo / calculo----
    private var gpsJob: Job? = null
    private var demoJob: Job? = null
    private var calcJob: Job? = null
    private var fixesFueraConsecutivos = 0
    private var ultimoRecalculoMs = 0L

    fun iniciarGPS() {
        if (gpsJob?.isActive == true) return
        gpsJob = scope.launch {
            LocationService.currentLocation().collect { loc -> procesarFix(loc) }
        }
    }

    private fun procesarFix(loc: android.location.Location) {
        if (demoJob?.isActive == true) return   // demo manda
        if (!loc.hasAccuracy() || loc.accuracy > 65f) return
        if (System.currentTimeMillis() - loc.time > 30_000) return
        aplicarPosicion(LatLng(loc.latitude, loc.longitude), heading = if (loc.hasBearing()) loc.bearing.toDouble() else -1.0)
    }

    /// Nucleo compartido por GPS real y demo: proyecta sobre la ruta combinada
    /// y actualiza fase/estados/sesion.
    private fun aplicarPosicion(pos: LatLng, heading: Double) {
        posicion = pos
        val ruta = rutaCombinada
        if (ruta == null) {
            // Sin viaje: estado LISTO y paradero cercano refrescado.
            if (estado != EstadoDemo.SIN_PERMISO) estado = EstadoDemo.LISTO
            refrescarParaderoCercano(pos)
            return
        }
        val proy = PolylineMatching.proyectarEnShape(pos, ruta.puntos)
        if (proy == null || proy.distanciaM > 60.0) {
            // Fuera de la ruta combinada.
            estado = EstadoDemo.FUERA_RUTA
            if (demoJob?.isActive != true && !recalculando) {
                fixesFueraConsecutivos++
                // 3 fixes seguidos fuera + cooldown 20 s (shouldRecalculate iOS).
                if (fixesFueraConsecutivos >= 3 &&
                    System.currentTimeMillis() - ultimoRecalculoMs > 20_000
                ) {
                    recalcular()
                }
            }
            return
        }
        fixesFueraConsecutivos = 0
        val nuevo = proy.fraccion
        if (!(nuevo < progreso && (progreso - nuevo) <= 0.03)) progreso = nuevo

        // Fase del viaje segun los metros recorridos del plan (journeyLeg del
        // iOS): caminata -> primer micro -> transbordo -> segundo micro.
        fase = ruta.faseEn(progreso)

        val restanteM = (1.0 - progreso) * ruta.totalM
        estado = when {
            restanteM <= 25.0 -> EstadoDemo.FINALIZADO
            restanteM < 180.0 -> EstadoDemo.CERCA_DESTINO
            else -> EstadoDemo.EN_RUTA
        }

        // TripSession: muestreo con separacion minima de 3 m (iOS idem).
        val ses = sesion
        if (ses != null && ses.estado == EstadoViaje.EN_PROGRESO) {
            val ultimo = ses.puntosRecorridos.lastOrNull()
            val lejos = ultimo == null || GTFSRepository.distanciaMetros(
                LatLng(ultimo.lat, ultimo.lon), pos
            ) >= 3.0
            if (lejos) {
                ses.puntosRecorridos.add(
                    PuntoRecorrido(pos.latitude, pos.longitude, heading, 0.0, System.currentTimeMillis() / 1000.0)
                )
            }
        }

        if (estado == EstadoDemo.FINALIZADO) finalizarSesion()
    }

    private fun refrescarParaderoCercano(pos: LatLng) {
        scope.launch {
            val rutas = cargarRutas()
            var mejor: ParaderoGTFS? = null
            var mejorD = Double.MAX_VALUE
            for (ruta in rutas) {
                for (p in ruta.paraderos) {
                    val d = GTFSRepository.distanciaMetros(p.coordinate, pos)
                    if (d < mejorD) { mejorD = d; mejor = p }
                }
            }
            paraderoCercano = mejor
            paraderoCercanoM = mejorD
        }
    }

    //----Inicio / recalculo / detencion del viaje----

    fun iniciarViaje(titulo: String, destino: LatLng): Job? {
        destinoTitulo = titulo
        this.destino = destino
        val origen = posicion
        if (origen == null) { estado = EstadoDemo.ESPERANDO_GPS; return null }
        return calcularRuta(origen, destino, esRecalculo = false)
    }

    private fun calcularRuta(origen: LatLng, destino: LatLng, esRecalculo: Boolean): Job {
        calcJob?.cancel()
        if (esRecalculo) recalculando = true
        calculandoRuta = true
        mensajeErrorRuta = null
        calcJob = scope.launch {
            try {
                val rutas = cargarRutas()
                val plan = TransitPlanner.plan(origen, destino, rutas, radioParadero, caminatas)
                coroutineContext.ensureActive()
                if (plan != null) {
                    ViajesActivos.estado.actualizar(TipoViaje.TRACKING, true)
                    itinerario = plan
                    caminataAproximada = plan.caminataAproximada
                    rutaCombinada = RutaCombinada(plan)
                    if (!esRecalculo) progreso = 0.0
                    fixesFueraConsecutivos = 0
                    fase = TransitPlanner.TramoViaje.CAMINANDO_A_SUBIDA
                    mensajeErrorRuta = null

                    // Sesion de viaje nueva (solo al iniciar, no al recalcular).
                    if (!esRecalculo) {
                        sesion = TripSession(
                            linea = plan.ruta.linea,
                            empresa = plan.ruta.empresa,
                            origen = PuntoRecorrido(origen.latitude, origen.longitude, -1.0, 0.0, System.currentTimeMillis() / 1000.0),
                            destinoLat = destino.latitude,
                            destinoLon = destino.longitude,
                            startedAt = System.currentTimeMillis() / 1000.0
                        )
                        estado = EstadoDemo.EN_RUTA
                    }
                } else if (!esRecalculo) {
                    // SIN RUTA: mensaje claro con el radio actual (el iOS muestra
                    // "No encontramos una ruta directa ni con un transbordo...").
                    // Compara contra el MAYOR radio del selector: con el tope
                    // puesto, la unica sugerencia que queda es otro destino.
                    estado = EstadoDemo.LISTO
                    itinerario = null
                    rutaCombinada = null
                    val maximo = TrackingDemoPrefs.radiosDisponibles.max()
                    val sugerencia = if (radioParadero < maximo)
                        L.t("Prueba un radio mayor u otro destino.", "Try a larger radius or another destination.")
                    else
                        L.t("Prueba otro destino.", "Try another destination.")
                    mensajeErrorRuta = L.t(
                        "No encontramos una ruta directa ni con un transbordo con paraderos a menos de ${radioParadero.toInt()} m de ambos extremos. ",
                        "No direct or one-transfer route has stops within ${radioParadero.toInt()} m of both ends. "
                    ) + sugerencia
                }
            } finally {
                if (kotlinx.coroutines.currentCoroutineContext().isActive) calculandoRuta = false
                if (esRecalculo && kotlinx.coroutines.currentCoroutineContext().isActive) {
                    recalculando = false
                    ultimoRecalculoMs = System.currentTimeMillis()
                }
            }
        }
        return checkNotNull(calcJob)
    }

    /// Recalculo tras desvio sostenido: replanifica desde la posicion actual.
    private fun recalcular() {
        val pos = posicion ?: return
        val dest = destino ?: return
        sesion?.estado = EstadoViaje.RECALCULANDO
        calcularRuta(pos, dest, esRecalculo = true)
    }

    private fun finalizarSesion() {
        // El resumen sigue perteneciendo al viaje hasta que el usuario lo cierre.
        val ses = sesion ?: return
        ses.estado = EstadoViaje.COMPLETADO
        ses.endedAt = System.currentTimeMillis() / 1000.0
        detenerDemo()
    }

    fun detenerViaje(cancelado: Boolean = true) {
        calcJob?.cancel()
        calculandoRuta = false
        ViajesActivos.estado.actualizar(TipoViaje.TRACKING, false)
        detenerDemo()
        val ses = sesion
        if (ses != null && ses.estado == EstadoViaje.EN_PROGRESO) {
            ses.estado = if (cancelado) EstadoViaje.CANCELADO else EstadoViaje.COMPLETADO
            ses.endedAt = System.currentTimeMillis() / 1000.0
        }
        itinerario = null
        rutaCombinada = null
        sesion = null
        progreso = 0.0
        estado = EstadoDemo.LISTO
    }

    //----Demo: avance por METROS reales, velocidad de la fase x multiplicador----

    fun iniciarDemo() {
        val ruta = rutaCombinada ?: return
        val plan = itinerario ?: return
        detenerDemo()
        demoJob = scope.launch {
            while (isActive) {
                delay(80)
                // Velocidad del tramo actual (iOS idem): a pie 1.4 m/s, cada
                // micro a la velocidad derivada de SU linea del feed.
                val velocidadBase = when (fase) {
                    TransitPlanner.TramoViaje.EN_BUS -> plan.busVelocidadMS
                    TransitPlanner.TramoViaje.EN_SEGUNDO_BUS -> plan.transbordo?.busVelocidadMS ?: 6.0
                    else -> 1.4
                }
                val avance = velocidadBase * multiplicadorVelocidad * 0.08   // 80 ms
                val nuevo = progreso + avance / ruta.totalM
                if (nuevo >= 1.0) {
                    progreso = 1.0
                    aplicarPosicion(destino ?: ruta.puntos.last(), heading = -1.0)
                    break
                }
                progreso = nuevo
                val pos = ruta.puntoEn(nuevo)
                // Rumbo REAL del tramo donde cayo la posicion (el heading
                // viaja al TripSession para el resumen; antes siempre era 0.0
                // pese a calcular la proyeccion).
                val heading = PolylineMatching.proyectarEnShape(pos, ruta.puntos)?.let { proy ->
                    val idx = proy.segmento
                    if (idx < ruta.puntos.size - 1) {
                        PolylineMatching.headingDegrees(ruta.puntos[idx], ruta.puntos[idx + 1])
                    } else -1.0
                } ?: -1.0
                aplicarPosicion(pos, heading = heading)
            }
        }
    }

    fun detenerDemo() {
        demoJob?.cancel()
        demoJob = null
    }

    val demoActivo: Boolean get() = demoJob?.isActive == true

    //----Vehiculos del provider----

    // Catalogo para resolver empresa/color de linea por route_id (route_id es
    // unico, `linea` no).
    private var catalogo: Map<String, RutaGTFS> = emptyList<RutaGTFS>().associateBy { it.id }

    init {
        scope.launch {
            catalogo = cargarRutas().associateBy { it.id }
        }
    }

    fun colorDeLinea(routeId: String): Color = catalogo[routeId]?.color ?: AppPrimary

    fun iniciarVehiculos(ancla: LatLng, rutaPreferida: String? = null) {
        anclaFlota = ancla; rutaFlota = rutaPreferida
        if (!pantallaVisible) return
        // La flota nace alrededor del ANCLA (la posicion del usuario), no de
        // un punto fijo: las lineas mas cercanas a donde este el pasajero.
        providerSimulado.follow(rutaPreferida, ancla)
        providerSimulado.start()
        // Un unico colector: iniciarVehiculos se llama de nuevo al arrancar
        // viaje (follow con la linea preferida) y no debe duplicar suscripciones.
        if (collectJob?.isActive != true) {
            collectJob = scope.launch {
                providerSimulado.posiciones.collect { vehiculos = it }
            }
        }
    }

    //----Negocios por viewport----

    fun refrescarNegocios(centro: LatLng) {
        val anterior = centroNegocios
        if (anterior != null && GTFSRepository.distanciaMetros(anterior, centro) < 120.0) return
        centroNegocios = centro
        scope.launch {
            negocios = NegociosService.distribuidos(centro, 2500.0, 14)
        }
    }

    //----Derivados para la UI----

    val planActual: TransitPlanner.Plan? get() = itinerario
    val conViaje: Boolean get() = rutaCombinada != null

    val restanteM: Double get() = rutaCombinada?.let { (1.0 - progreso) * it.totalM } ?: 0.0

    val minutosRestantes: Int
        get() {
            val plan = itinerario ?: return 0
            val ruta = rutaCombinada ?: return 0
            // Segundos restantes del plan tras los metros ya recorridos
            // (remainingSeconds de iOS, incluye la espera del transbordo).
            return ceil(plan.segundosRestantes(progreso * ruta.totalM) / 60.0).toInt().coerceAtLeast(0)
        }

    val horaLlegadaTexto: String
        get() = SimpleDateFormat("HH:mm", Locale.getDefault())
            .format(Date(System.currentTimeMillis() + minutosRestantes * 60_000L))

    val velocidadKmh: Int
        get() {
            val plan = itinerario ?: return 0
            return when (fase) {
                TransitPlanner.TramoViaje.EN_BUS -> (plan.busVelocidadMS * 3.6 * multiplicadorVelocidad).roundToInt()
                TransitPlanner.TramoViaje.EN_SEGUNDO_BUS ->
                    ((plan.transbordo?.busVelocidadMS ?: 6.0) * 3.6 * multiplicadorVelocidad).roundToInt()
                else -> (1.4 * 3.6 * multiplicadorVelocidad).roundToInt()
            }
        }

    private var collectJob: Job? = null

    private var pantallaVisible = false
    private var reanudarDemo = false
    private var anclaFlota: LatLng? = null
    private var rutaFlota: String? = null

    fun suspenderPantalla() {
        pantallaVisible = false
        gpsJob?.cancel(); gpsJob = null
        reanudarDemo = reanudarDemo || demoActivo
        detenerDemo()
        collectJob?.cancel(); collectJob = null
        providerSimulado.stop()
    }

    fun reanudarPantalla() {
        pantallaVisible = true
        if (LocationService.estaAutorizado) iniciarGPS()
        anclaFlota?.let { iniciarVehiculos(it, rutaFlota) }
        if (reanudarDemo && conViaje && estado != EstadoDemo.FINALIZADO) iniciarDemo()
        reanudarDemo = false
    }

    fun detener() {
        ViajesActivos.estado.actualizar(TipoViaje.TRACKING, false)
        gpsJob?.cancel()
        demoJob?.cancel()
        calcJob?.cancel()
        collectJob?.cancel()
        providerSimulado.stop()
        // Cancela el scope entero (fuga de hilo Main, igual que NavegacionVM).
        scope.cancel()
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun TrackingDemoScreen(router: AppRouter, holder: TrackingSessionViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm = holder.obtener { TrackingDemoVM(it, AjustesTrackingAndroid(context.applicationContext)) }
    val permiso = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    var mostrarBuscador by remember { mutableStateOf(false) }
    var confirmarDetener by remember { mutableStateOf(false) }

    BackHandler { router.navigate(AppScreen.MapaPrincipal) }
    AlEstarVisible(vm::reanudarPantalla, vm::suspenderPantalla)
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // GPS al entrar; la FLOTA nace con el primer fix, alrededor del USUARIO
    // (antes anclaba a la UTP y los buses quedaban lejos de quien miraba).
    var flotaIniciada by remember { mutableStateOf(false) }
    LaunchedEffect(permiso.status.isGranted) {
        if (permiso.status.isGranted && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            vm.iniciarGPS()
        } else if (!permiso.status.isGranted) {
            vm.estado = EstadoDemo.SIN_PERMISO
        }
    }
    LaunchedEffect(vm.posicion) {
        val pos = vm.posicion ?: return@LaunchedEffect
        if (!flotaIniciada) {
            flotaIniciada = true
            vm.iniciarVehiculos(pos)
        }
    }
    // Cuando arranca el viaje, la flota demo se centra en la linea elegida
    // (y en la posicion actual del pasajero).
    LaunchedEffect(vm.itinerario?.ruta?.id) {
        val id = vm.itinerario?.ruta?.id ?: return@LaunchedEffect
        val ancla = vm.posicion ?: return@LaunchedEffect
        vm.iniciarVehiculos(ancla, id)
    }

    //----Camara: persecucion con tilt cuando viajas; encuadre inicial----
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(GTFSRepository.coordenadaUTP, 15f)
    }
    var seguir by remember { mutableStateOf(true) }
    var animando by remember { mutableStateOf(false) }
    var ultimoSeguimientoMs by remember { mutableStateOf(0L) }

        LaunchedEffect(vm.posicion, seguir, vm.fase) {
            val pos = vm.posicion
            if (seguir && pos != null) {
                val ahora = System.currentTimeMillis()
                val intervalo = if (vm.conViaje) 900L else 1500L   // ~1 Hz: no marear la camara
                if (ahora - ultimoSeguimientoMs > intervalo) {
                    ultimoSeguimientoMs = ahora
                    animando = true
                    // En cualquier micro con demo: persecucion 3D (tilt 55).
                    if ((vm.fase == TransitPlanner.TramoViaje.EN_BUS ||
                        vm.fase == TransitPlanner.TramoViaje.EN_SEGUNDO_BUS) && vm.demoActivo
                    ) {
                        camera.animate(CameraUpdateFactory.newCameraPosition(CameraPosition(pos, 17f, 55f, 0f)))
                    } else {
                        camera.animate(CameraUpdateFactory.newLatLngZoom(pos, 17f))
                    }
                    animando = false
                }
            }
        }
    LaunchedEffect(camera.isMoving) {
        if (camera.isMoving && !animando) seguir = false
        if (camera.isMoving) vm.refrescarNegocios(camera.position.target)
    }

    Box(modifier = Modifier.fillMaxSize().background(ColorOscuro)) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
            onMapLoaded = { vm.refrescarNegocios(camera.position.target) }
        ) {
            val plan = vm.planActual
            if (plan != null) {
                // Ruta combinada dividida: tramo recorrido (tenue) + restante (vivo),
                // refrescando cada 0.5% de avance (iOS idem).
                val fraccion = (vm.progreso * 200).toInt() / 200.0
                val recorridos = remember(fraccion, plan) { TransitPlanner.puntosCombinados(plan) }
                val idxCorte = (fraccion * recorridos.size).toInt().coerceIn(0, recorridos.size - 1)
                val tenue = recorridos.take(idxCorte)
                val vivo = recorridos.drop(idxCorte)
                if (tenue.size >= 2) Polyline(points = tenue, color = plan.ruta.color.copy(alpha = 0.3f), width = 10f)
                if (vivo.size >= 2) Polyline(points = vivo, color = plan.ruta.color, width = 12f)

                // Paradas del plan: 1 SUBE; con transbordo, 2 BAJA (primer
                // micro), 3 CAMBIA y 4 BAJA final.
                MarkerComposable(state = MarkerState(plan.board.coordinate), anchor = Offset(0.5f, 1f), title = plan.board.nombre) {
                    MarcadorMini("1", L.t("SUBE", "BOARD"), Secondary)
                }
                plan.transbordo?.let { t ->
                    MarkerComposable(state = MarkerState(plan.firstAlight.coordinate), anchor = Offset(0.5f, 1f), title = plan.firstAlight.nombre) {
                        MarcadorMini("2", L.t("BAJA", "EXIT"), Naranja)
                    }
                    MarkerComposable(state = MarkerState(t.board.coordinate), anchor = Offset(0.5f, 1f), title = t.board.nombre) {
                        MarcadorMini("3", L.t("CAMBIA", "CHANGE"), t.ruta.color)
                    }
                }
                MarkerComposable(state = MarkerState(plan.alight.coordinate), anchor = Offset(0.5f, 1f), title = plan.alight.nombre) {
                    MarcadorMini(if (plan.transbordo == null) "2" else "4", L.t("BAJA", "EXIT"), AppPrimary)
                }
            }

            // Vehiculos del provider (reales o simulados) con popup.
            vm.vehiculos.take(12).forEach { v ->
                MarkerComposable(
                    state = MarkerState(LatLng(v.lat, v.lon)),
                    anchor = BusMarkerAncla,
                    title = L.t("Línea", "Route") + " ${v.linea}",
                    onClick = { vm.vehiculoSeleccionado = v; true }
                ) {
                    BusMarker3D(
                        linea = v.linea,
                        color = vm.colorDeLinea(v.routeId),
                        heading = if (v.heading >= 0) v.heading else -1.0,
                        seleccionado = vm.vehiculoSeleccionado?.id == v.id
                    )
                }
            }

            // Negocios demo por viewport.
            vm.negocios.forEach { negocio ->
                MarkerComposable(
                    state = MarkerState(LatLng(negocio.latitud, negocio.longitud)),
                    anchor = Offset(0.5f, 1f),
                    title = negocio.nombre,
                    onClick = { vm.negocioSeleccionado = negocio; true }
                ) {
                    NegocioBubbleMarker(negocio, vm.negocioSeleccionado?.id == negocio.id)
                }
            }

            // Posicion del usuario (GPS real o demo).
            vm.posicion?.let { pos ->
                MarkerComposable(
                    state = MarkerState(pos),
                    title = if (vm.demoActivo) L.t("Tu posición · demo", "Your position · demo") else L.t("Tu posición", "Your position")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (vm.demoActivo) {
                            Text(
                                L.t("DEMO", "DEMO"),
                                style = TextStyle(fontSize = 8.sp, color = Color.White),
                                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(AzulDemo).padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        PulsingUserMarker()
                    }
                }
            }
        }

        //----Barra superior: volver + titulo + badges----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(ColorOscuro)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = { router.navigate(AppScreen.MapaPrincipal) }) {
                Icon(Icons.Filled.ArrowBack, L.t("Volver", "Back"), tint = TextoOscuro)
            }
            Text(
                L.t("TRACKING DEMO", "TRACKING DEMO"),
                style = HeadlineSm, color = TextoOscuro, modifier = Modifier.weight(1f)
            )
            ChipBadge(if (vm.esFuenteReal) L.t("EN VIVO", "LIVE") else L.t("DEMO", "DEMO"), if (vm.esFuenteReal) Verde else AzulDemo)
            Spacer(Modifier.width(6.dp))
            ChipBadge(
                when {
                    !permiso.status.isGranted -> L.t("GPS OFF", "GPS OFF")
                    vm.posicion != null -> L.t("GPS ON", "GPS ON")
                    else -> L.t("SIN GPS", "NO GPS")
                },
                if (permiso.status.isGranted && vm.posicion != null) Verde else TextoOscuroVar
            )
        }

        //----Popup de vehiculo (tarjeta en vivo)----
        vm.vehiculoSeleccionado?.let { v ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = PanelOscuroDialogo),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DirectionsBus, null, tint = TextoOscuro, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(L.t("Línea", "Route") + " ${v.linea}", style = HeadlineSm, color = TextoOscuro)
                        Spacer(Modifier.width(8.dp))
                        // Unidad (VehiclePopupCard iOS): DEMO en esta pantalla.
                        ChipBadge(L.t("DEMO", "DEMO"), AzulDemo)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { vm.vehiculoSeleccionado = null }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Cancel, null, tint = TextoOscuroVar, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(
                        if (v.speed >= 0) L.t("Velocidad estimada: ", "Estimated speed: ") + "%.0f km/h".format(v.speed * 3.6)
                        else L.t("Velocidad no disponible", "Speed unavailable"),
                        style = BodySm, color = TextoOscuroVar
                    )
                    // Punto cardinal del rumbo (iOS idem).
                    Text(
                        L.t("Rumbo: ", "Heading: ") + puntoCardinal(v.heading),
                        style = BodySm, color = TextoOscuroVar
                    )
                    // Distancia a ti (si hay posicion propia).
                    vm.posicion?.let { pos ->
                        val distancia = GTFSRepository.distanciaMetros(
                            LatLng(pos.latitude, pos.longitude), LatLng(v.lat, v.lon)
                        )
                        Text(
                            L.t("A ", " ") + formatoDistancia(distancia) + " " + L.t("de ti", "from you"),
                            style = BodySm, color = TextoOscuroVar
                        )
                    }
                    // Antiguedad del snapshot: "Actualizado hace N s" en vivo.
                    val antiguedadS = ((System.currentTimeMillis() / 1000.0) - v.timestamp).coerceAtLeast(0.0)
                    Text(
                        if (antiguedadS < 60)
                            L.t("Actualizado hace ${antiguedadS.toInt()} s", "Updated ${antiguedadS.toInt()} s ago")
                        else
                            L.t("Actualizado hace ${(antiguedadS / 60).toInt()} min", "Updated ${(antiguedadS / 60).toInt()} min ago"),
                        style = BodyXs, color = TextoOscuroVar
                    )
                }
            }
        }

        //----Tarjeta de negocio seleccionado----
        vm.negocioSeleccionado?.let { negocio ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                NegocioDetailCard(
                    negocio = negocio,
                    desde = vm.posicion ?: LatLng(negocio.latitud, negocio.longitud),
                    onCerrar = { vm.negocioSeleccionado = null }
                )
            }
        }

        //----Botones flotantes de camara----
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 420.dp)
        ) {
            BotonCamara(Icons.Filled.Place, L.t("Ver toda la ruta", "View full route")) {
                val puntos = vm.planActual?.let { TransitPlanner.puntosCombinados(it) }
                    ?: return@BotonCamara
                if (puntos.size >= 2) {
                    seguir = false
                    scope.launch {
                        val builder = LatLngBounds.builder()
                        puntos.forEach(builder::include)
                        animando = true
                        camera.animate(CameraUpdateFactory.newLatLngBounds(builder.build(), 140))
                        animando = false
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            BotonCamara(Icons.Filled.MyLocation, L.t("Centrar en mi ubicación", "Center on my location")) { seguir = true }
        }

        //----Panel inferior (scrollable)----
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(ColorOscuro)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Instruccion por estado + fase del viaje.
            val instruccion = when (vm.estado) {
                EstadoDemo.SIN_PERMISO -> L.t("Se necesita permiso de ubicación", "Location permission needed")
                EstadoDemo.ESPERANDO_GPS -> L.t("Esperando GPS…", "Waiting for GPS…")
                EstadoDemo.LISTO -> L.t("Todo listo para empezar", "All set to start")
                EstadoDemo.EN_RUTA -> when (vm.fase) {
                    TransitPlanner.TramoViaje.CAMINANDO_A_SUBIDA -> L.t("Camina al paradero de subida", "Walk to the boarding stop")
                    TransitPlanner.TramoViaje.EN_BUS -> L.t("Toma el micro", "Ride the bus") + " ${vm.planActual?.ruta?.linea ?: ""}"
                    TransitPlanner.TramoViaje.TRANSBORDANDO -> L.t("Camina al segundo micro", "Walk to the second bus")
                    TransitPlanner.TramoViaje.EN_SEGUNDO_BUS -> L.t("Toma el micro ", "Take bus ") + "${vm.planActual?.transbordo?.ruta?.linea ?: ""}"
                    TransitPlanner.TramoViaje.CAMINANDO_A_DESTINO -> L.t("Baja y camina a tu destino", "Get off and walk to your destination")
                }
                EstadoDemo.FUERA_RUTA -> L.t("Fuera de la ruta", "Off the route")
                EstadoDemo.CERCA_DESTINO -> L.t("Estás cerca de tu destino", "You're close to your destination")
                EstadoDemo.FINALIZADO -> L.t("Fin del viaje", "End of the trip")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (vm.estado) {
                        EstadoDemo.FUERA_RUTA -> Icons.Filled.Cancel
                        EstadoDemo.FINALIZADO -> Icons.Filled.CheckCircle
                        else -> Icons.Filled.MyLocation
                    }, null,
                    tint = when (vm.estado) {
                        EstadoDemo.FUERA_RUTA -> Naranja
                        EstadoDemo.FINALIZADO, EstadoDemo.CERCA_DESTINO -> Verde
                        else -> TextoOscuro
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(instruccion, style = HeadlineSm, color = TextoOscuro)
            }

            // Banner naranja de recalculo.
            if (vm.recalculando) {
                Spacer(Modifier.height(8.dp))
                Text(
                    L.t("Recalculando ruta…", "Recalculating route…"),
                    style = BodySm, color = Naranja
                )
            }

            if (!vm.conViaje) {
                //----SIN VIAJE: paradero cercano, destino, radio, iniciar----
                vm.paraderoCercano?.let { p ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        L.t("Paradero cercano: ", "Nearby stop: ") + p.nombre + " · ${vm.paraderoCercanoM.toInt()} m",
                        style = BodySm, color = TextoOscuroVar
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { mostrarBuscador = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Search, null, tint = AppPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        vm.destinoTitulo?.let { L.t("Destino: ", "Destination: ") + it }
                            ?: L.t("Buscar destino", "Search destination"),
                        style = BodyMdMedium, color = AppPrimary, maxLines = 1
                    )
                }
                Spacer(Modifier.height(10.dp))
                // Radio de paradero configurable (200/500/800/1600 m, persistido).
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrackingDemoPrefs.radiosDisponibles.forEach { radio ->
                        FilterChip(
                            selected = vm.radioParadero == radio,
                            onClick = { vm.cambiarRadio(radio) },
                            label = { Text("${radio.toInt()} m", style = BodyXs) }
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                // Mensaje de error cuando no hay ruta en el radio elegido
                // (antes: silencio total y parecia que el boton no funciono).
                vm.mensajeErrorRuta?.let { mensaje ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x33FF5252))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.LocationOff, null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(mensaje, style = BodySm, color = TextoOscuro)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Button(
                    onClick = {
                        val titulo = vm.destinoTitulo ?: return@Button
                        val destino = vm.destino ?: return@Button
                        vm.iniciarViaje(titulo, destino)
                    },
                    enabled = vm.destino != null && vm.posicion != null && !vm.calculandoRuta,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
                ) {
                    if (vm.calculandoRuta) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(L.t("Calculando ruta…", "Calculating route…"), style = HeadlineSm, color = Color.White)
                    } else {
                        Icon(Icons.Filled.DirectionsWalk, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            L.t("Iniciar viaje a", "Start trip to") + " ${vm.destinoTitulo ?: ""}",
                            style = HeadlineSm, color = Color.White, maxLines = 1
                        )
                    }
                }
            } else {
                //----CON VIAJE: banner, resumen, progreso, stats, controles----
                val plan = vm.planActual ?: return@Column
                Spacer(Modifier.height(8.dp))
                Text(
                    L.t("Ruta real · Línea", "Real route · Line") + " ${plan.ruta.linea}" +
                        (plan.transbordo?.let { " → ${it.ruta.linea}" } ?: "") +
                        " · ${plan.ruta.empresa}" +
                        if (vm.caminataAproximada) " · " + L.t("caminata estimada", "estimated walk") else "",
                    style = BodySm, color = TextoOscuroVar
                )
                Spacer(Modifier.height(8.dp))
                // Resumen numerado (itinerarySummary del iOS).
                PasoDemo(Icons.Filled.DirectionsWalk, L.t("1 · ${plan.board.nombre} · ${plan.caminataBoardMetros.roundToInt()} m a pie", "1 · ${plan.board.nombre} · ${plan.caminataBoardMetros.roundToInt()} m walk"))
                PasoDemo(Icons.Filled.DirectionsBus, L.t("Micro", "Bus") + " ${plan.ruta.linea} · ${plan.ruta.precioTexto}", color = plan.ruta.color)
                plan.transbordo?.let { t ->
                    Spacer(Modifier.height(2.dp))
                    Text(L.t("1 transbordo", "1 transfer"), style = BodySmMedium, color = TextoOscuro)
                    PasoDemo(Icons.Filled.Place, L.t("Baja en", "Get off at") + " ${plan.firstAlight.nombre}")
                    PasoDemo(Icons.Filled.DirectionsWalk, L.t("Camina", "Walk") + " ${t.caminataMetros.roundToInt()} m · ${t.board.nombre}")
                    PasoDemo(Icons.Filled.SwapHoriz, L.t("Luego toma Micro", "Then take bus") + " ${t.ruta.linea} · ${t.ruta.precioTexto}", color = t.ruta.color)
                    Text(
                        L.t(
                            "El tiempo incluye una espera estimada para el segundo micro.",
                            "Time includes an estimated wait for the second bus."
                        ),
                        style = BodyXs, color = TextoOscuroVar
                    )
                }
                PasoDemo(
                    Icons.Filled.Flag,
                    (if (plan.transbordo == null) "2 · " else "4 · ") +
                        L.t("${plan.alight.nombre} · ${plan.caminataDestinoMetros.roundToInt()} m al destino", "${plan.alight.nombre} · ${plan.caminataDestinoMetros.roundToInt()} m to destination")
                )

                // Progreso.
                Spacer(Modifier.height(10.dp))
                val fraccion = animateFloatAsState((vm.progreso * 100).toFloat(), tween(300), label = "progresoDemo")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(PanelOscuroItem)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraccion.value / 100f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(plan.ruta.color)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    Text(
                        L.t("Llegada: ", "Arrival: ") + vm.horaLlegadaTexto,
                        style = BodySm, color = TextoOscuro, modifier = Modifier.weight(1f)
                    )
                    Text("~${vm.minutosRestantes} min", style = BodySm, color = TextoOscuro)
                }
                Text(
                    L.t("Restan ", "Remaining ") + formatoDistancia(vm.restanteM) +
                        " · " + L.t("puntos GPS: ", "GPS points: ") + "${vm.sesion?.puntosRecorridos?.size ?: 0}",
                    style = BodySm, color = TextoOscuroVar
                )

                // Controles del demo.
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { if (vm.demoActivo) vm.detenerDemo() else vm.iniciarDemo() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            if (vm.demoActivo) L.t("Pausar demo", "Pause demo") else L.t("Simular avance", "Simulate advance"),
                            style = BodySm, color = AppPrimary, maxLines = 1
                        )
                    }
                    Button(
                        onClick = { confirmarDetener = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RojoPeligro)
                    ) {
                        Icon(Icons.Filled.StopCircle, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(L.t("Detener", "Stop"), style = BodySm, color = Color.White)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(L.t("Velocidad", "Speed"), style = BodySm, color = TextoOscuroVar)
                    TrackingDemoPrefs.velocidadesDisponibles.forEach { mult ->
                        FilterChip(
                            selected = vm.multiplicadorVelocidad == mult,
                            onClick = { vm.cambiarVelocidad(mult) },
                            label = { Text("${mult}×", style = BodyXs) }
                        )
                    }
                    Text("· ~${vm.velocidadKmh} km/h", style = BodyXs, color = TextoOscuroVar)
                }
            }
        }

        //----Alerta/Resumen de llegada----
        if (vm.estado == EstadoDemo.FINALIZADO && vm.sesion != null) {
            val ses = vm.sesion ?: return   // smart-cast sin !!
            val duracionS = (ses.endedAt ?: System.currentTimeMillis() / 1000.0) - ses.startedAt
            var distanciaM = 0.0
            for (i in 1 until ses.puntosRecorridos.size) {
                distanciaM += GTFSRepository.distanciaMetros(
                    LatLng(ses.puntosRecorridos[i - 1].lat, ses.puntosRecorridos[i - 1].lon),
                    LatLng(ses.puntosRecorridos[i].lat, ses.puntosRecorridos[i].lon)
                )
            }
            val media = if (duracionS > 0) distanciaM / duracionS else 0.0
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(24.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(PanelOscuroDialogo)
                        .padding(28.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Verde, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(L.t("Resumen del viaje", "Trip summary"), style = HeadlineMd, color = TextoOscuro, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(L.t("Duración", "Duration") + ": ${duracionS.toInt() / 60} min ${duracionS.toInt() % 60} s", style = BodySm, color = TextoOscuroVar)
                    Text(L.t("Distancia recorrida", "Distance traveled") + ": " + formatoDistancia(distanciaM), style = BodySm, color = TextoOscuroVar)
                    Text(L.t("Velocidad media", "Average speed") + ": %.1f km/h".format(media * 3.6), style = BodySm, color = TextoOscuroVar)
                    Text(L.t("Puntos GPS", "GPS points") + ": ${ses.puntosRecorridos.size}", style = BodySm, color = TextoOscuroVar)
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { vm.detenerViaje(cancelado = false) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Verde)
                    ) {
                        Text(L.t("Terminar", "Finish"), style = HeadlineSm, color = Color.White)
                    }
                }
            }
        }

        //----Dialogos----
        if (mostrarBuscador) {
            BuscadorDestinoDialog(
                onElegir = { titulo, lat, lon ->
                    vm.destinoTitulo = titulo
                    vm.destino = LatLng(lat, lon)
                    mostrarBuscador = false
                },
                onCerrar = { mostrarBuscador = false }
            )
        }
        if (confirmarDetener) {
            AlertDialog(
                onDismissRequest = { confirmarDetener = false },
                title = { Text(L.t("¿Finalizar el viaje?", "End the trip?")) },
                text = { Text(L.t("Se cancelará la sesión de este viaje.", "This trip's session will be cancelled.")) },
                confirmButton = {
                    TextButton(onClick = { confirmarDetener = false; vm.detenerViaje() }) {
                        Text(L.t("Sí, detener", "Yes, stop"), color = RojoPeligro)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmarDetener = false }) { Text(L.t("Cancelar", "Cancel")) }
                }
            )
        }
    }
}

//----Piezas UI----

@Composable
private fun ChipBadge(texto: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(texto, style = LabelCapsSm, color = color)
    }
}

@Composable
private fun MarcadorMini(numero: String, titulo: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(color)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) { Text(titulo, style = LabelCapsSm, color = Color.White) }
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(numero, style = LabelCapsMd, color = Color.White) }
    }
}

@Composable
private fun BotonCamara(icono: androidx.compose.ui.graphics.vector.ImageVector, descripcion: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(Color(0xE6141414))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icono, descripcion, tint = TextoOscuro, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PasoDemo(icono: androidx.compose.ui.graphics.vector.ImageVector, texto: String, color: Color = TextoOscuro) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        Icon(icono, null, tint = color, modifier = Modifier.size(14.dp))
        Text(texto, style = BodySm, color = TextoOscuro, maxLines = 1)
    }
}

@Composable
private fun BuscadorDestinoDialog(
    onElegir: (String, Double, Double) -> Unit,
    onCerrar: () -> Unit
) {
    var texto by remember { mutableStateOf("") }
    var sugerencias by remember { mutableStateOf<List<com.example.rutautpnative.ui.screens.mapa.SugerenciaLugar>>(emptyList()) }
    var buscando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var jobBusqueda by remember { mutableStateOf<Job?>(null) }

    // Destinos rapidos (los chips fijos del iOS).
    val fijos = listOf(
        Triple("UTP", GTFSRepository.coordenadaUTP.latitude, GTFSRepository.coordenadaUTP.longitude),
        Triple(L.t("Centro", "Downtown"), -8.1090, -79.0270),
        Triple("Huanchaco", -8.0825, -79.1197)
    )

    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 60.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(ColorOscuro)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L.t("Buscar destino", "Search destination"), style = HeadlineSm, color = TextoOscuro, modifier = Modifier.weight(1f))
                IconButton(onClick = onCerrar) { Icon(Icons.Filled.Cancel, null, tint = TextoOscuroVar, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = texto,
                onValueChange = { nuevo ->
                    texto = nuevo
                    jobBusqueda?.cancel()
                    val t = nuevo.trim()
                    if (t.length >= 3) {
                        buscando = true
                        jobBusqueda = scope.launch {
                            delay(400)   // debounce
                            when (val r = PlacesService.buscarSugerencias(t)) {
                                is PlacesService.ResultadoSugerencias.Exito ->
                                    sugerencias = r.sugerencias.map {
                                        com.example.rutautpnative.ui.screens.mapa.SugerenciaLugar(it.nombre, it.direccion, it.lat, it.lon)
                                    }
                                else -> sugerencias = emptyList()
                            }
                            buscando = false
                        }
                    } else {
                        sugerencias = emptyList()
                        buscando = false
                    }
                },
                placeholder = { Text(L.t("¿A dónde vas?", "Where are you going?"), color = TextoOscuroVar) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fijos.forEach { (titulo, lat, lon) ->
                    FilterChip(
                        selected = false,
                        onClick = { onElegir(titulo, lat, lon) },
                        label = { Text(titulo, style = BodyXs) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (buscando) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AppPrimary)
                Spacer(Modifier.height(8.dp))
            }
            sugerencias.take(5).forEach { s ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onElegir(s.titulo, s.lat, s.lon) }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Filled.Place, null, tint = AppPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(s.titulo, style = BodySm, color = TextoOscuro, maxLines = 1)
                        if (s.subtitulo.isNotBlank()) Text(s.subtitulo, style = BodyXs, color = TextoOscuroVar, maxLines = 1)
                    }
                }
            }
        }
    }
}

/// Punto cardinal del rumbo (VehiclePopupCard iOS): N/NE/E/... o "—".
private fun puntoCardinal(rumbo: Double): String {
    if (!rumbo.isFinite() || rumbo < 0) return L.t("desconocido", "unknown")
    val puntos = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    val indice = (((rumbo % 360.0) / 45.0) + 0.5).toInt() % 8
    return puntos[indice]
}
