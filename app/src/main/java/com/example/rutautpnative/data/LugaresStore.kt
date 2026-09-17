package com.example.rutautpnative.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.model.CategoriaLugar
import com.example.rutautpnative.model.LugarGuardado
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

//----Almacenamiento de lugares guardados----
// Persiste la lista de lugares en DataStore (Preferences) como JSON bajo una
// clave versionada. La semilla inicial (UTP + Plaza de Armas) se usa solo si
// no hay nada guardado todavía.

private val Context.lugaresDataStore by preferencesDataStore(name = "lugares_guardados_v1")

object LugaresStore {

    private val KEY = stringPreferencesKey("lugares_json")
    private val json = Json { ignoreUnknownKeys = true }

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    //----Lugar fijo (UTP), con la coordenada real del campus----
    private val lugarUTP = LugarGuardado(
        nombre = "UTP",
        direccion = "Av. Nicolás de Piérola 1221, Trujillo",
        categoria = CategoriaLugar.UNIVERSIDAD,
        esFrecuente = true,
        lat = GTFSRepository.coordenadaUTP.latitude,
        lon = GTFSRepository.coordenadaUTP.longitude
    )

    private val lugarPlaza = LugarGuardado(
        nombre = "Plaza de Armas",
        direccion = "Centro Histórico de Trujillo",
        categoria = CategoriaLugar.PLAZA,
        lat = -8.1096,
        lon = -79.0287
    )

    //----Consulta de la lista actual----
    // Si no hay nada guardado devuelve la semilla; siempre reordena UTP primero.
    suspend fun cargar(): List<LugarGuardado> {
        val ctx = appContext ?: return semilla()
        val raw = ctx.lugaresDataStore.data.first()[KEY].orEmpty()
        val lista = if (raw.isBlank()) semilla() else deserializar(raw)
        return reordenarUTP(lista)
    }

    //----Persistencia inmediata de la lista actual----
    suspend fun guardar(lugares: List<LugarGuardado>) {
        val ctx = appContext ?: return
        ctx.lugaresDataStore.edit { prefs ->
            prefs[KEY] = json.encodeToString(lugares)
        }
    }

    private fun semilla(): List<LugarGuardado> = listOf(lugarUTP, lugarPlaza)

    // UTP siempre primero; si no existe, se inserta.
    private fun reordenarUTP(lista: List<LugarGuardado>): List<LugarGuardado> {
        val utp = lista.firstOrNull { it.esFijo }
        val resto = lista.filter { !it.esFijo }
        return listOf(utp ?: lugarUTP) + resto
    }

    private fun deserializar(raw: String): List<LugarGuardado> = try {
        json.decodeFromString<List<LugarGuardado>>(raw)
    } catch (e: Exception) {
        // Si el JSON guardado quedó corrupto, se cae a la semilla antes que romper.
        semilla()
    }
}