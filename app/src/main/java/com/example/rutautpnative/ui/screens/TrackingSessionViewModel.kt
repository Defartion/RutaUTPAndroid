package com.example.rutautpnative.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rutautpnative.data.tracking.TrackingDemoPrefs
import kotlinx.coroutines.CoroutineScope

internal interface AjustesTracking {
    val radio: Double
    val velocidad: Int
    fun guardarRadio(value: Double)
    fun guardarVelocidad(value: Int)
}

internal class AjustesTrackingAndroid(private val context: Context) : AjustesTracking {
    override val radio get() = TrackingDemoPrefs.leerRadio(context)
    override val velocidad get() = TrackingDemoPrefs.leerVelocidad(context)
    override fun guardarRadio(value: Double) = TrackingDemoPrefs.guardarRadio(context, value)
    override fun guardarVelocidad(value: Int) = TrackingDemoPrefs.guardarVelocidad(context, value)
}

internal class TrackingSessionViewModel : ViewModel() {
    private var modelo: TrackingDemoVM? = null
    fun obtener(crear: (CoroutineScope) -> TrackingDemoVM): TrackingDemoVM =
        modelo ?: crear(viewModelScope).also { modelo = it }
    override fun onCleared() { modelo?.detener() }
}
