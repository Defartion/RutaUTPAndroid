package com.example.rutautpnative.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.rutautpnative.model.LineaGuardadaRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

//----Almacenamiento de líneas guardadas----
// Igual que LugaresStore pero con su propia entrada de DataStore. Solo guarda
// referencias (routeId); no hay seed: empieza vacío.

private val Context.lineasDataStore by preferencesDataStore(name = "lineas_guardadas_v1")

object LineasGuardadasStore {

    private val _datosInvalidos = kotlinx.coroutines.flow.MutableStateFlow(false)
    val datosInvalidos: kotlinx.coroutines.flow.StateFlow<Boolean> = _datosInvalidos
    private val KEY = stringPreferencesKey("lineas_json")
    private val json = Json { ignoreUnknownKeys = true }

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun cargar(): List<LineaGuardadaRef> {
        val ctx = appContext ?: return emptyList()
        val raw = ctx.lineasDataStore.data.first()[KEY].orEmpty()
        return deserializar(raw)
    }

    fun observar(): Flow<List<LineaGuardadaRef>> {
        val ctx = appContext ?: return flowOf(emptyList())
        return ctx.lineasDataStore.data.map { deserializar(it[KEY].orEmpty()) }
    }

    internal fun deserializar(raw: String): List<LineaGuardadaRef> {
        if (raw.isBlank()) { _datosInvalidos.value = false; return emptyList() }
        return try {
            json.decodeFromString<List<LineaGuardadaRef>>(raw).also { _datosInvalidos.value = false }
        } catch (e: Exception) {
            _datosInvalidos.value = true
            emptyList()
        }
    }

    suspend fun guardar(refs: List<LineaGuardadaRef>) {
        modificar { refs }
    }

    suspend fun modificar(transformar: (List<LineaGuardadaRef>) -> List<LineaGuardadaRef>) {
        val ctx = appContext ?: return
        ctx.lineasDataStore.edit { prefs ->
            val actual = deserializar(prefs[KEY].orEmpty())
            if (_datosInvalidos.value) return@edit
            prefs[KEY] = json.encodeToString(transformar(actual).distinctBy { it.routeId })
        }
        com.example.rutautpnative.data.notificaciones.PushRutasService.sincronizar()
    }
    suspend fun reiniciarConRespaldo() {
        val ctx = checkNotNull(appContext)
        ctx.lineasDataStore.edit { prefs ->
            RecuperacionPreferencias.archivar(ctx, "lineas", prefs.asMap().mapKeys { it.key.name })
            prefs[KEY] = "[]"
        }
        _datosInvalidos.value = false
        com.example.rutautpnative.data.notificaciones.PushRutasService.sincronizar()
    }

}
