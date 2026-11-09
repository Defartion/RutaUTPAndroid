package com.example.rutautpnative.data.tracking

import com.example.rutautpnative.data.geo.PolylineMatching
import com.example.rutautpnative.data.gtfs.ParaderoGTFS
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.google.android.gms.maps.model.LatLng
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
//----Matching GPS contra rutas GTFS (puerto del RouteCandidateMatcher.swift)----
// Devuelve la ruta GEOMETRICAMENTE mas cercana sin filtrar por distancia:
// el umbral (40 m) lo aplica el motor de deteccion. Optimizacion exacta del
// iOS: descarta por bounding box (la distancia a la caja es cota inferior de
// la distancia al recorrido) antes de proyectar los ~250 vertices por ruta.

/// Ruta preparada para matching: shape decimado + paraderos + bbox.
class GeometriaRutaDeteccion(
    val routeId: String,
    val linea: String,
    val shape: List<LatLng>,
    val paraderos: List<ParaderoGTFS>,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val variante: String = ""
) {
    val lineaConLetra: String
        get() = if (variante.isEmpty()) linea else "$linea \"$variante\""

    // Letra del transporte (la variante entre comillas del short name GTFS).
    val letraTransporte: String get() = variante

    companion object {
        const val MAX_PUNTOS_SHAPE = 250

        fun desde(ruta: RutaGTFS): GeometriaRutaDeteccion? {
            if (ruta.shape.size < 2) return null
            val shape = PolylineMatching.decimate(ruta.shape, MAX_PUNTOS_SHAPE)
            if (shape.size < 2) return null
            return GeometriaRutaDeteccion(
                routeId = ruta.id,
                linea = ruta.linea,
                shape = shape,
                paraderos = ruta.paraderos,
                minLat = shape.minOf { it.latitude },
                maxLat = shape.maxOf { it.latitude },
                minLon = shape.minOf { it.longitude },
                maxLon = shape.maxOf { it.longitude },
                variante = ruta.variante
            )
        }
    }
}

/// Resultado del matching (RouteCandidateMatch del iOS).
data class EmparejamientoRuta(
    val routeId: String,
    val linea: String,
    val distanciaRuta: Double,          // m al shape
    val diferenciaRumbo: Double,         // 0..180; 180 = rumbo desconocido
    val distanciaParadero: Double,       // m al paradero mas cercano
    val segmentIndex: Int,
    val progreso: Double                 // fraccion 0..1 del recorrido
)

object RouteCandidateMatcher {

    // Constante compartida con PolylineMatching (antes duplicada aqui).
    private val METROS_POR_GRADO get() = PolylineMatching.METROS_POR_GRADO

    fun emparejamientoMasCercano(
        lat: Double,
        lon: Double,
        heading: Double,   // rumbo de brujula del dispositivo; <0/no finito = desconocido
        rutas: List<GeometriaRutaDeteccion>
    ): EmparejamientoRuta? {
        var mejor: EmparejamientoRuta? = null
        var mejorDistancia = Double.MAX_VALUE

        for (ruta in rutas) {
            // Pruning por bbox: distancia al cuadro >= mejor actual -> fuera.
            val cota = distanciaACaja(lat, lon, ruta)
            if (cota >= mejorDistancia) continue

            val proyeccion = PolylineMatching.proyectarEnShape(LatLng(lat, lon), ruta.shape)
            if (proyeccion == null || proyeccion.distanciaM >= mejorDistancia) continue

            mejorDistancia = proyeccion.distanciaM

            // Rumbo del segmento ganador, en el sentido de la ruta.
            val rumboSegmento = PolylineMatching.headingDegrees(
                ruta.shape[proyeccion.segmento],
                ruta.shape[proyeccion.segmento + 1]
            )

            // Diferencia angular 0..180. Rumbo desconocido (dispositivo o
            // segmento degenerado) -> 180: nunca cuenta como alineado (iOS).
            val diferencia = when {
                rumboSegmento < 0 -> 180.0
                !heading.isFinite() || heading < 0 -> 180.0
                else -> {
                    val d = abs(heading - rumboSegmento) % 360.0
                    min(d, 360.0 - d)
                }
            }

            mejor = EmparejamientoRuta(
                routeId = ruta.routeId,
                linea = ruta.linea,
                distanciaRuta = proyeccion.distanciaM,
                diferenciaRumbo = diferencia,
                distanciaParadero = distanciaParaderoMasCercano(lat, lon, ruta.paraderos),
                segmentIndex = proyeccion.segmento,
                progreso = proyeccion.fraccion
            )
        }

        return mejor
    }

    //----Cota inferior por bounding box----
    // Distancia (m, aproximada por ejes) del punto al cuadro; 0 si esta dentro.
    private fun distanciaACaja(lat: Double, lon: Double, ruta: GeometriaRutaDeteccion): Double {
        val dLat = max(0.0, max(ruta.minLat - lat, lat - ruta.maxLat))
        val dLon = max(0.0, max(ruta.minLon - lon, lon - ruta.maxLon))
        val mLat = dLat * METROS_POR_GRADO
        val mLon = dLon * METROS_POR_GRADO * cos(Math.toRadians(lat))
        return sqrt(mLat * mLat + mLon * mLon)
    }

    private fun distanciaParaderoMasCercano(lat: Double, lon: Double, paraderos: List<ParaderoGTFS>): Double {
        val factor = cos(Math.toRadians(lat))
        var minima = Double.MAX_VALUE
        for (p in paraderos) {
            val dx = (p.lon - lon) * factor * METROS_POR_GRADO
            val dy = (p.lat - lat) * METROS_POR_GRADO
            val d = sqrt(dx * dx + dy * dy)
            if (d < minima) minima = d
        }
        return minima
    }
}
