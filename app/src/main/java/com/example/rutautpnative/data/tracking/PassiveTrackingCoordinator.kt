package com.example.rutautpnative.data.tracking

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import com.example.rutautpnative.data.geo.PolylineMatching
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.ubicacion.LocationService
import com.example.rutautpnative.ui.idioma.L
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

//----Coordinador del rastreo pasivo (puerto del PassiveTrackingCoordinator.swift)----
// La "baliza del pasajero": une GPS (LocationService compartido de la Fase 1),
// actividad fisica, matching GTFS y el motor de deteccion para publicar
// observaciones anonimas SOLO cuando el usuario lo consiente y el motor
// confirma el abordaje. Vive como singleton (sobrevive a la navegacion), como
// el @StateObject de RutaUTPApp.swift.
//
// Reglas heredadas del iOS:
//   - Sin canal MQTT configurado, el consentimiento guardado se revoca y el
//     toggle queda deshabilitado.
//   - El arranque usa testigos de generacion (StartGuard): si el usuario
//     revoca mientras se pide el permiso o se carga el feed, la continuacion
//     no reactiva nada.
//   - Cada abordaje confirmado recibe una sesion nueva y anonima; el descenso
//     la cierra. La sesion se REUTILIZA al reanudar desde segundo plano.
//   - La evidencia de abordaje debe proceder de una sola ruta: si el candidato
//     cambia, el motor se reinicia.
object PassiveTrackingCoordinator {

    private const val PREFS = "rutautp.tracking"
    private const val LLAVE_CONSENTIMIENTO = "rutautp.passive-tracking-consent"

    // 60 x 2 s = ~2 min de "viaje" antes del descenso simulado.
    private const val PASOS_VIAJE_DEMO = 60

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    //----Canal MQTT (resuelto una vez; null => contribucion imposible)----
    var publicador: MQTTObservationPublisher? = null
        private set

    private val _publicadorConfigurado = MutableStateFlow(false)
    val publicadorConfigurado: StateFlow<Boolean> = _publicadorConfigurado.asStateFlow()

    //----Estado observable por la UI----
    private val _consentimiento = MutableStateFlow(false)
    val consentimiento: StateFlow<Boolean> = _consentimiento.asStateFlow()

    private val _ejecutando = MutableStateFlow(false)
    val ejecutando: StateFlow<Boolean> = _ejecutando.asStateFlow()

    private val _estadoDeteccion = MutableStateFlow(EstadoDeteccionPasajero.IDLE)
    val estadoDeteccion: StateFlow<EstadoDeteccionPasajero> = _estadoDeteccion.asStateFlow()

    private val _actividadActual = MutableStateFlow(ActividadMotora.UNKNOWN)
    val actividadActual: StateFlow<ActividadMotora> = _actividadActual.asStateFlow()

    private val _lineaConfirmada = MutableStateFlow<String?>(null)
    val lineaConfirmada: StateFlow<String?> = _lineaConfirmada.asStateFlow()

    private val _mensajeEstado = MutableStateFlow("")
    val mensajeEstado: StateFlow<String> = _mensajeEstado.asStateFlow()

    private val _estadoPublicador = MutableStateFlow(MQTTObservationPublisher.EstadoPublicador.INACTIVO)
    val estadoPublicador: StateFlow<MQTTObservationPublisher.EstadoPublicador> = _estadoPublicador.asStateFlow()

    //----Viaje declarado ("¿Ya subiste? Confirma tu línea", Fase 9)----
    // Puerto del bloque selectedTripRoute/tripStartedAt/boardingPlace del iOS.
    private val _rutaViajeSeleccionada = MutableStateFlow<GeometriaRutaDeteccion?>(null)
    val rutaViajeSeleccionada: StateFlow<GeometriaRutaDeteccion?> = _rutaViajeSeleccionada.asStateFlow()

    private val _viajeIniciadoEn = MutableStateFlow<Double?>(null)   // epoch s
    val viajeIniciadoEn: StateFlow<Double?> = _viajeIniciadoEn.asStateFlow()

    private val _lugarSubida = MutableStateFlow<String?>(null)
    val lugarSubida: StateFlow<String?> = _lugarSubida.asStateFlow()

