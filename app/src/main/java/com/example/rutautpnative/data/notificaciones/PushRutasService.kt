package com.example.rutautpnative.data.notificaciones

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import com.example.rutautpnative.data.LineasGuardadasStore
import com.example.rutautpnative.data.tracking.MQTTConfiguration
import com.example.rutautpnative.data.tracking.MQTTTLS
import com.example.rutautpnative.ui.idioma.L
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.time.ZoneId
import java.util.UUID

/** FCM sustituye a APNs. MQTT/TLS registra políticas; FCM entrega sin mantener MQTT en segundo plano. */
object PushRutasService {
    internal const val PREFS = "rutautp.push"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var context: Context? = null
    private var cliente: MqttAsyncClient? = null
    private var foreground = false
    private var token: String? = null
    private var generacion = 0L
    private var solicitud: String? = null
    private var revisionEnviada = 0L
    private var timeout: Runnable? = null
    private var lease: Runnable? = null
    private var sincronizacion: Job? = null
    private var tokenPendiente = false
    var disponible by mutableStateOf(false)
        private set
    var activadas by mutableStateOf(false)
        private set
    var registroConfirmado by mutableStateOf(false)
        private set
    var estado by mutableStateOf("")
        private set
    fun init(ctx: Context) {
        context = ctx.applicationContext
        disponible = runCatching { FirebaseApp.getApps(ctx).isNotEmpty() || FirebaseApp.initializeApp(ctx) != null }.getOrDefault(false)
        activadas = ctx.getSharedPreferences(PREFS, 0).getBoolean("notificaciones.push.rutas.v1", false)
        if (disponible) FirebaseMessaging.getInstance().isAutoInitEnabled = activadas
        estado = if (disponible) L.t("Avisos de cambios desactivados", "Route change notifications disabled")
            else L.t("Falta configurar Firebase para los avisos remotos.", "Firebase must be configured for remote notifications.")
    }
    fun alCambiarCuenta() {
        sincronizacion?.cancel(); desconectar()
        activadas = false; registroConfirmado = false
        context?.getSharedPreferences(PREFS, 0)?.edit()?.putBoolean("notificaciones.push.rutas.v1", false)?.apply()
        if (disponible) FirebaseMessaging.getInstance().isAutoInitEnabled = false
        estado = L.t("Activa los avisos de nuevo para esta cuenta.", "Enable notifications again for this account.")
    }
    fun cambiar(value: Boolean) {
        if (value && !disponible) return
        activadas = value
        context?.getSharedPreferences(PREFS, 0)?.edit()?.putBoolean("notificaciones.push.rutas.v1", value)?.apply()
        if (disponible) FirebaseMessaging.getInstance().isAutoInitEnabled = value
        sincronizar()
    }
    fun primerPlano(value: Boolean) {
        foreground = value
        if (value) sincronizar() else { sincronizacion?.cancel(); desconectar() }
    }
    fun tokenRenovado(value: String) { handler.post { if (value.isNotBlank() && value.length <= 4096) { token = value; sincronizar() } } }
    fun sincronizar() {
        if (Looper.myLooper() != Looper.getMainLooper()) { handler.post { sincronizar() }; return }
        val ctx = context ?: return
        sincronizacion?.cancel()
        desconectar()
        registroConfirmado = false
        if (!foreground) return
        val prefs = ctx.getSharedPreferences(PREFS, 0)
        if (!disponible) return
        if (!activadas && !prefs.contains("notificaciones.push.revision.v1")) {
            estado = L.t("Avisos de cambios desactivados", "Route change notifications disabled"); return
        }
        if (activadas && NotificacionesService.permitido(ctx) && token == null) {
            estado = L.t("Registrando este dispositivo…", "Registering this device…")
            if (tokenPendiente) return
            tokenPendiente = true
            FirebaseMessaging.getInstance().token.addOnSuccessListener { value -> tokenPendiente = false; tokenRenovado(value) }
                .addOnFailureListener { tokenPendiente = false; estado = L.t("No se pudo registrar el dispositivo en Firebase.", "Could not register the device with Firebase.") }
            return
        }
        val config = MQTTConfiguration.paraContribucion()?.takeIf { it.puedeContribuir }
        if (config == null || !config.useTLS) {
            estado = L.t("Configura una cuenta MQTT propia con TLS para registrar avisos.", "Configure your own MQTT account with TLS to register notifications."); return
        }
        val generation = generacion
        sincronizacion = scope.launch {
            val routes = LineasGuardadasStore.cargar().map { it.routeId }.distinct().sorted()
            ensureActive()
            if (!foreground || generation != generacion) return@launch
            if (routes.size > 100 || routes.any { it.isBlank() || it.length > 128 }) return@launch
            val settings = NotificacionesService.configuracion.takeIf { it.valida() } ?: ConfiguracionNotificaciones()
            val device = prefs.getString("notificaciones.push.instalacion.v1", null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString("notificaciones.push.instalacion.v1", it).commit()
            }
            if (runCatching { UUID.fromString(device) }.isFailure) return@launch
            val previous = prefs.getLong("notificaciones.push.revision.v1", 0)
            if (previous !in 0L..9_007_199_254_740_990L) return@launch
            val request = UUID.randomUUID().toString()
            revisionEnviada = previous + 1
            solicitud = request
            val enabled = activadas && ctx.getSharedPreferences("rutautp.persistencia", 0).getBoolean("perfil_notificaciones", false) && NotificacionesService.permitido(ctx) && !NotificacionesService.datosInvalidos && !LineasGuardadasStore.datosInvalidos.value && token != null
            val body = runCatching {
                RegistroPush.crear(request, device, revisionEnviada, System.currentTimeMillis() / 1000.0,
                    token.orEmpty(), enabled, routes, L.codigo, ZoneId.systemDefault().id, ctx.packageName, settings)
            }.getOrElse {
                estado = L.t("No se pudo registrar el servicio de avisos.", "Could not register notification service.")
                return@launch
            }
            if (!prefs.edit().putLong("notificaciones.push.revision.v1", revisionEnviada).commit()) return@launch
            estado = L.t("Sincronizando con el servicio…", "Syncing with the service…")
            try {
                val client = MqttAsyncClient(config.endpoint, "rutautp-push-${UUID.randomUUID()}", MemoryPersistence())
                cliente = client
                fun vigente() = foreground && generation == generacion && cliente === client
                client.setCallback(object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        handler.post {
                            if (!vigente()) return@post
                            client.subscribe("rutautp/notificaciones/${config.principal}/recibo", 1, null, object : IMqttActionListener {
                                override fun onSuccess(asyncActionToken: IMqttToken?) { handler.post {
                                    if (vigente()) client.publish("rutautp/notificaciones/${config.principal}/registro", body.toByteArray(), 1, false)
                                } }
                                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) = Unit
                            })
                        }
                    }
                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        if (topic != "rutautp/notificaciones/${config.principal}/recibo" || message == null || message.isRetained || message.payload.size > 2048) return
                        val text = message.toString()
                        handler.post {
                            if (!vigente()) return@post
                            val receipt = ReciboRegistroPush.leer(text) ?: return@post
                            val now = System.currentTimeMillis() / 1000.0
                            val result = receipt.evaluarFcm(solicitud, revisionEnviada, enabled, now, ctx.packageName)
                            if (result == ResultadoRegistroPush.IGNORAR) return@post
                            timeout?.let(handler::removeCallbacks)
                            solicitud = null
                            val expires = receipt.leaseUntil
                            val accepted = result != ResultadoRegistroPush.RECHAZADO
                            registroConfirmado = result == ResultadoRegistroPush.CONFIRMADO
                            estado = when (result) {
                                ResultadoRegistroPush.RECHAZADO -> L.t("El backend no aceptó el registro FCM. Revisa su configuración para Android.", "The backend did not accept FCM registration. Check its Android configuration.")
                                ResultadoRegistroPush.DESACTIVADO -> L.t("Avisos de cambios desactivados", "Route change notifications disabled")
                                ResultadoRegistroPush.CONFIRMADO -> L.t("Registro remoto confirmado", "Remote registration confirmed")
                                ResultadoRegistroPush.PROVEEDOR_PENDIENTE -> L.t("El servidor recibió tus ajustes, pero el envío FCM aún no está configurado.", "The server received your settings, but FCM delivery is not configured yet.")
                                ResultadoRegistroPush.IGNORAR -> estado
                            }
                            if (accepted) {
                                lease?.let(handler::removeCallbacks)
                                lease = Runnable { registroConfirmado = false }.also { handler.postDelayed(it, ((expires - now) * 1000).toLong()) }
                            }
                        }
                    }
                    override fun connectionLost(cause: Throwable?) { handler.post { if (vigente()) { registroConfirmado = false; estado = L.t("Cambios pendientes de sincronizar", "Changes waiting to sync") } } }
                    override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
                })
                val opts = MqttConnectOptions().apply {
                    userName = config.username; password = config.password.toCharArray(); isCleanSession = true
                    MQTTTLS.aplicar(this, config, ctx); connectionTimeout = 10
                }
                client.connect(opts)
                timeout = Runnable {
                    if (vigente() && solicitud != null) { registroConfirmado = false; solicitud = null; estado = L.t("Cambios pendientes de sincronizar", "Changes waiting to sync") }
                }.also { handler.postDelayed(it, 15_000) }
            } catch (_: Exception) { registroConfirmado = false; estado = L.t("No se pudo registrar el servicio de avisos.", "Could not register notification service.") }
        }
    }
    private fun desconectar() {
        generacion++
        registroConfirmado = false
        timeout?.let(handler::removeCallbacks)
        lease?.let(handler::removeCallbacks)
        solicitud = null
        val old = cliente; cliente = null
        runCatching { old?.disconnectForcibly(300); old?.close() }
    }
}
