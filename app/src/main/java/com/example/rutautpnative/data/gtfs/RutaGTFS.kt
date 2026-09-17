package com.example.rutautpnative.data.gtfs

import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.model.LatLng
import java.util.Locale

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
        get() = if (headwayMin > 0) "cada $headwayMin min" else "—"
}