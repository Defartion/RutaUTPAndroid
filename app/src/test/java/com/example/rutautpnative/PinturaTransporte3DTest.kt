package com.example.rutautpnative

import com.example.rutautpnative.ui.screens.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import java.util.zip.GZIPInputStream

class PinturaTransporte3DTest {
    private val assets = File(System.getProperty("rutautp.assets"), "transportes3d")
    private fun cargar(nombre: String): Pair<MeshTransporte, PartesModelo3D> {
        val mesh = DataInputStream(GZIPInputStream(File(assets, "$nombre.mesh.gz").inputStream())).use {
            assertEquals(0x52555433, it.readInt())
            val n = it.readInt()
            MeshTransporte(bufferModelo3D(FloatArray(n * 10) { _ -> it.readFloat() }), n)
        }
        return mesh to Json.decodeFromString(File(assets, "$nombre.partes.json").readText())
    }

    @Test fun seisModelosConservanVentanasYRuedasAlPintarLaCarroceria() {
        val nombres = assets.listFiles()!!.filter { it.name.endsWith(".partes.json") }.map { it.name.removeSuffix(".partes.json") }
        assertEquals(6, nombres.size)
        nombres.forEach { nombre ->
            val (mesh, partes) = cargar(nombre)
            val copia = FloatArray(mesh.cantidad * 10).also { mesh.vertices.duplicate().get(it) }
            val nueva = identificarModelo3D(mesh, partes, nombre, IdentidadModelo3D("C-01", "B ♥", listOf(0xFF00FF00.toInt())), 0xFFFF0000.toInt())
            assertEquals(mesh.cantidad, nueva.cantidad)
            assertNotNull(nueva.cartel)
            assertEquals("B ♥", nueva.cartel!!.letra)
            fun contiene(rangos: List<List<Int>>, n: Int) = rangos.any { n in it[0] until it[0] + it[1] }
            repeat(mesh.cantidad) { n ->
                // La identificación no cambia ninguna posición ni normal del USDZ.
                repeat(6) { c -> assertEquals(copia[n * 10 + c], nueva.vertices[n * 10 + c], 0f) }
                val rgb = when {
                    contiene(partes.carrocerias, n) -> floatArrayOf(0f, 1f, 0f)
                    contiene(partes.contornos, n) -> floatArrayOf(1f, 0f, 0f)
                    else -> FloatArray(3) { copia[n * 10 + 6 + it] }
                }
                repeat(3) { c -> assertEquals(rgb[c], nueva.vertices[n * 10 + 6 + c], 0f) }
            }
            repeat(30) { assertTrue(nueva.cartel!!.vertices[it].isFinite()) }
        }
    }

    @Test fun franjasDeReferenciaAgreganDosCostadosSinModificarLaMallaOriginal() {
        val (mesh, partes) = cargar("detallado_combi")
        val nueva = identificarModelo3D(mesh, partes, "detallado_combi", IdentidadModelo3D("C-01", "A", listOf(-1, 0xFF0000FF.toInt(), 0xFFFFFF00.toInt())), -1)
        assertEquals(mesh.cantidad + 2 * 2 * 36, nueva.cantidad)
        for (n in mesh.cantidad until nueva.cantidad) {
            repeat(10) { assertTrue(nueva.vertices[n * 10 + it].isFinite()) }
            assertTrue(nueva.vertices[n * 10 + 7] == 0f || nueva.vertices[n * 10 + 7] == 1f)
        }
        assertEquals(mesh.cantidad * 10, mesh.vertices.capacity())
        assertNull(mesh.cartel)
    }

    @Test fun galeriaNoInventaPinturaNiCartelDeLinea() {
        val (mesh, partes) = cargar("original_micro")
        val nueva = identificarModelo3D(mesh, partes, "original_micro", null, -1)
        assertEquals(mesh.cantidad, nueva.cantidad)
        assertNull(nueva.cartel)
    }
}
