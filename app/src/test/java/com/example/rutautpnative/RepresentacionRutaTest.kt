package com.example.rutautpnative

import androidx.compose.ui.graphics.Color
import com.example.rutautpnative.data.gtfs.*
import org.junit.Assert.*
import org.junit.Test

class RepresentacionRutaTest {
    private fun ruta(linea: String, variante: String = "") = RutaGTFS("id-gtfs", linea, variante, "Av. Grau → Av. Libertad", "Empresa", "FFFFFF", Color.White, emptyList(), emptyList(), 30, 5, 2.5, 8.0, 20.0)

    @Test fun colectivoMuestraSuLetraSinModificarLaIdentidadDelFeed() {
        val ruta = ruta("CT-04 B")
        assertEquals("CT-04", ruta.codigoTransporte)
        assertEquals("B", ruta.letraTransporte)
        assertEquals("CT-04 B", ruta.linea)
        assertEquals("id-gtfs", ruta.id)
        assertEquals("", ruta.variante)
    }
    @Test fun distintivoCorazonTieneLaMismaRepresentacionQueIos() {
        val ruta = ruta("C-01", " A<3  ")
        assertEquals("A ♥", ruta.letraTransporte)
        assertEquals(" A<3  ", ruta.variante)
    }
    @Test fun unNombreDeCalleNoSeConfundeConUnMarcadorDeNombreAusente() {
        assertEquals("sin registro", nombreParaderoVisible("  INNOMINÁDA ", "sin registro"))
        assertEquals("sin registro", nombreParaderoVisible("S/N", "sin registro"))
        assertEquals("Av. Innominada", nombreParaderoVisible(" Av. Innominada ", "sin registro"))
        assertEquals("Av. Grau", nombreParaderoVisible("Av. Grau", "sin registro"))
    }
    @Test fun extremosSinNombreNoUsanCoordenadasNiDestinosInventados() {
        val ruta = ruta("M-34", "A").copy(paraderos = listOf(ParaderoGTFS("a", "unnamed", -8.1, -79.0), ParaderoGTFS("b", "Av. Libertad", -8.11, -79.01)))
        assertEquals("Paradero inicial sin nombre", ruta.paradaInicioVisible)
        assertEquals("Av. Libertad", ruta.paradaFinVisible)
    }
}
