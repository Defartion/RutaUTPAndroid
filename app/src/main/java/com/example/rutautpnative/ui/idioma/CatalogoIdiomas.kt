package com.example.rutautpnative.ui.idioma

import kotlinx.serialization.json.*

/** Complemento Android: mantiene intacto el catálogo compartido con iOS. */
class CatalogoIdiomas(compartido: String, complemento: String) {
    private val base = Json.parseToJsonElement(compartido).jsonObject
    private val android = Json.parseToJsonElement(complemento).jsonObject
    private val aliases = android.getValue("aliases").jsonObject
    private val entradas = base + android.getValue("entries").jsonObject
    fun texto(es: String, en: String, codigo: String): String {
        if (codigo == "es") return es
        if (codigo == "en") return en
        val clave = aliases[es]?.jsonPrimitive?.content ?: es
        val texto = (entradas[clave] as? JsonObject)?.get(codigo)?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: en
        // Algunas claves históricas son prefijos; conservar su separación del valor.
        return if (es.endsWith(' ') && !texto.endsWith(' ') && codigo != "zh-Hans") texto + " " else texto
    }
    companion object {
        fun formatear(plantilla: String, argumentos: List<String>): String =
            Regex("\\{([0-9]+)\\}").replace(plantilla) { match -> argumentos.getOrNull(match.groupValues[1].toInt()) ?: match.value }
    }
}

/** Una lectura persistida antigua no revierte una selección todavía en escritura. */
internal class SeleccionIdioma(inicial: String) {
    var codigo = inicial; private set
    private var pendiente: String? = null
    fun seleccionar(nuevo: String) { codigo = nuevo; pendiente = nuevo }
    fun recibir(guardado: String) {
        if (pendiente == null || pendiente == guardado) {
            codigo = guardado
            pendiente = null
        }
    }
}
