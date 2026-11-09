package com.example.rutautpnative

import androidx.compose.ui.graphics.Color
import com.example.rutautpnative.data.geo.PolylineMatching
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.ParaderoGTFS
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.data.routing.TransitPlanner
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

// Test del TransitPlanner (puerto de GTFSCSVTests→TransitItineraryTests del
// iOS): linea directa entre dos puntos, sentido del recorrido, radios y
// TRANSBORDOS (dos micros, limite de 350 m a pie, espera del segundo micro,
// preferencia por el directo cuando los tiempos son parecidos).
class TransitPlannerTest {

    //----Helpers de geometria sintetica----

    /// Ruta sintetica recta de 11 puntos (~1.1 km) con tres paraderos.
    private fun rutaSintetica(): RutaGTFS {
        val shape = (0..10).map { LatLng(-8.10, -79.04 + it * 0.001) }
        val paraderos = listOf(
            ParaderoGTFS("p0", "Inicio", shape[0].latitude, shape[0].longitude),
            ParaderoGTFS("p1", "Medio", shape[5].latitude, shape[5].longitude),
            ParaderoGTFS("p2", "Fin", shape[10].latitude, shape[10].longitude)
        )
        return RutaGTFS(
            id = "R1", linea = "C-01", variante = "B",
            recorrido = "Inicio → Fin", empresa = "Agencia de prueba",
            colorHex = "00CC00", color = Color(0xFF00CC00),
            shape = shape, paraderos = paraderos,
            duracionMin = 10, headwayMin = 5, precio = 2.5,
            distanciaKm = 1.1, distanciaUTPMetros = 100.0
        )
    }

    /// Linea con un paradero por punto del shape (helper `line` del iOS).
    private fun linea(id: String, puntos: List<LatLng>, headwayMin: Int = 6): RutaGTFS {
        val paraderos = puntos.mapIndexed { i, p -> ParaderoGTFS("$id-$i", "$id-$i", p.latitude, p.longitude) }
        return RutaGTFS(
            id = id, linea = id, variante = "", recorrido = id, empresa = "Test",
            colorHex = "00CC00", color = Color(0xFF00CC00),
            shape = puntos, paraderos = paraderos,
            duracionMin = 10, headwayMin = headwayMin, precio = 2.0,
            distanciaKm = PolylineMatching.totalLengthMeters(puntos) / 1000.0,
            distanciaUTPMetros = 0.0
        )
    }

    /// Velocidad derivada del feed, igual que el planificador (clamp 3-14).
    private fun velocidadDe(ruta: RutaGTFS): Double =
        (ruta.distanciaKm * 1000.0 / (ruta.duracionMin * 60.0)).coerceIn(3.0, 14.0)

    /// Red con transbordo: micro A hacia el este, micro B hacia el norte,
    /// conectados a pie (~111 m entre la bajada de A y la subida de B).
    private fun redTransbordo(): Pair<RutaGTFS, RutaGTFS> {
        val start = LatLng(-8.10, -79.04)
        val exit = LatLng(-8.10, -79.03)
        val enter = LatLng(-8.099, -79.03)
        val end = LatLng(-8.09, -79.03)
        return linea("A", listOf(start, exit)) to linea("B", listOf(enter, end))
    }

    //----Linea directa (TravelItineraryTests del iOS)----

    @Test
    fun encuentraLineaDirectaEntreExtremos() = runBlocking {
        val ruta = rutaSintetica()
        val candidatos = TransitPlanner.candidatos(ruta.shape.first(), ruta.shape.last(), listOf(ruta), 300.0)
        assertTrue("debería encontrar la línea directa", candidatos.isNotEmpty())
        val plan = candidatos.first()
        assertEquals("p0", plan.board.id)
        assertEquals("p2", plan.alight.id)
        assertTrue("un tramo en bus de menos de 50 m no es un viaje", plan.busMetros > 50)
        // Identidad de la geometria combinada (coordinates.count del iOS).
        assertEquals(
            TransitPlanner.puntosCombinados(plan).size,
            plan.walkToBoard.size + plan.busDibujo.size +
                (plan.transbordo?.caminata?.size ?: 0) + (plan.transbordo?.busDibujo?.size ?: 0) +
                plan.walkToDestination.size
        )
    }

    @Test
    fun respetaElSentidoDelRecorrido() = runBlocking {
        // Destino en el paradero inicial y origen en el final: no hay viaje
        // posible, porque el feed describe un solo sentido.
        val ruta = rutaSintetica()
        val candidatos = TransitPlanner.candidatos(ruta.shape.last(), ruta.shape.first(), listOf(ruta), 300.0)
        assertTrue("no debe invertirse el sentido del recorrido", candidatos.isEmpty())
    }

