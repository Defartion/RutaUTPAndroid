package com.example.rutautpnative.ui.components

import com.example.rutautpnative.ui.idioma.L

//----Vigencia y formato de fecha de cupones----
// Antes duplicados en PerfilScreen y NavegacionScreen. Una sola fuente.

// Vigencia del cupón: si tiene fecha ISO "yyyy-MM-dd" y ya pasó, está vencido.
fun cuponVigente(vence: String?): Boolean {
    if (vence.isNullOrBlank()) return true
    return try {
        !java.time.LocalDate.parse(vence).isBefore(java.time.LocalDate.now())
    } catch (e: Exception) {
        true
    }
}

// "2026-12-31" → "31 dic 2026" (ES) / "Dec 31, 2026" (EN), según el idioma activo.
fun formatoVenceCupon(vence: String): String = try {
    val locale = java.util.Locale.forLanguageTag(L.codigo)
    java.time.LocalDate.parse(vence)
        .format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale))
} catch (e: Exception) {
    vence
}
