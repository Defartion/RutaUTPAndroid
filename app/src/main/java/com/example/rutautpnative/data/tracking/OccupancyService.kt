package com.example.rutautpnative.data.tracking

import android.os.Handler
import android.os.Looper
import com.example.rutautpnative.ui.idioma.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.net.ssl.SSLSocketFactory

// Nombre corto del enum anidado, como el BusOccupancyState de nivel superior
// del iOS (OccupancyService.swift).
typealias EstadoOcupacion = OccupancyService.EstadoOcupacion

//----Ocupacion de buses en vivo (puerto del OccupancyService.swift)----
// Canal MQTT INDEPENDIENTE: no modifica posiciones, rutas ni estimaciones.
// El estado de un bus NO lo calcula el cliente: llega en snapshots del
// backend, que agrega los reportes y exige DOS cuentas distintas que
// coincidan en el mismo vehiculo y estado (defensa en profundidad: el
// cliente vuelve a validar). Un reporte vive maximo 3 minutos.
//
// Contrato de topics (igual que el backend del repo iOS):
//   - Suscribe:  rutautp/ocupacion/estado            (snapshot global)
//               rutautp/ocupacion/{principal}/recibo (recibo personal)
//   - Publica:   rutautp/ocupacion/{principal}/reporte
class OccupancyService(private val config: MQTTConfiguration, context: android.content.Context? = null) {
    private val appContext = context?.applicationContext

    enum class EstadoOcupacion(val valorPayload: String) {
        VACIO("empty"),
        CON_ESPACIO("space"),
        LLENO("full")
    }

