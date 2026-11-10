package com.example.rutautpnative.data.monedero

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToLong

//----Monedero de demostracion (puerto del MonederoStore.swift)----
// NO procesa pagos ni valida viajes: es saldo local de demo con historial.
// Reglas del iOS, respetadas al byte:
//   - El saldo se lleva en CENTIMOS (Long): nunca floats en dinero.
//   - Tarifa de referencia S/ 2.50; saldo inicial S/ 10.00; maximo S/ 1 000 000.
//   - El historial se recorta a 20 movimientos.
//   - Los datos corruptos BLOQUEAN recargas/cobros (datosLocalesInvalidos),
//     conservando los originales en disco.
//   - Claves: monedero.saldo.v1 / monedero.movimientos.v1 (inventario iOS).

private val Context.monederoDataStore by preferencesDataStore(name = "monedero_v1")

@Serializable
data class MovimientoMonedero(
    val id: String = UUID.randomUUID().toString(),
    val fecha: Double,             // UNIX segundos
    val importeCentimos: Long      // signo identifica la operacion
) {
    val esRecarga: Boolean get() = importeCentimos > 0
    val importe: Double get() = importeCentimos / 100.0
}

object MonederoStore {

    const val TARIFA_CENTIMOS = 250L            // S/ 2.50
    const val SALDO_INICIAL_CENTIMOS = 1_000L   // S/ 10.00
    const val MAXIMO_CENTIMOS = 100_000_000L    // S/ 1 000 000
    const val MAX_MOVIMIENTOS = 20

    private const val NOMBRE_DS = "monedero_v1"
    private val LLAVE_SALDO = longPreferencesKey("monedero.saldo.v1")
    private val LLAVE_MOVIMIENTOS = stringPreferencesKey("monedero.movimientos.v1")

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val _saldoCentimos = MutableStateFlow(SALDO_INICIAL_CENTIMOS)
    val saldoCentimos: StateFlow<Long> = _saldoCentimos.asStateFlow()

    private val _movimientos = MutableStateFlow<List<MovimientoMonedero>>(emptyList())
    val movimientos: StateFlow<List<MovimientoMonedero>> = _movimientos.asStateFlow()

    private val _datosLocalesInvalidos = MutableStateFlow(false)
    val datosLocalesInvalidos: StateFlow<Boolean> = _datosLocalesInvalidos.asStateFlow()

