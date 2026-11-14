package com.example.rutautpnative.data.geo

import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.google.android.gms.maps.model.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

//----Utilidades de matching sobre polylines----
// Puerto de `Utils/PolylineMatching.swift` (iOS, rama 3D-BUS). Funciones puras
// compartidas por la flota del mapa (fase 2), por la proyeccion del usuario
// sobre el recorrido y el matching de rutas.
object PolylineMatching {

    /// Metros por grado de latitud (compartida con RouteCandidateMatcher).
    const val METROS_POR_GRADO = 111_320.0

    /// Interpolacion de la coordenada a una fraccion (0..1) del shape.
    /// Busqueda binaria sobre las distancias acumuladas + lerp lat/lon.
    /// Antes duplicada en NavegacionScreen.puntoEnFraccion y
    /// TrackingDemoScreen.RutaCombinada.puntoEn (16 lineas identicas).
    fun puntoEnFraccion(
        shape: List<LatLng>,
        acumulados: DoubleArray,
        fraccion: Double,
        totalMetros: Double
    ): LatLng {
        val objetivo = fraccion.coerceIn(0.0, 1.0) * totalMetros
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

    /// Rumbo urbano [0, 360) con correccion cos(lat): 0 = norte, horario.
    /// Equivale a `headingDegrees(from:to:movingForward:)` de iOS (la inversion
    /// de sentido se resuelve pasando los puntos ya ordenados por quien llama).
    /// Devuelve -1 si los puntos son iguales o invalidos (rumbo desconocido).
    fun headingDegrees(from: LatLng, to: LatLng): Double {
        val dLat = to.latitude - from.latitude
        val dLon = to.longitude - from.longitude
        if (dLat == 0.0 && dLon == 0.0) return -1.0

        // Plano local equirectangular: suficiente para segmentos urbanos (<5 km).
        val este = dLon * cos(Math.toRadians(from.latitude))
        val norte = dLat

        var h = Math.toDegrees(atan2(este, norte))
        h %= 360.0
        if (h < 0) h += 360.0
        return h
    }

    /// Reduce la densidad de la shape conservando el orden y el punto final
    /// (para animar/mostrar sin procesar los ~53k puntos crudos del feed).
    fun decimate(puntos: List<LatLng>, maxPoints: Int): List<LatLng> {
        if (puntos.size <= maxPoints || puntos.size < 2) return puntos
        val resultado = ArrayList<LatLng>(maxPoints)
        val paso = (puntos.size - 1).toDouble() / (maxPoints - 1)
        for (i in 0 until maxPoints - 1) {
            resultado.add(puntos[(i * paso).toInt().coerceAtMost(puntos.size - 2)])
        }
        resultado.add(puntos.last())
        return resultado
    }

    /// Distancia haversine acumulada (m) en cada waypoint: [0, d01, d01+d12, ...].
    fun distanciasAcumuladas(puntos: List<LatLng>): DoubleArray {
        val acumulados = DoubleArray(puntos.size)
        for (i in 1 until puntos.size) {
            acumulados[i] = acumulados[i - 1] + GTFSRepository.distanciaMetros(puntos[i - 1], puntos[i])
        }
        return acumulados
    }

    /// Longitud total del recorrido en metros.
    fun totalLengthMeters(puntos: List<LatLng>): Double {
        var total = 0.0
        for (i in 1 until puntos.size) total += GTFSRepository.distanciaMetros(puntos[i - 1], puntos[i])
        return total
    }

    //----Proyeccion punto -> polilinea----
    // Resultado de `proyectarEnShape`: distancia al trazado, segmento ganador
    // y fraccion de progreso (0..1) sobre la longitud total.
    class ProyeccionShape(
        val distanciaM: Double,
        val segmento: Int,
        val fraccion: Double
    )

    /// Proyeccion ortogonal closest-point-on-segment sobre cada segmento
    /// (plano local equirectangular con UN factor cos(lat); suficiente para
    /// segmentos urbanos < 5 km, como el match() del iOS).
    fun proyectarEnShape(punto: LatLng, shape: List<LatLng>): ProyeccionShape? {
        if (shape.size < 2) return null

        val factor = cos(Math.toRadians(punto.latitude))
        val px = punto.longitude * factor
        val py = punto.latitude

        var mejorDistancia = Double.MAX_VALUE
        var mejorSegmento = 0
        var mejorT = 0.0
        var mejorAcumulado = 0.0
        var mejorLargo = 0.0
        var total = 0.0

        for (i in 0 until shape.size - 1) {
            val ax = shape[i].longitude * factor
            val ay = shape[i].latitude
            val bx = shape[i + 1].longitude * factor
            val by = shape[i + 1].latitude

            val dx = bx - ax
            val dy = by - ay
            val l2 = dx * dx + dy * dy

            val t = if (l2 > 0) {
                (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0.0, 1.0)
            } else 0.0

            val proyX = ax + t * dx
            val proyY = ay + t * dy
            val dist = sqrt((px - proyX) * (px - proyX) + (py - proyY) * (py - proyY))
            val largo = sqrt(l2)

            if (dist < mejorDistancia) {
                mejorDistancia = dist
                mejorSegmento = i
                mejorT = t
                mejorAcumulado = total
                mejorLargo = largo
            }
            total += largo
        }

        val fraccion = if (total > 0) {
            ((mejorAcumulado + mejorLargo * mejorT) / total).coerceIn(0.0, 1.0)
        } else 0.0

        return ProyeccionShape(mejorDistancia * METROS_POR_GRADO, mejorSegmento, fraccion)
    }
}
