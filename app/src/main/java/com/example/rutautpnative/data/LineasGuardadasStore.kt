package com.example.rutautpnative.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.rutautpnative.model.LineaGuardadaRef
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

//----Almacenamiento de líneas guardadas----
// Igual que LugaresStore pero con su propia entrada de DataStore. Solo guarda
// referencias (routeId); no hay seed: empieza vacío.

private val Context.lineasDataStore by preferencesDataStore(name = "lineas_guardadas_v1")

object LineasGuardadasStore {

    private val KEY = stringPreferencesKey("lineas_json")
    private val json = Json { ignoreUnknownKeys = true }

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun cargar(): List<LineaGuardadaRef> {
        val ctx = appContext ?: return emptyList()
        val raw = ctx.lineasDataStore.data.first()[KEY].orEmpty()
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<LineaGuardadaRef>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun guardar(refs: List<LineaGuardadaRef>) {
        val ctx = appContext ?: return
        ctx.lineasDataStore.edit { prefs ->
            prefs[KEY] = json.encodeToString(refs)
        }
    }
}