    data class LecturaOcupacion(
        val vehicleId: String,
        val estado: EstadoOcupacion?,
        val confirmaciones: Int,
        val updatedAt: Double,
        val expiresAt: Double
    ) {
        val venceTexto: String
            get() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date((expiresAt * 1000).toLong()))
    }

    //----JSON del contrato----
    @Serializable
    private data class LecturaJSON(
        @SerialName("vehicleId") val vehicleId: String,
        @SerialName("state") val state: String,
        @SerialName("confirmations") val confirmations: Int,
        @SerialName("updatedAt") val updatedAt: Double,
        @SerialName("expiresAt") val expiresAt: Double
    )

    @Serializable
    private data class SnapshotJSON(
        @SerialName("schemaVersion") val schemaVersion: Int = 0,
        @SerialName("timestamp") val timestamp: Double = 0.0,
        @SerialName("buses") val buses: List<LecturaJSON> = emptyList()
    )

    @Serializable
    private data class ReciboJSON(
        @SerialName("schemaVersion") val schemaVersion: Int = 0,
        @SerialName("requestId") val requestId: String = "",
        @SerialName("accepted") val accepted: Boolean = false,
        @SerialName("code") val code: String = ""
    )

    @Serializable
    private data class ReporteJSON(
        @SerialName("schemaVersion") val schemaVersion: Int = 1,
        @SerialName("requestId") val requestId: String,
        // El objetivo es UNO de los dos: vehicleId (panel del bus) o routeId
        // (reporte del propio viaje, asociado a la línea declarada).
        @SerialName("vehicleId") val vehicleId: String? = null,
        @SerialName("routeId") val routeId: String? = null,
        @SerialName("state") val state: String,
        @SerialName("timestamp") val timestamp: Double
    )

    //----Estado observable----
    private val _listo = MutableStateFlow(false)
    val listo: StateFlow<Boolean> = _listo.asStateFlow()

    private val _lecturas = MutableStateFlow<Map<String, LecturaOcupacion>>(emptyMap())
    val lecturas: StateFlow<Map<String, LecturaOcupacion>> = _lecturas.asStateFlow()

    private val _enviando = MutableStateFlow(false)
    val enviando: StateFlow<Boolean> = _enviando.asStateFlow()

    private val _resultado = MutableStateFlow<String?>(null)
    val resultado: StateFlow<String?> = _resultado.asStateFlow()

    //----Interno----
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private val json = Json { ignoreUnknownKeys = true }

    private var cliente: MqttAsyncClient? = null
    private var refrescoJob: Job? = null
    private var suscrito = false
    private var corriendo = false
    private var timestampUltimoSnapshot = 0.0
    private var requestIdPendiente: String? = null
    private var enviadoEnMs: Long = 0

    //----Reporte del propio viaje (prepareTripReport del iOS)----
    // Pendiente hasta detectar el viaje y tener conexion; reintenta como
    // maximo cada 30 s y VENCE a los 3 minutos sin renovarse solo.
    private data class ReporteViaje(val routeId: String, val estado: EstadoOcupacion, val at: Double)

    private var reporteViaje: ReporteViaje? = null
    private var ultimoIntentoViaje = Double.NEGATIVE_INFINITY

    val tieneReportePendiente: Boolean get() = reporteViaje != null

    /// Conserva solo la eleccion actual; nunca renueva automaticamente su edad.
    fun prepararReporteViaje(routeId: String, estado: EstadoOcupacion) {
        if (!config.puedeContribuir) return
        reporteViaje = ReporteViaje(routeId, estado, System.currentTimeMillis() / 1000.0)
        requestIdPendiente = null
        _enviando.value = false
        _resultado.value = L.t(
            "Se enviará al detectar tu viaje y tener conexión.",
            "Will send once your trip is detected and connected."
        )
    }

    fun limpiarReporteViaje() {
        reporteViaje = null
        requestIdPendiente = null
        _enviando.value = false
        _resultado.value = null
        ultimoIntentoViaje = Double.NEGATIVE_INFINITY
    }

    val topicEstado = "rutautp/ocupacion/estado"
    val topicRecibo = "rutautp/ocupacion/${config.principal}/recibo"
    val topicReporte = "rutautp/ocupacion/${config.principal}/reporte"

    companion object {
        const val MAX_BYTES_SNAPSHOT = 300 * 1024
        const val MAX_BUSES_SNAPSHOT = 500
        const val MIN_CUENTAS_CONFIRMACION = 2      // defensa en profundidad
        const val VIDA_MAX_REPORTE_S = 180.0        // 3 minutos
        const val FRESCO_SNAPSHOT_S = 20.0          // ready exige frescura <= 20 s
        const val TIMEOUT_RECIBO_S = 12.0
        const val INTERVALO_REFRESCO_MS = 1_000L
        const val INTERVALO_REINTENTO_VIAJE_S = 30.0 // reintento del reporte del viaje

        /// Hay canal solo si el broker esta configurado.
        val configurado: Boolean get() = MQTTConfiguration.actual() != null
    }

    //----Ciclo de vida (el panel lo enciende con onAppear, como el iOS)----

    fun start() {
        if (corriendo) return
        corriendo = true
        conectar()
        arrancarRefresco()
    }

    fun stop() {
        corriendo = false
        refrescoJob?.cancel()
        refrescoJob = null
        suscrito = false
        // Reset del timestamp (su gemelo RouteChangesService.stop() SI lo
        // resetea): sin esto, tras cerrar/reabrir el popup, un snapshot con
        // timestamp <= al de la sesion anterior se descartaria.
        timestampUltimoSnapshot = 0.0
        _listo.value = false
        _lecturas.value = emptyMap()
        _enviando.value = false
        _resultado.value = null
        requestIdPendiente = null
        try {
            cliente?.disconnectForcibly(1_000)
        } catch (e: Exception) {
            android.util.Log.w("Occupancy", "disconnect fallo", e)
        }
        cliente?.close()
        cliente = null
    }

    //----Envio----

    fun reportar(vehicleId: String, estado: EstadoOcupacion) {
        publicarReporte(vehicleId = vehicleId, routeId = null, estado = estado,
                        timestamp = System.currentTimeMillis() / 1000.0)
    }

    /// Publica un reporte con UNO de los dos objetivos: vehicleId (panel del
    /// bus) o routeId (reporte del viaje, con la hora de la eleccion).
    private fun publicarReporte(
        vehicleId: String?,
        routeId: String?,
        estado: EstadoOcupacion,
        timestamp: Double
    ) {
        if (!config.puedeContribuir || !corriendo || _enviando.value || !_listo.value) return
        val ahora = System.currentTimeMillis() / 1000.0
        // El reporte del viaje vence a los 3 min: caducado no se envia.
        if (routeId != null && ahora - timestamp >= VIDA_MAX_REPORTE_S) return

        val id = UUID.randomUUID().toString()
        requestIdPendiente = id
        enviadoEnMs = System.currentTimeMillis()
        _enviando.value = true
        _resultado.value = null

        val payload = ReporteJSON(
            requestId = id,
            vehicleId = vehicleId,
            routeId = routeId,
            state = estado.valorPayload,
            timestamp = timestamp
        )
        try {
            val mensaje = MqttMessage(json.encodeToString(ReporteJSON.serializer(), payload).toByteArray())
            mensaje.qos = 1
            mensaje.isRetained = false
            cliente?.publish(topicReporte, mensaje)
        } catch (e: Exception) {
            android.util.Log.w("Occupancy", "publish de reporte fallo", e)
        }
    }

    //----Recepcion----

    private fun recibirSnapshot(bytes: Int, texto: String) = handler.post {
        if (!corriendo) return@post
        // Un snapshot gigante o mal encuadrado se descarta entero.
        if (bytes > MAX_BYTES_SNAPSHOT) return@post
        val snapshot = try {
            json.decodeFromString(SnapshotJSON.serializer(), texto)
        } catch (e: Exception) {
            android.util.Log.w("Occupancy", "snapshot JSON invalido", e)
            return@post
        }
        if (snapshot.schemaVersion != 1) return@post

        val ahora = System.currentTimeMillis() / 1000.0
        // Frescura del propio snapshot: -10..20 s contra el reloj local.
        if (ahora - snapshot.timestamp !in -10.0..20.0) return@post
        // Anti-tardio: un snapshot viejo no sustituye uno mas reciente.
        if (snapshot.timestamp < timestampUltimoSnapshot) return@post
        if (snapshot.buses.size > MAX_BUSES_SNAPSHOT) return@post

        timestampUltimoSnapshot = snapshot.timestamp

        val mapa = LinkedHashMap<String, LecturaOcupacion>()
        for (l in snapshot.buses) {
            val lectura = validarLectura(l, ahora) ?: continue
            // Deduplicacion por vehicleId (el backend no deberia duplicar,
            // pero el canal es compartido).
            mapa.putIfAbsent(lectura.vehicleId, lectura)
        }
        _lecturas.value = mapa
        refrescarListo(ahora)
    }

    private fun validarLectura(l: LecturaJSON, ahora: Double): LecturaOcupacion? {
        if (l.vehicleId.isEmpty() || l.vehicleId.length > 200) return null
        if (l.confirmations < MIN_CUENTAS_CONFIRMACION) return null
        // updatedAt fresco: -10..180 s.
        if (ahora - l.updatedAt !in -10.0..VIDA_MAX_REPORTE_S) return null
        // Vigencia: un reporte dura maximo 3 minutos.
        if (l.expiresAt <= ahora || l.expiresAt > l.updatedAt + VIDA_MAX_REPORTE_S) return null
        val estado = when (l.state) {
            EstadoOcupacion.VACIO.valorPayload -> EstadoOcupacion.VACIO
            EstadoOcupacion.CON_ESPACIO.valorPayload -> EstadoOcupacion.CON_ESPACIO
            EstadoOcupacion.LLENO.valorPayload -> EstadoOcupacion.LLENO
            else -> null
        }
        return LecturaOcupacion(
            vehicleId = l.vehicleId,
            estado = estado,
            confirmaciones = l.confirmations,
            updatedAt = l.updatedAt,
            expiresAt = l.expiresAt
        )
    }

    private fun recibirRecibo(texto: String) = handler.post {
        if (!corriendo) return@post
        val recibo = try {
            json.decodeFromString(ReciboJSON.serializer(), texto)
        } catch (e: Exception) {
            android.util.Log.w("Occupancy", "recibo JSON invalido", e)
            return@post
        }
        // Solo el recibo del reporte en vuelo (puede llegar uno tardio).
        if (requestIdPendiente == null || recibo.requestId != requestIdPendiente) return@post

        requestIdPendiente = null
        _enviando.value = false

        if (recibo.accepted) {
            // Aceptado: el reporte del viaje ya no esta pendiente (el
            // backend se hace cargo de la vigencia y la corroboracion).
            reporteViaje = null
        }
        _resultado.value = when {
            recibo.accepted && recibo.code == "confirmed" ->
                L.t(
                    "Confirmado: al menos dos cuentas distintas coinciden en el estado del bus.",
                    "Confirmed: at least two distinct accounts agree on the bus state."
                )
            recibo.accepted ->
                L.t(
                    "Recibido, pero aún no hay suficientes reportes coincidentes para confirmarlo.",
                    "Received, but there aren't enough matching reports to confirm yet."
                )
            recibo.code == "not_onboard" && reporteViaje != null ->
                L.t(
                    "Esperando que tus ubicaciones confirmen el micro. Reintentaremos mientras el reporte siga vigente.",
                    "Waiting for your locations to identify the bus. We'll retry while your report is fresh."
                )
            recibo.code == "not_onboard" ->
                L.t(
                    "Debes estar detectado a bordo: activa \"Ayudar con ubicaciones\" y viaja en la línea.",
                    "You must be detected onboard: enable \"Help with locations\" and ride the line."
                )
            recibo.code == "unavailable" ->
                L.t("El bus ya no tiene una posición reciente.", "The bus no longer has a recent position.")
            recibo.code == "rate_limit" ->
                L.t(
                    "Espera 30 segundos entre reportes; repetir no suma confirmación.",
                    "Wait 30 seconds between reports; repeating doesn't add confirmation."
                )
            else -> L.t("Reporte no aceptado.", "Report not accepted.")
        }
    }

    //----Refresco periodico (1 s, igual que el iOS)----

    private fun arrancarRefresco() {
        if (refrescoJob?.isActive == true) return
        refrescoJob = scope.launch {
            while (isActive) {
                delay(INTERVALO_REFRESCO_MS)
                val ahora = System.currentTimeMillis() / 1000.0
                // Expira lecturas vencidas.
                _lecturas.value = _lecturas.value.filterValues { it.expiresAt > ahora }
                refrescarListo(ahora)

                // Reporte del viaje: vence a los 3 minutos sin renovarse;
                // reintenta como maximo cada 30 s mientras este pendiente.
                val viaje = reporteViaje
                if (viaje != null) {
                    if (ahora - viaje.at >= VIDA_MAX_REPORTE_S) {
                        limpiarReporteViaje()
                        _resultado.value = L.t(
                            "Actualiza qué tan lleno va el micro; tu elección venció.",
                            "Update how full your bus is; your selection expired."
                        )
                    } else if (_listo.value && !_enviando.value &&
                        ahora - ultimoIntentoViaje >= INTERVALO_REINTENTO_VIAJE_S
                    ) {
                        ultimoIntentoViaje = ahora
                        publicarReporte(
                            vehicleId = null,
                            routeId = viaje.routeId,
                            estado = viaje.estado,
                            timestamp = viaje.at
                        )
                    }
                }

                // Timeout del recibo: 12 s sin respuesta visible.
                if (_enviando.value && requestIdPendiente != null) {
                    val esperando = (System.currentTimeMillis() - enviadoEnMs) / 1000.0
                    if (esperando >= TIMEOUT_RECIBO_S) {
                        requestIdPendiente = null
                        _enviando.value = false
                        _resultado.value = L.t(
                            "No pudimos confirmar la recepción del reporte. Revisa tu conexión.",
                            "Couldn't confirm the report was received. Check your connection."
                        )
                    }
                }
            }
        }
    }

    private fun refrescarListo(ahora: Double) {
        val conectado = cliente?.isConnected == true
        val fresco = ahora - timestampUltimoSnapshot < FRESCO_SNAPSHOT_S
        val nuevo = corriendo && suscrito && conectado && fresco
        if (nuevo != _listo.value) {
            _listo.value = nuevo
            if (!nuevo) _lecturas.value = emptyMap()   // sin conexion: lista local vacia
        }
    }

    //----Conexion (Paho)----

    private fun conectar() {
        try {
            val cliente = MqttAsyncClient(
                config.endpoint,
                "rutautp-ocupacion-${UUID.randomUUID()}",
                MemoryPersistence()
            )
            cliente.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    handler.post {
                        if (!corriendo) return@post
                        try {
                            cliente.subscribe(topicEstado, 1)
                            cliente.subscribe(topicRecibo, 1)
                            suscrito = true
                        } catch (e: Exception) {
                            android.util.Log.w("Occupancy", "subscribe fallo", e)
                        }
                    }
                }

                override fun connectionLost(cause: Throwable?) = Unit

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val texto = message?.toString() ?: return
                    when (topic) {
                        topicEstado -> recibirSnapshot(message.payload.size, texto)
                        topicRecibo -> recibirRecibo(texto)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
            })

            val opciones = MqttConnectOptions().apply {
                userName = config.username
                password = config.password.toCharArray()
                isCleanSession = true
                isAutomaticReconnect = true
                keepAliveInterval = 30
                MQTTTLS.aplicar(this, config, appContext)
            }

            this.cliente = cliente
            cliente.connect(opciones, object : IMqttActionListener {
                override fun onSuccess(token: IMqttToken?) = Unit
                override fun onFailure(token: IMqttToken?, exception: Throwable?) { config.informarFallo(exception) }
            })
        } catch (e: Exception) {
            android.util.Log.w("Occupancy", "Conexión fallida")
        }
    }
}
