package com.example.rutautpnative.data.tracking

//----Deteccion pasiva de abordaje/descenso (puerto de Detection/ de iOS)----
// Motor 100% puro (sin sensores): recibe muestras ya combinadas de GPS +
// actividad fisica + matching contra rutas GTFS, y decide si el pasajero
// esta a bordo. SOLO el estado ONBOARD autoriza publicaciones MQTT.
//
// Umbrales y estados son los del PassengerDetectionEngine.swift (hipotesis
// MVP "a validar en campo" del equipo iOS).

/// Estados de la deteccion (mismo flujo que el enum de iOS).
enum class EstadoDeteccionPasajero {
    IDLE,               // sin viaje detectado
    APPROACHING_STOP,   // camina/quieto cerca de un paradero (NO publica)
    BOARDING_CANDIDATE, // movimiento vehicular compatible, faltan muestras
    ONBOARD,            // abordaje confirmado: UNICO estado que publica
    ALIGHTING_CANDIDATE // tras estar a bordo, muestras caminando/corriendo
}

/// Actividad fisica detectada. valorPayload = rawValue EXACTO del iOS, es el
/// string que viaja en el payload MQTT (motionActivity).
enum class ActividadMotora(val valorPayload: String) {
    STATIONARY("stationary"),
    WALKING("walking"),
    RUNNING("running"),
    AUTOMOTIVE("automotive"),
    CYCLING("cycling"),
    UNKNOWN("unknown")
}

/// Una muestra combinada para el motor (equivalente a PassengerDetectionSample).
data class MuestraDeteccion(
    val timestamp: Double,               // UNIX segundos del fix GPS
    val speed: Double,                   // m/s del GPS
    val horizontalAccuracy: Double,      // m
    val distanceToRoute: Double,         // m al shape GTFS mas cercano
    val headingDifference: Double,       // 0..180, rumbo dispositivo vs ruta
    val distanceToNearestStop: Double,   // m
    val motionActivity: ActividadMotora
)

/// Resultado de procesar una muestra (PassengerDetectionDecision).
data class DecisionDeteccion(
    val estado: EstadoDeteccionPasajero,
    val debePublicar: Boolean,           // solo en ONBOARD
    val confirmoAbordaje: Boolean,       // transicion -> ONBOARD
    val confirmoDescenso: Boolean        // transicion -> IDLE
)

/// Umbrales MVP (PassengerDetectionThresholds.default del iOS).
data class UmbralesDeteccion(
    val maximaPrecision: Double = 50.0,        // descarta muestras > 50 m
    val maximaDistanciaRuta: Double = 40.0,    // "a bordo" si <= 40 m del shape
    val maximaDiferenciaRumbo: Double = 45.0,  // rumbo alineado con la ruta
    val maximaDistanciaParadero: Double = 80.0,
    val minimaVelocidadVehiculo: Double = 4.0, // 14.4 km/h
    val maximaVelocidadVehiculo: Double = 30.0, // 108 km/h
    val evidenciasAbordaje: Int = 3,           // muestras vehiculares seguidas
    val evidenciasDescenso: Int = 4            // muestras caminando/corriendo
)

