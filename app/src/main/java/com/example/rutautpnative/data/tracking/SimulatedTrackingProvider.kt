package com.example.rutautpnative.data.tracking

import com.example.rutautpnative.data.geo.PolylineMatching
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.random.Random

//----Proveedor de flota SIMULADA (puerto del SimulatedTrackingProvider.swift)----
// La flota demo de la pantalla TrackingDemo: 12 vehiculos sobre shapes
// REALES del feed (GPS simulado, geometria real). El iOS lo inyecta por
// defecto cuando el broker no esta configurado; con broker, la factoria
// devuelve el MQTTTrackingProvider real y este no se usa.
class SimulatedTrackingProvider {

    private data class VehiculoSim(
        val id: String,
        val ruta: RutaGTFS,
        val shape: List<LatLng>,
        val acumulados: DoubleArray,
        val longitudTotalM: Double,
        val velocidadMS: Double,
        var distanciaM: Double,
        var haciaAdelante: Boolean
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var vehiculos: List<VehiculoSim> = emptyList()
    private var tickJob: Job? = null
    private var reconstrucciones = 0
    private var flotaJob: Job? = null

    private val _posiciones = MutableStateFlow<List<VehiclePosition>>(emptyList())
    val posiciones: StateFlow<List<VehiclePosition>> = _posiciones.asStateFlow()

    /// Recoge `cantidad` vehiculos (12, como el iOS) de las rutas mas cercanas
    /// al centro; la `rutaPreferida` (la del viaje iniciado) va SIEMPRE primero.
    /// Se reconstruye en una corrutina de fondo para no congelar la pantalla.
    fun follow(routeID: String?, near: LatLng) {
        reconstrucciones++
        val token = reconstrucciones
        flotaJob?.cancel()
        flotaJob = scope.launch {
            val rutas = GTFSRepository.rutas().filter { it.shape.size >= 2 }
            val ordenadas = rutas
                .map { it to (it.shape.minOfOrNull { p -> GTFSRepository.distanciaMetros(p, near) } ?: Double.MAX_VALUE) }
                .sortedBy { it.second }
                .map { it.first }

            val elegidas = buildList {
                // La ruta preferida siempre primera (follow del iOS).
                routeID?.let { id -> ordenadas.firstOrNull { it.id == id } }?.let { add(it) }
                for (ruta in ordenadas) {
                    if (size >= CANTIDAD) break
                    if (ruta.id != routeID) add(ruta)
                }
            }.take(CANTIDAD)

            vehiculos = elegidas.mapIndexedNotNull { index, ruta ->
                val shape = PolylineMatching.decimate(ruta.shape, 240)
                if (shape.size < 2) return@mapIndexedNotNull null
                val acumulados = PolylineMatching.distanciasAcumuladas(shape)
                val total = acumulados.last()
                if (total <= 0.0) return@mapIndexedNotNull null

                // Nace anclado al punto del shape mas cercano al centro menos
                // offsets de 130 m x (index%4+1) (regla del iOS).
                val idxCentro = shape.indices.minByOrNull {
                    GTFSRepository.distanciaMetros(shape[it], near)
                } ?: 0
                val nacimiento = (acumulados[idxCentro] - 130.0 * ((index % 4) + 1)).coerceIn(0.0, total)

                // Velocidad crucero: distancia/duracion del feed, clamp 4-12 m/s.
                val v = if (ruta.duracionMin > 0 && ruta.distanciaKm > 0) {
                    (ruta.distanciaKm * 1000.0 / (ruta.duracionMin * 60.0)).coerceIn(4.0, 12.0)
                } else 8.0

                VehiculoSim(
                    id = "SIM-${ruta.id}-$index",
                    ruta = ruta,
                    shape = shape,
                    acumulados = acumulados,
                    longitudTotalM = total,
                    velocidadMS = v,
                    distanciaM = nacimiento,
                    haciaAdelante = index % 2 == 0
                )
            }
            if (token != reconstrucciones) return@launch
            publicar()
        }
    }

    /// Tick de 250 ms con ida y vuelta (rebote en los extremos, como el iOS).
    fun start() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            var ultimoTick = System.currentTimeMillis()
            while (isActive) {
                delay(250)
                val ahora = System.currentTimeMillis()
                val dt = min((ahora - ultimoTick) / 1000.0, 1.0)   // cap 1 s
                ultimoTick = ahora
                vehiculos = vehiculos.map { v ->
                    var adelante = v.haciaAdelante
                    var distancia = v.distanciaM + v.velocidadMS * dt * (if (adelante) 1 else -1)
                    if (distancia >= v.longitudTotalM) { distancia = v.longitudTotalM; adelante = false }
                    if (distancia <= 0.0) { distancia = 0.0; adelante = true }
                    v.copy(distanciaM = distancia, haciaAdelante = adelante)
                }
                publicar()
            }
        }
    }

    fun stop() {
        tickJob?.cancel()
        tickJob = null
        flotaJob?.cancel()
        flotaJob = null
        _posiciones.value = emptyList()
    }

    private fun publicar() {
        val ahora = System.currentTimeMillis() / 1000.0
        _posiciones.value = vehiculos.map { v ->
            val p = proyectar(v.shape, v.acumulados, v.distanciaM, v.haciaAdelante)
            val rumbo = rumboSegmento(v)
            VehiclePosition(
                id = v.id,
                linea = v.ruta.linea,
                routeId = v.ruta.id,
                lat = p.latitude,
                lon = p.longitude,
                heading = rumbo,
                speed = v.velocidadMS * (if (v.haciaAdelante) 1 else -1),
                timestamp = ahora
            )
        }
    }

    private fun proyectar(shape: List<LatLng>, acumulados: DoubleArray, distancia: Double, haciaAdelante: Boolean): LatLng {
        var i = 0
        while (i < shape.size - 2 && distancia > acumulados[i + 1]) i++
        while (i > 0 && distancia < acumulados[i]) i--
        val a = acumulados[i]
        val b = acumulados[i + 1]
        val f = if (b > a) ((distancia - a) / (b - a)).coerceIn(0.0, 1.0) else 0.0
        return LatLng(
            shape[i].latitude + (shape[i + 1].latitude - shape[i].latitude) * f,
            shape[i].longitude + (shape[i + 1].longitude - shape[i].longitude) * f
        )
    }

    private fun rumboSegmento(v: VehiculoSim): Double {
        // Rumbo del tramo donde esta el vehiculo, en su sentido de marcha.
        var i = 0
        while (i < v.shape.size - 2 && v.distanciaM > v.acumulados[i + 1]) i++
        return if (v.haciaAdelante) {
            PolylineMatching.headingDegrees(v.shape[i], v.shape[i + 1])
        } else {
            PolylineMatching.headingDegrees(v.shape[i + 1], v.shape[i])
        }.let { if (it < 0) Random.nextDouble(360.0) else it }
    }

    companion object {
        const val CANTIDAD = 12   // 12 vehiculos, como el iOS
    }
}