    @Test
    fun sinParaderosCercaNoHayCandidatos() = runBlocking {
        // Origen y destino LEJOS de todos los paraderos: el radio es la
        // caminata máxima admitida hasta un paradero.
        val ruta = rutaSintetica()
        val lejos = LatLng(-8.20, -79.20)
        val candidatos = TransitPlanner.candidatos(lejos, lejos, listOf(ruta), 300.0)
        assertTrue(candidatos.isEmpty())
    }

    @Test
    fun subirSobreElParaderoValeConRadioMinimo() = runBlocking {
        // Documenta la semántica real del radio: mide la caminata hasta el
        // paradero, no el trayecto. El usuario está SOBRE el paradero de
        // subida y su destino cae sobre otro paradero de la misma línea.
        val ruta = rutaSintetica()
        val candidatos = TransitPlanner.candidatos(ruta.shape.first(), ruta.shape.last(), listOf(ruta), 1.0)
        assertEquals("p0", candidatos.first().board.id)
        assertEquals("p2", candidatos.first().alight.id)
    }

    @Test
    fun rutaSinGeometriaSeIgnora() = runBlocking {
        val vacia = RutaGTFS(
            id = "R0", linea = "X", variante = "", recorrido = "—", empresa = "—",
            colorHex = "000000", color = Color(0xFF000000),
            shape = emptyList(), paraderos = emptyList(),
            duracionMin = 0, headwayMin = 0, precio = 0.0,
            distanciaKm = 0.0, distanciaUTPMetros = Double.MAX_VALUE
        )
        val cualquiera = LatLng(-8.10, -79.04)
        assertTrue(TransitPlanner.candidatos(cualquiera, cualquiera, listOf(vacia), 500.0).isEmpty())
    }

    //----Transbordos (dos micros)----

    @Test
    fun encuentraDosMicrosSinLineaDirecta() = runBlocking {
        val (a, b) = redTransbordo()
        val candidatos = TransitPlanner.candidatos(a.shape[0], b.shape[1], listOf(a, b), 50.0)
        assertTrue(candidatos.isNotEmpty())
        val plan = candidatos.first()
        assertTrue("debería tener transbordo", plan.transbordo != null)
        val transbordo = plan.transbordo!!
        assertEquals("A", plan.ruta.id)
        assertEquals("B", transbordo.ruta.id)
        assertEquals("A-1", plan.firstAlight.id)
        assertEquals("B-0", transbordo.board.id)
        assertEquals("B-1", plan.alight.id)
        assertTrue("caminata=${plan.caminataTransbordoMetros}", plan.caminataTransbordoMetros > 100)
        assertTrue("caminata=${plan.caminataTransbordoMetros}", plan.caminataTransbordoMetros < 120)
        assertEquals(plan.busMetros + transbordo.busMetros, plan.busMetrosTotales, 0.001)
        // Identidad de la geometria combinada.
        assertEquals(
            TransitPlanner.puntosCombinados(plan).size,
            plan.walkToBoard.size + plan.busDibujo.size +
                (plan.transbordo?.caminata?.size ?: 0) + (plan.transbordo?.busDibujo?.size ?: 0) +
                plan.walkToDestination.size
        )
        assertEquals(plan.totalSegundos, plan.segundosRestantes(0.0), 0.001)
        assertEquals(0.0, plan.segundosRestantes(100_000.0), 0.001)
        // headway 6 min -> 6*30 + 120 = 300 s de espera media.
        assertEquals(300.0, plan.esperaTransbordoSeg, 0.001)
    }

    @Test
    fun transbordoNoInvierteSegundoMicro() = runBlocking {
        val (a, b) = redTransbordo()
        val invertida = linea("B", b.shape.reversed())
        assertTrue(TransitPlanner.candidatos(a.shape[0], b.shape[1], listOf(a, invertida), 50.0).isEmpty())
    }

    @Test
    fun transbordoNoInviertePrimerMicro() = runBlocking {
        val (a, b) = redTransbordo()
        val invertida = linea("A", a.shape.reversed())
        assertTrue(TransitPlanner.candidatos(a.shape[0], b.shape[1], listOf(invertida, b), 50.0).isEmpty())
    }

