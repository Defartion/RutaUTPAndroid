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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

//----Modelo de vista de Guardado (Lugares + Líneas)----
class GuardadoViewModel : ViewModel() {

    // --- Lugares ---
    private val _lugares = MutableStateFlow<List<LugarGuardado>>(emptyList())
    val lugares: StateFlow<List<LugarGuardado>> = _lugares.asStateFlow()

    // --- Líneas guardadas, resueltas al vuelo contra el catálogo GTFS ---
    private val _lineas = MutableStateFlow<List<RutaGTFS>>(emptyList())
    val lineas: StateFlow<List<RutaGTFS>> = _lineas.asStateFlow()

    // Catálogo completo, para el selector y la resolución en vivo.
    private val _catalogo = MutableStateFlow<List<RutaGTFS>>(emptyList())
    val catalogo: StateFlow<List<RutaGTFS>> = _catalogo.asStateFlow()

    private var refsLineas: List<LineaGuardadaRef> = emptyList()

    init {
        viewModelScope.launch {
            _lugares.value = LugaresStore.cargar()
            refsLineas = LineasGuardadasStore.cargar()
            _catalogo.value = GTFSRepository.rutas()
            _lineas.value = resolver()
        }
    }

    // --- Lugares ---
    fun agregar(lugar: LugarGuardado) {
        val nueva = _lugares.value + listOf(lugar)
        _lugares.value = nueva
        viewModelScope.launch { LugaresStore.guardar(nueva) }
    }

    fun eliminar(lugar: LugarGuardado) {
        if (lugar.esFijo) return // UTP es fijo; nunca se elimina.
        val nueva = _lugares.value.filter { it.id != lugar.id }
        _lugares.value = nueva
        viewModelScope.launch { LugaresStore.guardar(nueva) }
    }

    // --- Líneas ---
    fun agregarLinea(ruta: RutaGTFS) {
        refsLineas = refsLineas + LineaGuardadaRef(ruta.id)
        _lineas.value = resolver()
        viewModelScope.launch { LineasGuardadasStore.guardar(refsLineas) }
    }

    fun quitarLinea(routeId: String) {
        refsLineas = refsLineas.filter { it.routeId != routeId }
        _lineas.value = resolver()
        viewModelScope.launch { LineasGuardadasStore.guardar(refsLineas) }
    }

    // Resuelve cada referencia contra el catálogo; las que ya no existen se descartan.
    private fun resolver(): List<RutaGTFS> =
        refsLineas.mapNotNull { ref -> _catalogo.value.firstOrNull { it.id == ref.routeId } }
}