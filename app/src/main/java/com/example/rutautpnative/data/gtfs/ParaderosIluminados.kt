package com.example.rutautpnative.data.gtfs

import kotlin.math.roundToInt

//----Paraderos iluminados (selección)----
// GTFS no informa sobre iluminación real. Esto devuelve una muestra estable y
// determinística de `cantidad` paraderos, mezclando cercanía al campus con
// distribución en toda la red. Portado de ParaderosIluminados.seleccionar (iOS).
object ParaderosIluminados {

    fun seleccionar(feed: List<RutaGTFS>, cantidad: Int = 24): List<ParaderoGTFS> {
        if (cantidad <= 0) return emptyList()

        // Dedup por coordenada redondeada a 6 decimales (un solo paradero por
        // punto físico, aunque distintas rutas tengan ids distintos).
        val seen = mutableSetOf<String>()
        val stops = feed
            .flatMap { it.paraderos }
            .sortedBy { it.id }
            .filter {
                val key = "${(it.lat * 1e6).roundToInt()}:${(it.lon * 1e6).roundToInt()}"
                seen.add(key)
            }

        if (stops.isEmpty()) return emptyList()

        val nearCampus = stops.sortedBy {
            GTFSRepository.distanciaMetros(it.coordinate, GTFSRepository.coordenadaUTP)
        }

        // 8 más cercanos al campus + el resto repartido uniformemente por la red.
        val result = nearCampus.take(minOf(8, cantidad)).toMutableList()
        val remaining = stops.filter { s -> result.none { it.id == s.id } }
        val slots = minOf(cantidad - result.size, remaining.size)
        if (slots > 0) {
            for (i in 0 until slots) {
                result.add(remaining[i * remaining.size / slots])
            }
        }
        return result
    }
}