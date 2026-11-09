package com.example.rutautpnative.data.tracking

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

//----Actividad fisica (puerto de Motion/CoreMotionActivityService.swift)----
// El CMMotionActivityManager de iOS entrega la actividad ACTUAL con nivel de
// confianza. En Android el equivalente moderno es la ActivityRecognition
// Transition API, que entrega transiciones ENTER/EXIT: se mantiene el estado
// vigente y cada EXIT degrada a UNKNOWN (en iOS la confianza baja tambien
// degrada a unknown — el motor trata unknown como compatible con vehiculo,
// asi que el GPS sigue haciendo el trabajo pesado sin este permiso).
object MotionActivityService {

    private const val ACCION = "rutautp.TRANSICION_ACTIVIDAD"

    private val _actividadActual = MutableStateFlow(ActividadMotora.UNKNOWN)
    val actividadActual: StateFlow<ActividadMotora> = _actividadActual.asStateFlow()

    private var appContext: Context? = null
    private var receptorRegistrado = false
    private var pendiente: PendingIntent? = null

    fun tienePermiso(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED
        } else true

    /// Registra las transiciones (idempotente). Sin permiso no registra nada
    /// y la actividad queda en UNKNOWN: el motor de deteccion funciona igual.
    fun start(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        if (!tienePermiso(ctx)) {
            _actividadActual.value = ActividadMotora.UNKNOWN
            return
        }

        if (!receptorRegistrado) {
            val receptor = ReceptorTransiciones()
            val filtro = IntentFilter(ACCION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.registerReceiver(receptor, filtro, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                ctx.registerReceiver(receptor, filtro)
            }
            receptorRegistrado = true
        }

        val pi = pendiente(ctx) ?: return
        val request = ActivityTransitionRequest(
            listOf(
                DetectedActivity.STILL, DetectedActivity.WALKING, DetectedActivity.RUNNING,
                DetectedActivity.IN_VEHICLE, DetectedActivity.ON_BICYCLE
            ).flatMap { tipo ->
                listOf(
                    ActivityTransition.Builder()
                        .setActivityType(tipo)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build(),
                    ActivityTransition.Builder()
                        .setActivityType(tipo)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                        .build()
                )
            }
        )

        // Silencioso a proposito: si el dispositivo no soporta la API, la
        // actividad queda en unknown y la deteccion sigue por GPS.
        try {
            ActivityRecognition.getClient(ctx).requestActivityTransitionUpdates(request, pi)
                .addOnFailureListener { _actividadActual.value = ActividadMotora.UNKNOWN }
        } catch (_: SecurityException) {
            _actividadActual.value = ActividadMotora.UNKNOWN
        }
    }

    fun stop(context: Context) {
        val ctx = appContext ?: context.applicationContext
        try {
            pendiente(ctx)?.let {
                ActivityRecognition.getClient(ctx).removeActivityTransitionUpdates(it)
            }
        } catch (_: SecurityException) {
            // El permiso puede revocarse entre la solicitud y la retirada.
        } catch (e: Exception) {
            // Silencioso a proposito si el dispositivo no soporta la API, pero
            // se loguea para diagnostico de por que no llegan transiciones.
            android.util.Log.w("MotionActivity", "removeActivityTransitionUpdates fallo", e)
        }
        _actividadActual.value = ActividadMotora.UNKNOWN
    }

    private fun pendiente(ctx: Context): PendingIntent? {
        if (pendiente != null) return pendiente
        val intento = Intent(ACCION).setPackage(ctx.packageName)
        pendiente = PendingIntent.getBroadcast(
            ctx,
            0,
            intento,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        return pendiente
    }

    private class ReceptorTransiciones : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACCION) return
            val resultado = ActivityRecognitionResultCompat.extraer(intent) ?: return
            val evento: ActivityTransitionEvent = resultado

            val actividad = when (evento.activityType) {
                DetectedActivity.WALKING -> ActividadMotora.WALKING
                DetectedActivity.RUNNING -> ActividadMotora.RUNNING
                DetectedActivity.IN_VEHICLE -> ActividadMotora.AUTOMOTIVE
                DetectedActivity.ON_BICYCLE -> ActividadMotora.CYCLING
                DetectedActivity.STILL -> ActividadMotora.STATIONARY
                else -> ActividadMotora.UNKNOWN
            }

            _actividadActual.value =
                if (evento.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                    actividad
                } else {
                    // EXIT: sin nueva actividad confirmada se degrada a unknown.
                    ActividadMotora.UNKNOWN
                }
        }
    }
}

// Wrapper minimo: extrae el ultimo evento de transicion del Intent que emite
// la Play Services (compatible con el formato actual del SDK).
private object ActivityRecognitionResultCompat {
    fun extraer(intent: Intent): ActivityTransitionEvent? = try {
        val resultado = com.google.android.gms.location.ActivityTransitionResult.extractResult(intent)
        resultado?.transitionEvents?.lastOrNull()
    } catch (_: Exception) {
        null
    }
}
