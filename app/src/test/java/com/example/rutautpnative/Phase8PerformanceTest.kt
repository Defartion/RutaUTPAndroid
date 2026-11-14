package com.example.rutautpnative

import androidx.compose.ui.graphics.Color
import com.example.rutautpnative.data.geo.PolylineMatching
import com.example.rutautpnative.data.gtfs.*
import com.example.rutautpnative.data.tracking.*
import com.example.rutautpnative.reference.PolylineMatchingAnterior
import com.example.rutautpnative.reference.VehicleETAAnterior
import com.google.android.gms.maps.model.LatLng
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.*
import kotlin.random.Random
import kotlin.system.measureNanoTime

class Phase8PerformanceTest {
    companion object {
        private val assets get() = File(requireNotNull(System.getProperty("rutautp.assets")))
        private fun leerFeed() = GTFSRepository.parsearFuente { File(assets, "gtfs/" + it + ".txt").readText() }
        private val catalogo by lazy { leerFeed() }
        private val utilizables get() = catalogo.filter { it.shape.size >= 2 }
        private val ancla = GTFSRepository.coordenadaUTP
    }
    private fun ruta(id: String = "prueba", shape: List<LatLng> = listOf(LatLng(-8.1,-79.04), LatLng(-8.08,-79.04))) =
        RutaGTFS(id,"C-01","B","Recorrido","Empresa","00A850",Color.Green,shape,emptyList(),20,5,2.5,2.0,0.0)
    private fun posicion(ruta: RutaGTFS, indice: Int = 0, speed: Double = 7.0, heading: Double? = null): VehiclePosition {
        val punto = ruta.shape[indice]
        val segmento = indice.coerceAtMost(ruta.shape.size - 2)
        return VehiclePosition("unidad-" + indice, ruta.linea, ruta.id, punto.latitude, punto.longitude,
            heading ?: PolylineMatching.headingDegrees(ruta.shape[segmento],ruta.shape[segmento + 1]), speed, 1_800_000_000.0,
            VehicleDataOrigin.OBSERVED)
    }
    @Test fun etaCoincideConElCodigoAnteriorEnTodasLasRutasYSentidos() {
        val cache = VehicleETACache().apply { instalar(catalogo.associateBy { it.id }) }
        var estimaciones = 0
        for (r in utilizables) {
            for (indice in listOf(0,r.shape.lastIndex/2)) {
                val base = posicion(r,indice)
                for (heading in listOf(base.heading, (base.heading+180)%360, -1.0)) {
                    for (speed in listOf(-1.0,0.0,0.49,0.5,7.0,Double.NaN)) {
                        val p = base.copy(heading=heading,speed=speed)
                        for (target in listOf(ancla,r.shape.last())) {
                            val expected = VehicleETAAnterior.minutos(p,r,target)
                            assertEquals(r.id, expected, cache.minutos(p,target))
                            if (expected != null) estimaciones++
                        }
                    }
                }
            }
        }
        assertTrue(estimaciones > 0)
    }
    @Test fun laGeometriaYElObjetivoSePreparanUnaVezPorRuta() {
        val r=ruta();val cache=VehicleETACache().apply { instalar(mapOf(r.id to r)) }
        repeat(1000) { cache.minutos(posicion(r,speed=it%15+.5),r.shape.last()) }
        assertEquals(1,cache.preparacionesRuta);assertEquals(1,cache.proyeccionesObjetivo)
        assertEquals(1,cache.rutasMemorizadas);assertEquals(1,cache.objetivosMemorizados)
    }
    @Test fun unObjetivoFueraDeRutaTambienQuedaMemorizado() {
        val r=ruta();val cache=VehicleETACache().apply { instalar(mapOf(r.id to r)) }
        repeat(100) { assertNull(cache.minutos(posicion(r),LatLng(0.0,0.0))) }
        assertEquals(1,cache.proyeccionesObjetivo)
    }
    @Test fun unCambioMinimoDeCoordenadaInvalidaSoloElObjetivo() {
        val r=ruta();val cache=VehicleETACache().apply { instalar(mapOf(r.id to r)) }
        val a=r.shape.last();val b=LatLng(Math.nextUp(a.latitude),a.longitude)
        cache.minutos(posicion(r),a);cache.minutos(posicion(r),b)
        assertEquals(1,cache.preparacionesRuta);assertEquals(2,cache.proyeccionesObjetivo)
    }
    @Test fun unCatalogoNuevoConElMismoIdNoHeredaGeometriaNiVelocidad() {
        val a=ruta();val b=a.copy(shape=listOf(LatLng(-8.1,-79.04),LatLng(-8.0,-79.04)),duracionMin=100)
        val cache=VehicleETACache().apply { instalar(mapOf(a.id to a)) }
        cache.minutos(posicion(a),a.shape.last())
        cache.instalar(mapOf(b.id to b))
        assertEquals(VehicleETAAnterior.minutos(posicion(b,speed=-1.0),b,b.shape.last()),cache.minutos(posicion(b,speed=-1.0),b.shape.last()))
        assertEquals(1,cache.preparacionesRuta)
    }
    @Test fun reinstalarLaMismaReferenciaSigueSiendoUnaRevisionNueva() {
        val r=ruta();val map=mapOf(r.id to r);val cache=VehicleETACache()
        cache.instalar(map);cache.minutos(posicion(r),r.shape.last());cache.instalar(map)
        assertEquals(0,cache.rutasMemorizadas);assertEquals(0,cache.objetivosMemorizados)
    }
    @Test fun cambiarMuchasVecesDeDestinoNoAcumulaAnclasAntiguas() {
        val r=ruta();val cache=VehicleETACache().apply { instalar(mapOf(r.id to r)) }
        repeat(1000) { cache.minutos(posicion(r),LatLng(-8.08+it*1e-8,-79.04)) }
        assertEquals(1,cache.rutasMemorizadas);assertEquals(1,cache.objetivosMemorizados)
        cache.instalar(emptyMap());assertEquals(0,cache.rutasMemorizadas);assertNull(cache.minutos(posicion(r),ancla))
    }
    @Test fun rutaInvalidaORutaDesconocidaNoGeneraPreparacionesRepetidas() {
        val r=ruta(shape=emptyList());val cache=VehicleETACache().apply { instalar(mapOf(r.id to r)) }
        val p=posicion(ruta())
        repeat(100) { assertNull(cache.minutos(p,ancla));assertNull(cache.minutos(p.copy(routeId="utp:otro"),ancla)) }
        assertEquals(1,cache.preparacionesRuta);assertEquals(0,cache.proyeccionesObjetivo)
    }
    @Test fun laGeometriaPreparadaNoCambiaAlMutarLaListaDeEntrada() {
        val shape=mutableListOf(LatLng(-8.1,-79.04),LatLng(-8.08,-79.04));val r=ruta(shape=shape)
        val cache=VehicleETACache().apply { instalar(mapOf(r.id to r)) };val p=posicion(r);val objetivo=shape.last()
        val before=cache.minutos(p,objetivo);shape[1]=LatLng(0.0,0.0)
        assertEquals(before,cache.minutos(p,objetivo))
    }
    @Test fun laProyeccionSinArraysAuxiliaresConservaDistanciaSegmentoYFraccion() {
        val random=Random(824)
        val shapes=utilizables.map { it.shape } + List(40) {
            List(24) { LatLng(-8.2+random.nextDouble()*.2,-79.1+random.nextDouble()*.2) }
        } + listOf(listOf(LatLng(-8.1,-79.0),LatLng(-8.1,-79.0),LatLng(-8.08,-79.0)))
        for (shape in shapes) for (p in listOf(ancla,shape.first(),shape.last(),LatLng(-8.3,-78.9))) {
            val old=PolylineMatchingAnterior.proyectarEnShape(p,shape)!!;val new=PolylineMatching.proyectarEnShape(p,shape)!!
            assertEquals(old.segmento,new.segmento);assertEquals(old.distanciaM,new.distanciaM,0.0);assertEquals(old.fraccion,new.fraccion,0.0)
            assertEquals(PolylineMatchingAnterior.totalLengthMeters(shape),PolylineMatching.totalLengthMeters(shape),0.0)
        }
    }
    private fun comprobarCercanas(rutas: List<RutaGTFS>, punto: LatLng, radio: Double) {
        val expected=GTFSRepository.seleccionarCercanas(rutas,punto,radio)
        val index=GTFSNearbyRouteIndex(rutas)
        val actual=GTFSRepository.seleccionarCercanas(index.candidatas(punto,radio),punto,radio)
        assertEquals(expected.size,actual.size)
        expected.indices.forEach { assertSame(expected[it],actual[it]) }
    }
    @Test fun elPrefiltroConservaResultadosOrdenYDuplicadosDelCatalogoReal() {
        val queries=utilizables.flatMap { listOf(it.shape.first(),it.shape.last()) }+listOf(ancla,LatLng(-8.3,-79.3))
        for (q in queries) for (radius in listOf(0.0,400.0,800.0,1600.0))
            comprobarCercanas(catalogo+catalogo.take(2),q,radius)
    }
    @Test fun limitesDelRadioYSegmentosLargosNoEliminanCoincidencias() {
        val random=Random(812);val rutas=List(60) { i ->
            ruta("r"+i,List(4) { LatLng(-8.2+random.nextDouble()*.2,-79.1+random.nextDouble()*.2) })
        }
        repeat(80) {
            val p=LatLng(-8.2+random.nextDouble()*.2,-79.1+random.nextDouble()*.2)
            for (radius in listOf(0.0,400.0,800.0,1600.0)) comprobarCercanas(rutas,p,radius)
        }
        val punto=LatLng(-8.1,-79.0);val r=ruta(shape=listOf(punto))
        val q=LatLng(-8.1+400.0/111_320.0,-79.0)
        val radius=GTFSRepository.distanciaMetros(punto,q)
        comprobarCercanas(listOf(r),q,radius);comprobarCercanas(listOf(r),q,Math.nextDown(radius))
    }
    @Test fun polosAntimeridianoYDatosInvalidosSeConservanComoCandidatos() {
        val especiales=listOf(ruta("polo",listOf(LatLng(85.0,0.0))),
            ruta("cruce",listOf(LatLng(0.0,179.9),LatLng(0.0,-179.9))),
            ruta("vacia",emptyList()),ruta("nan",listOf(LatLng(Double.NaN,0.0))))
        val index=GTFSNearbyRouteIndex(especiales)
        assertEquals(especiales,index.candidatas(ancla,400.0))
        for (q in listOf(LatLng(85.0,0.0),LatLng(0.0,179.999),LatLng(Double.NaN,0.0)))
            assertEquals(especiales,index.candidatas(q,400.0))
        for (r in listOf(-1.0,Double.NaN,Double.POSITIVE_INFINITY))
            assertEquals(especiales,index.candidatas(ancla,r))
    }
    @Test fun elPrefiltroReduceCandidatasEnUnaConsultaLejana() {
        val routes=utilizables
        assertTrue(GTFSNearbyRouteIndex(routes).candidatas(LatLng(0.0,0.0),400.0).size < routes.size)
    }

