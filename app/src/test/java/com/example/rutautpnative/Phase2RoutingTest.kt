package com.example.rutautpnative

import androidx.compose.ui.graphics.Color
import com.example.rutautpnative.data.geo.PolylineMatching
import com.example.rutautpnative.data.gtfs.ParaderoGTFS
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.data.routing.TransitPlanner
import com.example.rutautpnative.data.tracking.VehicleETAEstimator
import com.example.rutautpnative.data.tracking.VehiclePosition
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class Phase2RoutingTest {
    private fun route(id: String, shape: List<LatLng>, duration: Int = 5, headway: Int = 0) =
        RutaGTFS(id, id, "", id, "Prueba", "00AA00", Color.Green, shape,
            listOf(ParaderoGTFS(id + "-0", "Inicio", shape.first().latitude, shape.first().longitude),
                ParaderoGTFS(id + "-1", "Fin", shape.last().latitude, shape.last().longitude)),
            duration, headway, 2.0, PolylineMatching.totalLengthMeters(shape) / 1000, 0.0)

    private fun vehicle(point: LatLng, heading: Double = 90.0, speed: Double = 10.0) =
        VehiclePosition("bus", "A", "A", point.latitude, point.longitude, heading, speed, 1_800_000_000.0)

    @Test
    fun circularesCalculanLaVueltaEnAmbosSentidos() {
        val circular = route("A", listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.01),
            LatLng(0.01, 0.01), LatLng(0.01, 0.0), LatLng(0.0, 0.0)))
        val bus = vehicle(LatLng(0.0, 0.002))
        val target = LatLng(0.002, 0.0)
        assertEquals(7, VehicleETAEstimator.minutos(bus, circular, target))
        assertEquals(1, VehicleETAEstimator.minutos(bus.copy(heading = 270.0), circular, target))
    }

    @Test
    fun conservaUnaCurvaQueLaReduccionDeDibujoEliminaría() {
        val dense = (0..250).map { index ->
            if (index == 249) LatLng(0.01, 0.0249) else LatLng(0.0, index * 0.0001)
        }
        val line = route("A", dense)
        val bus = vehicle(LatLng(0.005, 0.02485), heading = 0.573)
        assertEquals(3, VehicleETAEstimator.minutos(bus, line, dense.last()))
    }

    @Test
    fun detenidoEnElDestinoYVelocidadesNoFinitasNoGeneranLlegada() {
        val line = route("A", listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.02)))
        val target = LatLng(0.0, 0.012)
        assertNull(VehicleETAEstimator.minutos(vehicle(target, speed = 0.0), line, target))
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
            assertNull(VehicleETAEstimator.minutos(vehicle(LatLng(0.0, 0.002), speed = it), line, target))
        }
    }

    private val origin = LatLng(0.0, 0.0)
    private val change = LatLng(0.0, 0.02)
    private val target = LatLng(0.02, 0.02)
    private fun twoBuses() = listOf(route("A", listOf(origin, change)), route("B", listOf(change, target)))
    private fun direct(headway: Int = 0) = route("Direct", listOf(origin, change, target), duration = 60, headway = headway)

    @Test
    fun directoCortoDescartaCambiosDelComparadorPeroNoDeLosCandidatos() = runBlocking {
        val catalog = twoBuses() + direct()
        val options = TransitPlanner.opciones(origin, target, catalog, 50.0)
        assertEquals(listOf("Direct"), options.alternativas.map { it.ruta.id })
        assertTrue(options.destacadas.all { it.plan.transbordo == null })
        assertTrue(TransitPlanner.candidatos(origin, target, catalog, 50.0).any { it.transbordo != null })
    }

    @Test
    fun viajeDirectoLargoIncluyeEsperaInicialAlCompararElAhorro() = runBlocking {
        val options = TransitPlanner.opciones(origin, target, twoBuses() + direct(headway = 60), 50.0)
        assertTrue(options.alternativas.any { it.ruta.id == "Direct" })
        assertTrue(options.alternativas.any { it.ruta.id == "A" && it.transbordo?.ruta?.id == "B" })
        val fastest = options.destacadas.first { TransitPlanner.Categoria.MAS_RAPIDA in it.categorias }.plan
        assertEquals("A", fastest.ruta.id)
        assertEquals("B", fastest.transbordo?.ruta?.id)
        val recommended = options.destacadas.first { TransitPlanner.Categoria.RECOMENDADA in it.categorias }.plan
        assertEquals(fastest.optionID, recommended.optionID)
    }

    @Test
    fun sinDirectoConservaElTransbordoYVerificaSusCaminatas() = runBlocking {
        val options = TransitPlanner.opciones(origin, target, twoBuses(), 50.0)
        assertEquals(1, options.alternativas.size)
        val plan = options.alternativas.single()
        assertEquals("A-0", plan.board.id)
        assertEquals("A-1", plan.firstAlight.id)
        assertEquals("B-0", plan.transbordo!!.board.id)
        assertEquals("B-1", plan.alight.id)
        val fallback = TransitPlanner.conCaminatasReales(plan) { _, _ -> null }
        assertTrue(fallback.caminataAproximada)
        assertEquals(plan.optionID, fallback.optionID)
        val verified = TransitPlanner.conCaminatasReales(plan) { from, to ->
            TransitPlanner.Caminata(listOf(from, to), PolylineMatching.totalLengthMeters(listOf(from, to)), false)
        }
        assertFalse(verified.caminataAproximada)
        assertTrue(verified.dentroDeLimiteTransbordo)
        assertEquals(plan.optionID, verified.optionID)
    }

    @Test
    fun todosLosDirectosPermanecenAunqueSuperen32Lineas() = runBlocking {
        val lines = (1..40).map { direct().copy(id = "direct-" + it, linea = it.toString()) }
        val options = TransitPlanner.opciones(origin, target, lines, 50.0)
        assertEquals(lines.map { it.id }.toSet(), options.alternativas.map { it.ruta.id }.toSet())
        assertEquals(40, options.alternativas.size)
        assertTrue(options.alternativas.all { it.transbordo == null })
    }

    @Test
    fun comparacionRequiereDosTramosUtilesDeMasDe800Metros() = runBlocking {
        val middle = LatLng(0.0, 0.005)
        val end = LatLng(0.005, 0.005)
        val lines = listOf(route("short-A", listOf(origin, middle)), route("short-B", listOf(middle, end)))
        assertTrue(TransitPlanner.candidatos(origin, end, lines, 50.0).any { it.transbordo != null })
        assertTrue(TransitPlanner.opciones(origin, end, lines, 50.0).alternativas.isEmpty())
    }
}
