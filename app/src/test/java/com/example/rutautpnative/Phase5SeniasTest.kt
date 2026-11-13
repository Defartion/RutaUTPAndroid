package com.example.rutautpnative

import com.example.rutautpnative.data.senias.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Phase5SeniasTest {
    private fun clip(clave:String)=EstadoSenia.Clip("asset:///es/$clave.mp4",clave)
    @Test fun cerrarAdelantaLaAccionSoloUnaVez() = runTest {
        var veces=0;val p=PresentadorSenias(backgroundScope,{true},{clip(it)})
        p.ejecutarTrasVerSenia("A") { veces++ };runCurrent()
        p.cerrarPorUsuario();p.cerrarPorUsuario();advanceTimeBy(4000);runCurrent()
        assertEquals(1,veces);assertNull(p.presentacion.value)
    }
    @Test fun elTemporizadorDeTresSegundosEjecutaUnaSolaAccion() = runTest {
        var veces=0;val p=PresentadorSenias(backgroundScope,{true},{clip(it)})
        p.ejecutarTrasVerSenia("A") { veces++ };runCurrent()
        advanceTimeBy(2999);runCurrent();assertEquals(0,veces)
        advanceTimeBy(1);runCurrent();assertEquals(1,veces)
    }
    @Test fun salirOSegundoPlanoCancelaSinEjecutar() = runTest {
        var veces=0;val p=PresentadorSenias(backgroundScope,{true},{clip(it)})
        p.ejecutarTrasVerSenia("A") { veces++ };runCurrent();p.cancelarPorNavegacion()
        advanceTimeBy(4000);runCurrent();assertEquals(0,veces)
    }
    @Test fun resolverUnaSeniaAntiguaNoHabilitaLaAccionDeOtra() = runTest {
        val antigua=CompletableDeferred<EstadoSenia>();var a=0;var b=0
        val p=PresentadorSenias(backgroundScope,{true},{ if(it=="A") withContext(NonCancellable) { antigua.await() } else EstadoSenia.Pendiente("falta") })
        p.ejecutarTrasVerSenia("A") { a++ };runCurrent()
        p.ejecutarTrasVerSenia("B") { b++ };runCurrent()
        antigua.complete(clip("A"));runCurrent();p.cerrarPorUsuario();advanceTimeBy(4000);runCurrent()
        assertEquals(0,a);assertEquals(0,b)
    }
    @Test fun clipPendienteNuncaEjecutaLaAccion() = runTest {
        var veces=0;val p=PresentadorSenias(backgroundScope,{true},{EstadoSenia.Pendiente("ausente")})
        p.ejecutarTrasVerSenia("A") { veces++ };runCurrent();advanceTimeBy(4000);runCurrent();p.cerrarPorUsuario()
        assertEquals(0,veces)
    }
    @Test fun sinModoSeniasLaAccionEsInmediata() = runTest {
        var veces=0;val p=PresentadorSenias(backgroundScope,{false},{error("No debe buscar clip")})
        p.ejecutarTrasVerSenia("A") { veces++ };assertEquals(1,veces);assertNull(p.presentacion.value)
    }
    @Test fun pulsarDosVecesNoReiniciaElTiempoNiDuplicaLaAccion() = runTest {
        var veces=0;val p=PresentadorSenias(backgroundScope,{true},{clip(it)})
        p.ejecutarTrasVerSenia("A") { veces++ };runCurrent();advanceTimeBy(2000)
        p.ejecutarTrasVerSenia("A") { veces+=10 };advanceTimeBy(1000);runCurrent();assertEquals(1,veces)
    }
    @Test fun idiomasSinClipNuncaReutilizanElVideoEspanol() {
        val entradas=listOf(SeniaEntrada("mapa","mapa.mp4","local"))
        for (idioma in listOf("fr","pt-BR","de","zh-Hans")) assertTrue(resolverEstadoSenia("mapa",idioma,entradas,emptySet()) is EstadoSenia.Pendiente)
        assertEquals("asset:///senias/clips/en/mapa.mp4",(resolverEstadoSenia("mapa","en",entradas,setOf("mapa.mp4")) as EstadoSenia.Clip).uri)
    }
}
