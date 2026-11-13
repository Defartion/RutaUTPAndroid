package com.example.rutautpnative

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelStore
import com.example.rutautpnative.data.gtfs.*
import com.example.rutautpnative.data.places.PlacesService
import com.example.rutautpnative.data.routing.TransitPlanner
import com.example.rutautpnative.navigation.*
import com.example.rutautpnative.ui.screens.*
import com.example.rutautpnative.ui.screens.mapa.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Phase3AsyncTest {
    private val camara = object : ControlCamaraMapa {
        override suspend fun centrar(punto: LatLng, zoom: Float) {}
        override suspend fun encuadrar(puntos: List<LatLng>) {}
    }
    private fun servicios(cerca: suspend (LatLng, Double) -> List<RutaGTFS> = { _, _ -> emptyList() },
                          sugerir: suspend (String) -> PlacesService.ResultadoSugerencias = { PlacesService.ResultadoSugerencias.SinResultados }) = ServiciosMapa(
        rutas = { emptyList() }, cercanas = cerca, sugerir = sugerir, proveedor = { null },
        institucional = { false }, utpPendiente = { false }, reloj = { 0L },
        caminar = TransitPlanner.CaminatasReales { _, _ -> null })
    private fun ruta(id: String): RutaGTFS {
        val puntos = listOf(LatLng(-8.1, -79.0), LatLng(-8.1, -78.98))
        return RutaGTFS(id, "10", "", "Centro", "Empresa", "00AA00", Color.Green, puntos,
            listOf(ParaderoGTFS("p0", "Inicio", -8.1, -79.0), ParaderoGTFS("p1", "Fin", -8.1, -78.98)),
            5, 10, 2.0, 2.2, 0.0)
    }
    @Test fun sugerenciasTardiasNoReabrenElDestinoAnterior() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try {
            val respuesta = CompletableDeferred<PlacesService.ResultadoSugerencias>()
            val vm = MapaViewModel(servicios(sugerir = { withContext(NonCancellable) { respuesta.await() } }), camara)
            store.put("mapa", vm); runCurrent()
            vm.actualizarTextoBusqueda("Centro", true); advanceTimeBy(401); runCurrent()
            vm.seleccionarLugarExterno("B", -8.12, -79.04); runCurrent()
            respuesta.complete(PlacesService.ResultadoSugerencias.Exito(listOf(PlacesService.Sugerencia("A", "A", -8.1, -79.0))))
            runCurrent()
            assertEquals("B", vm.destinoSeleccionado?.label); assertTrue(vm.sugerencias.isEmpty()); assertFalse(vm.buscandoSugerencias)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun lineasTardiasNoSustituyenLaFlotaDeB() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try {
            val respuesta = CompletableDeferred<List<RutaGTFS>>()
            val vm = MapaViewModel(servicios(cerca = { punto, _ ->
                if (punto.latitude == -8.11) withContext(NonCancellable) { respuesta.await() }
                else if (punto.latitude == -8.12) listOf(ruta("B")) else emptyList()
            }), camara)
            store.put("mapa", vm); runCurrent()
            vm.seleccionarLugarExterno("A", -8.11, -79.02); runCurrent()
            vm.seleccionarLugarExterno("B", -8.12, -79.04); runCurrent()
            respuesta.complete(listOf(ruta("A"))); runCurrent()
            assertEquals(listOf("B"), vm.rutasCercanas.map { it.id })
            assertEquals(listOf("B"), vm.busesAnimados.map { it.rutaId })
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun resumenFinalDeNavegacionSigueProtegidoHastaCerrarlo() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try {
            val holder = NavegacionSessionViewModel(); store.put("linea", holder)
            val vm = holder.obtener(ruta("A")); vm.iniciarDemo(); advanceTimeBy(72_100); runCurrent()
            assertEquals(EstadoNav.FINALIZADO, vm.estado)
            assertTrue(ViajesActivos.estado.contiene(TipoViaje.NAVEGACION))
            holder.finalizar(); assertFalse(ViajesActivos.estado.contiene(TipoViaje.NAVEGACION))
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun resumenFinalDeTrackingSigueProtegidoHastaFinalizarElViaje() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try {
            val holder = TrackingSessionViewModel(); store.put("tracking", holder)
            val ajustes = object : AjustesTracking {
                override val radio = 1600.0
                override val velocidad = 1
                override fun guardarRadio(value: Double) {}
                override fun guardarVelocidad(value: Int) {}
            }
            val vm = holder.obtener { TrackingDemoVM(it, ajustes, { listOf(ruta("A")) }, TransitPlanner.CaminatasReales { _, _ -> null }) }
            vm.posicion = ruta("A").shape.first(); vm.iniciarViaje("Destino", ruta("A").shape.last())!!.join()
            vm.progreso = 0.999999; vm.iniciarDemo(); advanceTimeBy(81); runCurrent()
            assertEquals(EstadoDemo.FINALIZADO, vm.estado); assertTrue(vm.conViaje)
            assertTrue(ViajesActivos.estado.contiene(TipoViaje.TRACKING))
            vm.detenerViaje(); assertFalse(ViajesActivos.estado.contiene(TipoViaje.TRACKING))
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun navegacionDeLineaRetenidaPausaYReanudaSinReiniciarProgreso() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val store = ViewModelStore()
        try {
            val holder = NavegacionSessionViewModel(); store.put("linea", holder)
            val vm = holder.obtener(ruta("A")); vm.iniciarDemo(); advanceTimeBy(801); runCurrent()
            val progreso = vm.progreso; assertTrue(progreso > 0.0)
            vm.suspenderPantalla(); advanceTimeBy(1000)
            assertEquals(progreso, vm.progreso, 0.0); assertTrue(ViajesActivos.estado.contiene(TipoViaje.NAVEGACION))
            assertSame(vm, holder.obtener(ruta("A")))
            vm.reanudarPantalla(); advanceTimeBy(81); runCurrent()
            assertTrue(vm.progreso > progreso)
            vm.detenerDemo(); vm.iniciarDemo()
            assertEquals(0.0, vm.progreso, 0.0)
            holder.finalizar(); assertFalse(ViajesActivos.estado.contiene(TipoViaje.NAVEGACION))
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
