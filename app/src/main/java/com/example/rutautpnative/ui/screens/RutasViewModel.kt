package com.example.rutautpnative.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rutautpnative.data.gtfs.IndiceBusquedaRuta
import com.example.rutautpnative.data.gtfs.ConsultaRuta
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.data.gtfs.TransporteApp
import com.example.rutautpnative.navigation.DestinoPendiente
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

//----Modelo de vista de Rutas----
// Consume GTFSRepository y expone la lista de rutas del feed a la UI,
// junto con la búsqueda por texto y el filtro "líneas cerca de un lugar"
// (lugarCercanoPendiente del router, radio 300 m — mismo criterio que iOS).
class RutasViewModel : ViewModel() {
    var seleccionada by mutableStateOf<RutaGTFS?>(null)
    var galeria by mutableStateOf(false)
    var modelo by mutableStateOf<RutaGTFS?>(null)
    var navegando by mutableStateOf<RutaGTFS?>(null)

    companion object {
        // Radio del filtro "transporte cerca de este lugar" (iOS: 300 m).
        const val RADIO_CERCANIA_METROS = 300.0
    }

    private val _rutas = MutableStateFlow<List<RutaGTFS>>(emptyList())
    val rutas: StateFlow<List<RutaGTFS>> = _rutas.asStateFlow()

    private val _textoBusqueda = MutableStateFlow("")
    val textoBusqueda: StateFlow<String> = _textoBusqueda.asStateFlow()

    // Lugar del filtro de cercanía (null = sin filtro).
    private val _lugarCercano = MutableStateFlow<DestinoPendiente?>(null)
    val lugarCercano: StateFlow<DestinoPendiente?> = _lugarCercano.asStateFlow()

    // Resultado del filtro combinado: rutas que pasan el texto + cercanía,
    // con la distancia al lugar (m) para mostrar "a X m del lugar" en la card.
    data class ResultadoFiltro(
        val rutas: List<RutaGTFS>,
        val distancias: Map<String, Int>
    )

    private data class CatalogoBusqueda(val rutas: List<RutaGTFS>) {
        val indices = rutas.associate { it.id to IndiceBusquedaRuta(it) }
        val letras = indices.values.map { it.letra }.filter { it.isNotBlank() }.toSet()
    }

    val rutasFiltradas: StateFlow<ResultadoFiltro> =
        combine(_rutas.map { CatalogoBusqueda(it) }, _textoBusqueda, _lugarCercano) { catalogo, texto, lugar ->
            val rutas = catalogo.rutas
            val indices = catalogo.indices
            val consulta = ConsultaRuta(texto, catalogo.letras)
            var base = rutas.filter { indices[it.id]?.coincide(consulta) == true }

            var distancias = emptyMap<String, Int>()
            if (lugar != null) {
                // "¿Qué líneas tienen un paradero a menos de 300 m?": distancia
                // mínima paradero->lugar por ruta, ordenadas por cercanía.
                val punto = LatLng(lugar.lat, lugar.lon)
                val conDistancia = base.mapNotNull { ruta ->
                    val min = ruta.paraderos.minOfOrNull {
                        GTFSRepository.distanciaMetros(it.coordinate, punto)
                    } ?: return@mapNotNull null
                    if (min <= RADIO_CERCANIA_METROS) ruta to min else null
                }.sortedBy { it.second }

                distancias = conDistancia.associate { it.first.id to it.second.toInt() }
                base = conDistancia.map { it.first }
            }

            ResultadoFiltro(rutas = base, distancias = distancias)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, ResultadoFiltro(emptyList(), emptyMap()))

    fun actualizarBusqueda(texto: String) {
        _textoBusqueda.value = texto
    }

    fun limpiarBusqueda() {
        _textoBusqueda.value = ""
    }

    /// Activa/quita el filtro "líneas cerca de <lugar>".
    fun filtrarCercaDe(lugar: DestinoPendiente?) {
        _lugarCercano.value = lugar
    }

    private val _cargando = MutableStateFlow(true)
    val cargando = _cargando.asStateFlow()
    val errorCarga = GTFSRepository.errorCarga
    private var carga: Job? = null
    fun cargar(reintentar: Boolean = false) {
        carga?.cancel()
        carga = viewModelScope.launch {
            _cargando.value = true
            try {
                val nuevas = GTFSRepository.rutas(reintentar)
                coroutineContext.ensureActive()
                _rutas.value = nuevas
            } finally { if (isActive) _cargando.value = false }
        }
    }
    init {
        viewModelScope.launch {
            androidx.compose.runtime.snapshotFlow { TransporteApp.busesUTPActivos }.collect { cargar() }
        }
    }
}
