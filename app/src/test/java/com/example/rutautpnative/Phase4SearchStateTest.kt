package com.example.rutautpnative

import androidx.lifecycle.ViewModelStore
import com.example.rutautpnative.data.places.PlacesService
import com.example.rutautpnative.ui.screens.mapa.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Phase4SearchStateTest {
    private val camara=object:ControlCamaraMapa {
        override suspend fun centrar(punto:LatLng,zoom:Float) {}
        override suspend fun encuadrar(puntos:List<LatLng>) {}
    }
    private fun servicios(buscar:suspend(String)->PlacesService.Resultado)=ServiciosMapa(buscar=buscar,
        sugerir={PlacesService.ResultadoSugerencias.SinConfiguracion},rutas={emptyList()},cercanas={_,_->emptyList()},
        proveedor={null},institucional={false},utpPendiente={false},reloj={0L})
    @Test fun unFalloDeAutorizacionEsVisibleYNoSeleccionaUnDestino() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val store=ViewModelStore()
        try {
            val vm=MapaViewModel(servicios { PlacesService.Resultado.NoAutorizado },camara);store.put("mapa",vm);runCurrent()
            vm.buscarTexto("Centro");runCurrent()
            assertNull(vm.destinoSeleccionado);assertNotNull(vm.mensajeBusqueda)
        } finally {store.clear();Dispatchers.resetMain()}
    }
    @Test fun unFalloAntiguoNoSustituyeElMensajeDeUnaSeleccionPosterior() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val store=ViewModelStore()
        try {
            val respuesta=CompletableDeferred<PlacesService.Resultado>()
            val vm=MapaViewModel(servicios { withContext(NonCancellable) { respuesta.await() } },camara);store.put("mapa",vm);runCurrent()
            vm.buscarTexto("Centro");runCurrent();vm.seleccionarLugarExterno("B",-8.12,-79.04);runCurrent()
            respuesta.complete(PlacesService.Resultado.NoAutorizado);runCurrent()
            assertEquals("B",vm.destinoSeleccionado?.label);assertNull(vm.mensajeBusqueda)
        } finally {store.clear();Dispatchers.resetMain()}
    }
}
