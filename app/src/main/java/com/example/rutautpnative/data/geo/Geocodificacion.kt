package com.example.rutautpnative.data.geo

import android.content.Context
import android.location.Geocoder
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

//----Geocodificacion nativa (android.location.Geocoder)----
// Puerto del uso de CLGeocoder en iOS:
//   - direccionDe: geocodificacion INVERSA del picker "elegir en el mapa"
//     (calle -> sublocalidad -> localidad; null si no hay resultado y el
//     llamador decide el respaldo, p.ej. "Punto en el mapa").
//   - coordenadaDe: geocodificacion DIRECTA "direccion, Trujillo, Peru" para
//     lugares guardados que todavia no tienen coordenada (el detalle de un
//     lugar la resuelve al tocar "como llegar", como el LugarDetailSheet iOS).
//
// Corre en Dispatchers.IO: Geocoder hace trabajo de red/y de sistema.
object Geocodificacion {

    /// Direccion legible de una coordenada, o null si no hay resultado.
    @Suppress("DEPRECATION") // variante sincrona: funciona en todos los API levels
    suspend fun direccionDe(coordenada: LatLng, context: Context): String? =
        withContext(Dispatchers.IO) {
            try {
                val dir = Geocoder(context)
                    .getFromLocation(coordenada.latitude, coordenada.longitude, 1)
                    ?.firstOrNull() ?: return@withContext null

                // Mismo criterio de composicion que iOS: thoroughfare +
                // sublocalidad + locality, sin duplicados.
                val partes = listOfNotNull(
                    dir.thoroughfare,
                    dir.subLocality?.takeIf { it != dir.thoroughfare },
                    dir.locality
                )
                partes.joinToString(", ").ifEmpty { null }
            } catch (e: Exception) {
                null
            }
        }

    /// Coordenada de una direccion libre (se completa con ", Trujillo, Peru"
    /// quien llama), o null si no hay resultado.
    @Suppress("DEPRECATION")
    suspend fun coordenadaDe(direccion: String, context: Context): LatLng? =
        withContext(Dispatchers.IO) {
            try {
                val dir = Geocoder(context).getFromLocationName(direccion, 1)?.firstOrNull()
                    ?: return@withContext null
                LatLng(dir.latitude, dir.longitude)
            } catch (e: Exception) {
                null
            }
        }
}
