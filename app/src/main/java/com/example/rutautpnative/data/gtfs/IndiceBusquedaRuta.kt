package com.example.rutautpnative.data.gtfs

import java.text.Normalizer
import java.util.Locale

/** Índice del catálogo con las mismas reglas de IndiceBusquedaRuta.swift. */
class IndiceBusquedaRuta(ruta: RutaGTFS) {
    val letra = compactar(ruta.variante)
    private val codigo = compactar(ruta.linea)
    private val texto = normalizar((listOf(ruta.linea, ruta.variante, ruta.empresa, ruta.recorrido) + ruta.paraderos.map { it.nombre }).joinToString(" "))
    fun coincide(consulta: ConsultaRuta): Boolean =
        (consulta.letra == null || consulta.letra == letra) && consulta.terminos.all { it in texto || compactar(it) in codigo }
    companion object {
        fun normalizar(texto: String): String = Normalizer.normalize(
            texto.replace("<3", "♥").replace(Regex("(?i)\\b(ct|m|c)[-\\s]+([0-9]+)\\b"), "$1$2"), Normalizer.Form.NFD
        ).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}♥]+" )).filter { it.isNotEmpty() }.joinToString(" ")
        fun compactar(texto: String): String = normalizar(texto).replace(" ", "")
    }
}

class ConsultaRuta(texto: String, letras: Set<String>) {
    val letra: String?
    val terminos: List<String>
    init {
        var normalizada = IndiceBusquedaRuta.normalizar(texto)
        listOf("字母", "线路", "公司", "大道", "街道").forEach { normalizada = normalizada.replace(it, " $it ") }
        val palabras = normalizada.split(" ").filter { it.isNotBlank() }.toMutableList()
        fun longitud(desde: Int): Int? = (minOf(2, palabras.size - desde) downTo 1).firstOrNull { palabras.subList(desde, desde + it).joinToString("") in letras }
        val indice = palabras.indexOfFirst { it in setOf("letra", "letter", "lettre", "buchstabe", "字母") }
        var elegida: String? = null
        if (indice >= 0 && indice + 1 < palabras.size) {
            val n = longitud(indice + 1) ?: 1
            elegida = palabras.subList(indice + 1, indice + n + 1).joinToString("")
            repeat(n + 1) { palabras.removeAt(indice) }
        } else {
            val n = if ('.' !in texto) longitud(0) else null
            if (n != null) { elegida = palabras.take(n).joinToString(""); repeat(n) { palabras.removeAt(0) } }
            else if (palabras.size == 1 && palabras[0].length == 1 && (palabras[0][0].isLetter() || palabras[0] == "♥")) {
                elegida = palabras.removeAt(0)
            }
        }
        letra = elegida
        val calificadores = setOf("linea", "line", "empresa", "company", "avenida", "av", "calle", "ligne", "compagnie", "entreprise", "avenue", "rue", "linha", "rua", "linie", "unternehmen", "firma", "straße", "strasse", "allee", "线路", "公司", "大道", "街道")
        terminos = palabras.filter { it !in calificadores }
    }
}
