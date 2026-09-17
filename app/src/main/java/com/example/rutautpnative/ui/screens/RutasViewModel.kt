package com.example.rutautpnative.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.RutaGTFS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

//----Modelo de vista de Rutas----
// Consume GTFSRepository y expone la lista de rutas del feed a la UI,
// junto con la búsqueda por texto.
class RutasViewModel : ViewModel() {

    private val _rutas = MutableStateFlow<List<RutaGTFS>>(emptyList())
    val rutas: StateFlow<List<RutaGTFS>> = _rutas.asStateFlow()

    private val _textoBusqueda = MutableStateFlow("")
    val textoBusqueda: StateFlow<String> = _textoBusqueda.asStateFlow()

    // Rutas que coinciden con la búsqueda actual (línea, empresa, recorrido o
    // variante), case-insensitive. Con búsqueda vacía, devuelve la lista completa.
    val rutasFiltradas: StateFlow<List<RutaGTFS>> =
        combine(_rutas, _textoBusqueda) { rutas, texto ->
            val q = texto.trim()
            if (q.isEmpty()) {
                rutas
            } else {
                rutas.filter { r ->
                    r.linea.contains(q, ignoreCase = true) ||
                        r.empresa.contains(q, ignoreCase = true) ||
                        r.recorrido.contains(q, ignoreCase = true) ||
                        r.variante.contains(q, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun actualizarBusqueda(texto: String) {
        _textoBusqueda.value = texto
    }

    fun limpiarBusqueda() {
        _textoBusqueda.value = ""
    }

    init {
        viewModelScope.launch {
            _rutas.value = GTFSRepository.rutas()
        }
    }
}