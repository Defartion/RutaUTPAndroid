package com.example.rutautpnative.data.notificaciones

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.rutautpnative.MainActivity
import com.example.rutautpnative.R
import com.example.rutautpnative.data.LineasGuardadasStore
import com.example.rutautpnative.ui.idioma.L
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

class RutaFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { PushRutasService.tokenRenovado(token) }
    override fun onMessageReceived(message: RemoteMessage) {
        // Solo data messages: la política local decide antes de mostrar cualquier aviso.
        val cambio = CambioRutaRemoto.leer(message.data) ?: return
        val now = System.currentTimeMillis() / 1000.0
        if (cambio.expiresAt <= now || cambio.expiresAt > now + 910) return
        val prefs = getSharedPreferences(PushRutasService.PREFS, 0)
        if (!prefs.getBoolean("notificaciones.push.rutas.v1", false) || !getSharedPreferences("rutautp.persistencia", 0).getBoolean("perfil_notificaciones", false) || !NotificacionesService.permitido(this)) return
        LineasGuardadasStore.init(this)
        val routes = runBlocking(Dispatchers.IO) { LineasGuardadasStore.cargar() }
        if (routes.none { it.routeId == cambio.routeID }) return
        runBlocking(Dispatchers.Main) {
            L.init(this@RutaFirebaseMessagingService)
            NotificacionesService.init(this@RutaFirebaseMessagingService, programar = false)
        }
        if (NotificacionesService.datosInvalidos || !NotificacionesService.configuracion.permite(java.time.Instant.now(), java.time.ZoneId.systemDefault())) return
        synchronized(dedupLock) {
            val original = prefs.getString("notificaciones.push.eventos.v1", "{}") ?: return
            val previous = runCatching { Json.parseToJsonElement(original).jsonObject }.getOrNull() ?: return
            val vigentes = previous.filterValues { (it as? JsonPrimitive)?.doubleOrNull?.let { expiry -> expiry.isFinite() && expiry > now } == true }.toMutableMap()
            if (cambio.eventID in vigentes || vigentes.size >= 256) return
            vigentes[cambio.eventID] = JsonPrimitive(cambio.expiresAt)
            if (!prefs.edit().putString("notificaciones.push.eventos.v1", JsonObject(vigentes).toString()).commit()) return
        }
        val intent = Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("pushRouteID", cambio.routeID).putExtra("pushExpiresAt", cambio.expiresAt)
        val pending = PendingIntent.getActivity(this, cambio.eventID.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val config = NotificacionesService.configuracion
        val text = L.t("Consulta los reportes actuales de tu línea. El aviso no confirma por sí solo el estado de la ruta.", "Check current reports for your line. This notification alone does not confirm the route's status.")
        val notification = NotificationCompat.Builder(this, if (config.sonido) "viajes" else "viajes_sin_sonido")
            .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle(L.t("Cambio en tu línea guardada", "Change on your saved route"))
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(pending)
            .setTimeoutAfter(((cambio.expiresAt - now) * 1000).toLong()).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(this).notify(cambio.eventID.hashCode(), notification) } catch (_: SecurityException) { }
    }
    companion object { private val dedupLock = Any() }
}
