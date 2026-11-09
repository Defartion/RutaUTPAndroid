package com.example.rutautpnative.data

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.runBlocking

//----Preferencia de tema (interruptor manual, persistida)----
// `isDarkMode` default false. NO sigue el tema del sistema: es manual,
// igual que el @AppStorage("isDarkMode") de iOS.
private val Context.temaDataStore by preferencesDataStore(name = "tema_prefs_v1")

object TemaStore {

    private val KEY_OSCURO = booleanPreferencesKey("isDarkMode")

    private var appContext: Context? = null

    //----Espejo en memoria del estado del tema----
    // Mismo patrón que las vars adaptativas de Color.kt: se hidrata del disco
    // al iniciar y se actualiza al instante al cambiar (la recomposición es
    // inmediata y no depende del timing DataStore→Flow→collectAsState).
    var oscuroActual by mutableStateOf(false)
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        // HIDRATACION SINCRONA: el primer frame de setContent lee
        // oscuroActual y DEBE ver el valor persistido, no el default.
        // Sin esto, la app arrastra un flash de tema claro de ~200ms al
        // arrancar en modo oscuro. runBlocking aqui es correcto: es una
        // lectura de un archivo de ~100 bytes, ocurre UNA sola vez al
        // arrancar, y bloquea menos de un frame.
        try {
            oscuroActual = runBlocking {
                context.temaDataStore.data.first()[KEY_OSCURO] ?: false
            }
        } catch (e: Exception) {
            Log.w("TemaStore", "No se pudo leer el tema persistido", e)
        }
        // Manten el espejo sincronizado si algo más escribe la preferencia
        // (en Main para no escribir estado de Compose desde IO).
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            try {
                observarOscuro().collect { oscuroActual = it }
            } catch (e: Exception) {
                Log.w("TemaStore", "Collector del tema fallo", e)
            }
        }
    }

    //----Observable (DataStore)----
    fun observarOscuro(): Flow<Boolean> {
        val ctx = appContext ?: return kotlinx.coroutines.flow.flowOf(false)
        return ctx.temaDataStore.data.map { it[KEY_OSCURO] ?: false }
    }

    //----Cambiar: espejo al instante + persiste de una vez----
    suspend fun establecerOscuro(oscuro: Boolean) {
        oscuroActual = oscuro
        val ctx = appContext ?: return
        ctx.temaDataStore.edit { it[KEY_OSCURO] = oscuro }
    }
}
