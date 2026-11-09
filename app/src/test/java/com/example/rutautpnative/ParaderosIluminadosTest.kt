package com.example.rutautpnative

import androidx.compose.ui.graphics.Color
import com.example.rutautpnative.data.gtfs.ParaderoGTFS
import com.example.rutautpnative.data.gtfs.ParaderosIluminados
import com.example.rutautpnative.data.gtfs.RutaGTFS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Test de la seleccion de paraderos iluminados (puerto de
// ParaderosIluminadosTests iOS): deduplicacion por coordenada, tope de
// cantidad, feed vacio y cantidad cero.
class ParaderosIluminadosTest {

    private fun ruta(id: String, stops: List<ParaderoGTFS>): RutaGTFS = RutaGTFS(
        id = id, linea = "C-01", variante = "", recorrido = "—", empresa = "—",
        colorHex = "00CC00", color = Color(0xFF00CC00),
        shape = stops.map { com.google.android.gms.maps.model.LatLng(it.lat, it.lon) },
        paraderos = stops,
        duracionMin = 10, headwayMin = 5, precio = 2.5,
        distanciaKm = 1.0, distanciaUTPMetros = 100.0
    )

    @Test
    fun seleccionNoDuplicaParaderos() {
        val stops = listOf(
            ParaderoGTFS("s1", "A", -8.10, -79.04),
            ParaderoGTFS("s2", "B", -8.11, -79.05),
            ParaderoGTFS("s3", "C", -8.12, -79.06)
        )
        // Dos rutas que comparten los MISMOS paraderos: la deduplicación por
        // coordenada debe dejar tres, no seis.
        val seleccion = ParaderosIluminados.seleccionar(
            listOf(ruta("R1", stops), ruta("R2", stops)), cantidad = 24
        )
        assertEquals(3, seleccion.size)
        val claves = seleccion.map { "${(it.lat * 1e6).toInt()}:${(it.lon * 1e6).toInt()}" }.toSet()
        assertEquals(seleccion.size, claves.size)
    }

    @Test
    fun seleccionRespetaElTope() {
        val stops = (0 until 20).map {
            ParaderoGTFS("s$it", "P$it", -8.10 - it * 0.001, -79.04)
        }
        assertTrue(
            ParaderosIluminados.seleccionar(listOf(ruta("R1", stops)), cantidad = 8).size <= 8
        )
    }

    @Test
    fun feedVacioDevuelveVacio() {
        assertTrue(ParaderosIluminados.seleccionar(emptyList(), cantidad = 24).isEmpty())
    }

    @Test
    fun cantidadCeroDevuelveVacio() {
        val stops = listOf(ParaderoGTFS("s1", "A", -8.10, -79.04))
        assertTrue(ParaderosIluminados.seleccionar(listOf(ruta("R1", stops)), cantidad = 0).isEmpty())
    }

    @Test
    fun seleccionIncluyeLosMasCercanosAlCampus() {
        // Con 10 paraderos y tope 10, los 8 más cercanos al campus UTP deben
        // estar en la selección (misma regla que el iOS).
        val cercanos = (0 until 5).map {
            // ~200-1000 m del campus (que está en -8.0982, -79.0382).
            ParaderoGTFS("c$it", "C$it", -8.0982 - it * 0.001, -79.0382)
        }
        val lejanos = (0 until 5).map {
            ParaderoGTFS("l$it", "L$it", -8.1300 - it * 0.001, -79.0500)
        }
        val seleccion = ParaderosIluminados.seleccionar(
            listOf(ruta("R1", cercanos + lejanos)), cantidad = 10
        )
        // Todos los cercanos deben estar.
        cercanos.forEach { c ->
            assertTrue("debería incluir ${c.id}", seleccion.any { it.id == c.id })
        }
    }
}
