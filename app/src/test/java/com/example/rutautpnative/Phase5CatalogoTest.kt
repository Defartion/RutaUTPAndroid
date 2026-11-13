package com.example.rutautpnative

import com.example.rutautpnative.data.gtfs.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Phase5CatalogoTest {
    @Test fun catalogoVacioFalloYFeedInstitucionalPendienteSonDiferentes() = runTest {
        val c=CargaCatalogo(backgroundScope) { when(it) {
            "vacio" -> ResultadoCatalogo.Disponible(emptyList())
            "utp" -> ResultadoCatalogo.InstitucionalPendiente
            else -> throw java.io.FileNotFoundException()
        } }
        assertEquals(ResultadoCatalogo.Disponible(emptyList()),c.cargar("vacio"))
        assertSame(ResultadoCatalogo.InstitucionalPendiente,c.cargar("utp"))
        assertSame(ResultadoCatalogo.Error,c.cargar("urbano"))
    }
    @Test fun unErrorNoSeMemorizaComoExitoVacioYPermiteReintentar() = runTest {
        var veces=0
        val c=CargaCatalogo(backgroundScope) { if (++veces==1) error("CSV inválido") else ResultadoCatalogo.Disponible(emptyList()) }
        assertSame(ResultadoCatalogo.Error,c.cargar("gtfs"))
        assertTrue(c.cargar("gtfs",true) is ResultadoCatalogo.Disponible)
        assertEquals(2,veces)
    }
    @Test fun cacheDeExitoSoloSeInvalidaCuandoSeSolicita() = runTest {
        var veces=0
        val c=CargaCatalogo(backgroundScope) { veces++;ResultadoCatalogo.Disponible(emptyList()) }
        c.cargar("gtfs");c.cargar("gtfs");assertEquals(1,veces)
        c.cargar("gtfs",true);assertEquals(2,veces)
    }
    @Test fun solicitudesConcurrentesCompartenLaLecturaDelMismoFeed() = runTest {
        var veces=0;val entrada=CompletableDeferred<Unit>()
        val c=CargaCatalogo(backgroundScope) { veces++;entrada.await();ResultadoCatalogo.Disponible(emptyList()) }
        val a=async { c.cargar("gtfs") };val b=async { c.cargar("gtfs") }
        runCurrent();assertEquals(1,veces);entrada.complete(Unit)
        assertSame(a.await(),b.await())
    }
    @Test fun cargarOtroCatalogoNoBorraElErrorUrbano() = runTest {
        val c=CargaCatalogo(backgroundScope) { if(it=="gtfs") error("ilegible") else ResultadoCatalogo.InstitucionalPendiente }
        c.cargar("gtfs");c.cargar("gtfs-utp")
        assertSame(ResultadoCatalogo.Error,c.estados.value["gtfs"])
        assertSame(ResultadoCatalogo.InstitucionalPendiente,c.estados.value["gtfs-utp"])
    }
    @Test fun cancelarLaPantallaNoCancelaLaCargaCompartida() = runTest {
        val entrada=CompletableDeferred<Unit>();var veces=0
        val c=CargaCatalogo(backgroundScope) { veces++;entrada.await();ResultadoCatalogo.Disponible(emptyList()) }
        val pantalla=async { c.cargar("gtfs") };runCurrent();pantalla.cancel();runCurrent()
        entrada.complete(Unit);runCurrent()
        assertTrue(c.cargar("gtfs") is ResultadoCatalogo.Disponible);assertEquals(1,veces)
    }
    @Test fun unCSVMalFormadoNoEsUnCatalogoVacioValido() = runTest {
        val c=CargaCatalogo(backgroundScope) { ResultadoCatalogo.Disponible(GTFSRepository.parsearFuente { "sin_columnas\nvalor" }) }
        assertSame(ResultadoCatalogo.Error,c.cargar("gtfs"))
    }
}
