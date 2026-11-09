package com.example.rutautpnative.ui.idioma

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.rutautpnative.data.idioma.IdiomaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

//----Sistema de idioma (helper inline, como Idioma.swift de iOS)----
// NO es strings.xml: cada texto traducible se escribe L.t("español", "inglés")
// directamente en el composable. El código activo es un Estado global
// reactivo (mismo patrón que las vars adaptativas de Color.kt): al cambiar
// de idioma, toda la UI que llamó L.t() durante composición recompone sola.
object L {

    data class Idioma(val codigo: String, val nombre: String)
    val idiomas = listOf(Idioma("es", "Español"), Idioma("en", "English"), Idioma("fr", "Français"),
        Idioma("pt-BR", "Português (Brasil)"), Idioma("de", "Deutsch"), Idioma("zh-Hans", "中文（简体）"))
    private var catalogo: kotlinx.serialization.json.JsonObject? = null
    val nombreIdioma: String get() = idiomas.firstOrNull { it.codigo == codigo }?.nombre ?: "Español"
    // Estado global reactivo. Se hidrata del disco al iniciar (ver init) y
    // se actualiza desde el interruptor de Perfil (ver establecer).
    var codigo: String by mutableStateOf("es"); private set

    val esIngles: Boolean get() = codigo == "en"

    // Texto según idioma activo.
    fun t(es: String, en: String): String {
        if (codigo == "es") return es
        if (codigo == "en") return en
        val entrada = catalogo?.get(es) as? kotlinx.serialization.json.JsonObject
        return (entrada?.get(codigo) as? kotlinx.serialization.json.JsonPrimitive)?.content ?: es
    }
    fun f(es: String, en: String, vararg argumentos: String): String =
        Regex("\\{([0-9]+)\\}").replace(t(es, en)) { match -> argumentos.getOrNull(match.groupValues[1].toInt()) ?: match.value }

    // Igual que t(), pensada para textos que además son señables: la clave ya
    // existe en el catálogo de assets/senias/manifest.json. El texto devuelto
    // se envuelve con el composable Signable (ui/components/Signable.kt), que
    // es la puerta de entrada única: texto traducido + toque con modo señas.
    fun signable(clave: String, es: String, en: String): String = t(es, en)

    // Hidratación desde DataStore (se suscribe y sigue los cambios persistidos).
    // Se recolecta en MAIN (no IO): `codigo` es mutableStateOf que lee la
    // composición; escribirlo desde IO es el patrón de threading que
    // sorprende en tests y puede dar valores desincronizados.
    private var iniciado = false
    fun init(context: Context) {
        if (iniciado) return
        iniciado = true
        IdiomaStore.init(context)
        catalogo = try {
            kotlinx.serialization.json.Json.parseToJsonElement(context.assets.open("traducciones.json").bufferedReader().use { it.readText() }) as? kotlinx.serialization.json.JsonObject
        } catch (_: Exception) { null }
        codigo = kotlinx.coroutines.runBlocking { IdiomaStore.observarCodigo().first() }.takeIf { guardado -> idiomas.any { it.codigo == guardado } } ?: "es"
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            IdiomaStore.observarCodigo().collect { guardado -> codigo = guardado.takeIf { c -> idiomas.any { it.codigo == c } } ?: "es" }
        }
    }

    // Cambiado por el usuario (actualiza la UI al instante Y persiste).
    fun establecer(nuevoCodigo: String) {
        if (idiomas.none { it.codigo == nuevoCodigo } || nuevoCodigo == codigo) return
        codigo = nuevoCodigo
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            IdiomaStore.establecerCodigo(nuevoCodigo)
        }
    }
}
