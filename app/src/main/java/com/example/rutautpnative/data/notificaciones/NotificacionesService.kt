package com.example.rutautpnative.data.notificaciones

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.*
import com.example.rutautpnative.MainActivity
import com.example.rutautpnative.R
import com.example.rutautpnative.data.LugaresStore
import com.example.rutautpnative.data.PreferenciasPerfilStore
import com.example.rutautpnative.ui.idioma.L
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId

object NotificacionesService {
    private const val KEY = "notificaciones.config.v1"
    private const val PREFS = "rutautp.persistencia"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var context: Context? = null
    private var trabajo: Job? = null
    var configuracion by mutableStateOf(ConfiguracionNotificaciones())
        private set
    var datosInvalidos by mutableStateOf(false)
        private set
    var programadas by mutableIntStateOf(0)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    fun init(value: Context, programar: Boolean = true) { context = value.applicationContext; recargar(); canales(value); if (programar) refrescar() }
    private fun recargar() {
        val raw = context?.getSharedPreferences(PREFS, 0)?.getString(KEY, null)
        val config = if (raw == null) ConfiguracionNotificaciones() else runCatching { Json.decodeFromString<ConfiguracionNotificaciones>(raw) }.getOrNull()
        datosInvalidos = config?.valida() != true
        if (config != null && !datosInvalidos) configuracion = config
    }
    fun guardar(config: ConfiguracionNotificaciones): Boolean {
        recargar()
        if (datosInvalidos || !config.valida()) return false
        val ok = context?.getSharedPreferences(PREFS, 0)?.edit()?.putString(KEY, Json.encodeToString(ConfiguracionNotificaciones.serializer(), config))?.commit() == true
        if (ok) { configuracion = config; refrescar(); PushRutasService.sincronizar() }
        return ok
    }
    fun reiniciarConRespaldo() {
        val ctx = checkNotNull(context)
        val prefs = ctx.getSharedPreferences(PREFS, 0)
        val original = prefs.all.filterKeys { it == KEY || it == "perfil_notificaciones" }
        com.example.rutautpnative.data.RecuperacionPreferencias.archivar(ctx, "notificaciones", original)
        check(original == prefs.all.filterKeys { it == KEY || it == "perfil_notificaciones" })
        check(prefs.edit().remove(KEY).putBoolean("perfil_notificaciones", false).commit())
        PreferenciasPerfilStore.init(ctx)
        recargar()
        refrescar()
        PushRutasService.sincronizar()
    }
    fun permitido(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    private fun canales(ctx: Context) {
        val manager = ctx.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("viajes", L.t("Recordatorios de viaje", "Trip reminders"), NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel("viajes_sin_sonido", L.t("Avisos sin sonido", "Silent notifications"), NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) })
    }
    private fun pendiente(ctx: Context, id: Int, aviso: AvisoProgramado? = null): PendingIntent {
        val intent = Intent(ctx, AvisoReceiver::class.java).setAction("rutautp.AVISO.$id")
        aviso?.let { intent.putExtra("configuracionID", if (it.destinoID != null) configuracion.recordatorio?.id else configuracion.diarioID) }
        aviso?.destinoID?.let { intent.putExtra("destinoID", it) }
        aviso?.tipoDiario?.let { intent.putExtra("diario", it) }
        return PendingIntent.getBroadcast(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun refrescar() {
        trabajo?.cancel()
        trabajo = scope.launch { reprogramar() }
    }
    suspend fun reprogramar() {
        val ctx = context ?: return
        recargar()
        val alarmas = ctx.getSystemService(AlarmManager::class.java)
        val prefs = ctx.getSharedPreferences(PREFS, 0)
        val ids = prefs.getStringSet("notificaciones.alarmas.v1", emptySet()).orEmpty()
        ids.mapNotNull { it.toIntOrNull() }.forEach { alarmas.cancel(pendiente(ctx, it)) }
        programadas = 0
        if (datosInvalidos || !prefs.getBoolean("perfil_notificaciones", false) || !permitido(ctx)) return
        val now = Instant.now(); val zona = ZoneId.systemDefault()
        val destinos = LugaresStore.cargar()
        val destino = configuracion.recordatorio?.destinoID
        val valido = destino?.removePrefix("lugar:")?.let { id -> destinos.any { it.id == id } } == true ||
            destino?.let { it == "sede:${com.example.rutautpnative.data.SedeTrabajoStore.sede?.id}" } == true
        val viajes = if (valido) ProgramadorRecordatorios.fechas(configuracion, now, zona).map { AvisoProgramado(it, destinoID = destino) } else emptyList()
        val agenda = (viajes + ProgramadorRecordatorios.diarios(configuracion, now, zona)).sortedBy { it.fecha }.take(60)
        val nuevos = mutableSetOf<String>()
        try {
            for ((index, aviso) in agenda.withIndex()) {
                currentCoroutineContext().ensureActive()
                val id = 20_000 + index
                // Entregas inexactas de Android: no requieren acceso especial a alarmas exactas.
                alarmas.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, aviso.fecha.toEpochMilli(), pendiente(ctx, id, aviso))
                nuevos.add(id.toString())
            }
            error = null
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = L.t("No se pudo completar la programación.", "Could not complete scheduling.") }
        finally { prefs.edit().putStringSet("notificaciones.alarmas.v1", nuevos).commit(); programadas = nuevos.size }
    }
    fun mostrar(ctx: Context, destino: String?, diario: Int?, prueba: Boolean = false, configuracionID: String? = null) {
        if (!permitido(ctx)) return
        if (!prueba && (!ctx.getSharedPreferences(PREFS, 0).getBoolean("perfil_notificaciones", false) || datosInvalidos || !configuracion.permite(Instant.now(), ZoneId.systemDefault()))) return
        if (!prueba && !configuracion.aceptaAviso(configuracionID, destino, diario)) return
        val intent = Intent(ctx, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra("notificacionAccionID", "local:" + java.util.UUID.randomUUID())
            // Los locales dependen de configuración y destino vigentes, no de un TTL inventado.
            .putExtra("notificacionVenceEn", Double.MAX_VALUE)
            .putExtra("notificacionConfiguracionID", configuracionID)
        if (destino != null) intent.putExtra("notificacionDestinoID", destino)
        if (diario != null) intent.putExtra("notificacionDiaria", diario)
        val titulo = when (diario) {
            0 -> L.t("Antes de salir", "Before heading out")
            1 -> L.t("Consulta los cambios de ruta", "Check route changes")
            2 -> L.t("Revisa las condiciones de tus cupones", "Check your coupon terms")
            3 -> L.t("Prepara tu próximo viaje", "Prepare your next trip")
            else -> if (prueba) L.t("Notificación de prueba", "Test notification") else L.t("Prepara tu viaje", "Prepare your trip")
        }
        val texto = if (prueba) L.t("Las notificaciones están disponibles en este dispositivo.", "Notifications are available on this device.")
            else L.t("Abre RutaUTP para revisar tu línea, sus paraderos y la información disponible antes de salir.", "Open RutaUTP to check your line, stops and available information before leaving.")
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val content = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(ctx, if (configuracion.sonido) "viajes" else "viajes_sin_sonido")
            .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle(titulo).setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto)).setContentIntent(content).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(ctx).notify(id, notification) } catch (_: SecurityException) { }
    }
}

class AvisoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            try {
                withTimeout(8_000) {
                    LugaresStore.init(context)
                    L.init(context)
                    com.example.rutautpnative.model.CatalogoSedesTrabajo.init(context)
                    com.example.rutautpnative.data.SedeTrabajoStore.init(context)
                    NotificacionesService.init(context, programar = false)
                    if (intent.action?.startsWith("rutautp.AVISO.") == true) {
                        NotificacionesService.mostrar(context, intent.getStringExtra("destinoID"), intent.takeIf { it.hasExtra("diario") }?.getIntExtra("diario", 0), configuracionID = intent.getStringExtra("configuracionID"))
                    }
                    NotificacionesService.reprogramar()
                }
            } finally { pending.finish() }
        }
    }
}
