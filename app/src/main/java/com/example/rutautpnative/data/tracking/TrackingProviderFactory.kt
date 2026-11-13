package com.example.rutautpnative.data.tracking

import android.content.Context

//----Factoria de fuente de flota (puerto del TrackingProviderFactory.swift)----
// Decide en tiempo de ejecucion entre flota REAL (broker MQTT configurado) y
// la simulacion propia del mapa (la flota sobre shapes de la Fase 2, que en
// iOS equivale al SimulatedTrackingProvider). El "simulado" de Android vive
// dentro del propio MapaViewModel, asi que aqui solo se expone el real.
object TrackingProviderFactory {
    private var context: android.content.Context? = null
    fun init(value: android.content.Context) { context = value.applicationContext }

    /// Flota real si el canal MQTT esta configurado; null para seguir con la
    /// simulada (mismo criterio que makeDefault() del iOS).
    fun crearReal(): MQTTTrackingProvider? =
        MQTTConfiguration.actual()?.let { MQTTTrackingProvider(it, context) }
}
