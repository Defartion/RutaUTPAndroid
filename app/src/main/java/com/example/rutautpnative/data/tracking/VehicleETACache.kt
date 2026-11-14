package com.example.rutautpnative.data.tracking

import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.google.android.gms.maps.model.LatLng

/** Local al mapa y confinado a su hilo. Un catálogo y un destino exacto, sin timers. */
internal class VehicleETACache {
    private var catalogo: Map<String, RutaGTFS> = emptyMap()
    private val rutas = mutableMapOf<String, VehicleETAEstimator.RutaPreparada?>()
    private val objetivos = mutableMapOf<String, VehicleETAEstimator.ObjetivoPreparado>()
    private var ancla: Pair<Long, Long>? = null
    internal var preparacionesRuta = 0; private set
    internal var proyeccionesObjetivo = 0; private set
    internal val rutasMemorizadas get() = rutas.size
    internal val objetivosMemorizados get() = objetivos.size

    fun instalar(catalogo: Map<String, RutaGTFS>) {
        this.catalogo = catalogo.toMap()
        rutas.clear(); objetivos.clear(); ancla = null
        preparacionesRuta = 0; proyeccionesObjetivo = 0
    }

    fun minutos(posicion: VehiclePosition, objetivo: LatLng): Int? {
        val clave = objetivo.latitude.toRawBits() to objetivo.longitude.toRawBits()
        if (ancla != clave) { ancla = clave; objetivos.clear() }
        val ruta = catalogo[posicion.routeId] ?: return null
        if (!rutas.containsKey(ruta.id)) {
            preparacionesRuta++
            rutas[ruta.id] = VehicleETAEstimator.prepararRuta(ruta)
        }
        val geometria = rutas[ruta.id] ?: return null
        val preparada = objetivos.getOrPut(ruta.id) {
            proyeccionesObjetivo++
            VehicleETAEstimator.prepararObjetivo(geometria, objetivo)
        }
        return VehicleETAEstimator.minutos(posicion, preparada)
    }
}
