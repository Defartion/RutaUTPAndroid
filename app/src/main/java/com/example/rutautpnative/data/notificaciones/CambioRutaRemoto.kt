package com.example.rutautpnative.data.notificaciones

import kotlinx.serialization.json.*
import java.util.UUID

data class CambioRutaRemoto(val eventID: String, val routeID: String, val expiresAt: Double) {
    companion object {
        fun leer(data: Map<String, String>): CambioRutaRemoto? = runCatching {
            val o = data["rutaUTP"]?.let { Json.parseToJsonElement(it).jsonObject } ?: JsonObject(data.mapValues { JsonPrimitive(it.value) })
            if (o["schemaVersion"]?.jsonPrimitive?.content != "1" || o["kind"]?.jsonPrimitive?.content != "routeChange") return null
            val id = o["eventID"]?.jsonPrimitive?.content ?: return null
            if (id.length != 36 || UUID.fromString(id).toString() != id.lowercase()) return null
            val route = o["routeID"]?.jsonPrimitive?.content ?: return null
            if (route.isBlank() || route.length > 128 || route.any { it.isISOControl() }) return null
            val expires = o["expiresAt"]?.jsonPrimitive?.doubleOrNull ?: return null
            if (!expires.isFinite() || expires <= 0 || expires >= 4_102_444_800.0) return null
            CambioRutaRemoto(id.lowercase(), route, expires)
        }.getOrNull()
    }
}