    private val _puntoSubida = MutableStateFlow<PuntoSubida?>(null)
    val puntoSubida: StateFlow<PuntoSubida?> = _puntoSubida.asStateFlow()

    private val _ubicacionReciente = MutableStateFlow<LatLng?>(null)
    val ubicacionReciente: StateFlow<LatLng?> = _ubicacionReciente.asStateFlow()

    // Tras "Finalizar viaje": espera que el usuario declare su proximo micro.
    private var esperandoNuevoViaje = false
    private var enPrimerPlano = true

    /// Canal de ocupacion del propio viaje (tripOccupancy del iOS): vive y
    /// muere con la sesion de observacion; sin broker no existe.
    var ocupacionViaje: OccupancyService? = null
        private set

    //----Motor, rutas y sesion----
    private val motor = MotorDeteccionPasajero()
    private var rutasDeteccion: List<GeometriaRutaDeteccion> = emptyList()
    private var gpsJob: Job? = null
    private var motionJob: Job? = null
    private var startJob: Job? = null

    // Testigos de generacion (StartGuard del iOS): invalidan arranques
    // obsoletos que quedaron en vuelo tras una revocacion.
    private val guardia = AtomicInteger(0)

    private var sesionID: String? = null
    private var rutaConfirmadaID: String? = null
    private var rutaAbordajeID: String? = null

    //----Ciclo de vida----

    fun init(context: Context) {
        // La actividad se recrea al girar; los colectores pertenecen al proceso.
        if (appContext != null) return
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val config = MQTTConfiguration.desdeBuildConfig()?.takeIf { it.puedeContribuir }
        publicador = config?.let { MQTTObservationPublisher(it, context) }
        _publicadorConfigurado.value = publicador != null
        ocupacionViaje = config?.let { OccupancyService(it, context) }

        val consentPrevio = prefs?.getBoolean(LLAVE_CONSENTIMIENTO, false) == true
        if (consentPrevio && publicador == null) {
            // Sin canal configurado: se revoca el consentimiento guardado
            // (misma regla del iOS), no se enciende GPS ni detectores.
            prefs?.edit()?.putBoolean(LLAVE_CONSENTIMIENTO, false)?.apply()
            _mensajeEstado.value = L.t("Canal MQTT no configurado", "MQTT channel not configured")
        }
        _consentimiento.value = consentPrevio && publicador != null

        // El estado del publicador se propaga al instante (no por sondeo).
        scope.launch {
            publicador?.estado?.collect { _estadoPublicador.value = it }
        }
        scope.launch {
            MotionActivityService.actividadActual.collect { _actividadActual.value = it }
        }
    }

    /// Al arrancar la app (MainActivity.onCreate): reanuda la baliza si el
    /// usuario ya habia dado su consentimiento. No hace nada si no.
    fun reanudarSiConsentido() {
        if (_consentimiento.value) arrancar()
    }

    /// Interruptor del toggle de Ajustes (con el consentimiento YA mostrado
    /// por la UI; aqui se verifica el permiso de ubicacion dentro del arranque).
    fun setContribucionActivada(activada: Boolean) {
        if (activada && publicador == null) {
            prefs?.edit()?.putBoolean(LLAVE_CONSENTIMIENTO, false)?.apply()
            _consentimiento.value = false
            _mensajeEstado.value = L.t("Canal MQTT no configurado", "MQTT channel not configured")
            return
        }
        prefs?.edit()?.putBoolean(LLAVE_CONSENTIMIENTO, activada)?.apply()
        _consentimiento.value = activada
        if (activada) arrancar() else detener()
    }

