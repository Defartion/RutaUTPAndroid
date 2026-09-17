package com.example.rutautpnative.data.gtfs

import androidx.compose.ui.graphics.Color

//----Utilidades de color GTFS----
// Porta la lógica de `Color.colorRuta(hex:)` de iOS: ajusta los colores que se
// "pierden" sobre el mapa (blancos y amarillos) para que sean visibles. El
// valor crudo se conserva siempre en `RutaGTFS.colorHex`.
//
// Nota sobre iOS: allá se usa un color adaptativo (claro/oscuro) que elige
// según el esquema. Aquí se resuelve a un único color (la variante clara)
// porque Compose no representa un color dinámico en un solo `Color`.
object GTFSColor {

    fun colorRuta(hex: String): Color = when (hex.trim('#', ' ').uppercase()) {
        "FFFF00", "FFFF66", "FFFF33" -> Color(0x79, 0x60, 0x00) // amarillos → "#796000"
        "FFFFFF"                       -> Color(0x59, 0x63, 0x6F) // blanco   → "#59636F"
        else                           -> fromHex(hex)
    }

    private fun fromHex(hex: String): Color {
        val limpio = hex.filter { it.isLetterOrDigit() }
        val valor = limpio.toLongOrNull(16) ?: 0L
        val r = ((valor shr 16) and 0xFF).toInt()
        val g = ((valor shr 8) and 0xFF).toInt()
        val b = (valor and 0xFF).toInt()
        return Color(r, g, b)
    }
}