    @Test fun medirCargaDestinoEInstantaneasConDatosRealesSinRed() {
        val routes=utilizables;val map=routes.associateBy { it.id }
        val fleet=List(1000) { i -> val r=routes[i%routes.size];posicion(r,(i*17)%r.shape.lastIndex) }
        val queries=(routes.flatMap { listOf(it.shape.first(),it.shape.last()) }).filterIndexed { i, _ -> i%5==0 }.take(32)
        val index=GTFSNearbyRouteIndex(catalogo)
        val cache=VehicleETACache().apply { instalar(map) }
        val expected=fleet.map { VehicleETAAnterior.minutos(it,map.getValue(it.routeId),ancla) }
        assertEquals(expected,fleet.map { cache.minutos(it,ancla) })
        assertEquals(map.size,cache.preparacionesRuta);assertEquals(map.size,cache.proyeccionesObjetivo)
        fun measure(block: () -> Unit): List<Long> {
            repeat(2) { block() }
            return List(5) { measureNanoTime { block() } }
        }
        val old=measure { assertEquals(expected,fleet.map { VehicleETAAnterior.minutos(it,map.getValue(it.routeId),ancla) }) }
        val cold=measure {
            val fresh=VehicleETACache().apply { instalar(map) }
            assertEquals(expected,fleet.map { fresh.minutos(it,ancla) })
        }
        val warm=measure { assertEquals(expected,fleet.map { cache.minutos(it,ancla) }) }
        val newTarget=LatLng(ancla.latitude+.001,ancla.longitude+.001)
        val expectedTarget=fleet.map { VehicleETAAnterior.minutos(it,map.getValue(it.routeId),newTarget) }
        val target=measure {
            cache.minutos(fleet.first(),ancla)
            assertEquals(expectedTarget,fleet.map { cache.minutos(it,newTarget) })
        }
        val before=queries.map { GTFSRepository.seleccionarCercanas(catalogo,it,400.0) }
        val scan=measure { assertEquals(before,queries.map { GTFSRepository.seleccionarCercanas(catalogo,it,400.0) }) }
        val filtered=measure { assertEquals(before,queries.map { GTFSRepository.seleccionarCercanas(index.candidatas(it,400.0),it,400.0) }) }
        val parse=measure { assertEquals(catalogo.size,leerFeed().size) }
        val candidatesAtAnchor=index.candidatas(ancla,400.0).size
        val buildIndex=measure { assertEquals(candidatesAtAnchor,GTFSNearbyRouteIndex(catalogo).candidatas(ancla,400.0).size) }
        fun tiempos(values: List<Long>) = buildJsonObject {
            val sorted=values.sorted();put("medianMs",sorted[sorted.size/2]/1e6);put("p95Ms",sorted.last()/1e6)
        }
        val root=requireNotNull(File(requireNotNull(System.getProperty("rutautp.parity"))).parentFile)
        val contract=Json.parseToJsonElement(File(root,"parity/performance-contract.json").readText()).jsonObject
        val report=buildJsonObject {
            put("schemaVersion",1);put("environment","host-jvm");put("os",System.getProperty("os.name"));put("java",System.getProperty("java.version"))
            put("baselineCommit","08ba24a96017cc135511c689652977cde80a39f7");put("equivalentResults",true)
            put("androidDeviceMeasured",false);put("networkUsed",false);put("routes",catalogo.size);put("shapePoints",catalogo.sumOf { it.shape.size })
            put("vehicles",fleet.size);put("samples",5);put("nearbyQueries",queries.size)
            put("geometryPreparationsWarm",map.size);put("targetProjectionsWarm",map.size)
            put("nearbyCandidates",queries.sumOf { index.candidatas(it,400.0).size });put("nearbyFullCandidates",queries.size*catalogo.size)
            put("parseCatalog",tiempos(parse));put("buildNearbyIndex",tiempos(buildIndex))
            put("snapshotBefore",tiempos(old));put("snapshotCold",tiempos(cold));put("snapshotWarm",tiempos(warm));put("targetChange",tiempos(target))
            put("nearbyBefore",tiempos(scan));put("nearbyIndexed",tiempos(filtered))
            put("sourceHashes",buildJsonObject {
                contract.getValue("sourcePaths").jsonArray.forEach { path ->
                    val relative=path.jsonPrimitive.content
                    val bytes=File(root,relative).readText().replace("\r\n","\n").toByteArray(Charsets.UTF_8)
                    val digest=java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                    put(relative,digest.joinToString("") { "%02x".format(it) })
                }
            })
        }
        val file=File(root,"app/build/reports/parity/performance-check.json")
        file.parentFile?.mkdirs();file.writeText(report.toString())
    }
}
