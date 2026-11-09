package com.example.rutautpnative

import com.example.rutautpnative.data.geo.PolylineMatching
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Test de matching sobre polilineas (puerto de PolylineMatchingTests iOS):
// proyeccion sobre recorridos, deteccion de fuera de ruta, decimacion con
// extremos conservados, longitudes totales y umbral de recalculo.
class PolylineMatchingTest {

    // Linea recta de ~1.1 km sobre el paralelo -8.10.
    private val inicio = LatLng(-8.10, -79.04)
    private val fin = LatLng(-8.10, -79.03)
    private val recta get() = listOf(inicio, fin)

    @Test
    fun matchProyectaSobreLaLinea() {
        // Punto a ~55 m al norte del punto medio.
        val punto = LatLng(-8.0995, -79.035)
        val match = PolylineMatching.proyectarEnShape(punto, recta) ?: return run { assertFalse(true) }
        assertTrue("debería proyectar sobre la recta", match.distanciaM < 100.0)
        // El punto medio de la recta está al 50% del recorrido.
        assertTrue("fraccion=${match.fraccion}", match.fraccion in 0.4..0.6)
    }

    @Test
    fun matchDetectaFueraDeRuta() {
        // Punto a ~550 m de la recta: fuera de ruta con umbral holgado.
        val punto = LatLng(-8.105, -79.035)
        val match = PolylineMatching.proyectarEnShape(punto, recta)
        // proyectarEnShape no filtra por distancia (eso lo hace el consumidor
        // con su umbral): verifica que la distancia sea grande.
        if (match != null) {
            assertTrue("a ~550 m no puede considerarse en ruta", match.distanciaM > 400.0)
        }
    }

    @Test
    fun matchNecesitaAlMenosDosPuntos() {
        assertNull(PolylineMatching.proyectarEnShape(inicio, listOf(inicio)))
    }

    @Test
    fun decimateConservaExtremosYCuenta() {
        val puntos = (0..99).map { LatLng(-8.10, -79.04 + it * 0.0001) }
        val reducidos = PolylineMatching.decimate(puntos, 10)
        assertEquals(10, reducidos.size)
        assertEquals(puntos.first().longitude, reducidos.first().longitude, 1e-9)
        assertEquals(puntos.last().longitude, reducidos.last().longitude, 1e-9)
    }

    @Test
    fun decimateNoAmpliaListasCortas() {
        val puntos = listOf(inicio, fin)
        assertEquals(2, PolylineMatching.decimate(puntos, 10).size)
    }

    @Test
    fun longitudTotalDeLaRecta() {
        // 0.01° de longitud a latitud -8.10 ≈ 1.10 km.
        val metros = PolylineMatching.totalLengthMeters(recta)
        assertTrue("metros=$metros", metros in 1000.0..1200.0)
    }

    @Test
    fun distanciasAcumuladasEmpiezanEnCeroYTerminanEnElTotal() {
        val acumulados = PolylineMatching.distanciasAcumuladas(recta)
        assertEquals(0.0, acumulados[0], 1e-9)
        assertEquals(PolylineMatching.totalLengthMeters(recta), acumulados.last(), 0.001)
    }
}
