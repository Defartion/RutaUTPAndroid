package com.example.rutautpnative

import com.example.rutautpnative.data.gtfs.GTFSCSV
import com.example.rutautpnative.data.notificaciones.CambioRutaRemoto
import com.example.rutautpnative.data.notificaciones.ReciboRegistroPush
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import java.util.zip.GZIPInputStream

class RecursosParidadTest {
    private val assets: File get() = File(requireNotNull(System.getProperty("rutautp.assets")))
    @Test fun seisModelosOriginalesSonMallasCompletasYFinitas() {
        val modelos = File(assets, "transportes3d").listFiles().orEmpty().filter { it.name.endsWith(".mesh.gz") }
        assertEquals(6, modelos.size)
        modelos.forEach { file -> DataInputStream(GZIPInputStream(file.inputStream())).use { input ->
            assertEquals(0x52555433, input.readInt())
            val n = input.readInt()
            assertTrue(n in 3..300_000); assertEquals(0, n % 3)
            repeat(n * 10) { assertTrue(input.readFloat().isFinite()) }
            assertEquals(-1, input.read())
        } }
    }
    @Test fun referenciasConservanIdentidadCompletaDeLas102Rutas() {
        val refs = Json.parseToJsonElement(File(assets, "referencias_transporte.json").readText()).jsonArray
        assertEquals(102, refs.size)
        assertEquals(102, refs.map { it.jsonObject.getValue("routeId").jsonPrimitive.content }.distinct().size)
        val routes = GTFSCSV.parsear(File(assets, "gtfs/routes.txt").readText())
        val porId = (0 until routes.rowCount).associateBy { routes.valor(it, "route_id") }
        refs.forEach { ref ->
            val r = ref.jsonObject
            assertTrue(porId.containsKey(r.getValue("routeId").jsonPrimitive.content))
            assertTrue(r.getValue("colores").jsonArray.isNotEmpty())
        }
    }
    @Test fun traduccionesIncluyenLosSeisIdiomasDelOrigen() {
        val catalogo = Json.parseToJsonElement(File(assets, "traducciones.json").readText()).jsonObject
        assertTrue(catalogo.size > 100)
        listOf("en", "fr", "pt-BR", "de", "zh-Hans").forEach { codigo -> assertTrue(catalogo.values.any { codigo in it.jsonObject }) }
    }
    @Test fun pushNoAceptaEventosMalformados() {
        val data = mapOf("schemaVersion" to "1", "kind" to "routeChange", "eventID" to "12345678-1234-1234-1234-123456789abc", "routeID" to "17522670", "expiresAt" to "1900000000")
        assertNotNull(CambioRutaRemoto.leer(data))
        assertNull(CambioRutaRemoto.leer(data + ("expiresAt" to "NaN")))
        assertNull(CambioRutaRemoto.leer(data + ("eventID" to "cualquiera")))
        assertNull(CambioRutaRemoto.leer(data + ("kind" to "otraCosa")))
        assertNull(CambioRutaRemoto.leer(data + ("routeID" to "")))
    }
    @Test fun reciboPushRechazaTiposIncorrectosSinLanzarExcepciones() {
        val valido = """{"requestID":"12345678-1234-1234-1234-123456789abc","revision":1,"accepted":true,"providerReady":true,"leaseUntil":1900000000}"""
        assertNotNull(ReciboRegistroPush.leer(valido))
        assertNull(ReciboRegistroPush.leer(valido.replace("\"revision\":1", "\"revision\":\"1\"")))
        assertNull(ReciboRegistroPush.leer(valido.replace("\"accepted\":true", "\"accepted\":{}")))
        assertNull(ReciboRegistroPush.leer(valido.replace("1900000000", "-1")))
        assertNull(ReciboRegistroPush.leer("[]"))
    }
}
