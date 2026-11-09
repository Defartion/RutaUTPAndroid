package com.example.rutautpnative.data.notificaciones

import kotlinx.serialization.json.*
import java.util.UUID

/** Un recibo malformado se ignora; nunca se interpreta como registro confirmado. */
data class ReciboRegistroPush(val requestID: String, val revision: Long, val accepted: Boolean,
    val providerReady: Boolean, val leaseUntil: Double) {
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
            ReciboRegistroPush(id, revision, accepted, ready, until)
        }.getOrNull()
    }
}
