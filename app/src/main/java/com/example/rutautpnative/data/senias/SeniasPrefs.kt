package com.example.rutautpnative.data.senias

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

//----Interruptor global "Modo Señas"----
// SÍ persiste (a diferencia de casi todo lo demás en Perfil) porque se lee
// desde cualquier pantalla: si está activo, tocar un texto señable muestra el
// overlay; si está apagado, el texto se comporta normal.
private val Context.seniasPrefsDataStore by preferencesDataStore(name = "senias_prefs_v1")

object SeniasPrefs {

    private val KEY_ACTIVO = booleanPreferencesKey("senias_modo_activado")

    private var appContext: Context? = null

    //----Espejo SINCRONO del estado (Fase 6)----
    // Lo leen Signable/SeniasOverlay desde onClick (fuera de corrutinas).
    // Se hidrata al init y se mantiene con el collector del Flow observable.
    var activoActual by mutableStateOf(false)
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            activoActual = estaActivo()
            observarActivo().collect { activoActual = it }
        }
    }

    //----Lectura puntual----
    suspend fun estaActivo(): Boolean {
        val ctx = appContext ?: return activoActual
        return ctx.seniasPrefsDataStore.data.first()[KEY_ACTIVO] ?: false
    }

    /** Lectura sincrona del espejo (llamable desde cualquier onClick). */
    fun estaActivoSync(): Boolean = activoActual

    //----Observable: la UI reacciona sin reiniciar----
    fun observarActivo(): Flow<Boolean> {
        val ctx = appContext ?: return kotlinx.coroutines.flow.flowOf(false)
        return ctx.seniasPrefsDataStore.data.map { it[KEY_ACTIVO] ?: false }
    }

    //----Cambiar (persistido de inmediato; el espejo reacciona solo)----
    suspend fun establecerActivo(activo: Boolean) {
        val ctx = appContext ?: return
        ctx.seniasPrefsDataStore.edit { it[KEY_ACTIVO] = activo }
        activoActual = activo
    }
}