    private fun arrancar() {
        // Invalida cualquier arranque pendiente antes de abrir uno nuevo.
        startJob?.cancel()
        val token = guardia.incrementAndGet()

        startJob = scope.launch {
            if (!_consentimiento.value) return@launch

            _mensajeEstado.value = L.t("Preparando detección pasiva", "Preparing passive detection")

            // Permiso de ubicacion: lo pide la UI antes de activar; aqui solo
            // se verifica (el iOS suspende pidiendolo, con el mismo resultado).
            if (!LocationService.estaAutorizado) {
                prefs?.edit()?.putBoolean(LLAVE_CONSENTIMIENTO, false)?.apply()
                _consentimiento.value = false
                _mensajeEstado.value = L.t("Se necesita permiso de ubicación", "Location permission required")
                return@launch
            }
            if (token != guardia.get()) return@launch

            // Feed GTFS: solo la primera vez; se re-verifica el testigo tras
            // la espera (el usuario pudo revocar mientras cargaba).
            if (rutasDeteccion.isEmpty()) {
                val rutas = GTFSRepository.rutas()
                if (token != guardia.get()) return@launch
                rutasDeteccion = rutas.mapNotNull { GeometriaRutaDeteccion.desde(it) }
            }
            if (rutasDeteccion.isEmpty()) {
                _mensajeEstado.value = L.t("No se pudieron cargar las rutas GTFS", "Couldn't load GTFS routes")
                return@launch
            }

            _ejecutando.value = true
            _mensajeEstado.value = L.t("Analizando movilidad", "Analyzing movement")

            appContext?.let { MotionActivityService.start(it) }
            observarActividad()
            observarUbicacion()
        }
    }

    private fun observarActividad() {
        motionJob?.cancel()
        motionJob = scope.launch {
            MotionActivityService.actividadActual.collect { _actividadActual.value = it }
        }
    }

    private fun observarUbicacion() {
        gpsJob?.cancel()
        gpsJob = scope.launch {
            LocationService.currentLocation().collect { loc -> procesar(loc) }
        }
    }

    //----Procesamiento de una muestra GPS (equivalente a process(_:activity:))----

    private fun procesar(location: Location) {
        if (!enPrimerPlano || !_consentimiento.value) return
        if (!location.hasAccuracy() || location.accuracy > 50f || location.accuracy < 0f || System.currentTimeMillis() - location.time > 30_000 || location.time > System.currentTimeMillis() + 10_000) return
        // latestLocation del iOS: lo leen el selector de lineas cercanas y el
        // mapa del punto de subida (siempre fresco, viaje o no).
        _ubicacionReciente.value = LatLng(location.latitude, location.longitude)
        if (esperandoNuevoViaje) return

        val heading = if (location.hasBearing()) location.bearing.toDouble() else -1.0
        // La linea declarada guia al detector SIN forzar el abordaje: con
        // viaje activo solo se evalua ESA ruta (iOS: selectedTripRoute.map { [$0] }).
        val rutasCandidatas = _rutaViajeSeleccionada.value?.let { listOf(it) } ?: rutasDeteccion
        val match = RouteCandidateMatcher.emparejamientoMasCercano(
            location.latitude, location.longitude, heading, rutasCandidatas
        )
        if (match == null) {
            _mensajeEstado.value = L.t("Sin rutas próximas", "No nearby routes")
            return
        }

        val muestra = MuestraDeteccion(
            timestamp = location.time / 1000.0,
            speed = max(0.0, location.speed.toDouble()),
            horizontalAccuracy = location.accuracy.toDouble(),
            distanceToRoute = match.distanciaRuta,
            headingDifference = match.diferenciaRumbo,
            distanceToNearestStop = match.distanciaParadero,
            // En modo demo, la actividad la impone el generador sintetico
            // (AUTOMOTIVE/WALKING); fuera de el, la del sensor real.
            motionActivity = actividadDemo ?: _actividadActual.value
        )

        // La evidencia de abordaje debe proceder de UNA sola ruta: si el
        // candidato cambio (calles paralelas, cruce), lo acumulado no vale.
        if (motor.estado == EstadoDeteccionPasajero.BOARDING_CANDIDATE &&
            rutaAbordajeID != null && rutaAbordajeID != match.routeId
        ) {
            motor.reset()
            rutaAbordajeID = null
        }

        val decision = motor.procesar(muestra)
        _estadoDeteccion.value = decision.estado

        when {
            decision.confirmoAbordaje -> {
                _lineaConfirmada.value = match.linea
                rutaConfirmadaID = match.routeId
                rutaAbordajeID = null
                // Sesion nueva y anonima por cada abordaje (UUID ya minuscula).
                val sesion = UUID.randomUUID().toString()
                sesionID = sesion
                publicador?.start(sesion, match.linea)
                _mensajeEstado.value =
                    L.t("Abordaje probable: línea ${match.linea}", "Likely boarding: line ${match.linea}")
            }

            decision.confirmoDescenso -> {
                if (_rutaViajeSeleccionada.value != null) {
                    // Un descenso detectado tambien termina el viaje declarado.
                    terminarViajeDeclarado()
                    _mensajeEstado.value =
                        L.t("Descenso detectado. Viaje terminado.", "Alighting detected. Trip ended.")
                    return
                }
                terminarSesion()
                _lineaConfirmada.value = null
                rutaAbordajeID = null
                _mensajeEstado.value = L.t("Descenso detectado", "Alighting detected")
            }

            else -> {
                rutaAbordajeID =
                    if (decision.estado == EstadoDeteccionPasajero.BOARDING_CANDIDATE) match.routeId else null
                _mensajeEstado.value = mensajePara(decision.estado, match.linea)
            }
        }

        // Solo el motor autoriza la publicacion, y con la ruta CONFIRMADA al
        // abordar (no la candidata de la muestra actual): conserva la
        // identidad del viaje.
        val rutaId = rutaConfirmadaID   // local para smart-cast sin !!
        if (decision.debePublicar && sesionID != null && rutaId != null) {
            publicador?.publicar(
                lat = location.latitude,
                lon = location.longitude,
                speed = location.speed.toDouble(),
                heading = heading,
                accuracy = location.accuracy.toDouble(),
                routeId = rutaId,
                linea = _lineaConfirmada.value ?: match.linea,
                actividad = _actividadActual.value
            )
            // El canal de ocupacion del viaje se enciende con la primera
            // publicacion autorizada si hay un reporte pendiente (iOS idem).
            if (_rutaViajeSeleccionada.value != null && ocupacionViaje?.tieneReportePendiente == true) {
                ocupacionViaje?.start()
            }
        }
    }