    private val operaciones = Mutex()
    private var cargaInicial: Job? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        cargaInicial = scope.launch { operaciones.withLock { cargar() } }
    }

    //----Consultas----

    fun reintentarLectura() {
        scope.launch { cargaInicial?.join(); operaciones.withLock { cargar() } }
    }

    val saldo: Double get() = _saldoCentimos.value / 100.0
    val pasajesDisponibles: Int get() = (_saldoCentimos.value / TARIFA_CENTIMOS).toInt()
    val saldoTexto: String get() = "S/ %.2f".format(saldo)
    val tarifaTexto: String get() = "S/ %.2f".format(TARIFA_CENTIMOS / 100.0)

    //----Operaciones----

    /// Recarga de demostracion. Rechaza no finitos, <= 0, fracciones de
    /// centimo (tolera solo 1e-6 de error binario) y el exceso del maximo.
    fun importeCentimosValido(soles: Double): Long? {
        if (!soles.isFinite() || soles <= 0 || soles > MAXIMO_CENTIMOS / 100.0) return null
        val centimos = (soles * 100).roundToLong()
        return centimos.takeIf { it > 0 && abs(soles * 100 - it) <= 1e-6 }
    }

    suspend fun recargar(soles: Double): Boolean = operar(soles, recarga = true)
    suspend fun cobrarPasaje(soles: Double = TARIFA_CENTIMOS / 100.0): Boolean = operar(soles, recarga = false)

    private suspend fun operar(soles: Double, recarga: Boolean): Boolean {
        val centimos = importeCentimosValido(soles) ?: return false
        cargaInicial?.join()
        return operaciones.withLock {
            if (_datosLocalesInvalidos.value) return@withLock false
            val nuevo = _saldoCentimos.value + if (recarga) centimos else -centimos
            if (nuevo !in 0..MAXIMO_CENTIMOS) return@withLock false
            val movimiento = MovimientoMonedero(fecha = System.currentTimeMillis() / 1000.0,
                importeCentimos = if (recarga) centimos else -centimos)
            val historial = (listOf(movimiento) + _movimientos.value).take(MAX_MOVIMIENTOS)
            val ctx = appContext ?: return@withLock false
            try {
                ctx.monederoDataStore.edit {
                    it[LLAVE_SALDO] = nuevo
                    it[LLAVE_MOVIMIENTOS] = json.encodeToString(historial)
                }
                _saldoCentimos.value = nuevo
                _movimientos.value = historial
                true
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { false }
        }
    }

    suspend fun reiniciarConRespaldo() {
        cargaInicial?.join()
        operaciones.withLock {
            val ctx = checkNotNull(appContext)
            ctx.monederoDataStore.edit { prefs ->
                com.example.rutautpnative.data.RecuperacionPreferencias.archivar(ctx, "monedero", prefs.asMap().mapKeys { it.key.name })
                prefs[LLAVE_SALDO] = SALDO_INICIAL_CENTIMOS
                prefs[LLAVE_MOVIMIENTOS] = "[]"
            }
            _saldoCentimos.value = SALDO_INICIAL_CENTIMOS
            _movimientos.value = emptyList()
            _datosLocalesInvalidos.value = false
        }
    }

    //----Interno----

    private fun registrar(movimiento: MovimientoMonedero) {
        // Historial recortado a 20: el mas reciente primero en disco (iOS
        // guarda prepend), y la UI toma los que necesite.
        val nueva = listOf(movimiento) + _movimientos.value
        _movimientos.value = nueva.take(MAX_MOVIMIENTOS)
    }

    private suspend fun cargar() {
        val ctx = appContext ?: return
        try {
            val datos = ctx.monederoDataStore.data.first()
            val saldo = datos[LLAVE_SALDO]
            val movsRaw = datos[LLAVE_MOVIMIENTOS]

            var movimientos: List<MovimientoMonedero>? = null
            if (!movsRaw.isNullOrBlank()) {
                movimientos = try {
                    json.decodeFromString<List<MovimientoMonedero>>(movsRaw)
                } catch (e: Exception) {
                    null
                }
            }

            // Validacion (iOS idem): ids unicos y valores finitos. Algo
            // corrupto bloquea las operaciones antes que operar con basura.
            val movs = movimientos
            val movsOk = movs != null &&
                movs.map { it.id }.distinct().size == movs.size &&
                movs.all { it.fecha.isFinite() && it.importeCentimos != 0L }

            when {
                saldo != null && saldo in 0..MAXIMO_CENTIMOS && movsOk && movs != null -> {
                    _saldoCentimos.value = saldo
                    _movimientos.value = movs.take(MAX_MOVIMIENTOS)
                    _datosLocalesInvalidos.value = false
                }
                saldo == null && movsRaw == null -> {
                    // Instalacion nueva: saldo inicial de demo, sin historial.
                    _saldoCentimos.value = SALDO_INICIAL_CENTIMOS
                    _movimientos.value = emptyList()
                    _datosLocalesInvalidos.value = false
                    persistir()
                }
                else -> {
                    // Datos irrecuperables: se BLOQUEA (el original queda en disco).
                    _datosLocalesInvalidos.value = true
                    _saldoCentimos.value = 0
                    _movimientos.value = emptyList()
                }
            }
        } catch (e: Exception) {
            // Datastore ilegible: bloqueado (se conservan los datos originales).
            _datosLocalesInvalidos.value = true
        }
    }

    private suspend fun persistir() {
        val ctx = appContext ?: return
        try {
            ctx.monederoDataStore.edit { prefs ->
                prefs[LLAVE_SALDO] = _saldoCentimos.value
                prefs[LLAVE_MOVIMIENTOS] = json.encodeToString(_movimientos.value)
            }
        } catch (e: Exception) {
            android.util.Log.w("Monedero", "persistencia fallo (datos conservados en memoria)", e)
        }
    }
}
