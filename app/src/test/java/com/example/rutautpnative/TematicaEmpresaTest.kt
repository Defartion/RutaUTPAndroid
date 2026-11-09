package com.example.rutautpnative

import androidx.compose.ui.graphics.Color
import com.example.rutautpnative.ui.theme.PaletasEmpresa
import com.example.rutautpnative.ui.theme.TematicaEmpresa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Test del sistema de tematicas (TematicaEmpresa/PaletasEmpresa): resolucion
// de ids, paleta UTP identica a los tokens originales y paletas distintas por
// empresa (puerto del PaletaEmpresa.swift).
class TematicaEmpresaTest {

    @Test
    fun desdeIdResuelveTodasLasEmpresas() {
        TematicaEmpresa.entries.forEach { assertEquals(it, TematicaEmpresa.desdeId(it.id)) }
        assertNull(TematicaEmpresa.desdeId("desconocida"))
        assertNull(TematicaEmpresa.desdeId(null))
    }

    @Test
    fun catalogoCompletoDeDiezEmpresas() {
        assertEquals(10, TematicaEmpresa.todas.size)
        assertEquals(9, TematicaEmpresa.empresas.size)
        assertEquals(TematicaEmpresa.UTP, TematicaEmpresa.todas.first())
    }

    @Test
    fun utpReproduceLosTokensOriginales() {
        val p = PaletasEmpresa.paleta(TematicaEmpresa.UTP)
        assertEquals(Color(0xFFa80033), p.appPrimary.claro)
        assertEquals(Color(0xFFd31245), p.primaryContainer)
        assertEquals(Color(0xFF005b6e), p.tertiary)
        assertEquals(Color(0xFF99b8fe), p.secondaryContainer.claro)
        assertEquals(Color(0xFF5c3f41), p.onSurfaceVariant.claro)
        assertEquals(Color(0xFFc9adaf), p.onSurfaceVariant.oscuro)
    }

    @Test
    fun tokensConVarianteClaroOscuro() {
        // Innova Schools: primaria azul en claro, clarificada en oscuro (iOS).
        val innova = PaletasEmpresa.paleta(TematicaEmpresa.INNOVA_SCHOOLS)
        assertEquals(Color(0xFF0069AD), innova.appPrimary.claro)
        assertEquals(Color(0xFF8BCBFA), innova.appPrimary.oscuro)
        // Inkafarma: la tinta sobre el amarillo NUNCA es blanca.
        val inka = PaletasEmpresa.paleta(TematicaEmpresa.INKAFARMA)
        assertEquals(Color(0xFFFFF200), inka.primaryFill)
        assertEquals(Color(0xFF193D23), inka.onPrimaryFill)
    }

    @Test
    fun cadaEmpresaTieneColorDeMarcaDistinto() {
        val colores = TematicaEmpresa.todas.map { PaletasEmpresa.paleta(it).colorMarca }
        assertEquals(TematicaEmpresa.todas.size, colores.toSet().size)
    }
}
