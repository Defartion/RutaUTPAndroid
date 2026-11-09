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

//----Cambios de ruta en vivo (puerto del RouteChangesService.swift)----
// Tercer canal MQTT (gemelo de ocupacion): los pasajeros reportan obras,
// cierres o desvios sobre una linea; con DOS cuentas distintas en la misma
// ruta, motivo y zona, el cambio queda CONFIRMADO y visible para todos con
// vigencia maxima de 15 minutos. Contrato exacto del iOS:
//   - Suscribe:  rutautp/cambios/estado             (snapshot de alertas)
//               rutautp/cambios/{principal}/recibo
//   - Publica:   rutautp/cambios/{principal}/reporte
//   - Reporte:  {schemaVersion:1, requestId, routeId, reason, lat, lon, place, timestamp}
//   - Alerta:   {id, routeId, reason, lat, lon, place, confirmations, expiresAt}
//   - Snapshot: {schemaVersion:1, timestamp, alerts:[Alerta]}
class RouteChangesService(private val config: MQTTConfiguration, context: android.content.Context? = null) {
    private val appContext = context?.applicationContext

    enum class MotivoCambio(val valorPayload: String) {
        OBRAS("works"),
        CIERRE("closure"),
        DESVIO("detour")
    }

    @Serializable
    data class AlertaCambio(
        @SerialName("id") val id: String,
        @SerialName("routeId") val routeId: String,
        @SerialName("reason") val reason: String,
        @SerialName("lat") val lat: Double,
        @SerialName("lon") val lon: Double,
        @SerialName("place") val place: String,
        @SerialName("confirmations") val confirmations: Int,
        @SerialName("expiresAt") val expiresAt: Double
    ) {
        val vigenteHastaTexto: String
            get() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date((expiresAt * 1000).toLong()))