    private fun mensajePara(estado: EstadoDeteccionPasajero, linea: String): String = when (estado) {
        EstadoDeteccionPasajero.IDLE -> L.t("Sin viaje detectado", "No ride detected")
        EstadoDeteccionPasajero.APPROACHING_STOP -> L.t("Cerca de un paradero", "Near a stop")
        EstadoDeteccionPasajero.BOARDING_CANDIDATE ->
            L.t("Posible abordaje en $linea", "Possible boarding on $linea")
        EstadoDeteccionPasajero.ONBOARD ->
            L.t("Viaje detectado en $linea", "Ride detected on $linea")
        EstadoDeteccionPasajero.ALIGHTING_CANDIDATE ->
            L.t("Comprobando posible descenso", "Checking possible alighting")
    }

    //----Pausa/reanudacion con el ciclo de vida de la app----

    /// Corta la publicacion al pasar a segundo plano (Android mata el proceso
    /// poco despues): cierra la conexion conservando la sesion y la ruta,
    /// porque el viaje sigue vigente y debe poder reanudarse.
    fun pausarParaSegundoPlano() {
        enPrimerPlano = false
        startJob?.cancel()
        guardia.incrementAndGet()
        gpsJob?.cancel()
        motionJob?.cancel()
        appContext?.let { MotionActivityService.stop(it) }
        publicador?.stop()
        ocupacionViaje?.stop()
        _mensajeEstado.value =
            L.t("Publicación pausada: app en segundo plano", "Publishing paused: app in background")
    }

    /// Reanuda con el MISMO sessionId si el viaje detectado sigue vigente.
    fun reanudarSesionSiPosible() {
        enPrimerPlano = true
        if (_consentimiento.value) arrancar()
        val sesion = sesionID ?: return
        val linea = _lineaConfirmada.value ?: return
        if (_estadoDeteccion.value != EstadoDeteccionPasajero.ONBOARD) return
        publicador?.start(sesion, linea)
        _mensajeEstado.value = L.t("Viaje detectado en $linea", "Ride detected on $linea")
    }

    //----Viaje declarado (beginTrip / endTrip del iOS)----

