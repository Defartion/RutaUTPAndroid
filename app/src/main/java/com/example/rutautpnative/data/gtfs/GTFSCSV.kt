package com.example.rutautpnative.data.gtfs

//----Parser CSV----
// Parser CSV minimalista para los archivos .txt del feed GTFS, portado de
// `GTFSCSV.swift`.
//
// - Soporta campos entre comillas con comas adentro (RFC 4180 básico).
// - Devuelve una `Tabla` con acceso por nombre de columna, sin forzar a
//   construir un objeto por cada celda (más liviano para shapes.txt de 53k+).
object GTFSCSV {

    //----Archivo CSV ya parseado----
    class Tabla(
        val encabezado: List<String>,
        val filas: List<List<String>>
    ) {
        val rowCount: Int get() = filas.size

        // Valor de la columna `nombre` en la fila `fila`. "" si no existe.
        fun valor(fila: Int, nombre: String): String {
            val idx = encabezado.indexOf(nombre)
            if (idx < 0 || fila !in filas.indices) return ""
            val f = filas[fila]
            return if (idx < f.size) f[idx] else ""
        }

        // Toda una columna, por nombre (útil para leer agency.txt sin modelar fila).
        fun columna(nombre: String): List<String> {
            val idx = encabezado.indexOf(nombre)
            if (idx < 0) return List(rowCount) { "" }
            return filas.map { if (idx < it.size) it[idx] else "" }
        }
    }

    //----Parseo de un texto completo a Tabla----
    // lineSequence() consume \n, \r\n y \r; las líneas en blanco se descartan
    // (mismo comportamiento que `omittingEmptySubsequences` en Swift).
    fun parsear(texto: String): Tabla {
        val filas = texto.lineSequence()
            .filter { it.isNotBlank() }
            .map { parsearLinea(it) }
            .toList()
        val encabezado = filas.firstOrNull() ?: emptyList()
        return Tabla(encabezado, if (filas.size > 1) filas.drop(1) else emptyList())
    }

    //----División de una línea respetando comillas----
    // Port de `parsearLinea` de Swift.
    //
    // Nota sobre comillas "embebidas": los short names de este feed traen
    // valores como `C-01 "B"`. Como el campo NO abre entrecomillado al inicio
    // (ya tiene contenido), esas comillas se tratan como texto literal y no
    // como delimitadores RFC.
    fun parsearLinea(linea: String): List<String> {
        // Fast path: sin comillas, split directo.
        if (!linea.contains('"')) {
            return linea.split(",")
        }

        val valores = mutableListOf<String>()
        val actual = StringBuilder()
        var enComillas = false

        for (c in linea) {
            when {
                enComillas -> {
                    if (c == '"') enComillas = false else actual.append(c)
                }
                c == '"' -> {
                    if (actual.toString().trim().isEmpty()) enComillas = true else actual.append(c)
                }
                c == ',' -> {
                    valores.add(actual.toString())
                    actual.clear()
                }
                else -> actual.append(c)
            }
        }
        valores.add(actual.toString())
        return valores
    }
}