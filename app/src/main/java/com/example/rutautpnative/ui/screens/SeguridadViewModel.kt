package com.example.rutautpnative.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rutautpnative.data.ComunidadDatos
import com.example.rutautpnative.data.LugaresStore
import com.example.rutautpnative.data.SeguridadTilesStore
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.data.places.PlacesService
import com.example.rutautpnative.model.LugarGuardado
import com.example.rutautpnative.model.ReporteComunidad
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

//----Eventos de búsqueda de zonas de referencia----
// Se emiten una sola vez por búsqueda; los consume la pantalla.
sealed class ZonaEvento {
    data class Exito(val titulo: String, val lat: Double, val lon: Double) : ZonaEvento()
    object SinResultados : ZonaEvento()
    object Fallo : ZonaEvento()
}

//----Voto del usuario en una publicación de Comunidad----
enum class VotoComunidad { UTIL, NO_UTIL }

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

    //----Búsqueda de zonas de referencia (declaraciones)----
    private val _zonaEventos = MutableSharedFlow<ZonaEvento>()
    val zonaEventos: SharedFlow<ZonaEvento> = _zonaEventos.asSharedFlow()

    private val _buscandoZona = MutableStateFlow(false)
    val buscandoZona: StateFlow<Boolean> = _buscandoZona.asStateFlow()

    private var busquedaZonaJob: Job? = null

    //----Comunidad (declaraciones)----
    // Ventana visible: grupos de 3, calculada del reloj real del dispositivo:
    // (epochSeconds / 240) % totalVentanas. No es un timer desde que se abrió
    // la pantalla: todas las sesiones ven la misma ventana a la misma hora.
    private val _ventanaComunidad = MutableStateFlow(0)
    val ventanaComunidad: StateFlow<Int> = _ventanaComunidad.asStateFlow()

    // Votos del usuario, SOLO en memoria (como en iOS: demo de sesión, no se
    // persiste). Se inician con los que vienen pre-marcados en el dato.
    private val _votosComunidad = MutableStateFlow(
        ComunidadDatos.publicaciones.filter { it.utilMarcado }
            .associate { it.id to VotoComunidad.UTIL }
    )
    val votosComunidad: StateFlow<Map<UUID, VotoComunidad>> = _votosComunidad.asStateFlow()

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
        iniciarRelojComunidad()
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

    //----Búsqueda de zonas de referencia (Places Text Search)----
    // buscarZona del port iOS: "{zona}, Trujillo, Perú" contra Places Text Search.
    // Si el usuario toca otra zona antes de que termine, la búsqueda anterior se
    // cancela y su respuesta se descarta (el job cancelado nunca emite).
    fun buscarZona(nombreZona: String) {
        busquedaZonaJob?.cancel()
        _buscandoZona.value = true
        busquedaZonaJob = viewModelScope.launch {
            val resultado = PlacesService.buscarTexto("$nombreZona, Trujillo, Perú")
            _buscandoZona.value = false
            val evento = when (resultado) {
                is PlacesService.Resultado.Exito -> ZonaEvento.Exito(resultado.nombre, resultado.lat, resultado.lon)
                PlacesService.Resultado.SinResultados -> ZonaEvento.SinResultados
                PlacesService.Resultado.Error -> ZonaEvento.Fallo
            }
            _zonaEventos.emit(evento)
        }
    }

    //----Comunidad: lógica de rotación y votos de sesión----

    //----Ventana actual según el tiempo real----
    private fun ventanaActual(): Int {
        val total = (ComunidadDatos.publicaciones.size + 2) / 3
        if (total == 0) return 0
        val epochSeg = System.currentTimeMillis() / 1000
        return ((epochSeg / 240) % total).toInt()
    }

    //----Publicaciones de la ventana actual----
    fun publicacionesVisibles(): List<ReporteComunidad> =
        ComunidadDatos.publicaciones.drop(_ventanaComunidad.value * 3).take(3)

    //----Votar: tocar el mismo voto lo retira; tocar el contrario lo cambia----
    fun votar(id: UUID, voto: VotoComunidad) {
        val mapa = _votosComunidad.value.toMutableMap()
        if (mapa[id] == voto) mapa.remove(id) else mapa[id] = voto
        _votosComunidad.value = mapa
    }

    //----Contadores mostrados: base ± 1 según el voto actual del usuario----
    // (los pre-marcados ya traían la marca del usuario contada en su base).
    fun utilesMostrados(r: ReporteComunidad): Int =
        r.utiles + (if (_votosComunidad.value[r.id] == VotoComunidad.UTIL) 1 else 0) -
            (if (r.utilMarcado) 1 else 0)

    fun noUtilesMostrados(r: ReporteComunidad): Int =
        r.dislikes + (if (_votosComunidad.value[r.id] == VotoComunidad.NO_UTIL) 1 else 0)

    //----Reloj: despierta justo en el siguiente borde de 240 s del reloj real----
    private fun iniciarRelojComunidad() {
        viewModelScope.launch {
            while (isActive) {
                _ventanaComunidad.value = ventanaActual()
                val ahoraMs = System.currentTimeMillis()
                val siguienteBorde = ((ahoraMs / 240_000) + 1) * 240_000
                delay(siguienteBorde - ahoraMs)
            }
        }
    }
}
