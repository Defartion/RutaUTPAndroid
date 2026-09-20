package com.example.rutautpnative.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.rutautpnative.model.LugarGuardado
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

//----Preferencias de los tiles de lugares en Seguridad----
// Qué lugares se muestran en la fila de Seguridad (elegidos) y en qué orden.
// Separada de LugaresStore: solo lee/especificable para no tocar nada más.
private val Context.seguridadTilesDataStore by preferencesDataStore(name = "seguridad_tiles_v1")

object SeguridadTilesStore {

    private val KEY_ELEGIDOS = stringPreferencesKey("elegidos")
    private val KEY_ORDEN = stringPreferencesKey("orden")

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // Lista de ids elegidos para mostrar en la fila.
    // null = nunca personalizó (usar los 2 primeros por defecto);
    // lista (incluso vacía) = selección explícita del usuario.
    suspend fun leerElegidos(): List<String>? {
        val ctx = appContext ?: return null
        val raw = ctx.seguridadTilesDataStore.data.map { it[KEY_ELEGIDOS] }.first()
            ?: return null
        return raw.split(",").filter { it.isNotBlank() }
    }

    // Orden persistido (ids). Los no presentes aparecen al final.
    suspend fun leerOrden(): List<String> {
        val ctx = appContext ?: return emptyList()
        return ctx.seguridadTilesDataStore.data.map { it[KEY_ORDEN] }.first()
            .orEmpty()
            .split(",")
            .filter { it.isNotBlank() }
    }

    suspend fun guardarElegidos(ids: List<String>) {
        val ctx = appContext ?: return
        ctx.seguridadTilesDataStore.edit { it[KEY_ELEGIDOS] = ids.joinToString(",") }
    }

    suspend fun guardarOrden(ids: List<String>) {
        val ctx = appContext ?: return
        ctx.seguridadTilesDataStore.edit { it[KEY_ORDEN] = ids.joinToString(",") }
    }

    /**
     * Reconstruye la lista ordenada de tiles que se muestran en Seguridad:
     * 1. UTP siempre primero (viene de LugaresStore, es el lugar fijo).
     * 2. Si no hay selección guardada, se muestran los primeros 2 no-fijos.
     * 3. Si hay, se usa esa selección ordenada por la preferencia [orden].
     */
    fun reconstruir(
        lugares: List<LugarGuardado>,
        elegidos: List<String>?,
        orden: List<String>
    ): List<LugarGuardado> {
        val utp = lugares.firstOrNull { it.esFijo } ?: return emptyList()
        val noFijos = lugares.filter { !it.esFijo }

        val seleccionados = if (elegidos == null) {
            noFijos.take(2)
        } else {
            val tmp = noFijos.filter { it.id in elegidos.toSet() }
            val ordenIndex = orden.withIndex().associate { it.value to it.index }
            tmp.sortedBy { ordenIndex[it.id] ?: Int.MAX_VALUE }  // no guardados van al final
        }
        return listOf(utp) + seleccionados
    }
}