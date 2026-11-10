package com.example.rutautpnative

import com.example.rutautpnative.data.routing.TransitPlanner
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class IosRouteComparisonReferenceTest(private val id: String, private val case: JsonObject) {
    @Test
    fun comparacionCoincideConReferenciaIos() {
        val direct = case.getValue("directMinutes").let { if (it == JsonNull) null else it.jsonPrimitive.double * 60 }
        assertEquals(id, case.getValue("expectedIncludeTransfer").jsonPrimitive.boolean,
            TransitPlanner.transbordoUtilParaComparar(direct, case.getValue("transferMinutes").jsonPrimitive.double * 60))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): Collection<Array<Any>> {
            val fixture = requireNotNull(
                IosRouteComparisonReferenceTest::class.java.getResourceAsStream("/parity/route-comparison.json")
            ).bufferedReader(Charsets.UTF_8).use { Json.parseToJsonElement(it.readText()).jsonObject }
            return fixture.getValue("cases").jsonArray.map { it.jsonObject }
                .filter { it.getValue("status").jsonPrimitive.content == "baseline" }
                .map { arrayOf<Any>(it.getValue("id").jsonPrimitive.content, it) }
        }
    }
}