    @Test
    fun rechazaConexionDemasiadoLejana() = runBlocking {
        val (a, b) = redTransbordo()
        // Subida del segundo micro a ~556 m: sobre el limite de 350 m.
        val lejana = linea("B", listOf(LatLng(-8.095, -79.03), b.shape[1]))
        assertTrue(TransitPlanner.candidatos(a.shape[0], b.shape[1], listOf(a, lejana), 50.0).isEmpty())
    }

    @Test
    fun noProponeCambiarAlMismoRecorrido() = runBlocking {
        val a = rutaSintetica()
        val planes = TransitPlanner.candidatos(a.shape[0], a.shape.last(), listOf(a), 50.0)
        assertTrue(planes.isNotEmpty())
        assertTrue(planes.all { it.transbordo == null })
    }

    @Test
    fun prefiereDirectoCuandoLosTiemposSonParecidos() = runBlocking {
        val (a, b) = redTransbordo()
        val directo = linea("Direct", listOf(a.shape[0], a.shape[1], b.shape[0], b.shape[1]))
        val planes = TransitPlanner.candidatos(a.shape[0], b.shape[1], listOf(a, b, directo), 50.0)
        assertNull("el directo debe ir primero", planes.first().transbordo)
        assertTrue(
            "debe conservar el transbordo A→B como alternativa",
            planes.any { it.ruta.id == "A" && it.transbordo?.ruta?.id == "B" }
        )
    }

    @Test
    fun prefiereTransbordoAnteUnDesvioDirectoLargo() = runBlocking {
        val (a, b) = redTransbordo()
        // Directo con desvio enorme por el suroeste: el transbordo gana.
        val desvio = linea("Detour", listOf(a.shape[0], LatLng(-8.30, -79.20), b.shape[1]))
        val planes = TransitPlanner.candidatos(a.shape[0], b.shape[1], listOf(a, b, desvio), 50.0)
        assertTrue("el transbordo debe ir primero", planes.first().transbordo != null)
    }

    @Test
    fun rechazaCaminataRealDeConexionSuperiorAlLimite() = runBlocking {
        val (a, b) = redTransbordo()
        val base = TransitPlanner.candidatos(a.shape[0], b.shape[1], listOf(a, b), 50.0).first()
        // Simula un Directions que alarga la conexion mas alla del limite.
        val alterado = base.copy(
            transbordo = base.transbordo!!.copy(
                caminata = listOf(a.shape[1], LatLng(-8.11, -79.03), b.shape[0])
            )
        )
        assertFalse(alterado.dentroDeLimiteTransbordo)
    }

    @Test
    fun cacheInvalidaGeometriaModificadaConMismoID() = runBlocking {
        val ruta = rutaSintetica()
        val original = TransitPlanner.candidatos(ruta.shape.first(), ruta.shape.last(), listOf(ruta), 300.0)
        assertTrue(original.isNotEmpty())
        // Misma ID, shape desplazado: no reutilizar paraderos proyectados
        // sobre el shape anterior.
        val modificada = ruta.copy(
            shape = ruta.shape.map { LatLng(it.latitude + 0.1, it.longitude) }
        )
        val invalido = TransitPlanner.candidatos(ruta.shape.first(), ruta.shape.last(), listOf(modificada), 300.0)
        assertTrue(invalido.isEmpty())
    }

    //----Equivalencia con enumeracion exhaustiva----

    @Test
    fun ordenOptimizadoCoincideConEnumeracionDirecta() = runBlocking {
        val ruta = linea("Oracle", rutaSintetica().shape)
        val origen = LatLng(-8.1003, -79.0401)
        val destino = LatLng(-8.0997, -79.0299)
        val radio = 500.0
        // Referencia: enumerar todas las subidas/bajadas con sus tiempos.
        data class Ref(val boardId: String, val alightId: String, val total: Double)
        val referencia = mutableListOf<Ref>()
        for (board in ruta.paraderos.indices) {
            for (alight in board + 1 until ruta.paraderos.size) {
                val walk1 = GTFSRepository.distanciaMetros(origen, ruta.shape[board])
                val walk2 = GTFSRepository.distanciaMetros(ruta.shape[alight], destino)
                val busMetros = PolylineMatching.totalLengthMeters(ruta.shape.subList(board, alight + 1))
                if (walk1 <= radio && walk2 <= radio && busMetros > 50) {
                    referencia.add(Ref(ruta.paraderos[board].id, ruta.paraderos[alight].id, (walk1 + walk2) / 1.4 + busMetros / velocidadDe(ruta)))
                }
            }
        }
        referencia.sortBy { it.total }
        val optimizados = TransitPlanner.candidatos(origen, destino, listOf(ruta), radio)
        assertEquals(min(32, referencia.size), optimizados.size)
        referencia.forEachIndexed { i, esperado ->
            assertEquals(esperado.boardId, optimizados[i].board.id)
            assertEquals(esperado.alightId, optimizados[i].alight.id)
            assertEquals(esperado.total, optimizados[i].totalSegundos, 0.001)
        }
    }

