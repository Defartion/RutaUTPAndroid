package com.example.rutautpnative.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.model.CategoriaLugar
import com.example.rutautpnative.model.LugarGuardado
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

//----Almacenamiento de lugares guardados----
// Persiste la lista de lugares en DataStore (Preferences) como JSON bajo una
// clave versionada. La semilla inicial (UTP + Plaza de Armas) se usa solo si
// no hay nada guardado todavía.

private val Context.lugaresDataStore by preferencesDataStore(name = "lugares_guardados_v1")

object LugaresStore {

    private val _datosInvalidos = kotlinx.coroutines.flow.MutableStateFlow(false)
    val datosInvalidos: kotlinx.coroutines.flow.StateFlow<Boolean> = _datosInvalidos

    private val KEY = stringPreferencesKey("lugares_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    //----Lugar fijo (UTP), con la coordenada real del campus----
    private val lugarUTP = LugarGuardado(
        id = "utp-trujillo",
        nombre = "UTP",
        direccion = "Av. Nicolás de Piérola 1221, Trujillo",
        categoria = CategoriaLugar.UNIVERSIDAD,
        esFrecuente = true,
        esFijo = true,
        lat = GTFSRepository.coordenadaUTP.latitude,
        lon = GTFSRepository.coordenadaUTP.longitude
    )

    private val lugarPlaza = LugarGuardado(
        id = "plaza-armas-trujillo",
        nombre = "Plaza de Armas",
        direccion = "Centro Histórico de Trujillo",
        categoria = CategoriaLugar.PLAZA,
        lat = -8.1096,
        lon = -79.0287
    )

    //----Consulta de la lista actual (una sola lectura)----
    // Si no hay nada guardado devuelve la semilla; siempre reordena UTP primero.
    suspend fun cargar(): List<LugarGuardado> {
        val ctx = appContext ?: return semilla()
        val raw = ctx.lugaresDataStore.data.first()[KEY].orEmpty()
        val lista = if (raw.isBlank()) semilla() else deserializar(raw)
        return reordenarUTP(lista)
    }

    //----Observación reactiva----
    // Devuelve un Flow que se emite cada vez que cambia el valor guardado.
    fun observar(): Flow<List<LugarGuardado>> {
        val ctx = appContext ?: return kotlinx.coroutines.flow.emptyFlow()
        return ctx.lugaresDataStore.data.map { prefs ->
            val raw = prefs[KEY].orEmpty()
            val lista = if (raw.isBlank()) semilla() else deserializar(raw)
            reordenarUTP(lista)
        }
    }

    //----Persistencia inmediata de la lista actual----
    suspend fun guardar(lugares: List<LugarGuardado>) {
        val ctx = appContext ?: return
        ctx.lugaresDataStore.edit { prefs ->
            val anterior = prefs[KEY]
            if (!anterior.isNullOrBlank()) { deserializar(anterior); if (_datosInvalidos.value) return@edit }
            prefs[KEY] = json.encodeToString(lugares)
        }
        com.example.rutautpnative.data.notificaciones.NotificacionesService.refrescar()
    }

    suspend fun modificar(transformar: (List<LugarGuardado>) -> List<LugarGuardado>) {
        val ctx = appContext ?: return
        ctx.lugaresDataStore.edit { prefs ->
            val raw = prefs[KEY].orEmpty()
            val actual = reordenarUTP(if (raw.isBlank()) semilla() else deserializar(raw))
            if (_datosInvalidos.value) return@edit
            prefs[KEY] = json.encodeToString(reordenarUTP(transformar(actual)))
        }
        com.example.rutautpnative.data.notificaciones.NotificacionesService.refrescar()
    }

    suspend fun reiniciarConRespaldo() {
        val ctx = checkNotNull(appContext)
        ctx.lugaresDataStore.edit { prefs ->
            RecuperacionPreferencias.archivar(ctx, "lugares", prefs.asMap().mapKeys { it.key.name })
            prefs[KEY] = json.encodeToString(semilla())
        }
        _datosInvalidos.value = false
    }

    private fun semilla(): List<LugarGuardado> = listOf(lugarUTP, lugarPlaza)

    // UTP siempre primero; si no existe, se inserta.
    private fun reordenarUTP(lista: List<LugarGuardado>): List<LugarGuardado> {
        val utp = lista.firstOrNull { it.esFijo }
        val resto = lista.filter { !it.esFijo }
        return listOf(utp ?: lugarUTP) + resto
    }

    internal fun deserializar(raw: String): List<LugarGuardado> = try {
        json.decodeFromString<List<LugarGuardado>>(raw).also { _datosInvalidos.value = false }.let { lugares ->
            // Los registros anteriores carecían de esFijo: migrar solo esos registros.
            val originales = Json.parseToJsonElement(raw) as kotlinx.serialization.json.JsonArray
            lugares.mapIndexed { i, lugar ->
                if ((originales.getOrNull(i) as? kotlinx.serialization.json.JsonObject)?.containsKey("esFijo") != true && lugar.nombre.equals("UTP", true)) lugar.copy(esFijo = true) else lugar
            }
        }
    } catch (e: Exception) {
        _datosInvalidos.value = true
        // Si el JSON guardado quedó corrupto, se cae a la semilla antes que romper.
        semilla()
    }
}
