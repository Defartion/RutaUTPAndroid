package com.example.rutautpnative

import androidx.lifecycle.ViewModelStore
import com.example.rutautpnative.ui.screens.mapa.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Phase6PanelMapaTest {
    private val camara=object:ControlCamaraMapa {
        override suspend fun centrar(punto:LatLng,zoom:Float) {}
        override suspend fun encuadrar(puntos:List<LatLng>) {}
    }
    @Test fun estadosDelPanelNoConfundenCargaErrorOPendienteConAusenciaDeVehiculos() {
        assertEquals(EstadoPanelMapa.UTP_PENDIENTE,estadoPanelMapa(true,true,true,true,true))
        assertEquals(EstadoPanelMapa.CARGANDO,estadoPanelMapa(true,false,false,false,true))
        assertEquals(EstadoPanelMapa.ERROR,estadoPanelMapa(false,true,false,true,true))
        assertEquals(EstadoPanelMapa.CON_BUSES,estadoPanelMapa(false,false,false,true,true))
        assertEquals(EstadoPanelMapa.ESPERANDO,estadoPanelMapa(false,false,false,false,true))
        assertEquals(EstadoPanelMapa.VACIO,estadoPanelMapa(false,false,false,false,false))
    }
    @Test fun unFalloDeCatalogoMuestraErrorYElReintentoPuedeRecuperarlo() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val store=ViewModelStore()
        try {
            var fallo=true;var reintentos=0
            val vm=MapaViewModel(ServiciosMapa(rutas={emptyList()},cercanas={_,_->emptyList()},proveedor={null},
                institucional={false},utpPendiente={false},reloj={0L},falloCatalogo={fallo},
                reintentarCatalogo={reintentos++;fallo=false}),camara)
            store.put("mapa",vm);runCurrent();assertTrue(vm.errorCatalogo);assertFalse(vm.cargandoCatalogo)
            vm.reintentarCatalogo();assertTrue(vm.cargandoCatalogo);runCurrent()
            assertEquals(1,reintentos);assertFalse(vm.errorCatalogo);assertFalse(vm.cargandoCatalogo)
        } finally { store.clear();Dispatchers.resetMain() }
    }
    @Test fun unErrorAntiguoNoSustituyeElPanelTrasReintentar() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val store=ViewModelStore()
        try {
            val viejo=CompletableDeferred<Unit>();var primera=true
            val vm=MapaViewModel(ServiciosMapa(rutas={emptyList()},cercanas={_,_->
                if(primera) { primera=false;withContext(NonCancellable) { viejo.await();error("antiguo") } }
                emptyList() },proveedor={null},institucional={false},utpPendiente={false},reloj={0L},
                falloCatalogo={false},reintentarCatalogo={}),camara)
            store.put("mapa",vm);runCurrent();vm.reintentarCatalogo();runCurrent()
            viejo.complete(Unit);runCurrent();assertFalse(vm.errorCatalogo);assertFalse(vm.cargandoCatalogo)
        } finally { store.clear();Dispatchers.resetMain() }
    }
    @Test fun utpPendienteNoLeeNiPresentaElErrorDelCatalogoUrbano() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val store=ViewModelStore()
        try {
            val vm=MapaViewModel(ServiciosMapa(rutas={error("no debe cargar")},cercanas={_,_->error("no debe cargar")},
                proveedor={null},institucional={true},utpPendiente={true},reloj={0L},falloCatalogo={true}),camara)
            store.put("mapa",vm);runCurrent()
            assertFalse(vm.errorCatalogo);assertFalse(vm.cargandoCatalogo);assertTrue(vm.busesAnimados.isEmpty())
        } finally { store.clear();Dispatchers.resetMain() }
    }
}
