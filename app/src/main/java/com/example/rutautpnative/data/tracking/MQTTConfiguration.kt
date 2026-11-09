package com.example.rutautpnative.data.tracking

import com.example.rutautpnative.BuildConfig

//----Configuracion del canal MQTT (puerto del MQTTConfiguration.swift)----
// Las credenciales NO viven en el repo: llegan por local.properties / -P en
// tiempo de compilacion (BuildConfig), igual que iOS las lee del Scheme de
// Xcode. Sin MQTT_HOST + usuario + contrasena, `configurada` es null y toda
// la contribucion queda deshabilitada (la UI muestra "Canal MQTT no
// configurado").
data class MQTTConfiguration(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val useTLS: Boolean,
    val caAsset: String,  // asset PEM/DER con la CA propia; "" = confianza del sistema
    val readOnly: Boolean = false
) {
    val puedeContribuir: Boolean get() = !readOnly && username !in setOf("backend", "observer", "presentation-reader") && !username.startsWith("sim-")
    /// Identidad que la ACL del broker obliga a incluir en el topico.
    val principal: String get() = username

    val endpoint: String
        get() = "${if (useTLS) "ssl" else "tcp"}://$host:$port"

    companion object {
        private val PUERTO_PLANO = 1883
        private val PUERTO_TLS = 8883
        private val flagsTLS = setOf("1", "true", "yes")

        /// Resuelve la configuracion desde BuildConfig; null si falta algo
        /// obligatorio (host vacio, credenciales incompletas, puerto absurdo).
        fun desdeBuildConfig(): MQTTConfiguration? {
            val host = BuildConfig.MQTT_HOST.trim()
            val username = BuildConfig.MQTT_USERNAME.trim()
            val password = BuildConfig.MQTT_PASSWORD
            if (host.isEmpty() || username.isEmpty() || password.isEmpty()) return null

            val useTLS = BuildConfig.MQTT_TLS.trim().lowercase() in flagsTLS

            // Puerto explicito obligatorio si se declara: un error visible no
            // debe redirigir la conexion a otro puerto (misma regla del iOS).
            val puertoConfigurado = BuildConfig.MQTT_PORT.trim().toIntOrNull()
            if (BuildConfig.MQTT_PORT.isNotBlank() && puertoConfigurado == null) return null
            if (puertoConfigurado != null && puertoConfigurado !in 1..65535) return null

            val port = puertoConfigurado ?: if (useTLS) PUERTO_TLS else PUERTO_PLANO

            return MQTTConfiguration(
                host = host,
                port = port,
                username = username,
                password = password,
                useTLS = useTLS,
                caAsset = BuildConfig.MQTT_CA_ASSET.trim(),
                readOnly = BuildConfig.MQTT_READ_ONLY.trim().lowercase() in flagsTLS
            )
        }
    }
}