    /// La declaracion orienta el detector; nunca salta sus comprobaciones.
    /// Registra la hora de inicio, guarda la referencia de subida y prepara
    /// el reporte de ocupacion del viaje si el usuario indico uno.
    fun iniciarViajeDeclarado(
        ruta: GeometriaRutaDeteccion,
        ocupacion: EstadoOcupacion?,
        lugarSubida: String?,
        puntoSubida: PuntoSubida?
    ) {
        if (!_consentimiento.value || publicador == null) return
        terminarSesion()
        ocupacionViaje?.limpiarReporteViaje()
        motor.reset()
        _estadoDeteccion.value = EstadoDeteccionPasajero.IDLE
        _lineaConfirmada.value = null
        _rutaViajeSeleccionada.value = ruta
        com.example.rutautpnative.navigation.ViajesActivos.estado.actualizar(
            com.example.rutautpnative.navigation.TipoViaje.DECLARADO, true)
        _viajeIniciadoEn.value = System.currentTimeMillis() / 1000.0
        val lugar = BoardingPlaceStore.normalizado(lugarSubida ?: "")
        _lugarSubida.value = lugar
        _puntoSubida.value = puntoSubida
        BoardingPlaceStore.guardar(appContext, ruta.routeId, ruta.linea, lugar ?: "", puntoSubida)
        esperandoNuevoViaje = false
        _mensajeEstado.value = L.t(
            "Esperando detectar el viaje en la línea ${ruta.linea}",
            "Waiting to detect your trip on line ${ruta.linea}"
        )
        if (ocupacion != null) actualizarOcupacionViaje(ocupacion)
    }

    /// Ocupacion del propio viaje: queda pendiente hasta detectar el viaje y
    /// tener conexion; se reintenta cada 30 s durante los 3 min de validez.
    fun actualizarOcupacionViaje(estado: EstadoOcupacion) {
        val ruta = _rutaViajeSeleccionada.value ?: return
        if (!_consentimiento.value) return
        ocupacionViaje?.prepararReporteViaje(ruta.routeId, estado)
    }

    /// "Finalizar viaje" (o contribucion desactivada): corta la sesion,
    /// descarta pendientes y espera otro viaje declarado.
    fun terminarViajeDeclarado() {
        com.example.rutautpnative.navigation.ViajesActivos.estado.actualizar(
            com.example.rutautpnative.navigation.TipoViaje.DECLARADO, false)
        terminarSesion()
        _rutaViajeSeleccionada.value = null
        _viajeIniciadoEn.value = null
        _lugarSubida.value = null
        _puntoSubida.value = null
        motor.reset()
        _estadoDeteccion.value = EstadoDeteccionPasajero.IDLE
        _lineaConfirmada.value = null
        rutaAbordajeID = null
        esperandoNuevoViaje = true
        _mensajeEstado.value = L.t(
            "Viaje terminado. Indica tu próximo micro para volver a ayudar.",
            "Trip ended. Tell us your next bus to help again."
        )
    }

    private fun terminarSesion() {
        publicador?.stop()
        ocupacionViaje?.stop()
        sesionID = null
        rutaConfirmadaID = null
    }

    //----Detencion total----

    fun detener() {
        com.example.rutautpnative.navigation.ViajesActivos.estado.actualizar(
            com.example.rutautpnative.navigation.TipoViaje.DECLARADO, false)
        startJob?.cancel()
        guardia.incrementAndGet()   // invalida arranques en vuelo
        detenerViajeDemo()
        gpsJob?.cancel()
        motionJob?.cancel()
        appContext?.let { MotionActivityService.stop(it) }
        motor.reset()
        terminarSesion()
        // Desactivar la contribucion limpia tambien la seleccion de viaje
        // (iOS idem): no se restaura tras cerrar el proceso.
        _rutaViajeSeleccionada.value = null
        _viajeIniciadoEn.value = null
        _lugarSubida.value = null
        _puntoSubida.value = null
        esperandoNuevoViaje = false
        ocupacionViaje?.limpiarReporteViaje()
        _ejecutando.value = false
        _estadoDeteccion.value = EstadoDeteccionPasajero.IDLE
        _actividadActual.value = ActividadMotora.UNKNOWN
        _lineaConfirmada.value = null
        rutaAbordajeID = null
        _mensajeEstado.value = L.t("Contribución desactivada", "Contribution disabled")
    }

    fun actualizarModoTransporte() {
        detener()
        rutasDeteccion = emptyList()
        if (_consentimiento.value && enPrimerPlano) arrancar()
    }

