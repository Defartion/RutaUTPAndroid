package com.example.rutautpnative

import com.example.rutautpnative.data.gtfs.GTFSCSV
import com.example.rutautpnative.data.gtfs.GTFSNombreParser
import org.junit.Assert.assertEquals
import org.junit.Test

// Test del nucleo GTFS (puerto de GTFSCSVTests + GTFSNombreParserTests iOS):
// parser CSV (comillas, comas, columnas) y separacion linea/variante con
// deteccion de ramal circular.
class GTFSCSVTest {

    //----Parser CSV----

    @Test
    fun parserCuentaFilasYColumnas() {
        val tabla = GTFSCSV.parsear("a,b,c\n1,2,3\n4,5,6\n")
        assertEquals(2, tabla.rowCount)
        assertEquals(listOf("1", "4"), tabla.columna("a"))
        assertEquals(listOf("2", "5"), tabla.columna("b"))
        assertEquals(listOf("3", "6"), tabla.columna("c"))
    }

    @Test
    fun parserRespetaComasDentroDeComillas() {
        val tabla = GTFSCSV.parsear("a,b,c\n1,\"x,y\",3\n")
        assertEquals(listOf("1"), tabla.columna("a"))
        // La coma entrecomillada no debe partir el campo.
        assertEquals(listOf("x,y"), tabla.columna("b"))
        assertEquals(listOf("3"), tabla.columna("c"))
    }

    @Test
    fun parserTrataLaComillaEmbebidaComoTexto() {
        // Los short names del feed real son del tipo `C-01 "B"`. La comilla no
        // abre un campo entrecomillado porque el valor ya tiene contenido: si
        // el parser la interpretara como delimitador, la variante se perdería.
        val tabla = GTFSCSV.parsear("route_id,route_short_name\n1,C-01 \"B\"\n")
        assertEquals(listOf("C-01 \"B\""), tabla.columna("route_short_name"))
    }

    @Test
    fun columnaInexistenteDevuelveVacios() {
        val tabla = GTFSCSV.parsear("a\n1\n2\n")
        assertEquals(listOf("", ""), tabla.columna("no_existe"))
    }

    //----Parser de nombres----

    @Test
    fun nombreParserSeparaLineaYVariante() {
        val r = GTFSNombreParser.lineaYVariante("C-01 \"B\"")
        assertEquals("C-01", r.linea)
        assertEquals("B", r.variante)
    }

    @Test
    fun nombreParserSinVariante() {
        val r = GTFSNombreParser.lineaYVariante("M-07")
        assertEquals("M-07", r.linea)
        assertEquals("", r.variante)
    }

    @Test
    fun recorridoMarcaRamalCircular() {
        // Origen == destino: el feed lo publica como un recorrido que vuelve.
        val texto = GTFSNombreParser.recorrido(
            longName = "C-01 \"B\" : Av. Grau → Av. Grau",
            shortName = "C-01 \"B\""
        )
        assertEquals("Av. Grau (ramal circular)", texto)
    }

    @Test
    fun recorridoNormalNoSeAltera() {
        val texto = GTFSNombreParser.recorrido(
            longName = "C-01 \"B\" : Av. Grau → Av. Libertad",
            shortName = "C-01 \"B\""
        )
        assertEquals("Av. Grau → Av. Libertad", texto)
    }
}
