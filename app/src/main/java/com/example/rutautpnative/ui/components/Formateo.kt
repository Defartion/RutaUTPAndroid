package com.example.rutautpnative.ui.components

//----Formateo compartido (antes duplicado en NavegacionScreen, TrackingDemoScreen y NegociosDemoComponents)----

/// "850.0 m" o "2.3 km" — para stats, tarjetas y popups.
fun formatoDistancia(metros: Double): String =
    if (metros >= 1000) "%.1f km".format(metros / 1000) else "${metros.toInt()} m"