    @Test
    fun transbordoOptimizadoCoincideConBusquedaExhaustiva() = runBlocking {
        val a = linea("A", listOf(-79.06, -79.05, -79.04, -79.03).map { LatLng(-8.1, it) })
        val b = linea("B", listOf(-8.1, -8.098, -8.096, -8.092, -8.085).map { LatLng(it, -79.029) })
        val origen = a.shape.first()
        val destino = b.shape.last()
        val radio = 1400.0
        val espera = max(0, b.headwayMin) * 30.0 + 120.0
        // Referencia: enumerar TODAS las combinaciones de transbordo validas.
        data class Ref(val boardId: String, val exitId: String, val enterId: String, val alightId: String, val total: Double)
        var esperado: Ref? = null
        for (board in a.paraderos.indices) {
            for (exit in board + 1 until a.paraderos.size) {
                for (enter in b.paraderos.indices) {
                    for (alight in enter + 1 until b.paraderos.size) {
                        val walk1 = GTFSRepository.distanciaMetros(origen, a.shape[board])
                        val walk2 = GTFSRepository.distanciaMetros(b.shape[alight], destino)
                        val transbordo = GTFSRepository.distanciaMetros(a.shape[exit], b.shape[enter])
                        if (walk1 > radio || walk2 > radio || transbordo > 350.0) continue
                        val bus1 = PolylineMatching.totalLengthMeters(a.shape.subList(board, exit + 1))
                        val bus2 = PolylineMatching.totalLengthMeters(b.shape.subList(enter, alight + 1))
                        val total = (walk1 + walk2 + transbordo) / 1.4 +
                            bus1 / velocidadDe(a) + bus2 / velocidadDe(b) + espera
                        if (esperado == null || total < esperado!!.total) {
                            esperado = Ref(a.paraderos[board].id, a.paraderos[exit].id, b.paraderos[enter].id, b.paraderos[alight].id, total)
                        }
                    }
                }
            }
        }
        val real = esperado ?: run { assertFalse("debería existir referencia", false); return@runBlocking }
        val planes = TransitPlanner.candidatos(origen, destino, listOf(a, b), radio)
        assertTrue(planes.isNotEmpty())
        val plan = planes.first()
        assertTrue("debería tener transbordo", plan.transbordo != null)
        val transbordo = plan.transbordo!!
        assertEquals(real.boardId, plan.board.id)
        assertEquals(real.exitId, plan.firstAlight.id)
        assertEquals(real.enterId, transbordo.board.id)
        assertEquals(real.alightId, plan.alight.id)
        assertEquals(real.total, plan.totalSegundos, 0.01)
    }

    //----Caminatas reales: paralelismo y respaldo----

    @Test
    fun caminatasSeConsultanEnParaleloYRespaldoSeIdentifica() = runBlocking {
        val (a, b) = redTransbordo()
        val plan = TransitPlanner.candidatos(a.shape[0], b.shape[1], listOf(a, b), 50.0).first()
        assertTrue(plan.transbordo != null)

        // Sonda: las tres caminatas del mismo viaje no deben esperarse en serie.
        val enVuelo = AtomicInteger(0)
        val maximo = AtomicInteger(0)
        val caminatas = TransitPlanner.CaminatasReales { _, _ ->
            val actual = enVuelo.incrementAndGet()
            maximo.accumulateAndGet(actual) { prev, v -> max(prev, v) }
            delay(150)
            enVuelo.decrementAndGet()
            TransitPlanner.Caminata(
                listOf(LatLng(-8.1005, -79.035), LatLng(-8.0995, -79.035)),
                300.0,
                aproximada = false
            )
        }
        val resuelto = TransitPlanner.conCaminatasReales(plan, caminatas)
        assertEquals(3, maximo.get())
        assertFalse(resuelto.caminataAproximada)

        // Directions caido: respaldo en linea recta identificado como aproximado.
        val fallidas = TransitPlanner.CaminatasReales { _, _ -> null }
        val aproximado = TransitPlanner.conCaminatasReales(plan, fallidas)
        assertTrue(aproximado.caminataAproximada)
        assertEquals(plan.ruta.id, aproximado.ruta.id)
        assertEquals(plan.transbordo?.ruta?.id, aproximado.transbordo?.ruta?.id)
    }

