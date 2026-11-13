package com.example.rutautpnative.data.places

import com.example.rutautpnative.data.gtfs.GTFSCSV
import kotlin.math.cos

/** Área del servicio: todos los paraderos y sedes, más el radio peatonal de 1600 m. */
data class CoberturaBusqueda(val sur: Double, val oeste: Double, val norte: Double, val este: Double) {
    fun contiene(lat: Double, lon: Double): Boolean = lat.isFinite() && lon.isFinite() && lat in sur..norte && lon in oeste..este
    companion object {
        fun desde(puntos: List<Pair<Double, Double>>, margenMetros: Double = 1600.0): CoberturaBusqueda {
            require(puntos.isNotEmpty() && margenMetros.isFinite() && margenMetros in 0.0..10000.0)
            require(puntos.all { (a,b) -> a.isFinite() && b.isFinite() && a in -90.0..90.0 && b in -180.0..180.0 })
            val latMin = puntos.minOf { it.first }; val latMax = puntos.maxOf { it.first }
            val dLat = margenMetros / 111_000.0
            val dLon = margenMetros / (111_000.0 * cos(Math.toRadians((latMin + latMax) / 2)).coerceAtLeast(0.01))
            return CoberturaBusqueda((latMin-dLat).coerceAtLeast(-90.0), (puntos.minOf { it.second }-dLon).coerceAtLeast(-180.0),
                (latMax+dLat).coerceAtMost(90.0), (puntos.maxOf { it.second }+dLon).coerceAtMost(180.0))
        }
        fun desdeGTFS(stops: String, sedes: List<Pair<Double, Double>>): CoberturaBusqueda {
            val tabla = GTFSCSV.parsear(stops); tabla.exigir("stop_lat", "stop_lon")
            return desde(tabla.filas.indices.map { tabla.valor(it, "stop_lat").toDouble() to tabla.valor(it, "stop_lon").toDouble() } + sedes)
        }
    }
}