    //----Viaje de prueba (puerto-comodin del MQTT_FORCE_ONBOARD del iOS)----
    // En iOS forzar un abordaje era una variable de DEBUG del Scheme; aqui es
    // un boton visible para poder probar el circuito COMPLETO sin subirse a
    // un micro: recorre una linea REAL del feed con GPS sintetico — deteccion
    // de abordaje, sesion anonima, publicacion MQTT real y descenso — de
    // punta a punta. Con el agregador corriendo, el "bus" aparece en vivo en
    // el mapa de todos.

    private val _demoActivo = MutableStateFlow(false)
    val demoActivo: StateFlow<Boolean> = _demoActivo.asStateFlow()

    private var demoJob: Job? = null
    private var actividadDemo: ActividadMotora? = null

    fun iniciarViajeDemo() {
        // Solo con contribucion activa: publica observaciones reales.
        if (!_consentimiento.value) return
        detenerViajeDemo()

        demoJob = scope.launch {
            // La linea del demo: la mas cercana al campus (el feed viene
            // ordenado por cercania a la UTP) — el bus nace donde se mira.
            val ruta = GTFSRepository.rutas().firstOrNull { it.shape.size >= 2 } ?: return@launch
            val shape = PolylineMatching.decimate(ruta.shape, 480)
            if (shape.size < 2) return@launch
            val acumulados = PolylineMatching.distanciasAcumuladas(shape)
            val total = acumulados.lastOrNull() ?: 0.0
            if (total < 200.0) return@launch

            // Asegura las rutas de deteccion (mismo camino que arrancar()).
            if (rutasDeteccion.isEmpty()) {
                rutasDeteccion = GTFSRepository.rutas().mapNotNull { GeometriaRutaDeteccion.desde(it) }
            }
            if (rutasDeteccion.isEmpty()) {
                _mensajeEstado.value = L.t("No se pudieron cargar las rutas GTFS", "Couldn't load GTFS routes")
                return@launch
            }
            if (!_ejecutando.value) _ejecutando.value = true

            // Nace en el waypoint mas cercano a la UTP.
            var idx = shape.indices.minByOrNull {
                GTFSRepository.distanciaMetros(shape[it], GTFSRepository.coordenadaUTP)
            } ?: 0
            var distanciaM = acumulados[idx]
            var haciaAdelante = true
            val velocidad = 10.0   // m/s: dentro del umbral vehicular (4-30)

            // Pausa el GPS REAL para no entrelazar muestras (estar quieto en
            // casa romperia la evidencia vehicular del demo).
            gpsJob?.cancel()
            _demoActivo.value = true

            try {
                // Fase 1: viaje vehicular (~2 min = ~1.2 km por la linea real).
                actividadDemo = ActividadMotora.AUTOMOTIVE
                repeat(PASOS_VIAJE_DEMO) {
                    delay(2_000)
                    distanciaM += velocidad * 2 * (if (haciaAdelante) 1 else -1)
                    if (distanciaM >= total) { distanciaM = total; haciaAdelante = false }
                    if (distanciaM <= 0.0) { distanciaM = 0.0; haciaAdelante = true }

                    val p = puntoEnShape(shape, acumulados, distanciaM, haciaAdelante)
                    procesar(locationDemo(p, velocidad))
                }

                // Fase 2: descenso — caminata lenta hasta confirmar (4 muestras).
                actividadDemo = ActividadMotora.WALKING
                repeat(5) {
                    delay(2_000)
                    distanciaM += 1.4 * 2 * (if (haciaAdelante) 1 else -1)
                    val p = puntoEnShape(shape, acumulados, distanciaM, haciaAdelante)
                    procesar(locationDemo(p, 1.4))
                }
            } finally {
                actividadDemo = null
                _demoActivo.value = false
                // Retoma el GPS real si la contribucion sigue activa.
                if (_ejecutando.value && _consentimiento.value) observarUbicacion()
            }
        }
    }

    fun detenerViajeDemo() {
        val estabaActivo = _demoActivo.value
        demoJob?.cancel()
        demoJob = null
        actividadDemo = null
        _demoActivo.value = false
        if (estabaActivo && _ejecutando.value && _consentimiento.value) observarUbicacion()
    }