    //----Plan puerta a puerta con caminatas en linea recta (tests previos)----

    /// Ruta en "L" de ~3 km, paraderos al inicio, esquina, intermedio y final.
    private fun rutaEnL(): RutaGTFS {
        val shape = mutableListOf(LatLng(-8.1000, -79.0000))
        for (i in 1..10) shape.add(LatLng(-8.1000 + i * 0.0009, -79.0000))
        for (j in 1..10) shape.add(LatLng(-8.0910, -79.0000 + j * 0.00102))
        val paraderos = listOf(
            ParaderoGTFS("p0", "Inicio", -8.1000, -79.0000),
            ParaderoGTFS("p1", "Esquina", -8.0910, -79.0000),
            ParaderoGTFS("p2", "Intermedio", -8.0910, -78.99490),
            ParaderoGTFS("p3", "Final", -8.0910, -78.98980)
        )
        return RutaGTFS(
            id = "R1", linea = "T-1", variante = "", recorrido = "Test",
            empresa = "Prueba", colorHex = "00CC00", color = Color(0xFF00CC00),
            shape = shape, paraderos = paraderos,
            duracionMin = 10, headwayMin = 5, precio = 1.5,
            distanciaKm = 2.1, distanciaUTPMetros = 0.0
        )
    }

    // ~300 m al oeste del paradero inicial.
    private val origen = LatLng(-8.1000, -79.00275)
    // ~300 m al este del paradero final.
    private val destino = LatLng(-8.0910, -78.98705)

    @Test
    fun planEncuentraSubidaYBajadaEnOrden() = runBlocking {
        val plan = TransitPlanner.plan(origen, destino, listOf(rutaEnL())) { _, _ -> null }

        assertTrue("debe encontrar plan", plan != null)
        plan!!
        assertEquals("sube en el paradero inicial", "Inicio", plan.board.nombre)
        assertEquals("baja en el paradero final", "Final", plan.alight.nombre)
        // Tramo de bus: 1 km norte + ~1.1 km este = ~2.1 km (tolerancia).
        assertTrue("busMetros=${plan.busMetros}", plan.busMetros in 1900.0..2400.0)
        // Caminatas: sin Directions -> recta aproximada de ~300 m.
        assertTrue(plan.caminataBoardMetros in 250.0..350.0)
        assertTrue(plan.caminataDestinoMetros in 250.0..350.0)
        assertTrue("debe marcar caminata aproximada", plan.caminataAproximada)
        assertTrue("etaMinutos=${plan.etaMinutos}", plan.etaMinutos >= 10)
        // El dibujo del bus conecta paradero -> shape -> paradero.
        assertEquals("primero el paradero de subida", "Inicio", coordIgual(plan.busDibujo.first(), LatLng(-8.1000, -79.0000)))
        assertTrue("camina hasta la subida", plan.walkToBoard.first() == origen && plan.walkToBoard.last() == plan.board.coordinate)
    }

    @Test
    fun sinLineaCercanaDevuelveNull() = runBlocking {
        val lejos = LatLng(-8.2000, -79.1000)
        val plan = TransitPlanner.plan(origen, lejos, listOf(rutaEnL())) { _, _ -> null }
        assertNull(plan)
    }

    @Test
    fun noInvierteElRecorrido() = runBlocking {
        // Origen cerca del FINAL y destino cerca del INICIO: la bajada debe ir
        // DESPUES de la subida en el orden del feed -> sin candidatos -> null.
        // Radio explicito 800 m: con 1600 el paradero p1 queda a pie alcanzable
        // y existe (aunque sea absurdo) un viaje hacia adelante p1 -> p2.
        val plan = TransitPlanner.plan(destino, origen, listOf(rutaEnL()), 800.0) { _, _ -> null }
        assertNull(plan)
    }

    @Test
    fun velocidadDelBusQuedaEntre3Y14() = runBlocking {
        // duracionMin = 10, distanciaKm = 2.1 -> 3.5 m/s (dentro del clamp).
        val plan = TransitPlanner.plan(origen, destino, listOf(rutaEnL())) { _, _ -> null }
        assertTrue(plan != null)
        assertTrue(
            "velocidad=${plan!!.busVelocidadMS}",
            plan.busVelocidadMS in 3.0..14.0
        )
        assertEquals("sin Directions debe caer a recta aproximada", true, plan.caminataAproximada)
    }

    private fun coordIgual(a: LatLng, b: LatLng): String =
        if (a.latitude == b.latitude && a.longitude == b.longitude) "Inicio" else "otro"
}
