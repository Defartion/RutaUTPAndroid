package com.example.rutautpnative.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.accessibility.AccessibilityManager

//----Feedback háptico compartido (puerto del AppHaptics.swift)----
// No visual: selección, impacto, éxito y advertencia. Con TalkBack (o un
// lector de pantalla con feedback hablado) activo, el dispositivo ya vibra
// al mover el foco, así que la selección se OMITE para no duplicar el aviso
// táctil (iOS hace lo propio con VoiceOver).
//
// MinSdk 26: VibrationEffect.EFFECT_* predefinidos llegaron en API 29, así
// que se usan pulsos por amplitud (disponibles desde la 26).

object AppHaptics {
    private var vibrator: Vibrator? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun pulso(ms: Long, amplitud: Int) {
        val v = vibrator ?: return
        try {
            if (!v.hasVibrator()) return
            v.vibrate(VibrationEffect.createOneShot(ms, amplitud))
        } catch (e: Exception) {
            // Sin permiso VIBRATE o vibrador no disponible: no rompe nada.
        }
    }

    /// Selección: cambio de opción en un selector o en un tab.
    fun seleccion() {
        if (hablaUnLector()) return
        pulso(15, 120)
    }

    /// Impacto físico. `medio` para las acciones que fijan algo.
    fun impacto(medio: Boolean = false) {
        pulso(if (medio) 40 else 22, if (medio) 255 else 180)
    }

    /// Operación completada con éxito (guardar, copiar, confirmar).
    fun exito() {
        pulso(50, 255)
    }

    /// Aviso: el usuario intentó algo que todavía no está disponible.
    fun advertencia() {
        pulso(70, 255)
    }

    /// ¿Hay un lector de pantalla con feedback hablado activo? (TalkBack).
    /// Publico: las vistas lo usan para elegir alternativas accesibles a los
    /// gestos directos (p. ej. la colocación del marcador abre el picker).
    fun lectorDePantallaActivo(): Boolean = hablaUnLector()

    private fun hablaUnLector(): Boolean {
        val contexto = appContext ?: return false
        return try {
            val am = contexto.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            am?.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_SPOKEN
            )?.isNotEmpty() == true
        } catch (e: Exception) {
            false
        }
    }
}
