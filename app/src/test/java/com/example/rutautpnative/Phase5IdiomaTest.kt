package com.example.rutautpnative

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.rutautpnative.ui.idioma.*
import com.example.rutautpnative.navigation.*
import com.example.rutautpnative.data.places.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class Phase5IdiomaTest {
    private val assets get() = File(requireNotNull(System.getProperty("rutautp.assets")))
    private fun catalogo() = CatalogoIdiomas(File(assets,"traducciones.json").readText(), File(assets,"traducciones-android.json").readText())

    @Test fun erroresAndroidSeResuelvenEnLosCuatroIdiomasAdicionales() {
        val c=catalogo()
        val frases=listOf("Acceso a la cámara denegado","Conexión de transporte","No se pudo cargar el catálogo de rutas. Vuelve a intentarlo.")
        for (codigo in listOf("fr","pt-BR","de","zh-Hans")) for (es in frases) {
            val texto=c.texto(es,"EN_AUSENTE",codigo)
            assertNotEquals(es,texto);assertNotEquals("EN_AUSENTE",texto);assertTrue(texto.isNotBlank())
        }
    }
    @Test fun mantieneElTextoEspanolEInglesYReutilizaLaClaveCompartida() {
        val c=catalogo()
        assertEquals("Cerrar",c.texto("Cerrar","Close","es"))
        assertEquals("Close",c.texto("Cerrar","Close","en"))
        assertEquals(c.texto("Elegir de Contactos","Choose from contacts","fr"),c.texto("Elegir de contactos","Choose from contacts","fr"))
    }
    @Test fun plantillaDeDistanciaConservaValoresEnLosSeisIdiomas() {
        val c=catalogo()
        for (codigo in listOf("es","en","fr","pt-BR","de","zh-Hans")) {
            val valor=CatalogoIdiomas.formatear(c.texto("Sube en {0} · a {1} m","Board at {0} · {1} m away",codigo),listOf("Av. España","123"))
            assertTrue(valor.contains("Av. España"));assertTrue(valor.contains("123"));assertFalse(valor.contains("{0}"))
        }
    }
    @Test fun losParametrosSonLiteralesYNoSeInterpretanComoOtraPlantilla() {
        assertEquals("B · $5\\{1} · B",CatalogoIdiomas.formatear("{1} · {0} · {1}",listOf("$5\\{1}","B")))
    }
    @Test fun catalogoInesperadoUsaElRespaldoInglesSinInventarTraduccion() {
        assertEquals("Fallback",catalogo().texto("Nueva clave","Fallback","fr"))
    }
    @Test fun persistenciaAntiguaNoRevierteUnaSeleccionRapida() {
        val s=SeleccionIdioma("es")
        s.seleccionar("fr");s.seleccionar("de");s.recibir("es");assertEquals("de",s.codigo)
        s.recibir("fr");assertEquals("de",s.codigo)
        s.recibir("de");assertEquals("de",s.codigo)
        s.recibir("en");assertEquals("en",s.codigo)
    }
    @Test fun cambiarIdiomaConservaPantallaYModelosDeViaje() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val anterior=L.codigo
        val modelos=ModelosCatalogo();val padre=ViewModelStore().apply { put("modelos",modelos) }
        val router=AppRouter();router.navigate(AppScreen.Rutas)
        try {
            val factory=object:ViewModelProvider.Factory {}
            val owner=modelos.obtener(false,factory)
            for (codigo in listOf("fr","zh-Hans","de","en")) {
                L.establecer(codigo)
                assertSame(owner,modelos.obtener(false,factory));assertSame(AppScreen.Rutas,router.currentScreen)
            }
        } finally { L.establecer(anterior);padre.clear();Dispatchers.resetMain() }
    }
    @Test fun laSolicitudDeLugaresUsaElIdiomaElegidoSinAbrirRed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler));val anterior=L.codigo
        try {
            val area=CoberturaBusqueda.desde(listOf(-8.1 to -79.0))
            for ((codigo,google) in listOf("fr" to "fr","pt-BR" to "pt-BR","zh-Hans" to "zh-CN")) {
                L.establecer(codigo)
                assertEquals(google,Json.parseToJsonElement(PlacesService.solicitud("Centro",area)).jsonObject.getValue("languageCode").jsonPrimitive.content)
            }
        } finally { L.establecer(anterior);Dispatchers.resetMain() }
    }
}
