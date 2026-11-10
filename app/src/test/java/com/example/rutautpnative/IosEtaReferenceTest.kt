package com.example.rutautpnative

import androidx.compose.ui.graphics.Color
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.data.tracking.VehicleETAEstimator
import com.example.rutautpnative.data.tracking.VehiclePosition
import com.google.android.gms.maps.model.LatLng
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Casos compartidos con la referencia iOS que Android ya satisface.
 * Los casos pendientes conservan su resultado iOS en el JSON; se activarán
 * al corregir F01. No se convierten los defectos conocidos en valores esperados.
 */
@RunWith(Parameterized::class)
class IosEtaReferenceTest(private val id: String, private val case: JsonObject) {
    @Test
    fun llegadaCoincideConReferenciaIos() {
        val base = reference.getValue("route").jsonObject
        val shape = (case["shape"] ?: base.getValue("shape")).jsonArray.map { it.coordinate() }
        val route = RutaGTFS(
            id = base.getValue("id").jsonPrimitive.content,
            linea = "10", variante = "A", recorrido = "Inicio → Fin", empresa = "Prueba",
            colorHex = "00AA00", color = Color.Green, shape = shape, paraderos = emptyList(),
            duracionMin = base.getValue("durationMinutes").jsonPrimitive.int,
            headwayMin = base.getValue("headwayMinutes").jsonPrimitive.int,
            precio = 2.0, distanciaKm = base.getValue("distanceKm").jsonPrimitive.double,
            distanciaUTPMetros = 0.0
        )
        val position = case.getValue("position").coordinate()
        val vehicle = VehiclePosition(
            id = id, linea = "10", routeId = route.id,
            lat = position.latitude, lon = position.longitude,
            heading = case.getValue("heading").jsonPrimitive.double,
            speed = case.getValue("speed").jsonPrimitive.double, timestamp = 1_800_000_000.0
        )
        val expected = case.getValue("expectedMinutes").let { if (it == JsonNull) null else it.jsonPrimitive.int }
        assertEquals(id + ": " + case.getValue("name").jsonPrimitive.content,
            expected, VehicleETAEstimator.minutos(vehicle, route, case.getValue("target").coordinate()))
    }

    companion object {
        private val reference: JsonObject = requireNotNull(
            IosEtaReferenceTest::class.java.getResourceAsStream("/parity/eta.json")
        ).bufferedReader(Charsets.UTF_8).use { Json.parseToJsonElement(it.readText()).jsonObject }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): Collection<Array<Any>> = reference.getValue("cases").jsonArray
            .map { it.jsonObject }
            .filter { it.getValue("status").jsonPrimitive.content == "baseline" }
            .map { arrayOf<Any>(it.getValue("id").jsonPrimitive.content, it) }

        private fun JsonElement.coordinate(): LatLng =
            jsonArray.let { LatLng(it[0].jsonPrimitive.double, it[1].jsonPrimitive.double) }
    }
}
