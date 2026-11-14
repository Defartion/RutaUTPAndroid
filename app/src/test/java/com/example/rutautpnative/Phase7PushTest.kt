package com.example.rutautpnative

import com.example.rutautpnative.data.notificaciones.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId

class Phase7PushTest {
    private val fixture get() = Json.parseToJsonElement(File(requireNotNull(System.getProperty("rutautp.parity")), "push-contract.json").readText()).jsonObject
    private val registration get() = fixture.getValue("fcmRegistration").jsonObject
    private val receipt get() = fixture.getValue("fcmReceipt").jsonObject
    private val now get() = fixture.getValue("now").jsonPrimitive.double
    private fun resultado(body: JsonObject = receipt, enabled: Boolean = true, time: Double = now,
        request: String? = registration.getValue("requestID").jsonPrimitive.content, revision: Long = 1) =
        ReciboRegistroPush.leer(body.toString())!!.evaluarFcm(request, revision, enabled, time, "com.example.rutautpnative")
    private fun cambiar(key: String, value: JsonElement) = JsonObject(receipt + (key to value))

    @Test fun elRegistroAndroidCoincideConElContratoQueAceptaPython() {
        val r = registration
        val actual = Json.parseToJsonElement(RegistroPush.crear(
            r.getValue("requestID").jsonPrimitive.content, r.getValue("deviceID").jsonPrimitive.content, 1, now,
            r.getValue("token").jsonPrimitive.content, true, listOf("17350695"), "es", "UTC",
            "com.example.rutautpnative", ConfiguracionNotificaciones())).jsonObject
        assertEquals(r.keys, actual.keys)
        r.forEach { (key, value) ->
            if (key == "timestamp") assertEquals(value.jsonPrimitive.double, actual.getValue(key).jsonPrimitive.double, 0.0)
            else assertEquals(key, value, actual[key])
        }
        assertNull(actual["environment"])
    }
    @Test fun unReciboFcmVigenteConfirmaElRegistro() = assertEquals(ResultadoRegistroPush.CONFIRMADO, resultado())
    @Test fun aceptacionSinProveedorListoNoAfirmaEntrega() =
        assertEquals(ResultadoRegistroPush.PROVEEDOR_PENDIENTE, resultado(cambiar("providerReady", JsonPrimitive(false))))
    @Test fun revocacionAceptadaNoQuedaComoActivada() =
        assertEquals(ResultadoRegistroPush.DESACTIVADO, resultado(enabled = false))
    @Test fun unaRespuestaViejaNoConsumeLaSolicitudNueva() {
        assertEquals(ResultadoRegistroPush.IGNORAR, resultado(request = "otra"))
        assertEquals(ResultadoRegistroPush.IGNORAR, resultado(revision = 2))
        assertEquals(ResultadoRegistroPush.IGNORAR, resultado(request = null))
    }
    @Test fun unReciboApnsOLegadoNuncaConfirmaFcm() {
        assertEquals(ResultadoRegistroPush.RECHAZADO, resultado(cambiar("provider", JsonPrimitive("apns"))))
        assertEquals(ResultadoRegistroPush.RECHAZADO, resultado(JsonObject(receipt - setOf("schemaVersion", "provider", "platform", "packageName"))))
        assertEquals(ResultadoRegistroPush.RECHAZADO, resultado(cambiar("schemaVersion", JsonPrimitive(1))))
    }
    @Test fun otraAplicacionOPlataformaNoPrestaSuConfirmacion() {
        assertEquals(ResultadoRegistroPush.RECHAZADO, resultado(cambiar("packageName", JsonPrimitive("otra.app"))))
        assertEquals(ResultadoRegistroPush.RECHAZADO, resultado(cambiar("platform", JsonPrimitive("ios"))))
    }
    @Test fun caducidadYRechazoNoQuedanComoRegistroConfirmado() {
        assertEquals(ResultadoRegistroPush.RECHAZADO, resultado(cambiar("leaseUntil", JsonPrimitive(now))))
        assertEquals(ResultadoRegistroPush.RECHAZADO, resultado(cambiar("leaseUntil", JsonPrimitive(now + 32 * 86400))))
        assertEquals(ResultadoRegistroPush.RECHAZADO, resultado(cambiar("accepted", JsonPrimitive(false))))
        assertEquals(ResultadoRegistroPush.RECHAZADO, resultado(time = Double.NaN))
    }
    @Test fun metadatosDelReciboConTiposIncorrectosSeIgnoran() {
        assertNull(ReciboRegistroPush.leer(cambiar("schemaVersion", JsonPrimitive("2")).toString()))
        assertNull(ReciboRegistroPush.leer(cambiar("provider", JsonPrimitive(1)).toString()))
    }
    @Test fun datosDeEventoProducidosPorElServidorSeLeenSinCrearUnCierreConfirmado() {
        val data = fixture.getValue("dataMessage").jsonObject.mapValues { it.value.jsonPrimitive.content }
        val evento = CambioRutaRemoto.leer(data)!!
        assertEquals("17350695", evento.routeID); assertEquals(now + 300, evento.expiresAt, 0.0)
        assertNull(CambioRutaRemoto.leer(data + ("kind" to "confirmado")))
    }
    @Test fun tokensInvalidosNoSeConstruyenYNuncaSeCambiaSuProveedor() {
        fun crear(token: String, enabled: Boolean = true) = RegistroPush.crear(
            registration.getValue("requestID").jsonPrimitive.content, registration.getValue("deviceID").jsonPrimitive.content,
            1, now, token, enabled, emptyList(), "es", "UTC", "com.example.rutautpnative", ConfiguracionNotificaciones())
        for (token in listOf("", "con espacio", "con\nsalto", "á", "x".repeat(4097)))
            assertThrows(IllegalArgumentException::class.java) { crear(token) }
        assertFalse(Json.parseToJsonElement(crear("", false)).jsonObject.getValue("enabled").jsonPrimitive.boolean)
        assertEquals("fcm", Json.parseToJsonElement(crear("aa")).jsonObject.getValue("provider").jsonPrimitive.content)
    }
    @Test fun silenciosYPausasCoincidenConLosMismosVectoresPython() {
        fixture.getValue("policies").jsonArray.forEach { value ->
            val case = value.jsonObject; val updates = case.getValue("updates").jsonObject
            val config = ConfiguracionNotificaciones(
                pausaHasta = updates["pauseUntil"]?.jsonPrimitive?.double?.times(1000)?.toLong(),
                diasSilencio = updates["mutedDays"]?.jsonArray?.map { it.jsonPrimitive.int }?.toSet() ?: emptySet(),
                silencioHorario = updates["quietHours"]?.jsonPrimitive?.boolean ?: false,
                inicioSilencio = updates["quietStart"]?.jsonPrimitive?.int ?: 1320,
                finSilencio = updates["quietEnd"]?.jsonPrimitive?.int ?: 420)
            assertEquals(case.getValue("id").jsonPrimitive.content, case.getValue("allowed").jsonPrimitive.boolean,
                config.permite(Instant.parse(case.getValue("instant").jsonPrimitive.content),
                    ZoneId.of(case.getValue("timeZone").jsonPrimitive.content)))
        }
    }
}
