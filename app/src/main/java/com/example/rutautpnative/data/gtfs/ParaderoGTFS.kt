package com.example.rutautpnative.data.gtfs

import com.google.android.gms.maps.model.LatLng

//----Paradero----
// Paradero dentro del recorrido de una ruta, tal como viene en stops.txt.
data class ParaderoGTFS(
    val id: String,
    val nombre: String,
    val lat: Double,
    val lon: Double
) {
    // Coordenada en el tipo que usa Google Maps (equivalente a `coordinate` de iOS).
    val coordinate: LatLng get() = LatLng(lat, lon)
}