package com.example.rutautpnative.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Pausa sensores y animación tanto al ocultar la pantalla como al salir de la app. */
@Composable
fun AlEstarVisible(alReanudar: () -> Unit, alPausar: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val reanudar by rememberUpdatedState(alReanudar)
    val pausar by rememberUpdatedState(alPausar)
    DisposableEffect(owner) {
        var activa = false
        fun aplicar(visible: Boolean) {
            if (activa == visible) return
            activa = visible
            if (visible) reanudar() else pausar()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) aplicar(true)
            if (event == Lifecycle.Event.ON_PAUSE) aplicar(false)
        }
        owner.lifecycle.addObserver(observer)
        aplicar(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { owner.lifecycle.removeObserver(observer); aplicar(false) }
    }
}