/// La maquina de estados (PassengerDetectionEngine). Mutable y NO thread-safe:
/// la alimenta un unico consumidor (el coordinador) desde el hilo principal.
class MotorDeteccionPasajero(
    private val umbrales: UmbralesDeteccion = UmbralesDeteccion()
) {
    var estado: EstadoDeteccionPasajero = EstadoDeteccionPasajero.IDLE
        private set

    private var evidenciaAbordaje = 0
    private var evidenciaDescenso = 0

    fun procesar(m: MuestraDeteccion): DecisionDeteccion {
        // Muestra imprecisa: no publica y mantiene el estado (no rompe la
        // evidencia acumulada, igual que el iOS).
        if (!m.horizontalAccuracy.isFinite() ||
            m.horizontalAccuracy < 0 ||
            m.horizontalAccuracy > umbrales.maximaPrecision
        ) {
            return DecisionDeteccion(estado, debePublicar = false, confirmoAbordaje = false, confirmoDescenso = false)
        }

        val cercaRuta = m.distanceToRoute <= umbrales.maximaDistanciaRuta
        val rumboAlineado = m.headingDifference <= umbrales.maximaDiferenciaRumbo
        val cercaParadero = m.distanceToNearestStop <= umbrales.maximaDistanciaParadero
        val velocidadVehiculo =
            m.speed >= umbrales.minimaVelocidadVehiculo && m.speed <= umbrales.maximaVelocidadVehiculo
        // La actividad "unknown" es compatible con vehiculo (GPS solo, sin
        // permiso de actividad fisica): el rumbo/velocidad/escenario hacen el
        // resto. Igual que el motor iOS.
        val actividadVehiculo =
            m.motionActivity == ActividadMotora.AUTOMOTIVE || m.motionActivity == ActividadMotora.UNKNOWN

        val evidenciaVehiculo = cercaRuta && rumboAlineado && velocidadVehiculo && actividadVehiculo
        val evidenciaCaminata = m.motionActivity == ActividadMotora.WALKING || m.motionActivity == ActividadMotora.RUNNING
        val acercandoParadero =
            cercaParadero && (m.motionActivity == ActividadMotora.WALKING || m.motionActivity == ActividadMotora.STATIONARY)

        var abordajeConfirmado = false
        var descensoConfirmado = false

        when (estado) {
            EstadoDeteccionPasajero.IDLE ->
                if (evidenciaVehiculo) {
                    evidenciaAbordaje = 1
                    estado = EstadoDeteccionPasajero.BOARDING_CANDIDATE
                } else if (acercandoParadero) {
                    estado = EstadoDeteccionPasajero.APPROACHING_STOP
                }

            EstadoDeteccionPasajero.APPROACHING_STOP ->
                if (evidenciaVehiculo) {
                    evidenciaAbordaje = 1
                    estado = EstadoDeteccionPasajero.BOARDING_CANDIDATE
                } else if (!cercaParadero) {
                    estado = EstadoDeteccionPasajero.IDLE
                }

            EstadoDeteccionPasajero.BOARDING_CANDIDATE ->
                if (evidenciaVehiculo) {
                    evidenciaAbordaje++
                    if (evidenciaAbordaje >= umbrales.evidenciasAbordaje) {
                        estado = EstadoDeteccionPasajero.ONBOARD
                        evidenciaAbordaje = 0
                        abordajeConfirmado = true
                    }
                } else {
                    evidenciaAbordaje = 0
                    estado = if (acercandoParadero) EstadoDeteccionPasajero.APPROACHING_STOP
                    else EstadoDeteccionPasajero.IDLE
                }

            EstadoDeteccionPasajero.ONBOARD ->
                if (evidenciaCaminata) {
                    evidenciaDescenso = 1
                    estado = EstadoDeteccionPasajero.ALIGHTING_CANDIDATE
                }

            EstadoDeteccionPasajero.ALIGHTING_CANDIDATE ->
                if (evidenciaVehiculo) {
                    evidenciaDescenso = 0
                    estado = EstadoDeteccionPasajero.ONBOARD
                } else if (evidenciaCaminata) {
                    evidenciaDescenso++
                    if (evidenciaDescenso >= umbrales.evidenciasDescenso) {
                        estado = EstadoDeteccionPasajero.IDLE
                        evidenciaDescenso = 0
                        descensoConfirmado = true
                    }
                } else {
                    evidenciaDescenso = 0
                    estado = EstadoDeteccionPasajero.ONBOARD
                }
        }

        return DecisionDeteccion(
            estado = estado,
            debePublicar = estado == EstadoDeteccionPasajero.ONBOARD,
            confirmoAbordaje = abordajeConfirmado,
            confirmoDescenso = descensoConfirmado
        )
    }

    fun reset() {
        estado = EstadoDeteccionPasajero.IDLE
        evidenciaAbordaje = 0
        evidenciaDescenso = 0
    }
}
