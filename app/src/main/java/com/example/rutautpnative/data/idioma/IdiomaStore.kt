package com.example.rutautpnative.data.idioma

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch

//----Idioma persistido ("es" por defecto)----
// Mismo patrón que TemaStore/SeniasPrefs: preferencia global del usuario que
// se lee desde toda la app, por eso sí persiste.
private val Context.idiomaDataStore by preferencesDataStore(name = "idioma_prefs_v1")

object IdiomaStore {

    private val KEY = stringPreferencesKey("idioma_codigo")

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun observarCodigo(): Flow<String> {
        val ctx = appContext ?: return kotlinx.coroutines.flow.flowOf("es")
        return ctx.idiomaDataStore.data.map { it[KEY] ?: "es" }.catch { error ->
            if (error is java.io.IOException) emit("es") else throw error
        }
    }

    suspend fun establecerCodigo(codigo: String) {
        val ctx = appContext ?: return
        ctx.idiomaDataStore.edit { it[KEY] = codigo }
    }
}
