package com.example.rutautpnative

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.rutautpnative.data.gtfs.*
import com.example.rutautpnative.data.notificaciones.ConfiguracionNotificaciones
import com.example.rutautpnative.data.notificaciones.RecordatorioViaje
import com.example.rutautpnative.navigation.*
import com.example.rutautpnative.ui.screens.EstadoGuardadoViewModel
import org.junit.Assert.*
import org.junit.Test

class Phase3SessionTest {
    private fun aviso(id: String = "uno", vence: Double = 200.0, tipo: TipoNotificacion = TipoNotificacion.RECORDATORIO) =
        AccionNotificacion(id, tipo, "lugar:B", "route-B", vence, 0)
    private val destino = DestinoPendiente("B", -8.1, -79.0)
    @Test fun avisoValidoSinViajeAbreDestino() {
        val router = AppRouter(viajes = EstadoViajes()); val accion = aviso()
        router.recibirNotificacion(accion); router.procesarNotificacion(accion, destino, true, 100.0)
        assertEquals(destino, router.destinoPendiente); assertSame(AppScreen.MapaPrincipal, router.currentScreen)
        assertNull(router.aviso)
    }
    @Test fun avisoVencidoNoCambiaPantallaNiDestino() {
        val router = AppRouter(viajes = EstadoViajes()); router.navigate(AppScreen.Perfil)
        val accion = aviso(vence = 100.0)
        router.recibirNotificacion(accion); router.procesarNotificacion(accion, destino, true, 100.0)
        assertSame(AppScreen.Perfil, router.currentScreen); assertNull(router.destinoPendiente); assertNotNull(router.aviso)
    }
    @Test fun destinoEliminadoNoNavega() {
        val router = AppRouter(viajes = EstadoViajes()); val accion = aviso()
        router.recibirNotificacion(accion); router.procesarNotificacion(accion, null, false, 100.0)
        assertSame(AppScreen.Bienvenida, router.currentScreen); assertNotNull(router.aviso)
    }
    @Test fun vigenciaNoFinitaSeRechaza() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
            assertEquals(DecisionNotificacion.NO_DISPONIBLE, decidirNotificacion(aviso(vence = it), false, true, 100.0))
        }
    }
    @Test fun viajeIniciadoDuranteLecturaBloqueaElAviso() {
        val viajes = EstadoViajes(); val router = AppRouter(viajes = viajes); val accion = aviso()
        router.navigate(AppScreen.TrackingDemo); router.recibirNotificacion(accion)
        viajes.actualizar(TipoViaje.TRACKING, true)
        router.procesarNotificacion(accion, destino, true, 100.0)
        assertSame(AppScreen.TrackingDemo, router.currentScreen); assertNull(router.destinoPendiente); assertNotNull(router.aviso)
    }
    @Test fun terminarUnTipoNoBorraOtroViaje() {
        val viajes = EstadoViajes()
        viajes.actualizar(TipoViaje.TRACKING, true); viajes.actualizar(TipoViaje.DECLARADO, true)
        viajes.actualizar(TipoViaje.TRACKING, false)
        assertTrue(viajes.enCurso); assertTrue(viajes.contiene(TipoViaje.DECLARADO))
        viajes.actualizar(TipoViaje.DECLARADO, false); assertFalse(viajes.enCurso)
    }
    @Test fun mismaAccionEnColaSeProcesaUnaVez() {
        val router = AppRouter(viajes = EstadoViajes()); val accion = aviso()
        repeat(3) { router.recibirNotificacion(accion) }
        router.procesarNotificacion(accion, destino, true, 100.0)
        assertNull(router.notificacionPendiente)
        router.navigate(AppScreen.Perfil)
        router.procesarNotificacion(accion, destino, true, 100.0)
        assertSame(AppScreen.Perfil, router.currentScreen)
    }
    @Test fun accionesConsumidasSeConservanEnEstadoGuardado() {
        val estado = SavedStateHandle(); val accion = aviso()
        val router = AppRouter(estado, EstadoViajes()); router.recibirNotificacion(accion)
        router.procesarNotificacion(accion, destino, true, 100.0)
        val recreado = AppRouter(estado, EstadoViajes()); recreado.recibirNotificacion(accion)
        assertNull(recreado.notificacionPendiente)
    }
    @Test fun lasAccionesSeConsumenEnOrden() {
        val router = AppRouter(viajes = EstadoViajes()); val a = aviso(); val b = aviso("dos")
        router.recibirNotificacion(a); router.recibirNotificacion(b)
        router.procesarNotificacion(b, destino, true, 100.0); assertEquals(a, router.notificacionPendiente)
        router.procesarNotificacion(a, destino, true, 100.0); assertEquals(b, router.notificacionPendiente)
        router.procesarNotificacion(b, destino, true, 100.0); assertNull(router.notificacionPendiente)
    }
    @Test fun cambioRutaValidoSeDirigeAlMapa() {
        val router = AppRouter(viajes = EstadoViajes()); val accion = aviso(tipo = TipoNotificacion.CAMBIO_RUTA)
        router.recibirNotificacion(accion); router.procesarNotificacion(accion, null, true, 100.0)
        assertEquals("route-B", router.cambioRutaPendiente); assertSame(AppScreen.MapaPrincipal, router.currentScreen)
    }
    @Test fun consejoSePuedeLeerPeroUnViajeIniciadoConLaHojaAbiertaBloqueaSuBoton() {
        val viajes = EstadoViajes(); val router = AppRouter(viajes = viajes)
        router.navigate(AppScreen.TrackingDemo)
        val accion = aviso(tipo = TipoNotificacion.CONSEJO)
        router.recibirNotificacion(accion); router.procesarNotificacion(accion, null, true, 100.0)
        assertEquals(0, router.consejoAbierto); assertTrue(router.puedeNavegarConsejo)
        assertSame(AppScreen.TrackingDemo, router.currentScreen)
        viajes.actualizar(TipoViaje.TRACKING, true)
        router.abrirConsejo()
        assertFalse(router.puedeNavegarConsejo); assertSame(AppScreen.TrackingDemo, router.currentScreen)
        assertEquals(0, router.consejoAbierto)
        router.cerrarConsejo(); assertNull(router.consejoAbierto)
    }
    @Test fun consejoDeCambiosSinViajeAbreLaConsultaActualSoloAlTocarElBoton() {
        val router = AppRouter(viajes = EstadoViajes())
        val accion = aviso(tipo = TipoNotificacion.CONSEJO).copy(consejo = 1)
        router.recibirNotificacion(accion); router.procesarNotificacion(accion, null, true, 100.0)
        assertSame(AppScreen.Bienvenida, router.currentScreen); assertFalse(router.consultaCambiosPendiente)
        router.abrirConsejo()
        assertSame(AppScreen.MapaPrincipal, router.currentScreen); assertTrue(router.consultaCambiosPendiente)
        assertNull(router.consejoAbierto)
    }
    @Test fun avisoAnteriorAUnCambioDeCatalogoNoSeAplicaAunqueSeVuelvaAlMismoModo() {
        val router = AppRouter(viajes = EstadoViajes()); val accion = aviso()
        router.navigate(AppScreen.Perfil); router.recibirNotificacion(accion)
        val contexto = router.revisionCatalogo
        router.invalidarCatalogo(); router.invalidarCatalogo()
        router.procesarNotificacion(accion, destino, true, 100.0, contexto)
        assertSame(AppScreen.Perfil, router.currentScreen); assertNull(router.destinoPendiente); assertNotNull(router.aviso)
    }
    @Test fun catalogoInvalidaTodosLosDestinosPendientes() {
        val router = AppRouter(); router.rutaPendiente = "r"; router.cambioRutaPendiente = "r"; router.consultaCambiosPendiente = true
        router.destinoPendiente = destino; router.lugarCercanoPendiente = destino; router.lugarGuardadoPendiente = "l"
        router.invalidarCatalogo()
        assertNull(router.rutaPendiente); assertNull(router.cambioRutaPendiente); assertNull(router.destinoPendiente)
        assertNull(router.lugarCercanoPendiente); assertNull(router.lugarGuardadoPendiente); assertFalse(router.consultaCambiosPendiente)
    }
    @Test fun elMismoCatalogoConservaModelosYCambiarLoRetira() {
        val modelos = ModelosCatalogo(); val padre = ViewModelStore().apply { put("m", modelos) }
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.getDeclaredConstructor().newInstance()
        }
        try {
            val uno = modelos.obtener(false, factory)
            val estado = ViewModelProvider(uno, factory)[EstadoGuardadoViewModel::class.java]
            assertSame(uno, modelos.obtener(false, factory)); assertNull(modelos.consumirCambio())
            val dos = modelos.obtener(true, factory)
            assertNotSame(uno, dos); assertEquals(0, modelos.consumirCambio()); assertNull(modelos.consumirCambio())
            assertNotSame(estado, ViewModelProvider(dos, factory)[EstadoGuardadoViewModel::class.java])
            assertEquals(1, modelos.revision)
            assertNotSame(dos, modelos.obtener(false, factory)); assertEquals(2, modelos.revision)
        } finally { padre.clear() }
    }
    private fun configuracion() = ConfiguracionNotificaciones(recordatorio = RecordatorioViaje("recordatorio-1", 540, setOf(2), "lugar:B"))
    @Test fun recordatorioVigenteExigeSuIdentidadYDestino() {
        assertTrue(configuracion().aceptaAviso("recordatorio-1", "lugar:B", null))
        assertFalse(configuracion().aceptaAviso(null, "lugar:B", null))
    }
    @Test fun cambiarOEliminarElRecordatorioInvalidaElAvisoAnterior() {
        val nueva = configuracion().copy(recordatorio = configuracion().recordatorio!!.copy(id = "recordatorio-2"))
        assertFalse(nueva.aceptaAviso("recordatorio-1", "lugar:B", null))
        assertFalse(configuracion().copy(recordatorio = null).aceptaAviso("recordatorio-1", "lugar:B", null))
    }
    @Test fun cambiarElDestinoInvalidaElAvisoAunqueElLugarAnteriorAunExista() {
        val nueva = configuracion().copy(recordatorio = configuracion().recordatorio!!.copy(destinoID = "lugar:C"))
        assertFalse(nueva.aceptaAviso("recordatorio-1", "lugar:B", null))
    }
    @Test fun consejoExigeAgendaActivaYLaMismaIdentidad() {
        val config = ConfiguracionNotificaciones(diariosActivos = true, diarioID = "agenda-1")
        assertTrue(config.aceptaAviso("agenda-1", null, 0))
        assertFalse(config.copy(diariosActivos = false).aceptaAviso("agenda-1", null, 0))
        assertFalse(config.copy(diarioID = "agenda-2").aceptaAviso("agenda-1", null, 0))
        assertFalse(config.aceptaAviso("agenda-1", null, 4))
    }
    @Test fun idsInstitucionalesSeSeparanSinCambiarValoresNiComillas() {
        val tabla = GTFSCSV.parsear("agency_id,route_id,trip_id,shape_id,stop_id,fare_id,stop_name,price\n1,1,1,1,1,1,\"Centro, norte\",2.50\n,,,,,,Sin ids,0")
        val nueva = tabla.conIdentificadoresInstitucionales()
        listOf("agency_id", "route_id", "trip_id", "shape_id", "stop_id", "fare_id").forEach {
            assertEquals("utp:1", nueva.valor(0, it)); assertEquals("", nueva.valor(1, it)); assertEquals("1", tabla.valor(0, it))
        }
        assertEquals("Centro, norte", nueva.valor(0, "stop_name")); assertEquals("2.50", nueva.valor(0, "price"))
    }
    @Test fun feedInstitucionalConIdsHomonimosMantieneTodasLasRelaciones() {
        val tablas = mapOf(
            "agency" to "agency_id,agency_name\nag,Empresa",
            "routes" to "route_id,agency_id,route_short_name,route_long_name,route_color\nr,ag,10,Centro,FF0000",
            "trips" to "trip_id,route_id,shape_id\nt,r,s",
            "shapes" to "shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence\ns,-8.1,-79.0,1\ns,-8.11,-79.01,2",
            "stops" to "stop_id,stop_name,stop_lat,stop_lon\np,Paradero,-8.1,-79.0",
            "stop_times" to "trip_id,stop_sequence,stop_id,departure_time\nt,1,p,08:00:00\nt,2,p,09:00:00",
            "frequencies" to "trip_id,headway_secs\nt,600",
            "fare_attributes" to "fare_id,price\nf,2.50",
            "fare_rules" to "fare_id,route_id\nf,r")
        val urbano = GTFSRepository.parsearFuente(tablas::getValue).single()
        val institucional = GTFSRepository.parsearFuente(true, tablas::getValue).single()
        assertEquals("r", urbano.id); assertEquals("utp:r", institucional.id)
        assertEquals("utp:p", institucional.paraderos.first().id); assertEquals("p", urbano.paraderos.first().id)
        assertEquals(urbano.shape, institucional.shape); assertEquals(urbano.empresa, institucional.empresa)
        assertEquals(urbano.precio, institucional.precio, 0.0); assertEquals(2.50, institucional.precio, 0.0)
        assertEquals(10, institucional.headwayMin); assertEquals(60, institucional.duracionMin)
    }
}
