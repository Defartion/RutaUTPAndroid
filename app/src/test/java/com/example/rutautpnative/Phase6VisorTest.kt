package com.example.rutautpnative

import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.ui.screens.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import java.nio.FloatBuffer
import java.util.zip.GZIPInputStream
import kotlin.math.*

class Phase6VisorTest {
    private val assets get() = File(requireNotNull(System.getProperty("rutautp.assets")))
    private val rutas by lazy { GTFSRepository.parsearFuente { File(assets,"gtfs/$it.txt").readText() } }
    private fun mesh(file: File): FloatBuffer = DataInputStream(GZIPInputStream(file.inputStream())).use {
        assertEquals(0x52555433,it.readInt());val n=it.readInt()
        FloatBuffer.wrap(FloatArray(n*10) { _ -> it.readFloat() })
    }
    @Test fun laCamaraInicialMiraAlParabrisasDesdeElLadoDelAcompanante() {
        val c=CamaraTransporte3D();val v=c.vista()
        assertEquals(36f,c.campoVisual,0f);assertTrue(v.ojo[0]<0);assertTrue(v.ojo[1]>0);assertTrue(v.ojo[2]>0)
        assertArrayEquals(floatArrayOf(0f,0f,0f),v.objetivo,0f)
    }
    @Test fun lasSeisGeometriasCabenEnElEncuadreCuadradoAltoYAncho() {
        val files=File(assets,"transportes3d").listFiles()!!.filter { it.name.endsWith(".mesh.gz") }
        assertEquals(6,files.size)
        for (file in files) {
            val vertices=mesh(file);val cantidad=vertices.limit()/10
            val radio=radioModelo3D(vertices,cantidad)
            assertTrue(radio.isFinite() && radio > 0f)
            for ((ancho,alto) in listOf(340 to 340,320 to 800,800 to 200)) {
                val c=CamaraTransporte3D().apply { configurarRadio(radio);redimensionar(ancho,alto) };val v=c.vista()
                val forward=FloatArray(3) { -v.ojo[it]/v.distancia }
                val h=sqrt(forward[0]*forward[0]+forward[2]*forward[2])
                val right=floatArrayOf(-forward[2]/h,0f,forward[0]/h)
                val up=floatArrayOf(-right[2]*forward[1],right[2]*forward[0]-right[0]*forward[2],right[0]*forward[1])
                val t=tan(PI/10);val aspecto=ancho.toDouble()/alto
                repeat(cantidad) { i ->
                    val p=FloatArray(3) { vertices.get(i*10+it)-v.ojo[it] }
                    val z=(0..2).sumOf { (p[it]*forward[it]).toDouble() }
                    val x=(0..2).sumOf { (p[it]*right[it]).toDouble() } / (z*t*aspecto)
                    val y=(0..2).sumOf { (p[it]*up[it]).toDouble() } / (z*t)
                    assertTrue(file.name+" "+ancho+" x "+alto+": "+x+","+y,z>0 && abs(x)<=1.0 && abs(y)<=1.0)
                }
            }
        }
    }
    @Test fun radioNoModificaLaPosicionDelBufferDeOpenGL() {
        val b=mesh(File(assets,"transportes3d/original_micro.mesh.gz"));b.position(10)
        radioModelo3D(b,b.limit()/10);assertEquals(10,b.position())
    }
    @Test fun zoomTieneLimitesRelativosYRestablecerRecuperaElEncuadre() {
        val c=CamaraTransporte3D().apply { configurarRadio(2f);redimensionar(340,340) }
        val inicial=c.vista().distancia
        c.ampliar(.00001f);assertEquals(2.5f,c.vista().distancia,.0001f)
        c.ampliar(100000f);assertEquals(inicial*2.5f,c.vista().distancia,.0001f)
        c.restablecer();assertEquals(inicial,c.vista().distancia,.0001f)
    }
    @Test fun desplazamientoConservaLaOrbitaYNoSaleDelLimite() {
        val c=CamaraTransporte3D().apply { configurarRadio(2f) };val a=c.vista()
        c.mover(.15f,.15f);val b=c.vista()
        assertTrue(b.objetivo.any { it!=0f })
        for(i in 0..2) assertEquals(a.ojo[i],b.ojo[i]-b.objetivo[i],.0001f)
        c.mover(100000f,-100000f);assertTrue(c.vista().objetivo.all { abs(it)<=4f })
    }
    @Test fun elGestoDeUnDedoGiraYElDeDosDesplaza() {
        val c=CamaraTransporte3D();val antes=c.guardar()
        c.arrastrarUnDedo(20f,10f);val giro=c.guardar()
        assertTrue(giro[0]<antes[0]);assertTrue(giro[1]>antes[1])
        c.arrastrarDosDedos(20f,10f,340);val desplazado=c.guardar()
        assertEquals(giro.take(3),desplazado.take(3));assertNotEquals(giro.drop(3),desplazado.drop(3))
    }
    @Test fun guardarYRestaurarConservaLaVistaTrasRecrearLaSuperficie() {
        val a=CamaraTransporte3D().apply { configurarRadio(2f);redimensionar(320,800);girar(.2f);ampliar(.7f);mover(4f,4f) }
        val b=CamaraTransporte3D().apply { restaurar(a.guardar());configurarRadio(2f);redimensionar(320,800) }
        assertArrayEquals(a.vista().ojo,b.vista().ojo,.0001f);assertArrayEquals(a.vista().objetivo,b.vista().objetivo,.0001f)
    }
    @Test fun gestosYRestauracionInvalidosNoContaminanLaCamara() {
        val c=CamaraTransporte3D();val antes=c.guardar()
        c.ampliar(Float.NaN);c.ampliar(-1f);c.girar(Float.NaN);c.mover(Float.POSITIVE_INFINITY,0f)
        c.restaurar(listOf(Float.NaN));assertEquals(antes,c.guardar())
        assertThrows(IllegalArgumentException::class.java) { radioModelo3D(FloatBuffer.wrap(FloatArray(30) { Float.NaN }),3) }
    }
    @Test fun elFeedInstitucionalYUnaCategoriaDesconocidaNoHeredanUnMicroUrbano() {
        val r=rutas.first()
        assertNull(tipoModeloRuta(r.copy(id="utp:"+r.id)))
        assertNull(tipoModeloRuta(r.copy(linea="DESCONOCIDA",variante="")))
    }
    @Test fun laExcepcionDeMiramarSoloSeAplicaALaIdentidadVerificada() {
        val r=rutas.first().copy(id="17522705",linea="C-44",variante="T2",empresa="Miramar")
        assertEquals("micro",tipoModeloRuta(r));assertEquals("combi",tipoModeloRuta(r.copy(empresa="Otra")))
    }
    @Test fun referenciasNoCoincidentesNoPrestanModeloNiColores() {
        val r=rutas.first().copy(linea="C-01",variante="B")
        val ref=ReferenciaRuta("otro",r.codigoTransporte,r.letraTransporte,r.empresa,listOf(ColorReferencia("#FF0000","Rojo","Red")),modelo="micro")
        assertEquals("combi",tipoModeloRuta(r,ref));assertTrue(identidadModelo(r,ref)!!.colores.isEmpty())
    }
    @Test fun lasReferenciasDelCatalogoConservanSuCategoriaVerificada() {
        val refs=Json.decodeFromString<List<ReferenciaRuta>>(File(assets,"referencias_transporte.json").readText())
        assertEquals(102,refs.size)
        refs.forEach { ref ->
            val r=rutas.first { it.id==ref.routeId }
            if(ref.modelo!=null) assertEquals(ref.routeId,ref.modelo,tipoModeloRuta(r,ref))
            assertEquals(ref.colores.size,identidadModelo(r,ref)!!.colores.size)
        }
    }
    @Test fun C2VRConservaLaIdentidadPeroDibujaVRSoloComoDistintivoVerificado() {
        val i=IdentidadModelo3D("M-15","C2VR",emptyList(),"17554634","El Cortijo")
        assertEquals("C2VR",i.letra);assertEquals("C2",i.letraPrincipal);assertEquals("VR",i.distintivoLetra)
        assertNull(i.copy(routeId="otra").distintivoLetra);assertEquals("C2VR",i.copy(empresa="Otra").letraPrincipal)
    }
}
