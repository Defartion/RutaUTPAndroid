package com.example.rutautpnative

import androidx.compose.ui.graphics.Color
import com.example.rutautpnative.data.ContactoEmergencia
import com.example.rutautpnative.data.gtfs.*
import com.example.rutautpnative.data.monedero.*
import com.example.rutautpnative.data.notificaciones.*
import com.example.rutautpnative.data.tracking.*
import com.example.rutautpnative.model.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ParidadIosTest {
    private val lima = ZoneId.of("America/Lima")
    private val run = "12345678-1234-1234-1234-123456789abc"
    private fun ruta(variante: String = "A", linea: String = "M-34") = RutaGTFS("ruta-1", linea, variante, "Centro", "Empresa de Transportes", "FFFFFF", Color.White,
        listOf(LatLng(-8.1, -79.0), LatLng(-8.1, -78.99)), listOf(ParaderoGTFS("p1", "Avenida España", -8.1, -79.0)), 10, 6, 2.5, 1.1, 0.0)
    private fun coincide(texto: String, variante: String = "A") = IndiceBusquedaRuta(ruta(variante)).coincide(ConsultaRuta(texto, setOf("a", "b♥")))

    @Test fun csvAdmiteBomEscapesYSaltosDentroDeCampos() {
        val tabla = GTFSCSV.parsear("\uFEFFid,nombre\r\n1,\"Av. "+"\"\"España\"\"\r\nNorte\"\r2,Centro\n")
        assertEquals(2, tabla.rowCount)
        assertEquals("Av. \"España\"\r\nNorte", tabla.valor(0, "nombre"))
        assertEquals("Centro", tabla.valor(1, "nombre"))
    }
    @Test fun csvConservaColumnasVaciasYUnicode() {
        val tabla = GTFSCSV.parsear("a,b,c\n,中文,\n")
        assertEquals(listOf("", "中文", ""), tabla.filas.single())
    }
    @Test fun busquedaDeLetraNoCoincideConCualquierAvenida() { assertTrue(coincide("A")); assertFalse(coincide("A", "B")) }
    @Test fun busquedaIncluyeCallesSinTildes() { assertTrue(coincide("avenida espana")); assertTrue(coincide("M 34")); assertTrue(coincide("M-34")) }
    @Test fun busquedaConCorazonYCalificadores() { assertTrue(coincide("letra B <3", "B ♥")); assertFalse(coincide("letra B", "B ♥")) }
    @Test fun consultaVaciaDevuelveElCatalogo() { assertTrue(coincide("")) }

    private fun lectura(extra: String) = ResultadoQR(PagoQR.leer("DEMO YAPO\ncelular: 999888777\n$extra"))
    @Test fun qrNoCobraImportesIlegiblesDuplicadosOFracciones() {
        listOf("importe: roto\nmoneda: PEN", "importe: 2.5\nimporte: 3\nmoneda: PEN", "importe: 0.001\nmoneda: PEN", "importe: NaN\nmoneda: PEN", "importe: -2\nmoneda: PEN").forEach {
            assertNotNull(it, lectura(it).errorParaPago); assertFalse(lectura(it).reservarPago())
        }
    }
    @Test fun qrSoloAdmiteSolesSinConvertirMonedas() {
        listOf("importe: 2.5", "importe: 2.5\nmoneda: USD", "importe: 2.5\nmoneda: PEN\nmoneda: PEN", "moneda: ").forEach { assertNotNull(lectura(it).errorParaPago) }
        assertEquals(2.5, lectura("importe: 2.5\nmoneda: pen").importeParaPago!!, 0.0)
    }
    @Test fun qrEstaticoConservaTarifaPeroCadaLecturaSoloCobraUnaVez() {
        val primera = lectura("")
        assertEquals(2.5, primera.importeParaPago!!, 0.0)
        assertTrue(primera.reservarPago()); assertFalse(primera.reservarPago()); assertTrue(lectura("").reservarPago())
    }
    @Test fun tlvTruncadoDuplicadoOCrcInvalidoNoEsPago() {
        listOf("0002016304FFFFbasura", "0002010002016304FFFF", "0002016304ZZZZ", "0002016304FFFF5409x").forEach { assertFalse(PagoQR.leer(it).esPago) }
    }
    @Test fun importesEnCentimosNoRedondeanDatosInvalidos() {
        assertEquals(30L, MonederoStore.importeCentimosValido(0.1 + 0.2))
        listOf(0.0, -1.0, 0.001, Double.NaN, Double.POSITIVE_INFINITY, 1e100).forEach { assertNull(MonederoStore.importeCentimosValido(it)) }
    }
    @Test fun telefonosInternacionalesYCaracteresNoAdmitidos() {
        assertEquals("+51999888777", ContactoEmergencia.telefonoValido("+51 (999) 888-777"))
        listOf("123", "9+99888777", "++51999888777", "999CALL777", "1234567890123456").forEach { assertNull(ContactoEmergencia.telefonoValido(it)) }
    }
    @Test fun invarianteDeLugarFijoNoDependeDelNombre() {
        assertFalse(LugarGuardado(nombre = "UTP", direccion = "", categoria = CategoriaLugar.OTRO).esFijo)
        assertTrue(LugarGuardado(nombre = "Campus renombrado", direccion = "", categoria = CategoriaLugar.UNIVERSIDAD, esFijo = true).esFijo)
    }
    @Test fun paradaGuardadaSeReconocePorIdTrasCambiarNombre() {
        val lugar = LugarGuardado(nombre = "Mi parada", direccion = "", categoria = CategoriaLugar.PARADERO, paraderoID = "p1")
        assertTrue(lugar.esParaderoGuardadoEn(emptyList()))
        val json = Json.encodeToString(LugarGuardado.serializer(), lugar)
        assertEquals(lugar, Json.decodeFromString<LugarGuardado>(json))
    }
    @Test fun limiteDeMicrosConservaSeleccionFueraDelPrefijo() {
        val buses = (1..20).map(Int::toString)
        val visibles = MapVehiclePreferences.limitar(buses, 5, "18") { it }
        assertEquals(5, visibles.size); assertTrue("18" in visibles); assertEquals(buses, MapVehiclePreferences.limitar(buses, 0, null) { it })
    }
    @Test fun silencioNocturnoYPausaSeAplicanALaAgenda() {
        val config = ConfiguracionNotificaciones(silencioHorario = true)
        assertFalse(config.permite(Instant.parse("2026-11-10T04:00:00Z"), lima))
        assertTrue(config.permite(Instant.parse("2026-11-10T15:00:00Z"), lima))
        assertFalse(config.copy(inicioSilencio = 0, finSilencio = 0).permite(Instant.parse("2026-11-10T15:00:00Z"), lima))
    }
    @Test fun recordatoriosRespetanDiasYSoloFechasFuturas() {
        val ahora = Instant.parse("2026-11-09T13:00:00Z")
        val config = ConfiguracionNotificaciones(recordatorio = RecordatorioViaje(minutoDelDia = 7 * 60, dias = setOf(2), destinoID = "lugar:x"))
        val fechas = ProgramadorRecordatorios.fechas(config, ahora, lima, 2)
        assertEquals(listOf(Instant.parse("2026-11-16T12:00:00Z"), Instant.parse("2026-11-23T12:00:00Z")), fechas)
    }
    @Test fun avisosDiariosNoSeVuelvenASortearAlReabrir() {
        val config = ConfiguracionNotificaciones(diariosActivos = true, cantidadDiaria = 3, diarioID = "semilla")
        val ahora = Instant.parse("2026-11-09T10:00:00Z")
        val primero = ProgramadorRecordatorios.diarios(config, ahora, lima, 6)
        val despues = ProgramadorRecordatorios.diarios(config, primero.first().fecha.plusSeconds(1), lima, 5)
        assertEquals(primero.drop(1), despues)
    }

    private fun payload(version: Int = 2, origin: String = "simulated") = """{"schemaVersion":$version,"vehicleId":"sim:$run:bus1","routeId":"ruta-1","linea":"A","lat":-8.1,"lon":-79.0,"speed":8,"heading":90,"timestamp":1000,"dataOrigin":"$origin","simulationRunId":"$run","controlRevision":1${if (version == 3) ",\"scenarioOccupancy\":\"full\",\"occupancyScriptVersion\":1" else ""}}"""
    @Test fun mqttValidaIdentidadTopicoYRetained() {
        val topic = "rutautp/vehiculos/sim:$run:bus1/posicion"
        assertNotNull(VehicleTrackingWire.position(payload().toByteArray(), topic))
        assertNull(VehicleTrackingWire.position(payload().toByteArray(), topic, true))
        assertNull(VehicleTrackingWire.position(payload().toByteArray(), topic.replace("bus1", "bus2")))
        assertNull(VehicleTrackingWire.position(payload().replace("\"controlRevision\":1", "\"controlRevision\":0").toByteArray(), topic))
    }
    @Test fun mqttOcupacionV3SoloPerteneceAEscenarioSimulado() {
        val topic = "rutautp/vehiculos/sim:$run:bus1/posicion"
        assertEquals("full", VehicleTrackingWire.position(payload(3).toByteArray(), topic)?.scenarioOccupancy)
        assertNull(VehicleTrackingWire.position(payload(3, "observed").toByteArray(), topic))
    }
    private fun control(revision: Long = 1, enabled: Boolean = true, at: Double = 1000.0) = SimulationControlState(1, enabled, run, revision, at, if (enabled) at + 15 else at, at + 1000, "America/Lima")
    private fun sim() = VehiclePosition("sim:$run:bus1", "A", "ruta-1", -8.1, -79.0, 90.0, 8.0, 1000.0, VehicleDataOrigin.SIMULATED, run, 1)
    @Test fun simulacionRequiereControlYVencePorRelojMonotono() {
        val gate = SimulationControlGate()
        assertFalse(gate.permits(sim(), 1000.0, 10.0))
        assertTrue(gate.receive(control(), 1000.0, 10.0))
        assertTrue(gate.permits(sim(), 1001.0, 11.0))
        assertFalse(gate.permits(sim(), 1015.0, 25.0))
    }
    @Test fun reinicioNoRestauraPermisoYRevocacionNoResucita() {
        val gate = SimulationControlGate(); gate.receive(control(), 1000.0, 10.0)
        val restaurada = SimulationControlGate(gate.checkpoint)
        assertFalse(restaurada.permits(sim(), 1001.0, 11.0))
        assertTrue(gate.receive(control(2, false, 1002.0), 1002.0, 12.0))
        assertFalse(gate.receive(control(), 1003.0, 13.0))
        assertFalse(gate.permits(sim(), 1003.0, 13.0))
    }
    @Test fun cambioDeRelojInvalidaSoloLaSimulacion() {
        val gate = SimulationControlGate(); gate.receive(control(), 1000.0, 10.0)
        assertFalse(gate.checkTime(999.0, 11.0))
        assertFalse(gate.permits(sim(), 1000.0, 12.0))
        assertTrue(gate.permits(sim().copy(dataOrigin = VehicleDataOrigin.OBSERVED), 1000.0, 12.0))
    }
    @Test fun alternativasSeparanLaSubidaCercanaDeLaMasRapida() = kotlinx.coroutines.runBlocking {
        fun linea(id: String, lat: Double, headway: Int) = ruta().copy(id = id, headwayMin = headway,
            shape = listOf(LatLng(lat, -79.0), LatLng(lat, -78.99)),
            paraderos = listOf(ParaderoGTFS("$id-p1", "Subida", lat, -79.0), ParaderoGTFS("$id-p2", "Bajada", lat, -78.99)))
        val cercana = linea("cercana", -8.1, 30)
        val rapida = linea("rapida", -8.099, 1)
        val opciones = com.example.rutautpnative.data.routing.TransitPlanner.opciones(LatLng(-8.1, -79.0), LatLng(-8.1, -78.99), listOf(cercana, rapida), 300.0)
        assertEquals("cercana", opciones.destacadas.first { com.example.rutautpnative.data.routing.TransitPlanner.Categoria.RECOMENDADA in it.categorias }.plan.ruta.id)
        assertEquals("rapida", opciones.destacadas.first { com.example.rutautpnative.data.routing.TransitPlanner.Categoria.MAS_RAPIDA in it.categorias }.plan.ruta.id)
        assertEquals(2, opciones.alternativas.size)
    }

}
