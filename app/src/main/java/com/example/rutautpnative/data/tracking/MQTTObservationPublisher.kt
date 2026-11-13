package com.example.rutautpnative.data.tracking

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID

//----Baliza del pasajero (puerto del MQTTObservationPublisher.swift)----
// Publica observaciones ANONIMAS de un pasajero probablemente a bordo. Una
// observacion NO es una posicion confirmada de bus: el backend agrega las de
// varios usuarios para estimar el vehiculo.
//
// Contrato con el backend (calzar EXACTO con el del iOS):
//   - Topic: rutautp/observaciones/{principal}/{sessionId}/posicion
//   - QoS 1, retained = false
//   - Payload JSON schemaVersion 1 (ver PayloadObservacion)
//   - Throttle: minimo 5 s entre publicaciones; flush timer que reenvia la
//     ultima muestra cada 5 s aunque no lleguen lecturas nuevas (tunel/techo
//     del vehiculo) para que la baliza no desaparezca del mapa de los demas.
//   - Sanitizacion: nada impreciso (accuracy > 50 m) ni coordenadas
//     imposibles sale del telefono; speed clamp [-1,100]; heading mod 360.
class MQTTObservationPublisher(
    private val config: MQTTConfiguration,
    appContext: Context
) {
    enum class EstadoPublicador { INACTIVO, CONECTANDO, CONECTADO, FALLO }

    @Serializable
    data class PayloadObservacion(
        @SerialName("schemaVersion") val schemaVersion: Int = 1,
        @SerialName("sessionId") val sessionId: String,
        @SerialName("routeId") val routeId: String,
        @SerialName("linea") val linea: String,
        @SerialName("lat") val lat: Double,
        @SerialName("lon") val lon: Double,
        @SerialName("speed") val speed: Double,
        @SerialName("heading") val heading: Double,
        @SerialName("accuracy") val accuracy: Double,
        @SerialName("motionActivity") val motionActivity: String,
        @SerialName("timestamp") val timestamp: Double
    )

    // Muestra sanitizada lista para publicar (o re-publicar en el flush).
    @Serializable
    private data class MuestraPendiente(
        val routeId: String,
        val linea: String,
        val lat: Double,
        val lon: Double,
        val speed: Double,
        val heading: Double,
        val accuracy: Double,
        val motionActivity: String,
        val timestamp: Double
    )

    private val context = appContext.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = Json { encodeDefaults = true }
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val _estado = MutableStateFlow(EstadoPublicador.INACTIVO)
    val estado: StateFlow<EstadoPublicador> = _estado.asStateFlow()

    //----Throttle (puerto de ObservationPublishThrottle)----
    private var ultimaPublicacionMs: Long = 0

    private fun throttlePermite(ahoraMs: Long, forzar: Boolean): Boolean =
        forzar || (ahoraMs - ultimaPublicacionMs) >= INTERVALO_MINIMO_MS

    private var cliente: MqttAsyncClient? = null
    private var sessionId: String? = null
    private var lineaActual: String? = null
    private var pendiente: MuestraPendiente? = null
    private var flushJob: Job? = null
    private var detenidoPorUsuario = false

    val estaActivo: Boolean get() = sessionId != null

    //----Sesion----

    /// Abre (o reabre con el MISMO sessionId al reanudar) la sesion de viaje.
    fun start(sessionID: String, linea: String) {
        if (!config.puedeContribuir) return
        sessionId = sessionID
        lineaActual = linea
        detenidoPorUsuario = false
        if (cliente?.isConnected == true) {
            _estado.value = EstadoPublicador.CONECTADO
            arrancarFlush()
            // Fuerza la primera publicacion de la sesion reabierta.
            publicarPendienteSiToca(forzar = true)
            return
        }
        _estado.value = EstadoPublicador.CONECTANDO
        conectar()
    }

    /// Registra la muestra y publica si el throttle lo permite. Devuelve
    /// false si la muestra fue DESCARTADA por sanitizacion (nunca sale del
    /// telefono).
    fun publicar(
        lat: Double,
        lon: Double,
        speed: Double,
        heading: Double,
        accuracy: Double,
        routeId: String,
        linea: String,
        actividad: ActividadMotora
    ): Boolean {
        // Sanitizacion (igual que el iOS): descartar antes de guardar.
        if (!accuracy.isFinite() || accuracy < 0 || accuracy > MAX_ACCURACY_M) return false
        if (!lat.isFinite() || lat !in -90.0..90.0) return false
        if (!lon.isFinite() || lon !in -180.0..180.0) return false

        val speedSan = when {
            !speed.isFinite() -> -1.0
            else -> speed.coerceIn(-1.0, MAX_SPEED_MS)
        }
        val headingSan = when {
            !heading.isFinite() || heading < 0 -> -1.0
            else -> heading % 360.0
        }

        pendiente = MuestraPendiente(
            routeId = routeId,
            linea = linea,
            lat = lat,
            lon = lon,
            speed = speedSan,
            heading = headingSan,
            accuracy = accuracy,
            motionActivity = actividad.valorPayload,
            timestamp = System.currentTimeMillis() / 1000.0
        )

        return publicarPendienteSiToca(forzar = false)
    }

    /// Cierra la sesion: limpia todo y desconecta.
    fun stop() {
        if (!config.puedeContribuir) return
        detenidoPorUsuario = true
        sessionId = null
        lineaActual = null
        pendiente = null
        flushJob?.cancel()
        flushJob = null
        ultimaPublicacionMs = 0
        try {
            cliente?.disconnectForcibly(1000)
        } catch (e: Exception) {
            android.util.Log.w("MQTTObs", "disconnect en stop() fallo", e)
        }
        cliente?.close()
        cliente = null
        _estado.value = EstadoPublicador.INACTIVO
    }

    //----Publicacion----

    private fun topic(): String? {
        val sesion = sessionId ?: return null
        return "rutautp/observaciones/${config.principal}/$sesion/posicion"
    }

    private fun publicarPendienteSiToca(forzar: Boolean): Boolean {
        val muestra = pendiente ?: return false
        val sesion = sessionId ?: return false
        val ahora = System.currentTimeMillis()
        if (!throttlePermite(ahora, forzar)) return false

        val payload = PayloadObservacion(
            sessionId = sesion,
            routeId = muestra.routeId,
            linea = muestra.linea,
            lat = muestra.lat,
            lon = muestra.lon,
            speed = muestra.speed,
            heading = muestra.heading,
            accuracy = muestra.accuracy,
            motionActivity = muestra.motionActivity,
            timestamp = muestra.timestamp
        )
        val topic = topic() ?: return false

        val cliente = this.cliente
        if (cliente == null || !cliente.isConnected) return false

        return try {
            val mensaje = MqttMessage(json.encodeToString(PayloadObservacion.serializer(), payload).toByteArray())
            mensaje.qos = 1
            mensaje.isRetained = false
            cliente.publish(topic, mensaje)
            ultimaPublicacionMs = ahora
            true
        } catch (e: Exception) {
            android.util.Log.w("MQTTObs", "publish de observacion fallo", e)
            false
        }
    }

    //----Flush: reenvia la ultima muestra cada 5 s aunque no llegen fixes----
    private fun arrancarFlush() {
        flushJob?.cancel()
        flushJob = scope.launch {
            while (true) {
                delay(INTERVALO_MINIMO_MS)
                if (sessionId != null) publicarPendienteSiToca(forzar = true)
            }
        }
    }

    //----Conexion (Paho)----

    private fun conectar() {
        try {
            val cliente = MqttAsyncClient(config.endpoint, "rutautp-observer-${UUID.randomUUID()}", MemoryPersistence())
            cliente.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    handler.post {
                        // Desconexion pedida por stop(): no es fallo.
                        if (detenidoPorUsuario || sessionId == null) return@post
                        _estado.value =
                            if (cause != null) EstadoPublicador.FALLO else EstadoPublicador.CONECTANDO
                    }
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) = Unit
                override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
            })

            val opciones = MqttConnectOptions().apply {
                userName = config.username
                password = config.password.toCharArray()
                isCleanSession = true
                isAutomaticReconnect = true
                keepAliveInterval = 30
                MQTTTLS.aplicar(this, config, context)
            }

            this.cliente = cliente
            cliente.connect(opciones, object : IMqttActionListener {
                override fun onSuccess(token: IMqttToken?) {
                    handler.post {
                        _estado.value = EstadoPublicador.CONECTADO
                        arrancarFlush()
                        // Fuerza el envio de la primera muestra acumulada
                        // mientras conectaba (igual que el iOS).
                        publicarPendienteSiToca(forzar = true)
                    }
                }

                override fun onFailure(token: IMqttToken?, exception: Throwable?) {
                    config.informarFallo(exception)
                    handler.post {
                        if (!detenidoPorUsuario) _estado.value = EstadoPublicador.FALLO
                    }
                }
            })
        } catch (e: Exception) {
            _estado.value = EstadoPublicador.FALLO
        }
    }

    companion object {
        const val INTERVALO_MINIMO_MS = 5_000L   // throttle y flush: 5 s
        const val MAX_ACCURACY_M = 50.0
        const val MAX_SPEED_MS = 100.0
    }
}
