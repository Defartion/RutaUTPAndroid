package com.example.rutautpnative.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rutautpnative.data.LineasGuardadasStore
import com.example.rutautpnative.data.LugaresStore
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.model.LineaGuardadaRef
import com.example.rutautpnative.model.LugarGuardado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

//----Modelo de vista de Guardado (Lugares + Líneas)----
class GuardadoViewModel : ViewModel() {

    // ---- Lugares: observación reactiva del store ----
    // Cualquier escritura a LugaresStore (p. ej. desde ParaderosIluminados)
    // se refleja de inmediato sin reiniciar.
    val lugares: StateFlow<List<LugarGuardado>> = LugaresStore.observar()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Líneas guardadas, resueltas al vuelo contra el catálogo GTFS ---
    private val _lineas = MutableStateFlow<List<RutaGTFS>>(emptyList())
    val lineas: StateFlow<List<RutaGTFS>> = _lineas.asStateFlow()

    // Catálogo completo, para el selector y la resolución en vivo.
    private val _catalogo = MutableStateFlow<List<RutaGTFS>>(emptyList())
    val catalogo: StateFlow<List<RutaGTFS>> = _catalogo.asStateFlow()

    private var refsLineas: List<LineaGuardadaRef> = emptyList()

    init {
        viewModelScope.launch {
            _catalogo.value = GTFSRepository.rutas()
            LineasGuardadasStore.observar().collect { refs ->
                refsLineas = refs
                _lineas.value = resolver()
            }
        }
    }

    // --- Lugares ---
    fun agregar(lugar: LugarGuardado) {
        viewModelScope.launch { LugaresStore.modificar { it + lugar } }
    }

    fun eliminar(lugar: LugarGuardado) {
        if (lugar.esFijo) return // UTP es fijo; nunca se elimina.
        viewModelScope.launch { LugaresStore.modificar { lista -> lista.filter { it.id != lugar.id || it.esFijo } } }
    }

    fun actualizar(lugar: LugarGuardado) {
        viewModelScope.launch { LugaresStore.modificar { lista -> lista.map { if (it.id == lugar.id) lugar.copy(esFijo = it.esFijo) else it } } }
    }

    // --- Líneas ---
    fun agregarLinea(ruta: RutaGTFS) {
        viewModelScope.launch { LineasGuardadasStore.modificar { it + LineaGuardadaRef(ruta.id) } }
    }

    fun quitarLinea(routeId: String) {
        viewModelScope.launch { LineasGuardadasStore.modificar { refs -> refs.filter { it.routeId != routeId } } }
    }

    // Resuelve cada referencia contra el catálogo; las que ya no existen se descartan.
    private fun resolver(): List<RutaGTFS> =
        refsLineas.mapNotNull { ref -> _catalogo.value.firstOrNull { it.id == ref.routeId } }
}
