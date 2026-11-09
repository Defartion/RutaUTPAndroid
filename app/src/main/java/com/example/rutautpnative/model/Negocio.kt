package com.example.rutautpnative.model

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

//----Texto bilingüe (ES/EN)----
// ES es la fuente de verdad; en es opcional con fallback. Resuelto con el
// idioma activo via L.esIngles (Fase 12: ya reacciona al cambio de idioma).
@Serializable
data class TextoBilingue(
    val es: String,
    val en: String
) {
    fun texto(): String =
        com.example.rutautpnative.ui.idioma.L.t(es, en.ifBlank { es })
}

//----Cupón de un negocio----
@Serializable
data class CuponNegocio(
    val codigo: String,
    val detalle: TextoBilingue,
    val condiciones: TextoBilingue,
    val vence: String? = null   // fecha ISO (opcional)
)

//----Categoría de negocio (12 valores, etiqueta + color)----
@Serializable
enum class CategoriaNegocio(val label: String, val color: Color) {
    @SerialName("polleria")   POLLERIA   ("Pollería",   Color(0xFFE65100)),
    @SerialName("menu")       MENU       ("Menú",       Color(0xFF2E7D32)),
    @SerialName("cafeteria")  CAFETERIA  ("Cafetería",  Color(0xFF6D4C41)),
    @SerialName("chifa")      CHIFA      ("Chifa",      Color(0xFFC62828)),
    @SerialName("salchipapas") SALCHIPAPAS("Salchipapas", Color(0xFFF9A825)),
    @SerialName("panaderia")  PANADERIA  ("Panadería",  Color(0xFFAD6800)),
    @SerialName("heladeria")  HELADERIA  ("Heladería",  Color(0xFF0288D1)),
    @SerialName("jugueria")   JUGUERIA   ("Juguería",   Color(0xFF43A047)),
    @SerialName("pizza")      PIZZA      ("Pizza",      Color(0xFF00897B)),
    @SerialName("burger")     BURGER     ("Burger",     Color(0xFF5D4037)),
    @SerialName("cevicheria") CEVICHERIA ("Cevichería", Color(0xFF0277BD)),
    @SerialName("empanadas")  EMPANADAS  ("Empanadas",  Color(0xFF8E24AA))
}

//----Negocio promocionado (data-driven desde el JSON de assets)----
@Serializable
data class Negocio(
    val id: String,
    val nombre: String,
    val categoria: CategoriaNegocio,
    val latitud: Double,
    val longitud: Double,
    val direccion: String,
    val distrito: String,
    val promoCorta: TextoBilingue,
    val promoDetalle: TextoBilingue,
    val cupon: CuponNegocio? = null,
    val horario: TextoBilingue,
    val telefono: String? = null,
    val patrocinado: Boolean = false,
    val calificacion: Double? = null
)

//----Raíz del JSON de assets (negocios_promocionados.json)----
@Serializable
data class CatalogoNegocios(
    val negocios: List<Negocio>
)
