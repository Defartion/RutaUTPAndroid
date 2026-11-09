package com.example.rutautpnative.data.negocios

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.rutautpnative.model.Negocio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

//----Cupones guardados por el usuario----
// ÚNICA parte de Perfil/Negocios que sí persiste en disco (es la métrica real,
// no una demo). Guarda solo los ids de negocio; el resto se resuelve contra
// el catálogo del NegociosService.
private val Context.cuponesDataStore by preferencesDataStore(name = "cupones_guardados_v1")

object CuponesStore {

    private val KEY = stringPreferencesKey("cupones_ids")

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun leerIds(prefs: androidx.datastore.preferences.core.Preferences): Set<String> =
        prefs[KEY].orEmpty().split(",").filter { it.isNotBlank() }.toSet()

    //----Ids guardados (una lectura)----
    suspend fun idsGuardados(): Set<String> {
        val ctx = appContext ?: return emptySet()
        return leerIds(ctx.cuponesDataStore.data.first())
    }

    //----Observable: cualquier pantalla que lo muestre se actualiza sola----
    fun observarIds(): Flow<Set<String>> {
        val ctx = appContext ?: return kotlinx.coroutines.flow.flowOf(emptySet())
        return ctx.cuponesDataStore.data.map { leerIds(it) }
    }

    //----¿Este negocio tiene su cupón guardado?----
    suspend fun cuponGuardado(negocio: Negocio): Boolean = negocio.id in idsGuardados()

    //----Negocios con cupón guardado (ordenados por nombre)----
    suspend fun cuponesGuardados(): List<Negocio> {
        val ids = idsGuardados()
        return NegociosService.todos()
            .filter { it.cupon != null && it.id in ids }
            .sortedBy { it.nombre }
    }

    //----Guardar / quitar (persiste de inmediato)----
    suspend fun alternarCupon(negocio: Negocio) {
        val ctx = appContext ?: return
        ctx.cuponesDataStore.edit { prefs ->
            val actuales = leerIds(prefs).toMutableSet()
            if (negocio.id in actuales) actuales.remove(negocio.id) else actuales.add(negocio.id)
            prefs[KEY] = actuales.joinToString(",")
        }
    }
}
