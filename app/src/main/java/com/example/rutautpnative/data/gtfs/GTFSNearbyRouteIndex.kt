package com.example.rutautpnative.data.gtfs

import com.google.android.gms.maps.model.LatLng
import kotlin.math.*

/** Prefiltro conservador de main iOS; la distancia y el orden final siguen intactos. */
internal class GTFSNearbyRouteIndex(val catalogo: List<RutaGTFS>) {
    private data class Limites(val latMin: Double, val latMax: Double, val lonMin: Double, val lonMax: Double) {
        fun intersecta(b: Limites) = latMin <= b.latMax && b.latMin <= latMax && lonMin <= b.lonMax && b.lonMin <= lonMax
    }
    private val entradas = catalogo.map { it to limites(it.shape) }

    fun candidatas(punto: LatLng, radioMetros: Double): List<RutaGTFS> {
        val zona = zona(punto, radioMetros) ?: return catalogo
        return entradas.mapNotNull { (ruta, limites) -> if (limites == null || limites.intersecta(zona)) ruta else null }
    }

    private fun valida(p: LatLng) = p.latitude.isFinite() && p.longitude.isFinite() &&
        p.latitude in -90.0..90.0 && p.longitude in -180.0..180.0

    private fun limites(shape: List<LatLng>): Limites? {
        if (shape.isEmpty() || shape.any { !valida(it) || abs(it.latitude) >= 80 }) return null
        val latMin = shape.minOf { it.latitude }; val latMax = shape.maxOf { it.latitude }
        val lonMin = shape.minOf { it.longitude }; val lonMax = shape.maxOf { it.longitude }
        if (lonMax - lonMin > 180) return null
        return Limites(latMin - 1e-7, latMax + 1e-7, lonMin - 1e-7, lonMax + 1e-7)
    }

    private fun zona(p: LatLng, radio: Double): Limites? {
        if (!valida(p) || !radio.isFinite() || radio < 0) return null
        val angular = (radio + 1) / 6_371_000
        val deltaLat = Math.toDegrees(angular)
        val extrema = abs(p.latitude) + deltaLat
        if (extrema >= 80) return null
        val termino = sin(angular / 2) / cos(Math.toRadians(extrema))
        if (!termino.isFinite() || termino < 0 || termino >= 1) return null
        val deltaLon = Math.toDegrees(2 * asin(termino))
        val minLon = p.longitude - deltaLon; val maxLon = p.longitude + deltaLon
        if (minLon <= -180 || maxLon >= 180) return null
        return Limites(p.latitude - deltaLat, p.latitude + deltaLat, minLon, maxLon)
    }
}
