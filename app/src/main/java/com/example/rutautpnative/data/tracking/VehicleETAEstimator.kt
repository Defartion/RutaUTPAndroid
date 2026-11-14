package com.example.rutautpnative.data.tracking

import com.example.rutautpnative.data.geo.PolylineMatching
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.data.routing.TransitPlanner
import com.google.android.gms.maps.model.LatLng
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

//----Estimador de llegada para vehiculos reales (VehicleETAEstimator del iOS)----
// Sin rumbo, con el vehiculo detenido o alejandose del objetivo, o fuera
// del trazado, devuelve null y la
// UI muestra "SIN ETA". Las posiciones reales muestran "~N MIN" (aproximado);
// el "~" distingue lo estimado de la simulacion.
object VehicleETAEstimator {

    private const val UMBRAL_VEHICULO_M = 120.0       // el vehiculo debe estar sobre el shape
    private const val MAX_DIFERENCIA_SENTIDO_GRADOS = 90.0
    private const val UMBRAL_RUTA_CIRCULAR_M = 200.0 // extremos casi unidos = ramal circular
    private const val VEL_MIN_OBSERVADA_MS = 0.5     // por debajo se considera detenido
    private const val VEL_MAX_OBSERVADA_MS = 15.0
    private const val VEL_PROGRAMADA_FALLBACK_MS = 7.0
    private const val MAX_MINUTOS = 120

    fun minutos(posicion: VehiclePosition, ruta: RutaGTFS, objetivo: LatLng): Int? {
        val preparada = prepararRuta(ruta) ?: return null
        return minutos(posicion, prepararObjetivo(preparada, objetivo))
    }

    internal class RutaPreparada(val shape: List<LatLng>, val total: Double,
        val circular: Boolean, val velocidadProgramada: Double)
    internal class ObjetivoPreparado(val ruta: RutaPreparada, val proyeccion: PolylineMatching.ProyeccionShape?)

    internal fun prepararRuta(ruta: RutaGTFS): RutaPreparada? {
        if (ruta.shape.size < 2) return null
        // Copia una sola vez: una ruta instalada no cambia por mutar una lista externa.
        val shape = ruta.shape.toList()
        val total = PolylineMatching.totalLengthMeters(shape)
        if (!total.isFinite() || total <= 0) return null
        return RutaPreparada(shape, total,
            GTFSRepository.distanciaMetros(shape.first(), shape.last()) <= UMBRAL_RUTA_CIRCULAR_M,
            if (ruta.duracionMin > 0) total / (ruta.duracionMin * 60.0) else VEL_PROGRAMADA_FALLBACK_MS)
    }

    internal fun prepararObjetivo(ruta: RutaPreparada, objetivo: LatLng) =
        ObjetivoPreparado(ruta, PolylineMatching.proyectarEnShape(objetivo, ruta.shape)
            ?.takeIf { it.distanciaM <= TransitPlanner.RADIO_PIE_METROS })

    internal fun minutos(posicion: VehiclePosition, objetivo: ObjetivoPreparado): Int? {
        val ruta = objetivo.ruta
        val shape = ruta.shape
        val total = ruta.total

        // Vehiculo y punto consultado deben caer sobre el recorrido.
        val proyVehiculo = PolylineMatching.proyectarEnShape(
            LatLng(posicion.lat, posicion.lon), shape
        ) ?: return null
        if (proyVehiculo.distanciaM > UMBRAL_VEHICULO_M) return null

        val proyObjetivo = objetivo.proyeccion ?: return null

        // Rumbo valido obligatorio: sin rumbo no hay sentido, y sin sentido
        // no hay llegada estimada.
        val heading = posicion.heading
        if (!heading.isFinite() || heading < 0) return null
        val rumboSegmento = PolylineMatching.headingDegrees(
            shape[proyVehiculo.segmento],
            shape[proyVehiculo.segmento + 1]
        )
        if (!rumboSegmento.isFinite() || rumboSegmento < 0) return null
        val dif = abs(heading - rumboSegmento) % 360.0
        val haciaAdelante = min(dif, 360.0 - dif) <= MAX_DIFERENCIA_SENTIDO_GRADOS

        var delta = proyObjetivo.fraccion - proyVehiculo.fraccion
        if (ruta.circular) {
            if (haciaAdelante && delta < 0) delta += 1.0
            if (!haciaAdelante && delta > 0) delta -= 1.0
        } else if (if (haciaAdelante) delta < 0 else delta > 0) {
            // En cualquiera de los sentidos, solo estimar si se acerca.
            return null
        }

        val restanteM = abs(delta) * total
        if (!restanteM.isFinite() || !posicion.speed.isFinite()) return null
        // Detenido no equivale a velocidad desconocida, incluso en el destino.
        if (posicion.speed >= 0 && posicion.speed < VEL_MIN_OBSERVADA_MS) return null

        // Solo una velocidad desconocida usa el horario y la longitud real.
        val velocidad = when {
            posicion.speed >= VEL_MIN_OBSERVADA_MS -> min(posicion.speed, VEL_MAX_OBSERVADA_MS)
            else -> ruta.velocidadProgramada.coerceIn(0.5, VEL_MAX_OBSERVADA_MS)
        }

        val estimados = ceil(restanteM / velocidad / 60.0)
        if (!estimados.isFinite() || estimados > MAX_MINUTOS) return null
        return max(1, estimados.toInt())
    }
}
