package com.example.rutautpnative.data.gtfs

import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.model.LatLng
import java.util.Locale
import com.example.rutautpnative.ui.idioma.L

//----Ruta GTFS (dominio)----
// Modelo construido a partir del feed GTFS estático (gtfs/*.txt en assets).
//
// IMPORTANTE: el GTFS describe la ESTRUCTURA del transporte (rutas, recorridos,
// paraderos, frecuencias y tarifas). NO contiene posiciones GPS en vivo; esas
// siguen siendo simuladas sobre los recorridos reales.
data class RutaGTFS(
    val id: String,                     // route_id del feed
    val linea: String,                  // "C-01"
    val variante: String,               // "B" (letra entre comillas del short name)
    val recorrido: String,              // "Av. Miguel Grau → Av. Libertad"
    val empresa: String,                // nombre de la agencia
    val colorHex: String,               // "00CC00"
    val color: Color,                   // color de presentación (ajustado para el mapa)
    val shape: List<LatLng>,            // recorrido completo ordenado
    val paraderos: List<ParaderoGTFS>,  // en orden de viaje
    val duracionMin: Int,               // duración del viaje según stop_times
    val headwayMin: Int,                // frecuencia: sale uno cada N min
    val precio: Double,                 // tarifa en PEN (fare_attributes)
    val distanciaKm: Double,            // longitud del recorrido
    val distanciaUTPMetros: Double      // distancia mínima del shape al campus UTP
) {
    val precioTexto: String
        get() = if (precio > 0) String.format(Locale.US, "S/ %.2f", precio) else "S/ —"

    val frecuenciaTexto: String
        get() = if (headwayMin > 0) L.f("cada {0} min", "every {0} min", headwayMin.toString()) else "—"

    // Letra del transporte (la variante entre comillas del short name GTFS).
    val codigoTransporte: String get() = if (variante.isBlank() && linea.startsWith("CT-")) linea.substringBefore(' ') else linea
    val letraTransporte: String get() = (if (variante.isBlank() && linea.startsWith("CT-")) linea.substringAfter(' ', "") else variante).replace("<3", " ♥").trim().replace(Regex("\\s+"), " ")
    val paradaInicioVisible: String get() = nombreParaderoVisible(paraderos.firstOrNull()?.nombre, L.t("Paradero inicial sin nombre", "Unnamed first stop"))
    val paradaFinVisible: String get() = nombreParaderoVisible(paraderos.lastOrNull()?.nombre, L.t("Paradero final sin nombre", "Unnamed last stop"))
    val tiempoTexto: String get() = if (duracionMin > 0) "$duracionMin min" else "—"

    /// "C-01 \"B\"" cuando hay variante; la línea sola si no (iOS idem).
    val lineaConLetra: String
        get() = if (letraTransporte.isEmpty()) linea else codigoTransporte + L.t(" · Letra ", " · Letter ") + letraTransporte
}

fun nombreParaderoVisible(nombre: String?, defecto: String = L.t("Paradero sin nombre", "Unnamed stop")): String {
    val limpio = nombre?.trim().orEmpty()
    val normalizado = java.text.Normalizer.normalize(limpio, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    return if (normalizado in setOf("", "innominada", "innominado", "inominada", "inominado", "unnamed", "sin nombre", "s/n")) defecto else limpio
}
