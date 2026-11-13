package com.example.rutautpnative.data.tracking

import com.example.rutautpnative.BuildConfig
import com.example.rutautpnative.data.conexion.*

/** Endpoint público separado de las cuentas aprovisionadas en este teléfono. */
data class MQTTConfiguration(
    val host: String, val port: Int, val username: String, val password: String,
    val useTLS: Boolean, val caAsset: String, val readOnly: Boolean = false, val caPem: String = ""
) {
    val puedeContribuir: Boolean get() = !readOnly && username !in setOf("backend", "observer", "presentation-reader") && !username.startsWith("sim-")
    val principal: String get() = username
    val endpoint: String get() = CanalConexion(host, port, useTLS).identidad
    override fun toString() = "MQTTConfiguration(endpoint=$endpoint, readOnly=$readOnly, credenciales=ocultas)"
    fun informarFallo(error: Throwable?) {
        var actual = error
        repeat(8) {
            if (actual is org.eclipse.paho.client.mqttv3.MqttException &&
                (actual as org.eclipse.paho.client.mqttv3.MqttException).reasonCode in listOf(4, 5)) {
                ConexionInstalacionStore.rechazar(CuentaConexion(CanalConexion(host, port, useTLS, caAsset, caPem),
                    if (readOnly) RolConexion.LECTURA else RolConexion.CONTRIBUCION, username, password))
                return
            }
            actual = actual?.cause
        }
    }
    companion object {
        fun actual(): MQTTConfiguration? = ConexionInstalacionStore.receptor()?.aConfiguracion()
        fun paraReportes(): MQTTConfiguration? = paraContribucion() ?: actual()
        fun paraContribucion(): MQTTConfiguration? = ConexionInstalacionStore.contribucion()?.aConfiguracion()
        private fun CuentaConexion.aConfiguracion() = MQTTConfiguration(canal.host, canal.puerto, usuario, clave,
            canal.tls, canal.caAsset, rol == RolConexion.LECTURA, canal.caPem)
        fun canalPublico(): CanalConexion? = runCatching {
            val tls = when (BuildConfig.MQTT_TLS.trim().lowercase()) { "", "1", "true", "yes" -> true; "0", "false", "no" -> false; else -> error("TLS no válido") }
            val port = if (BuildConfig.MQTT_PORT.isBlank()) (if (tls) 8883 else 1883) else BuildConfig.MQTT_PORT.toInt()
            CanalConexion(BuildConfig.MQTT_HOST.trim(), port, tls, BuildConfig.MQTT_CA_ASSET.trim()).also { it.validar(BuildConfig.DEBUG) }
        }.getOrNull()
    }
}
