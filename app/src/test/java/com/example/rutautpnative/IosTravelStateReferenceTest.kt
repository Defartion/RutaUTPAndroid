package com.example.rutautpnative

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.rutautpnative.data.geo.PolylineMatching
import com.example.rutautpnative.data.gtfs.*
import com.example.rutautpnative.data.places.PlacesService
import com.example.rutautpnative.data.routing.TransitPlanner
import com.example.rutautpnative.navigation.*
import com.example.rutautpnative.ui.screens.*
import com.example.rutautpnative.ui.screens.mapa.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Ejecuta los escenarios sobre los modelos y el router que usa la aplicación. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class IosTravelStateReferenceTest(private val id: String, private val case: JsonObject) {
    private val expected get() = case.getValue("expected").jsonObject
    private val factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.getDeclaredConstructor().newInstance()
    }
    @Test fun estadoCoincideConReferenciaIos() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val modelos = ModelosCatalogo()
        // El contenedor padre permite retirar también su store al terminar la prueba.
        val padre = androidx.lifecycle.ViewModelStore().apply { put("catalogos", modelos) }
        try {
            when (id) {
                "STATE-01", "STATE-02" -> {
                    val respuesta = CompletableDeferred<PlacesService.Resultado>()
                    val camara = CamaraPrueba()
                    val owner = modelos.obtener(false, factory)
                    val vm = MapaViewModel(servicios(buscar = { withContext(NonCancellable) { respuesta.await() } }), camara)
                    owner.viewModelStore.put("mapa", vm)
                    runCurrent()
                    vm.buscarTexto("A"); runCurrent()
                    if (id == "STATE-01") vm.seleccionarLugarExterno("B", -8.12, -79.04) else vm.limpiar()
                    runCurrent()
                    respuesta.complete(PlacesService.Resultado.Exito("A", -8.1, -79.0))
                    runCurrent()
                    val destino = expected.getValue("destination").let { if (it == JsonNull) null else it.jsonPrimitive.content }
                    assertEquals(destino, vm.destinoSeleccionado?.label)
                    assertEquals(expected.getValue("applySearchA").jsonPrimitive.boolean, camara.puntos.any { it == LatLng(-8.1, -79.0) })
                    assertTrue(vm.sugerencias.isEmpty())
                    assertNull(vm.itinerario)
                }
                "STATE-03" -> {
                    val urbano = modelos.obtener(false, factory)
                    val vm = MapaViewModel(servicios(rutas = listOf(ruta())), CamaraPrueba())
                    urbano.viewModelStore.put("mapa", vm); runCurrent()
                    assertEquals(1, vm.busesAnimados.size)
                    vm.seleccionarBus(vm.busesAnimados.single())
                    val institucional = modelos.obtener(true, factory)
                    assertNotSame(urbano, institucional)
                    var aperturasProveedor = 0
                    val nuevo = MapaViewModel(servicios(institucional = true, proveedor = { aperturasProveedor++; null }), CamaraPrueba())
                    institucional.viewModelStore.put("mapa", nuevo); runCurrent()
                    assertEquals(expected.getValue("fleet").jsonArray.size, nuevo.busesAnimados.size)
                    assertNull(nuevo.busSeleccionado)
                    assertNull(nuevo.itinerario)
                    assertTrue(nuevo.rutasCercanas.isEmpty())
                    assertEquals(0, aperturasProveedor)
                    assertEquals(0, modelos.consumirCambio())
                }
                "STATE-04", "STATE-05", "STATE-08" -> {
                    val viajes = EstadoViajes()
                    val tipoViaje = when (id) { "STATE-04" -> TipoViaje.TRACKING; "STATE-05" -> TipoViaje.NAVEGACION; else -> TipoViaje.DECLARADO }
                    viajes.actualizar(tipoViaje, true)
                    val router = AppRouter(viajes = viajes)
                    val pantalla = if (tipoViaje == TipoViaje.TRACKING) AppScreen.TrackingDemo else AppScreen.Rutas
                    router.navigate(pantalla)
                    val a = DestinoPendiente("A", -8.1, -79.0)
                    router.destinoPendiente = a
                    val tipoAviso = when (id) { "STATE-04" -> TipoNotificacion.RECORDATORIO; "STATE-05" -> TipoNotificacion.CAMBIO_RUTA; else -> TipoNotificacion.CONSEJO }
                    val accion = AccionNotificacion(id, tipoAviso, "B", "ruta", 200.0, 0)
                    router.recibirNotificacion(accion)
                    router.procesarNotificacion(accion, DestinoPendiente("B", -8.2, -79.1), true, 100.0)
                    if (id != "STATE-08") assertEquals(expected.getValue("destination").jsonPrimitive.content, router.destinoPendiente?.titulo)
                    assertEquals(a, router.destinoPendiente)
                    assertSame(pantalla, router.currentScreen)
                    assertTrue(viajes.contiene(tipoViaje))
                    if (id != "STATE-08") assertEquals(expected.getValue("showTripInProgress").jsonPrimitive.boolean, router.aviso != null)
                    else {
                        assertEquals(expected.getValue("allowNavigationFromTip").jsonPrimitive.boolean, router.puedeNavegarConsejo)
                        assertEquals(0, router.consejoAbierto)
                        router.abrirConsejo()
                        assertSame(pantalla, router.currentScreen)
                    }
                    if (id != "STATE-08") assertNotNull(router.aviso)
                    assertNull(router.notificacionPendiente)
                    router.recibirNotificacion(accion)
                    assertNull(router.notificacionPendiente)
                }
                "STATE-06" -> {
                    val owner = modelos.obtener(false, factory)
                    val estado = ViewModelProvider(owner, factory)[EstadoGuardadoViewModel::class.java]
                    estado.pestana = 1
                    estado.lista.requestScrollToItem(10, 25)
                    val router = AppRouter(); router.navigate(AppScreen.Guardado)
                    router.navigate(AppScreen.MapaPrincipal); router.navigate(AppScreen.Guardado)
                    val restaurado = ViewModelProvider(modelos.obtener(false, factory), factory)[EstadoGuardadoViewModel::class.java]
                    assertSame(estado, restaurado)
                    assertEquals(1, restaurado.pestana)
                    assertEquals(expected.getValue("listAnchor").jsonPrimitive.content, "route-" + restaurado.lista.firstVisibleItemIndex)
                    assertEquals(25, restaurado.lista.firstVisibleItemScrollOffset)
                    assertSame(AppScreen.Guardado, router.currentScreen)
                }
                "STATE-07" -> {
                    val owner = modelos.obtener(false, factory)
                    val holder = ViewModelProvider(owner, factory)[TrackingSessionViewModel::class.java]
                    val vm = holder.obtener { scope -> TrackingDemoVM(scope, AjustesPrueba, { listOf(ruta()) }, TransitPlanner.CaminatasReales { _, _ -> null }) }
                    vm.posicion = ruta().shape.first()
                    vm.iniciarViaje("A", ruta().shape.last())!!.join()
                    assertTrue(vm.conViaje)
                    val total = PolylineMatching.totalLengthMeters(TransitPlanner.puntosCombinados(vm.planActual!!))
                    val metros = case.getValue("given").jsonObject.getValue("progressMeters").jsonPrimitive.double
                    vm.progreso = metros / total
                    val sesion = vm.sesion
                    vm.suspenderPantalla()
                    val router = AppRouter(); router.navigate(AppScreen.Perfil); router.navigate(AppScreen.TrackingDemo)
                    val retenido = ViewModelProvider(modelos.obtener(false, factory), factory)[TrackingSessionViewModel::class.java]
                    assertSame(holder, retenido)
                    val restaurado = retenido.obtener { error("No debe crear un segundo viaje") }
                    assertSame(vm, restaurado)
                    assertSame(sesion, restaurado.sesion)
                    assertEquals(expected.getValue("destination").jsonPrimitive.content, restaurado.destinoTitulo)
                    assertEquals(expected.getValue("progressMeters").jsonPrimitive.double, restaurado.progreso * total, 0.00001)
                    assertTrue(ViajesActivos.estado.contiene(TipoViaje.TRACKING))
                }
                else -> error("Escenario sin adaptador: $id")
            }
        } finally { padre.clear(); Dispatchers.resetMain() }
    }

    private fun servicios(rutas: List<RutaGTFS> = emptyList(), institucional: Boolean = false,
                          buscar: suspend (String) -> PlacesService.Resultado = { PlacesService.Resultado.SinResultados },
                          proveedor: () -> com.example.rutautpnative.data.tracking.MQTTTrackingProvider? = { null }) = ServiciosMapa(
        buscar = buscar, sugerir = { PlacesService.ResultadoSugerencias.SinResultados }, rutas = { rutas },
        cercanas = { _, _ -> rutas }, proveedor = proveedor, institucional = { institucional }, utpPendiente = { institucional },
        caminar = TransitPlanner.CaminatasReales { _, _ -> null }, reloj = { 0L })
    private class CamaraPrueba : ControlCamaraMapa {
        val puntos = mutableListOf<LatLng>()
        override suspend fun centrar(punto: LatLng, zoom: Float) { puntos += punto }
        override suspend fun encuadrar(puntos: List<LatLng>) { this.puntos += puntos }
    }
    private object AjustesPrueba : AjustesTracking {
        override val radio = 1600.0
        override val velocidad = 1
        override fun guardarRadio(value: Double) {}
        override fun guardarVelocidad(value: Int) {}
    }
    private fun ruta(): RutaGTFS {
        val shape = listOf(LatLng(-8.1, -79.0), LatLng(-8.1, -78.98))
        return RutaGTFS("urban-1", "10", "", "Centro", "Empresa", "00AA00", Color.Green, shape,
            listOf(ParaderoGTFS("p0", "Inicio", -8.1, -79.0), ParaderoGTFS("p1", "Fin", -8.1, -78.98)),
            5, 10, 2.0, PolylineMatching.totalLengthMeters(shape) / 1000, 0.0)
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun cases(): Collection<Array<Any>> =
            requireNotNull(IosTravelStateReferenceTest::class.java.getResourceAsStream("/parity/travel-state.json"))
                .bufferedReader(Charsets.UTF_8).use { Json.parseToJsonElement(it.readText()).jsonObject }
                .getValue("cases").jsonArray.map { it.jsonObject }.filter { it.getValue("status").jsonPrimitive.content == "baseline" }.map { arrayOf<Any>(it.getValue("id").jsonPrimitive.content, it) }
    }
}
