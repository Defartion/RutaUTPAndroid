package com.example.rutautpnative.data.tracking

//----Posicion de vehiculo en vivo (puerto del VehiclePosition.swift)----
// Posicion publicada por el backend en rutautp/vehiculos/{id}/posicion, ya
// combinada por el servidor a partir de las observaciones de los pasajeros.
// NO es una observacion cruda: es la estimacion del vehiculo.
data class VehiclePosition(
    val id: String,          // placa o id del backend
    val linea: String,       // "10", "C-01"
    val routeId: String,     // route_id GTFS (vacio = no informado)
    val lat: Double,
    val lon: Double,
    val heading: Double,     // rumbo [0,360); -1 desconocido
    val speed: Double,       // m/s; -1 desconocido
    val timestamp: Double,    // UNIX segundos
    val dataOrigin: VehicleDataOrigin = VehicleDataOrigin.UNKNOWN,
    val simulationRunId: String? = null,
    val controlRevision: Long? = null,
    val scenarioOccupancy: String? = null
    //
)

//----Sanitizador de entrada (puerto del VehiclePositionSanitizer.swift)----
// El broker es un canal compartido: nada garantiza que el mensaje entrante
// sea razonable, asi que se filtra ANTES de tocar el estado que alimenta el
// mapa. Solo speed y heading son "reparables"; coordenadas y timestamp
// imposibles descartan la muestra completa.
object VehiclePositionSanitizer {

    /// Una posicion queda obsoleta tras 45 s sin novedades (9 ciclos de la
    /// baliza de 5 s del pasajero).
    const val LIMITE_OBSOLESCENCIA_S = 45.0

    /// Tolerancia contra relojes desincronizados: un mensaje "del futuro"
    /// solo se tolera hasta 10 s.
    const val TOLERANCIA_RELOJ_S = 10.0

    const val MAX_SPEED_MS = 100.0

    fun sanitizar(p: VehiclePosition, ahoraS: Double): VehiclePosition? {
        // Coordenas imposibles jamas entran al mapa.
        if (!p.lat.isFinite() || p.lat !in -90.0..90.0) return null
        if (!p.lon.isFinite() || p.lon !in -180.0..180.0) return null
        if (!p.timestamp.isFinite()) return null
        if (p.timestamp > ahoraS + TOLERANCIA_RELOJ_S) return null
        if (ahoraS - p.timestamp > LIMITE_OBSOLESCENCIA_S) return null

        // Reparables: speed (clamp, -1 si no finita) y heading (mod 360).
        val speed = if (!p.speed.isFinite()) -1.0 else p.speed.coerceIn(-1.0, MAX_SPEED_MS)
        val heading = when {
            !p.heading.isFinite() || p.heading < 0 -> -1.0
            else -> p.heading % 360.0
        }
        return p.copy(speed = speed, heading = heading)
    }
}
