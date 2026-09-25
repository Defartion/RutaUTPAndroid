package com.example.rutautpnative.ui.screens.mapa

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Modelos
data class BusSimulado(
    val id: Int,
    var lat: Double,
    var lon: Double,
    val linea: String,
    var angulo: Double,
    val velocidad: Double
)

data class DestinoChip(
    val id: Int,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val lat: Double,
    val lon: Double
)

// Modelo de vista
// NOTA (convención del proyecto): este ViewModel usa mutableStateOf de Compose
// (fue de los primeros escritos). Los ViewModels nuevos usan StateFlow puro
// (ver GuardadoViewModel/SeguridadViewModel). Migrar solo si hay refactor mayor.
class MapaViewModel : ViewModel() {

    var cameraPositionState = CameraPositionState(
        position = CameraPosition.fromLatLngZoom(GTFSRepository.coordenadaUTP, 14f)
    )

    var busSimulados by mutableStateOf<List<BusSimulado>>(emptyList())
        private set

    var textoBusqueda by mutableStateOf("")

    var destinoSeleccionado by mutableStateOf<DestinoChip?>(null)
        private set

    private var animacionJob: Job? = null

    // Destinos conocidos
    // Los iconos se asignan en MapaScreen para evitar dependencia de Compose aquí
    var destinos by mutableStateOf<List<DestinoChip>>(emptyList())

    // Rutas reales del feed cuyo recorrido pasa cerca del punto de anclaje actual
    // (destino seleccionado o campus UTP por defecto).
    var rutasCercanas by mutableStateOf<List<RutaGTFS>>(emptyList())
        private set

    // Al abrir el mapa (sin destino), muestra las líneas cercanas al campus UTP.
    init {
        viewModelScope.launch {
            actualizarRutasCercanas(GTFSRepository.coordenadaUTP)
        }
    }

    // Seleccion
    fun seleccionar(destino: DestinoChip) {
        if (destinoSeleccionado?.id == destino.id) return
        destinoSeleccionado = destino
        textoBusqueda = destino.label
        viewModelScope.launch {
            cameraPositionState.animate(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                    LatLng(destino.lat, destino.lon), 15f
                )
            )
        }
        spawnBuses(destino)
        viewModelScope.launch {
            actualizarRutasCercanas(LatLng(destino.lat, destino.lon))
        }
    }

    // Selecciona un lugar que llega de fuera del mapa (router.destinoPendiente,
    // p.ej. zonas de Seguridad). Se trata como si el usuario lo hubiera buscado
    // manualmente: mismo flujo de seleccionar(DestinoChip).
    fun seleccionarLugarExterno(titulo: String, lat: Double, lon: Double) {
        seleccionar(
            DestinoChip(
                id = "ext|$titulo|$lat|$lon".hashCode(),
                label = titulo,
                icon = Icons.Filled.Place,
                lat = lat,
                lon = lon
            )
        )
    }

    fun buscarTexto(texto: String) {
        val t = texto.trim()
        if (t.isEmpty()) return
        destinos.firstOrNull { it.label.lowercase().contains(t.lowercase()) }
            ?.let { seleccionar(it) }
    }

    fun limpiar() {
        textoBusqueda = ""
        destinoSeleccionado = null
        busSimulados = emptyList()
        detenerAnimacion()
        viewModelScope.launch {
            actualizarRutasCercanas(GTFSRepository.coordenadaUTP)
        }
        viewModelScope.launch {
            cameraPositionState.animate(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(GTFSRepository.coordenadaUTP, 14f)
            )
        }
    }

    // Carga las rutas cercanas a un punto: prueba 400 m y, si no encuentra,
    // amplía a 800 m para evitar "sin rutas" cuando el punto quedó algo lejos
    // del recorrido real.
    private suspend fun actualizarRutasCercanas(punto: LatLng) {
        var rutas = GTFSRepository.rutasCercaDe(punto, 400.0)
        if (rutas.isEmpty()) {
            rutas = GTFSRepository.rutasCercaDe(punto, 800.0)
        }
        rutasCercanas = rutas
    }

    // TODO(fase-mejoras-futuras): en iOS el mapa principal no dibuja el shape de una
    // línea específica (solo la ruta de navegación al destino, vía Directions). Si en el
    // futuro se quiere mostrar el recorrido de una línea elegida directamente aquí, retomar
    // desde el historial de este archivo (se implementó y luego se revirtió a propósito).

    // Simulacion de buses
    private fun spawnBuses(destino: DestinoChip) {
        detenerAnimacion()
        val lineas = listOf("B", "10", "4", "C", "7", "A")
        busSimulados = (0 until 6).map { i ->
            val angulo = i * 60.0
            val radio = 0.008 + Random.nextDouble(0.0, 0.004)
            val rad = Math.toRadians(angulo)
            BusSimulado(
                id = i,
                lat = destino.lat + sin(rad) * radio,
                lon = destino.lon + cos(rad) * radio,
                linea = lineas[i % lineas.size],
                angulo = angulo,
                velocidad = 0.0001 + Random.nextDouble(0.0, 0.00005)
            )
        }
        iniciarAnimacion()
    }

    private fun iniciarAnimacion() {
        animacionJob = viewModelScope.launch {
            while (isActive) {
                delay(50)
                busSimulados = busSimulados.map { bus ->
                    val rad = Math.toRadians(bus.angulo)
                    val newAngulo = if (Random.nextDouble() < 0.002)
                        Random.nextDouble(0.0, 360.0) else bus.angulo
                    bus.copy(
                        lat = bus.lat + sin(rad) * bus.velocidad,
                        lon = bus.lon + cos(rad) * bus.velocidad,
                        angulo = newAngulo
                    )
                }
            }
        }
    }

    fun detenerAnimacion() {
        animacionJob?.cancel()
        animacionJob = null
    }

    override fun onCleared() {
        super.onCleared()
        detenerAnimacion()
    }
}