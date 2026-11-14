package com.example.rutautpnative.ui.screens.mapa

enum class EstadoPanelMapa { CARGANDO, ERROR, UTP_PENDIENTE, CON_BUSES, ESPERANDO, VACIO }

fun estadoPanelMapa(cargando: Boolean, fallo: Boolean, utpPendiente: Boolean, hayBuses: Boolean, fuenteReal: Boolean) = when {
    utpPendiente -> EstadoPanelMapa.UTP_PENDIENTE
    cargando -> EstadoPanelMapa.CARGANDO
    fallo -> EstadoPanelMapa.ERROR
    hayBuses -> EstadoPanelMapa.CON_BUSES
    fuenteReal -> EstadoPanelMapa.ESPERANDO
    else -> EstadoPanelMapa.VACIO
}
