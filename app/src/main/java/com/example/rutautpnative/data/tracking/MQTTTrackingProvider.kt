package com.example.rutautpnative.data.tracking

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import java.util.UUID
import javax.net.ssl.SSLSocketFactory

/** Receptor del contrato actual: MQTT no implica que un dato sea observado. */
class MQTTTrackingProvider(private val config: MQTTConfiguration, context: android.content.Context? = null) {
    private val handler = Handler(Looper.getMainLooper())
    private val _posiciones = MutableStateFlow<List<VehiclePosition>>(emptyList())
    val posiciones: StateFlow<List<VehiclePosition>> = _posiciones.asStateFlow()
    private var cliente: MqttAsyncClient? = null
    private val porId = linkedMapOf<String, VehiclePosition>()
    private var corriendo = false
    private var conectando = false
    private var generacion = 0L
    private var foreground = true
    private var gate = SimulationControlGate()
    private var checkpointDisponible = true
    private val checkpointFile = context?.let { ctx ->
        val identity = "v1|${config.host.lowercase(java.util.Locale.ROOT).removeSuffix(".")}|${config.port}|${config.useTLS}"
        val key = java.security.MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
        android.util.AtomicFile(java.io.File(ctx.noBackupFilesDir, "tracking-control/$key.json"))
    }
    private val appContext = context?.applicationContext
    val topic = "rutautp/vehiculos/+/posicion"
    private val sweep = object : Runnable {
        override fun run() { if (corriendo) { refrescar(); handler.postDelayed(this, 1_000) } }
    }
    private var expiration: Runnable? = null
    init {
        try {
            val saved = leerCheckpoint()
            require(saved?.valid() != false)
            gate = SimulationControlGate(saved ?: SimulationCheckpoint())
        } catch (_: Exception) { checkpointDisponible = false; gate.invalidate(true) }
    }
    private fun leerCheckpoint(): SimulationCheckpoint? {
        val f = checkpointFile ?: return null
        if (!f.baseFile.exists()) return null
        val bytes = f.openRead().use { input ->
            val buffer = ByteArray(32_769)
            var count = 0
            while (count < buffer.size) {
                val read = input.read(buffer, count, buffer.size - count)
                if (read < 0) break
                count += read
            }
            buffer.copyOf(count)
        }
        require(bytes.size <= 32_768)
        return Json.decodeFromString<SimulationCheckpoint>(bytes.toString(Charsets.UTF_8)).also { require(it.valid()) }
    }
    private fun guardarCheckpoint() = synchronized(checkpointLock) {
        val f = checkpointFile ?: throw IllegalStateException("No hay almacenamiento de control")
        val next = gate.checkpoint
        val old = leerCheckpoint()
        require(next.valid() && old?.valid() != false)
        if (old?.lastControl != null && next.lastControl != null) {
            require(next.lastControl.controlRevision >= old.lastControl.controlRevision)
            if (next.lastControl.controlRevision == old.lastControl.controlRevision) {
                require(next.lastControl.enabled == old.lastControl.enabled && next.lastControl.runId == old.lastControl.runId && next.lastControl.serviceEndsAt == old.lastControl.serviceEndsAt)
            }
        }
        val runs = next.retiredRuns + old?.retiredRuns.orEmpty()
        val merged = next.copy(retiredRuns = runs.sorted().take(256).toSet(), exhausted = next.exhausted || old?.exhausted == true || runs.size > 256)
        require(merged.valid())
        f.baseFile.parentFile?.mkdirs()
        val stream = f.startWrite()
        try { stream.write(Json.encodeToString(SimulationCheckpoint.serializer(), merged).toByteArray()); f.finishWrite(stream) }
        catch (e: Exception) { f.failWrite(stream); throw e }
        require(!merged.exhausted)
        // Importar las revocaciones guardadas por otra instancia, sin restaurar una autorización.
        if (runs != next.retiredRuns) { gate = SimulationControlGate(merged); gate.invalidate(true) }
    }
    fun start() {
        if (corriendo) return
        corriendo = true
        generacion++
        gate.invalidate()
        handler.post(sweep)
        conectar()
    }
    fun stop() {
        conectando = false
        corriendo = false
        generacion++
        handler.removeCallbacks(sweep)
        expiration?.let(handler::removeCallbacks)
        gate.invalidate()
        porId.clear(); _posiciones.value = emptyList()
        val old = cliente; cliente = null
        try { old?.disconnectForcibly(500); old?.close() } catch (_: Exception) { }
    }
    fun pausar() { foreground = false; gate.invalidate(); refrescar() }
    fun reanudar() {
        foreground = true
        gate.invalidate()
        if (corriendo && cliente?.isConnected != true && !conectando && !com.example.rutautpnative.data.conexion.ConexionInstalacionStore.autorizacionRechazada) conectar()
        runCatching { if (cliente?.isConnected == true) cliente?.subscribe(VehicleTrackingWire.CONTROL_TOPIC, 1) }
    }
    /** Revalidar caducidad y control antes de reutilizar una posición al cambiar de destino. */
    fun posicionesParaPresentar(): List<VehiclePosition> {
        refrescar()
        return posiciones.value
    }
    private fun refrescar() {
        val wall = System.currentTimeMillis() / 1000.0
        val uptime = android.os.SystemClock.elapsedRealtime() / 1000.0
        gate.checkTime(wall, uptime)
        porId.entries.removeAll { (_, p) -> VehiclePositionSanitizer.sanitizar(p, wall) == null ||
            (p.dataOrigin != VehicleDataOrigin.OBSERVED && (!foreground || !checkpointDisponible || !gate.permits(p, wall, uptime))) }
        val next = porId.values.sortedBy { it.id }
        if (_posiciones.value != next) _posiciones.value = next
    }
    private fun recibir(topico: String, bytes: ByteArray, retained: Boolean) {
        val wall = System.currentTimeMillis() / 1000.0
        val uptime = android.os.SystemClock.elapsedRealtime() / 1000.0
        if (topico == VehicleTrackingWire.CONTROL_TOPIC) {
            if (!checkpointDisponible) return
            val state = VehicleTrackingWire.control(bytes) ?: run { gate.invalidate(true); refrescar(); return }
            if (gate.receive(state, wall, uptime)) {
                try { guardarCheckpoint() } catch (_: Exception) { checkpointDisponible = false; gate.invalidate(true) }
                if (!foreground) gate.invalidate()
                expiration?.let(handler::removeCallbacks)
                expiration = Runnable { refrescar() }.also { handler.postDelayed(it, ((minOf(state.validUntil, state.serviceEndsAt) - wall).coerceIn(0.0, 15.0) * 1000).toLong() + 1) }
            }
            refrescar()
            return
        }
        val p = VehicleTrackingWire.position(bytes, topico, retained)?.let { VehiclePositionSanitizer.sanitizar(it, wall) } ?: return
        gate.checkTime(wall, uptime)
        if (p.dataOrigin != VehicleDataOrigin.OBSERVED && (!foreground || !checkpointDisponible || !gate.permits(p, wall, uptime))) return
        val old = porId[p.id]
        if (old != null && p.timestamp <= old.timestamp) return
        porId[p.id] = p
        refrescar()
    }
    private fun conectar() {
        if (!corriendo || conectando || cliente?.isConnected == true) return
        conectando = true
        generacion++
        val token = generacion
        val anterior = cliente; cliente = null
        runCatching { anterior?.close() }
        try {
            val client = MqttAsyncClient(config.endpoint, "rutautp-android-${UUID.randomUUID()}", MemoryPersistence())
            cliente = client
            fun vigente() = corriendo && generacion == token && cliente === client
            client.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    handler.post {
                        if (!vigente()) return@post
                        conectando = false
                        gate.invalidate(); refrescar()
                        try { client.subscribe(arrayOf(topic, VehicleTrackingWire.CONTROL_TOPIC), intArrayOf(1, 1)) }
                        catch (e: Exception) { android.util.Log.w("MQTTTrack", "No se pudo suscribir", e) }
                    }
                }
                override fun connectionLost(cause: Throwable?) { handler.post { if (vigente()) { gate.invalidate(); refrescar() } } }
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    if (topic == null || message == null || message.payload.size > 4096) return
                    val bytes = message.payload.copyOf(); val retained = message.isRetained
                    handler.post { if (vigente()) recibir(topic, bytes, retained) }
                }
                override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
            })
            val opciones = MqttConnectOptions().apply {
                userName = config.username; password = config.password.toCharArray()
                isCleanSession = true; isAutomaticReconnect = true; keepAliveInterval = 30
                MQTTTLS.aplicar(this, config, appContext)
            }
            client.connect(opciones, object : IMqttActionListener {
                override fun onSuccess(token: IMqttToken?) { handler.post { if (vigente()) conectando = false } }
                override fun onFailure(token: IMqttToken?, exception: Throwable?) {
                    config.informarFallo(exception)
                    handler.post { if (vigente()) { conectando = false; gate.invalidate(); refrescar() } } }
            })
        } catch (e: Exception) { conectando = false; config.informarFallo(e); android.util.Log.w("MQTTTrack", "Conexión fallida") }
    }
    companion object { const val INTERVALO_BARRIDO_MS = 1_000L; private val checkpointLock = Any() }
}
