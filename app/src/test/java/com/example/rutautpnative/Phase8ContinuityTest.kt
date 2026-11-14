package com.example.rutautpnative

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelStore
import com.example.rutautpnative.data.*
import com.example.rutautpnative.data.gtfs.*
import com.example.rutautpnative.data.monedero.MovimientoMonedero
import com.example.rutautpnative.data.notificaciones.ConfiguracionNotificaciones
import com.example.rutautpnative.ui.screens.mapa.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Phase8ContinuityTest {
    private val ruta=RutaGTFS("conservada","C-01","B","Recorrido","Empresa","00A850",Color.Green,
        listOf(LatLng(-8.1,-79.04),LatLng(-8.08,-79.04)),emptyList(),20,5,2.5,2.0,0.0)
    private val camara=object:ControlCamaraMapa {
        override suspend fun centrar(punto:LatLng,zoom:Float) {}
        override suspend fun encuadrar(puntos:List<LatLng>) {}
    }
    @Test fun laFlotaSoloConsumeTicksMientrasElMapaEstaVisible() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val store=ViewModelStore()
        try {
            var ticks=0
            val vm=MapaViewModel(ServiciosMapa(rutas={listOf(ruta)},cercanas={_,_->listOf(ruta)},proveedor={null},
                institucional={false},utpPendiente={false},falloCatalogo={false},
                reloj={ticks++;testScheduler.currentTime}),camara)
            store.put("mapa",vm);runCurrent();assertTrue(vm.busesAnimados.isNotEmpty())
            advanceTimeBy(250);runCurrent();assertEquals(0,ticks)
            vm.reanudar();runCurrent();advanceTimeBy(250);runCurrent();assertTrue(ticks>0)
            vm.pausarPantalla();val anterior=ticks;advanceTimeBy(500);runCurrent()
            assertEquals(anterior,ticks)
            vm.reanudar();runCurrent();advanceTimeBy(150);runCurrent();assertTrue(ticks>anterior)
        } finally { store.clear();Dispatchers.resetMain() }
    }
    @Test fun unaCargaTardiaTrasOcultarElMapaNoReiniciaTrabajoNiPanel() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val store=ViewModelStore()
        try {
            val pendiente=CompletableDeferred<List<RutaGTFS>>()
            val vm=MapaViewModel(ServiciosMapa(rutas={emptyList()},cercanas={_,_->withContext(NonCancellable) { pendiente.await() }},
                proveedor={null},institucional={false},utpPendiente={false},falloCatalogo={false},reloj={0L}),camara)
            store.put("mapa",vm);runCurrent();assertTrue(vm.cargandoCatalogo)
            vm.pausarPantalla();pendiente.complete(listOf(ruta));runCurrent()
            assertFalse(vm.cargandoCatalogo);assertTrue(vm.busesAnimados.isEmpty());assertTrue(vm.rutasCercanas.isEmpty())
        } finally { store.clear();Dispatchers.resetMain() }
    }
    @Test fun lugaresHistoricosConservanIdCategoriaYLaCompatibilidadUtp() {
        try {
            val old="""[{"id":"lugar-historico","nombre":"UTP","direccion":"Referencia","categoria":"UNIVERSIDAD","esFrecuente":true,"lat":-8.1,"lon":-79.0}]"""
            val lugar=LugaresStore.deserializar(old).single()
            assertEquals("lugar-historico",lugar.id);assertTrue(lugar.esFijo);assertTrue(lugar.esFrecuente)
            assertFalse(LugaresStore.deserializar(old.replace(""""lat":""",""""esFijo":false,"lat":""")).single().esFijo)
        } finally { LugaresStore.deserializar("[]") }
    }
    @Test fun lineasHistoricasNoPierdenElRouteIdAlLeerLaVersionActual() {
        try {
            val refs=LineasGuardadasStore.deserializar("""[{"routeId":"17350695","campoFuturo":"conservado-por-origen"}]""")
            assertEquals("17350695",refs.single().routeId);assertFalse(LineasGuardadasStore.datosInvalidos.value)
        } finally { LineasGuardadasStore.deserializar("[]") }
    }
    @Test fun jsonIlegibleSigueBloqueadoSinConvertirseEnUnaListaValida() {
        try {
            LineasGuardadasStore.deserializar("{ilegible");assertTrue(LineasGuardadasStore.datosInvalidos.value)
            LugaresStore.deserializar("{ilegible");assertTrue(LugaresStore.datosInvalidos.value)
        } finally { LineasGuardadasStore.deserializar("[]");LugaresStore.deserializar("[]") }
    }
    @Test fun movimientosYPreferenciasVersionUnoSeSiguenDecodificando() {
        val json=Json { ignoreUnknownKeys=true }
        val movimiento=json.decodeFromString<MovimientoMonedero>("""{"id":"movimiento-anterior","fecha":1790000000.0,"importeCentimos":-250}""")
        assertEquals("movimiento-anterior",movimiento.id);assertEquals(-250L,movimiento.importeCentimos)
        val config=json.decodeFromString<ConfiguracionNotificaciones>("""{"version":1,"pausaHasta":null,"diasSilencio":[1],"silencioHorario":true,"inicioSilencio":1320,"finSilencio":420,"sonido":false}""")
        assertTrue(config.valida());assertEquals(setOf(1),config.diasSilencio);assertFalse(config.sonido)
    }
}
