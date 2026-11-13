package com.example.rutautpnative.ui.screens.mapa

import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.data.gtfs.TransporteApp
import com.example.rutautpnative.data.places.PlacesService
import com.example.rutautpnative.data.routing.TransitPlanner
import com.example.rutautpnative.data.tracking.MQTTTrackingProvider
import com.example.rutautpnative.data.tracking.TrackingProviderFactory
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState

class ServiciosMapa(
    val buscar: suspend (String) -> PlacesService.Resultado = { PlacesService.buscarTexto(it) },
    val sugerir: suspend (String) -> PlacesService.ResultadoSugerencias = { PlacesService.buscarSugerencias(it) },
    val rutas: suspend () -> List<RutaGTFS> = { GTFSRepository.rutas() },
    val cercanas: suspend (LatLng, Double) -> List<RutaGTFS> = { punto, radio -> GTFSRepository.rutasCercaDe(punto, radio) },
    val proveedor: () -> MQTTTrackingProvider? = { TrackingProviderFactory.crearReal() },
    val institucional: () -> Boolean = { TransporteApp.busesUTPActivos },
    val utpPendiente: () -> Boolean = { TransporteApp.rutasUTPPendientes },
    val caminar: TransitPlanner.CaminatasReales = TransitPlanner.CaminatasReales { a, b -> TransitPlanner.caminataReal(a, b) },
    val reloj: () -> Long = { android.os.SystemClock.elapsedRealtime() }
)

interface ControlCamaraMapa {
    suspend fun centrar(punto: LatLng, zoom: Float)
    suspend fun encuadrar(puntos: List<LatLng>)
}

internal class CamaraGoogle(private val estado: CameraPositionState) : ControlCamaraMapa {
    override suspend fun centrar(punto: LatLng, zoom: Float) {
        if (!com.example.rutautpnative.data.google.ServiciosGoogle.mapaConfigurado) return
        estado.animate(CameraUpdateFactory.newLatLngZoom(punto, zoom))
    }
    override suspend fun encuadrar(puntos: List<LatLng>) {
        if (puntos.isEmpty() || !com.example.rutautpnative.data.google.ServiciosGoogle.mapaConfigurado) return
        val bounds = LatLngBounds.builder()
        puntos.forEach(bounds::include)
        estado.animate(CameraUpdateFactory.newLatLngBounds(bounds.build(), 120))
    }
}
