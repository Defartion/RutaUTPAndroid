package com.example.rutautpnative

import com.example.rutautpnative.data.CarnetPerfil
import com.example.rutautpnative.data.Parentesco
import com.example.rutautpnative.data.PreferenciasVisualesPerfilStore
import com.example.rutautpnative.ui.theme.TematicaEmpresa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Test de las piezas puras del perfil (Fase de perfil completo):
// parentesco (clave estable en espanol + label traducible), titulos de los
// carnets por empresa y defaults de visibilidad (solo UTP muestra ambos).
class PreferenciasPerfilTest {

    @Test
    fun parentescoTraduceElLabelPeroConservaLaClave() {
        // La clave guardada es SIEMPRE el rawValue en espanol (estable).
        assertEquals("Padre/Madre", Parentesco.PADRE_MADRE.clave)
        // desdeClave resuelve el valor guardado a texto visible.
        assertEquals(Parentesco.CONYUGE_PAREJA.label, Parentesco.desdeClave("Conyuge/Pareja de hecho"))
        assertNull(Parentesco.desdeClave(""))
        // Valores antiguos desconocidos se conservan sin romperse.
        assertEquals("Primo", Parentesco.desdeClave("Primo"))
    }

    @Test
    fun titulosDeCarnetPorEmpresa() {
        assertEquals(
            "Carnet Universitario",
            CarnetPerfil.FOTO.titulo(TematicaEmpresa.UTP)
        )
        // Con otra empresa el titulo es generico (iOS idem).
        val titulo = CarnetPerfil.FOTO.titulo(TematicaEmpresa.INKAFARMA)
        assert(titulo.contains("empresa") || titulo.contains("Company"))
        assertEquals("Carné Digital", CarnetPerfil.DIGITAL.titulo(TematicaEmpresa.UTP))
    }

    @Test
    fun visibilidadPorDefectoSoloUtp() {
        // Sin preferencia guardada: UTP muestra ambas tarjetas; el resto
        // arranca sin el carne fisico hasta que el usuario lo pida.
        assertEquals(true, PreferenciasVisualesPerfilStore.visible(CarnetPerfil.FOTO, TematicaEmpresa.UTP))
        assertEquals(true, PreferenciasVisualesPerfilStore.visible(CarnetPerfil.DIGITAL, TematicaEmpresa.UTP))
        assertEquals(false, PreferenciasVisualesPerfilStore.visible(CarnetPerfil.FOTO, TematicaEmpresa.INTERBANK))
    }
}