        val motivo: MotivoCambio?
            get() = MotivoCambio.entries.firstOrNull { it.valorPayload == reason }
    }

    @Serializable
    private data class SnapshotJSON(
        @SerialName("schemaVersion") val schemaVersion: Int = 0,
        @SerialName("timestamp") val timestamp: Double = 0.0,
        @SerialName("alerts") val alerts: List<AlertaCambio> = emptyList()
    )

    @Serializable
    private data class ReciboJSON(
        @SerialName("requestId") val requestId: String = "",
        @SerialName("accepted") val accepted: Boolean = false,
        @SerialName("code") val code: String = ""
    )

    @Serializable
    private data class ReporteJSON(
        @SerialName("schemaVersion") val schemaVersion: Int = 1,
        @SerialName("requestId") val requestId: String,
        @SerialName("routeId") val routeId: String,
        @SerialName("reason") val reason: String,
        @SerialName("lat") val lat: Double,
        @SerialName("lon") val lon: Double,
        @SerialName("place") val place: String,
        @SerialName("timestamp") val timestamp: Double
    )

    //----Estado observable----
    private val _listo = MutableStateFlow(false)
    val listo: StateFlow<Boolean> = _listo.asStateFlow()

    private val _alertas = MutableStateFlow<List<AlertaCambio>>(emptyList())
    val alertas: StateFlow<List<AlertaCambio>> = _alertas.asStateFlow()

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
    private var timestampUltimoSnapshot: Double? = null
    private var requestIdPendiente: String? = null
    private var enviadoEnS: Double = 0.0

    val topicEstado = "rutautp/cambios/estado"
    val topicRecibo = "rutautp/cambios/${config.principal}/recibo"
    val topicReporte = "rutautp/cambios/${config.principal}/reporte"

    companion object {
        const val MAX_BYTES_SNAPSHOT = 300_000
        const val MAX_ALERTAS_SNAPSHOT = 500
        const val MIN_CUENTAS_CONFIRMACION = 2
        const val VIDA_MAX_CAMBIO_S = 910.0        // ~15 minutos
        const val FRESCO_SNAPSHOT_S = 20.0
        const val TIMEOUT_RECIBO_S = 12.0
        const val INTERVALO_REFRESCO_MS = 1_000L

        val configurado: Boolean get() = MQTTConfiguration.desdeBuildConfig() != null
    }

    //----Ciclo de vida (el sheet lo enciende/apaga)----

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
        timestampUltimoSnapshot = null
        requestIdPendiente = null
        _listo.value = false
        _alertas.value = emptyList()
        _enviando.value = false
        _resultado.value = null
        try {
            cliente?.disconnectForcibly(1_000)
        } catch (e: Exception) {
            android.util.Log.w("RouteChanges", "disconnect fallo", e)
        }
        cliente?.close()
        cliente = null
    }

    //----Envio (guard: listo && !enviando, como el iOS)----

    fun enviar(routeId: String, motivo: MotivoCambio, lat: Double, lon: Double, place: String) {
        if (!corriendo || !_listo.value || _enviando.value) return
        val id = UUID.randomUUID().toString()
        requestIdPendiente = id
        enviadoEnS = System.currentTimeMillis() / 1000.0
        _enviando.value = true
        _resultado.value = null

        val payload = ReporteJSON(
            requestId = id,
            routeId = routeId,
            reason = motivo.valorPayload,
            lat = lat,
            lon = lon,
            place = place,
            timestamp = System.currentTimeMillis() / 1000.0
        )
        try {
            val mensaje = MqttMessage(json.encodeToString(ReporteJSON.serializer(), payload).toByteArray())
            mensaje.qos = 1
            mensaje.isRetained = false
            cliente?.publish(topicReporte, mensaje)
        } catch (e: Exception) {
            android.util.Log.w("RouteChanges", "publish de reporte fallo", e)
        }
    }

    //----Recepcion----

    private fun recibirSnapshot(bytes: Int, texto: String) = handler.post {
        if (!corriendo) return@post
        if (bytes > MAX_BYTES_SNAPSHOT) return@post
        val snapshot = try {
            json.decodeFromString(SnapshotJSON.serializer(), texto)
        } catch (e: Exception) {
            android.util.Log.w("RouteChanges", "snapshot JSON invalido", e)
            return@post
        }
        if (snapshot.schemaVersion != 1 || !snapshot.timestamp.isFinite()) return@post

        val ahora = System.currentTimeMillis() / 1000.0
        // Frescura -10..20 s y tope de alertas.
        if (ahora - snapshot.timestamp !in -10.0..20.0) return@post
        if (snapshot.alerts.size > MAX_ALERTAS_SNAPSHOT) return@post
        // Un snapshot tardio no sustituye uno mas reciente.
        val ultimo = timestampUltimoSnapshot
        if (ultimo != null && snapshot.timestamp < ultimo) return@post
        timestampUltimoSnapshot = snapshot.timestamp

        // Filtrado de defensa en profundidad (el backend ya valido; igual que
        // el iOS, el cliente vuelve a chequear cada alerta).
        val vistas = HashSet<String>()
        val validas = snapshot.alerts.filter { a ->
            a.confirmations >= MIN_CUENTAS_CONFIRMACION &&
                a.expiresAt.isFinite() &&
                a.expiresAt > ahora &&
                a.expiresAt <= ahora + VIDA_MAX_CAMBIO_S &&
                a.lat in -90.0..90.0 &&
                a.lon in -180.0..180.0 &&
                a.place.length <= 120 &&
                MotivoCambio.entries.any { it.valorPayload == a.reason } &&
                vistas.add(a.id)   // dedup por id
        }
        _alertas.value = validas
        refrescar(ahora)
    }

    private fun recibirRecibo(texto: String) = handler.post {
        if (!corriendo) return@post
        val recibo = try {
            json.decodeFromString(ReciboJSON.serializer(), texto)
        } catch (e: Exception) {
            android.util.Log.w("RouteChanges", "recibo JSON invalido", e)
            return@post
        }
        if (requestIdPendiente == null || recibo.requestId != requestIdPendiente) return@post

        requestIdPendiente = null
        _enviando.value = false

        _resultado.value = when {
            recibo.accepted && recibo.code == "confirmed" ->
                L.t(
                    "Reporte recibido. El cambio ya cuenta con al menos dos cuentas distintas.",
                    "Report received. At least two different accounts now support this change."
                )
            recibo.accepted ->
                L.t(
                    "Reporte recibido. Queda pendiente: hace falta otra cuenta que confirme el mismo cambio.",
                    "Report received and pending: another account must confirm the same change."
                )
            recibo.code == "off_route" ->
                L.t(
                    "El punto está lejos de esta ruta. Revisa la línea y el lugar.",
                    "The point is far from this route. Check the line and location."
                )
            recibo.code == "rate_limit" ->
                L.t(
                    "Espera 30 segundos entre reportes. Repetirlo no suma otra confirmación.",
                    "Wait 30 seconds between reports. Repeating it won't add another confirmation."
                )
            else ->
                L.t(
                    "No se aceptó el reporte. Revisa los datos e inténtalo de nuevo.",
                    "Report not accepted. Check the details and try again."
                )
        }
    }

    //----Refresco (1 s)----

    private fun arrancarRefresco() {
        if (refrescoJob?.isActive == true) return
        refrescoJob = scope.launch {
            while (isActive) {
                delay(INTERVALO_REFRESCO_MS)
                refrescar(System.currentTimeMillis() / 1000.0)
            }
        }
    }

    private fun refrescar(ahora: Double) {
        val fresco = timestampUltimoSnapshot?.let { ahora - it < FRESCO_SNAPSHOT_S } == true
        val nuevo = corriendo && suscrito && cliente?.isConnected == true && fresco
        if (nuevo != _listo.value) {
            _listo.value = nuevo
            if (!nuevo) _alertas.value = emptyList()
        }
        if (_listo.value) {
            // Expira las alertas vencidas de la lista visible.
            val vigentes = _alertas.value.filter { it.expiresAt > ahora }
            if (vigentes != _alertas.value) _alertas.value = vigentes
        }
        // Timeout del recibo: 12 s.
        if (_enviando.value && requestIdPendiente != null) {
            if (ahora - enviadoEnS >= TIMEOUT_RECIBO_S) {
                requestIdPendiente = null
                _enviando.value = false
                _resultado.value = L.t(
                    "No pudimos confirmar la recepción. Intenta de nuevo cuando haya conexión.",
                    "We couldn't confirm receipt. Try again when connected."
                )
            }
        }
    }

    //----Conexion (Paho)----

    private fun conectar() {
        try {
            val cliente = MqttAsyncClient(
                config.endpoint,
                "rutautp-changes-${UUID.randomUUID()}",
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
                            android.util.Log.w("RouteChanges", "subscribe fallo", e)
                        }
                    }
                }

                override fun connectionLost(cause: Throwable?) {
                    handler.post {
                        if (!corriendo) return@post
                        suscrito = false
                        refrescar(System.currentTimeMillis() / 1000.0)
                    }
                }

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
                if (config.useTLS) {
                    socketFactory = MQTTTLS.socketFactory(config.caAsset, appContext)
                }
            }

            this.cliente = cliente
            cliente.connect(opciones, object : IMqttActionListener {
                override fun onSuccess(token: IMqttToken?) = Unit
                override fun onFailure(token: IMqttToken?, exception: Throwable?) = Unit
            })
        } catch (e: Exception) {
            android.util.Log.w("RouteChanges", "conexion fallo", e)
        }
    }
}
