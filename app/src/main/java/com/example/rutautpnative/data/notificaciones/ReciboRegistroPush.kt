package com.example.rutautpnative.data.notificaciones

import kotlinx.serialization.json.*
import java.util.UUID

/** Un recibo malformado se ignora; nunca se interpreta como registro confirmado. */
data class ReciboRegistroPush(val requestID: String, val revision: Long, val accepted: Boolean,
    val providerReady: Boolean, val leaseUntil: Double, val schemaVersion: Int? = null,
    val provider: String? = null, val platform: String? = null, val packageName: String? = null) {
    fun evaluarFcm(request: String?, expectedRevision: Long, enabled: Boolean, now: Double, application: String): ResultadoRegistroPush {
        if (requestID != request || revision != expectedRevision) return ResultadoRegistroPush.IGNORAR
        if (!now.isFinite() || !accepted || leaseUntil <= now || leaseUntil > now + 31 * 86400 ||
            schemaVersion != 2 || provider != "fcm" || platform != "android" || packageName != application)
            return ResultadoRegistroPush.RECHAZADO
        return when {
            !enabled -> ResultadoRegistroPush.DESACTIVADO
            !providerReady -> ResultadoRegistroPush.PROVEEDOR_PENDIENTE
            else -> ResultadoRegistroPush.CONFIRMADO
        }
    }
    companion object {
        fun leer(texto: String): ReciboRegistroPush? = runCatching {
            val o = Json.parseToJsonElement(texto).jsonObject
            val id = o.getValue("requestID").jsonPrimitive.also { require(it.isString) }.content
            require(id.length == 36 && UUID.fromString(id).toString() == id.lowercase())
            fun numero(key: String) = o.getValue(key).jsonPrimitive.also { require(!it.isString) }
            val revision = numero("revision").long
            require(revision in 1..9_007_199_254_740_991L)
            val accepted = numero("accepted").boolean
            val ready = numero("providerReady").boolean
            val until = numero("leaseUntil").double
            require(until.isFinite() && until >= 0)
            fun textoOpcional(key: String) = o[key]?.jsonPrimitive?.also { require(it.isString) }?.content
            val schema = o["schemaVersion"]?.jsonPrimitive?.also { require(!it.isString) }?.int
            ReciboRegistroPush(id, revision, accepted, ready, until, schema,
                textoOpcional("provider"), textoOpcional("platform"), textoOpcional("packageName"))
        }.getOrNull()
    }
}
