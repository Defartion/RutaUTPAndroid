package com.example.rutautpnative

import com.example.rutautpnative.data.places.*
import com.example.rutautpnative.data.google.*
import com.example.rutautpnative.data.gtfs.GTFSCSV
import com.example.rutautpnative.model.CatalogoSedesTrabajo
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Phase4GoogleTest {
    private val assets get() = File(requireNotNull(System.getProperty("rutautp.assets")))
    private fun sedes(): List<Pair<Double,Double>> = CatalogoSedesTrabajo.todas.map { it.lat to it.lon } +
        Json.parseToJsonElement(File(assets,"sedes-empresas-trujillo.json").readText()).jsonArray.map { it.jsonObject.let { s -> s.getValue("lat").jsonPrimitive.double to s.getValue("lon").jsonPrimitive.double } }
    @Test fun laCoberturaIncluyeTodosLosParaderosYSedesDelCatalogo() {
        val texto=File(assets,"gtfs/stops.txt").readText()
        val t=GTFSCSV.parsear(texto); val a=CoberturaBusqueda.desdeGTFS(texto,sedes())
        assertEquals(4067,t.rowCount)
        for (i in 0 until t.rowCount) assertTrue("paradero $i",a.contiene(t.valor(i,"stop_lat").toDouble(),t.valor(i,"stop_lon").toDouble()))
        sedes().forEach { (lat,lon) -> assertTrue(a.contiene(lat,lon)) }
        assertTrue(a.sur < -8.2240322); assertTrue(a.norte > -7.9920813)
        assertTrue(a.oeste < -79.1232817); assertTrue(a.este > -78.946644)
    }
    @Test fun lasReferenciasDeLosCuatroExtremosYaNoSeExcluyen() {
        val a=CoberturaBusqueda.desdeGTFS(File(assets,"gtfs/stops.txt").readText(),sedes())
        listOf(-8.2240322 to -79.0,-7.9920813 to -79.0,-8.1 to -79.1232817,-8.1 to -78.946644).forEach { assertTrue(a.contiene(it.first,it.second)) }
        assertFalse(a.contiene(-12.04,-77.03)); assertFalse(a.contiene(Double.NaN,-79.0))
    }
    @Test fun unaSedeNuevaAmpliaLaCoberturaSinEditarElRectangulo() {
        val a=CoberturaBusqueda.desde(listOf(-8.1 to -79.0,-8.3 to -79.2))
        assertTrue(a.contiene(-8.3,-79.2)); assertFalse(a.contiene(-9.0,-79.2))
    }
    @Test fun coordenadasNoFinitasOImposiblesNoGeneranUnaConsultaGlobal() {
        assertThrows(IllegalArgumentException::class.java) { CoberturaBusqueda.desde(listOf(Double.NaN to 0.0)) }
        assertThrows(IllegalArgumentException::class.java) { CoberturaBusqueda.desde(listOf(91.0 to 0.0)) }
        assertThrows(IllegalArgumentException::class.java) { CoberturaBusqueda.desde(emptyList()) }
    }
    @Test fun textoDeUsuarioSeSerializaSinInyectarJSON() {
        val query="Mall \"A\"\n, Trujillo".replace("\\n","\n")
        val a=CoberturaBusqueda.desde(listOf(-8.1 to -79.0))
        val body=Json.parseToJsonElement(PlacesService.solicitud(query,a)).jsonObject
        assertEquals(query,body.getValue("textQuery").jsonPrimitive.content)
        val rect=body.getValue("locationRestriction").jsonObject.getValue("rectangle").jsonObject
        assertEquals(a.sur,rect.getValue("low").jsonObject.getValue("latitude").jsonPrimitive.double,0.0)
        assertEquals(a.este,rect.getValue("high").jsonObject.getValue("longitude").jsonPrimitive.double,0.0)
    }
    @Test fun resultadosFueraDeLaCoberturaNoSePresentanComoDestinosLocales() {
        val a=CoberturaBusqueda.desde(listOf(-8.1 to -79.0))
        val raw="""{"places":[{"displayName":{"text":"Trujillo"},"location":{"latitude":-8.1,"longitude":-79.0}},{"displayName":{"text":"Ajeno"},"location":{"latitude":-12.0,"longitude":-77.0}}]}"""
        assertEquals(listOf("Trujillo"),PlacesService.leerLugares(raw,a).map { it.nombre })
    }
    @Test fun faltaDeClaveSeDistingueDeUnaBusquedaSinResultadosSinAbrirRed() = kotlinx.coroutines.runBlocking {
        assertSame(PlacesService.Resultado.SinConfiguracion,PlacesService.buscarTexto("Trujillo"))
        assertSame(PlacesService.ResultadoSugerencias.SinConfiguracion,PlacesService.buscarSugerencias("Trujillo"))
        assertSame(com.example.rutautpnative.data.directions.DirectionsService.Resultado.SinConfiguracion,
            com.example.rutautpnative.data.directions.DirectionsService.rutaPeatonal(com.google.android.gms.maps.model.LatLng(-8.1,-79.0),com.google.android.gms.maps.model.LatLng(-8.11,-79.01)))
    }
    @Test fun unServicioPeatonalSinConfigConservaElPlanComoAproximado() = kotlinx.coroutines.runBlocking {
        val a=com.google.android.gms.maps.model.LatLng(-8.1,-79.0);val b=com.google.android.gms.maps.model.LatLng(-8.1,-78.98)
        val ruta=com.example.rutautpnative.data.gtfs.RutaGTFS("r","10","","Centro","Empresa","00AA00",androidx.compose.ui.graphics.Color.Green,
            listOf(a,b),listOf(com.example.rutautpnative.data.gtfs.ParaderoGTFS("a","A",a.latitude,a.longitude),com.example.rutautpnative.data.gtfs.ParaderoGTFS("b","B",b.latitude,b.longitude)),5,10,2.0,2.2,0.0)
        val plan=com.example.rutautpnative.data.routing.TransitPlanner.plan(
            com.google.android.gms.maps.model.LatLng(-8.1005,-79.0),
            com.google.android.gms.maps.model.LatLng(-8.1005,-78.98),listOf(ruta),1600.0,
            com.example.rutautpnative.data.routing.TransitPlanner.CaminatasReales { origen, destino ->
                com.example.rutautpnative.data.routing.TransitPlanner.caminataReal(origen, destino)
            })
        assertNotNull(plan);assertTrue(plan!!.caminataAproximada)
    }
    @Test fun solicitudesSeIdentificanConElPaqueteYHuellaSinSeparadores() {
        val fingerprint=ServiciosGoogle.huellaSha1("abc".toByteArray())
        assertEquals("A9993E364706816ABA3E25717850C26C9CD0D89D",fingerprint)
        val headers=IdentidadAndroid("com.example.rutautpnative",fingerprint).cabeceras()
        assertEquals(fingerprint,headers["X-Android-Cert"]); assertEquals("com.example.rutautpnative",headers["X-Android-Package"])
        assertThrows(IllegalArgumentException::class.java) { IdentidadAndroid("app","AA:BB") }
    }
}