    /// Location sintetica con todo lo que el pipeline lee del GPS real.
    private fun locationDemo(p: PuntoDemo, velocidad: Double): Location =
        Location("rutautp-demo").apply {
            latitude = p.lat
            longitude = p.lon
            bearing = p.heading.toFloat()
            speed = velocidad.toFloat()
            accuracy = 8f
            time = System.currentTimeMillis()
        }

    private data class PuntoDemo(val lat: Double, val lon: Double, val heading: Double)

    /// Interpola la posicion a `distancia` metros sobre el shape y devuelve el
    /// rumbo del segmento en el sentido de la marcha.
    private fun puntoEnShape(
        shape: List<LatLng>,
        acumulados: DoubleArray,
        distancia: Double,
        haciaAdelante: Boolean
    ): PuntoDemo {
        var i = 0
        while (i < shape.size - 2 && distancia > acumulados[i + 1]) i++
        while (i > 0 && distancia < acumulados[i]) i--
        val a = acumulados[i]
        val b = acumulados[i + 1]
        val f = if (b > a) ((distancia - a) / (b - a)).coerceIn(0.0, 1.0) else 0.0
        val lat = shape[i].latitude + (shape[i + 1].latitude - shape[i].latitude) * f
        val lon = shape[i].longitude + (shape[i + 1].longitude - shape[i].longitude) * f
        val heading = if (haciaAdelante) {
            PolylineMatching.headingDegrees(shape[i], shape[i + 1])
        } else {
            PolylineMatching.headingDegrees(shape[i + 1], shape[i])
        }
        return PuntoDemo(lat, lon, if (heading < 0) 0.0 else heading)
    }
}

//----Punto de subida y referencias guardadas (BoardingPoint / BoardingPlaceStore del iOS)----

/// Punto donde el usuario dice que subio (solo al confirmar "Usar este punto").
@Serializable
data class PuntoSubida(val latitude: Double, val longitude: Double)

/// Referencia de subida asociada a la ruta y hora de registro (no hora de
/// abordaje verificada). Se guarda SOLO en el telefono, para un futuro
/// catalogo de paraderos de alumnos.
@Serializable
data class NotaLugarSubida(
    val id: String,
    val routeID: String,
    val line: String,
    val place: String,
    // Opcional para poder seguir leyendo las referencias anteriores sin mapa.
    val point: PuntoSubida? = null,
    val recordedAt: Double
)

object BoardingPlaceStore {
    const val LLAVE = "rutautp.boarding-place-notes.v1"
    private const val MAX_NOTAS = 200

    /// Texto normalizado: recortado a 120 y sin espacios; null si quedo vacio.
    fun normalizado(texto: String): String? {
        val valor = texto.trim().take(120)
        return valor.ifEmpty { null }
    }

    fun notas(context: Context?): List<NotaLugarSubida> {
        val ctx = context ?: return emptyList()
        val texto = ctx.getSharedPreferences("rutautp.tracking", Context.MODE_PRIVATE)
            .getString(LLAVE, null) ?: return emptyList()
        return try {
            kotlinx.serialization.json.Json.decodeFromString<List<NotaLugarSubida>>(texto)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /// Registra la referencia de subida (texto y/o punto). Historial local
    /// acotado a 200 entradas. Nunca publica nada: queda en este telefono.
    fun guardar(context: Context?, routeID: String, line: String, place: String, point: PuntoSubida?) {
        val ctx = context ?: return
        val lugar = normalizado(place)
        if (lugar == null && point == null) return
        val registros = notas(ctx).toMutableList()
        registros.add(
            NotaLugarSubida(
                id = UUID.randomUUID().toString(),
                routeID = routeID,
                line = line,
                place = lugar ?: "",
                point = point,
                recordedAt = System.currentTimeMillis() / 1000.0
            )
        )
        val texto = try {
            kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(NotaLugarSubida.serializer()),
                registros.takeLast(MAX_NOTAS)
            )
        } catch (e: Exception) {
            return
        }
        ctx.getSharedPreferences("rutautp.tracking", Context.MODE_PRIVATE)
            .edit().putString(LLAVE, texto).apply()
    }
}
