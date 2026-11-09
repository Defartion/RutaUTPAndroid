package com.example.rutautpnative.data.gtfs

/** CSV compatible con iOS: BOM, CR/LF, comillas escapadas y registros multilínea. */
object GTFSCSV {
    class Tabla(val encabezado: List<String>, val filas: List<List<String>>) {
        private val indices = encabezado.withIndex().associate { it.value to it.index }
        val rowCount: Int get() = filas.size
        fun valor(fila: Int, nombre: String): String = indices[nombre]?.let { filas.getOrNull(fila)?.getOrNull(it) }.orEmpty()
        fun columna(nombre: String): List<String> = filas.indices.map { valor(it, nombre) }
        fun exigir(vararg nombres: String) {
            require(nombres.all { it in indices }) { "Faltan columnas GTFS: ${nombres.filter { it !in indices }.joinToString()}" }
        }
    }
    fun parsear(texto: String): Tabla {
        val registros = registros(texto.removePrefix("\uFEFF"))
        return Tabla(registros.firstOrNull().orEmpty(), registros.drop(1))
    }
    fun parsearLinea(linea: String): List<String> = registros(linea).firstOrNull() ?: listOf("")
    private fun registros(texto: String): List<List<String>> {
        val salida = mutableListOf<List<String>>()
        var fila = mutableListOf<String>()
        val campo = StringBuilder()
        var citado = false
        var datos = false
        var i = 0
        fun cerrarCampo() { fila.add(campo.toString()); campo.clear() }
        fun cerrarFila() { cerrarCampo(); salida.add(fila); fila = mutableListOf(); datos = false }
        while (i < texto.length) {
            val c = texto[i++]
            when {
                citado -> if (c == '"') {
                    if (i < texto.length && texto[i] == '"') { campo.append('"'); i++ } else citado = false
                } else campo.append(c)
                c == '"' -> { datos = true; if (campo.isBlank()) citado = true else campo.append(c) }
                c == ',' -> { datos = true; cerrarCampo() }
                c == '\r' || c == '\n' -> {
                    if (c == '\r' && i < texto.length && texto[i] == '\n') i++
                    if (datos) cerrarFila()
                }
                else -> { datos = true; campo.append(c) }
            }
        }
        if (datos) cerrarFila()
        return salida
    }
}
