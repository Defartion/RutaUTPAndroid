package com.example.rutautpnative.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rutautpnative.data.LugaresStore
import com.example.rutautpnative.data.SeguridadTilesStore
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.model.LugarGuardado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

//----Modelo de vista de Seguridad----
// Expone el catálogo GTFS para calcular el conteo de paraderos iluminados,
// y los tiles de lugares que se muestran en la sección "Lugares Guardados".
class SeguridadViewModel : ViewModel() {

    private val _rutas = MutableStateFlow<List<RutaGTFS>>(emptyList())
    val rutas: StateFlow<List<RutaGTFS>> = _rutas.asStateFlow()

    //----Tiles de lugares----
    // Todos los lugares de LugaresStore (para el sheet con checkboxes).
    private val _lugares = MutableStateFlow<List<LugarGuardado>>(emptyList())
    val lugares: StateFlow<List<LugarGuardado>> = _lugares.asStateFlow()

    // Preferencias de "cuáles mostrar aquí": elegidos (null = nunca personalizó) y orden.
    private val _elegidos = MutableStateFlow<List<String>?>(null)
    val elegidos: StateFlow<List<String>?> = _elegidos.asStateFlow()

    private val _orden = MutableStateFlow<List<String>>(emptyList())
    val orden: StateFlow<List<String>> = _orden.asStateFlow()

    // Lista final de tiles: UTP primero, luego los elegidos (o los 2 primeros por defecto).
    private val _tiles = MutableStateFlow<List<LugarGuardado>>(emptyList())
    val tiles: StateFlow<List<LugarGuardado>> = _tiles.asStateFlow()

    init {
        viewModelScope.launch {
            _rutas.value = GTFSRepository.rutas()
        }
        viewModelScope.launch {
            _elegidos.value = SeguridadTilesStore.leerElegidos()
            _orden.value = SeguridadTilesStore.leerOrden()
            LugaresStore.observar().collect { nuevos ->
                _lugares.value = nuevos
                _tiles.value = SeguridadTilesStore.reconstruir(nuevos, _elegidos.value, _orden.value)
            }
        }
    }

    //----Persiste la selección hecha en el sheet (qué lugares mostrar)----
    fun guardarSeleccionElegidos(ids: List<String>) {
        viewModelScope.launch {
            SeguridadTilesStore.guardarElegidos(ids)
            _elegidos.value = ids
            _tiles.value = SeguridadTilesStore.reconstruir(_lugares.value, ids, _orden.value)
        }
    }

    //----Quita un lugar de los tiles mostrados (no lo borra de LugaresStore)----
    fun quitarTileElegido(id: String) {
        val actuales = _tiles.value.filter { !it.esFijo }.map { it.id }
        guardarSeleccionElegidos(actuales - id)
    }

    //----Persiste el nuevo orden al soltar un tile arrastrado----
    // Persiste también como "elegidos" para que el orden reordene algo estable
    // (los ids mostrados pasan a ser la selección, en ese orden).
    fun reordenarTilesMostrados(idsOrdenados: List<String>) {
        viewModelScope.launch {
            SeguridadTilesStore.guardarOrden(idsOrdenados)
            SeguridadTilesStore.guardarElegidos(idsOrdenados)
            _orden.value = idsOrdenados
            _elegidos.value = idsOrdenados
            _tiles.value = SeguridadTilesStore.reconstruir(_lugares.value, idsOrdenados, idsOrdenados)
        }
    }
}
