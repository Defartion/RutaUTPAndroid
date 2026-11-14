package com.example.rutautpnative.data.notificaciones

import kotlinx.serialization.json.*
import java.time.ZoneId
import java.util.UUID

/** Contrato compartido schema 2. El token no se interpreta como un token APNs. */
object RegistroPush {
    fun crear(requestID: String, deviceID: String, revision: Long, timestamp: Double,
        token: String, enabled: Boolean, routes: List<String>, language: String, timeZone: String,
        packageName: String, settings: ConfiguracionNotificaciones): String {
        fun uuid(value: String) = value.length == 36 && runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)
        require(uuid(requestID) && uuid(deviceID) && revision in 1..9_007_199_254_740_991L)
        require(timestamp.isFinite() && timestamp in 0.0..<4_102_444_800.0)
        require(token.length <= 4096 && token.all { it.code in 33..126 } && (!enabled || token.isNotEmpty()))
        require(routes.size <= 100 && routes.all { it.isNotBlank() && it.length <= 128 && it.none(Char::isISOControl) })
        require(language in setOf("es", "en", "fr", "pt-BR", "de", "zh-Hans") && timeZone.length <= 128)
        ZoneId.of(timeZone)
        require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")) && settings.valida())
        return buildJsonObject {
            put("schemaVersion", 2); put("provider", "fcm"); put("platform", "android"); put("packageName", packageName)
            put("requestID", requestID); put("deviceID", deviceID); put("revision", revision); put("timestamp", timestamp)
            put("token", token); put("enabled", enabled)
            put("routes", JsonArray(routes.distinct().sorted().map(::JsonPrimitive))); put("language", language); put("timeZone", timeZone)
            put("pauseUntil", settings.pausaHasta?.let { JsonPrimitive(it / 1000.0) } ?: JsonNull)
            put("mutedDays", JsonArray(settings.diasSilencio.sorted().map(::JsonPrimitive))); put("quietHours", settings.silencioHorario)
            put("quietStart", settings.inicioSilencio); put("quietEnd", settings.finSilencio); put("sound", settings.sonido)
        }.toString()
    }
}

enum class ResultadoRegistroPush { IGNORAR, RECHAZADO, DESACTIVADO, PROVEEDOR_PENDIENTE, CONFIRMADO }
