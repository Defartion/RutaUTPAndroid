package com.example.rutautpnative.data.tracking

import com.example.rutautpnative.data.geo.PolylineMatching
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.google.android.gms.maps.model.LatLng
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

//----Estimador de llegada para vehiculos reales (VehicleETAEstimator del iOS)----
// "No inventa": si falta el rumbo, el vehiculo va en sentido contrario, esta
// lejos del trazado o no se puede estimar la velocidad, devuelve null y la
// UI muestra "SIN ETA". Las posiciones reales muestran "~N MIN" (aproximado);
// el "~" distingue lo estimado de la simulacion.
object VehicleETAEstimator {

    private const val UMBRAL_VEHICULO_M = 120.0       // el vehiculo debe estar sobre el shape
    private const val UMBRAL_OBJETIVO_M = 800.0      // el punto consultado, igual
    private const val MAX_DIFERENCIA_SENTIDO_GRADOS = 90.0
    private const val UMBRAL_RUTA_CIRCULAR_M = 200.0 // extremos casi unidos = ramal circular
    private const val VEL_MIN_OBSERVADA_MS = 0.5     // por debajo se considera detenido
    private const val VEL_MAX_OBSERVADA_MS = 15.0
    private const val VEL_PROGRAMADA_FALLBACK_MS = 7.0
    private const val MAX_MINUTOS = 120

    fun minutos(posicion: VehiclePosition, ruta: RutaGTFS, objetivo: LatLng): Int? {
        val shape = PolylineMatching.decimate(ruta.shape, 250)
        if (shape.size < 2) return null
        val total = PolylineMatching.totalLengthMeters(shape)
        if (total <= 0) return null

        // Vehiculo y punto consultado deben caer sobre el recorrido.
        val proyVehiculo = PolylineMatching.proyectarEnShape(
            LatLng(posicion.lat, posicion.lon), shape
        ) ?: return null
        if (proyVehiculo.distanciaM > UMBRAL_VEHICULO_M) return null

        val proyObjetivo = PolylineMatching.proyectarEnShape(objetivo, shape) ?: return null
        if (proyObjetivo.distanciaM > UMBRAL_OBJETIVO_M) return null

        // Rumbo valido obligatorio: sin rumbo no hay sentido, y sin sentido
        // no hay llegada estimada.
        val heading = posicion.heading
        if (!heading.isFinite() || heading < 0) return null
        val rumboSegmento = PolylineMatching.headingDegrees(
            shape[proyVehiculo.segmento],
            shape[proyVehiculo.segmento + 1]
        )
        if (rumboSegmento < 0) return null
        val dif = abs(heading - rumboSegmento) % 360.0
        // Diferencia angular > 90 grados = el vehiculo se aleja del punto.
        if (min(dif, 360.0 - dif) > MAX_DIFERENCIA_SENTIDO_GRADOS) return null

        var delta = proyObjetivo.fraccion - proyVehiculo.fraccion
        if (GTFSRepository.distanciaMetros(shape.first(), shape.last()) <= UMBRAL_RUTA_CIRCULAR_M) {
            // Ramal circular: la vuelta larga tambien llega — se envuelve.
            delta %= 1.0
            if (delta < 0) delta += 1.0
        } else if (delta < 0) {
            // Lineal y ya paso el punto: SIN ETA (no se inventa la vuelta).
            return null
        }

        val restanteM = delta * total
        if (restanteM <= 1.0) return 1

        // Velocidad efectiva: la observada si es util; si esta detenido o no
        // informa, la programada del feed (fallback 7 m/s).
        val velocidad = when {
            posicion.speed >= VEL_MIN_OBSERVADA_MS -> min(posicion.speed, VEL_MAX_OBSERVADA_MS)
            ruta.duracionMin > 0 && ruta.distanciaKm > 0 ->
                (ruta.distanciaKm * 1000.0 / (ruta.duracionMin * 60.0)).coerceIn(3.0, 14.0)
            else -> VEL_PROGRAMADA_FALLBACK_MS
        }

        val estimados = ceil(restanteM / velocidad / 60.0).toInt()
        if (estimados > MAX_MINUTOS) return null
        return max(1, estimados)
    }
}
