package com.example.rutautpnative.data.gtfs

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.rutautpnative.data.SedeTrabajoStore
import com.example.rutautpnative.data.tracking.PassiveTrackingCoordinator
import com.example.rutautpnative.model.CatalogoSedesTrabajo
import com.example.rutautpnative.model.SedeTrabajo
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.TematicaEmpresa
import com.example.rutautpnative.ui.theme.TematicaEmpresaStore
import com.google.android.gms.maps.model.LatLng

//----Selección común de transporte (puerto del TransporteApp.swift)----
// Alterna entre el catálogo urbano y el institucional (Buses UTP) para rutas,
// paraderos, itinerarios y seguimiento. Hoy el feed gtfs-utp está VACÍO: el
// modo queda armado y los estados muestran "rutas próximamente" (iOS idem).
object TransporteApp {
    private const val PREFS = "rutautp.persistencia"
    const val LLAVE_BUSES_UTP = "transporte.buses.utp.v1"

    private var appContext: Context? = null

    // Toggle persistido; el modo solo cuenta como activo si está disponible.
    private var _toggleBusesUTP by mutableStateOf(false)

    /// La temática y la sede deben pertenecer a UTP para ofrecer sus buses.
    /// No usar `sedeSeleccionada`: el propio modo de buses fuerza el campus.
    val busesUTPDisponibles: Boolean
        get() = TematicaEmpresaStore.seleccion == TematicaEmpresa.UTP &&
            SedeTrabajoStore.utpComoReferencia

    val busesUTPActivos: Boolean
        get() = busesUTPDisponibles && _toggleBusesUTP

    fun init(context: Context) {
        appContext = context.applicationContext
        _toggleBusesUTP = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(LLAVE_BUSES_UTP, false)
    }

    /// Interruptor del modo (Perfil). Solo se activa si está disponible, y
    /// los viajes en curso terminan al cambiar de modo (iOS README idem).
    fun establecerBusesUTP(activado: Boolean) {
        val nuevo = activado && busesUTPDisponibles
        if (nuevo == _toggleBusesUTP) return
        _toggleBusesUTP = nuevo
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(LLAVE_BUSES_UTP, nuevo)?.apply()
        if (PassiveTrackingCoordinator.rutaViajeSeleccionada.value != null) {
            PassiveTrackingCoordinator.terminarViajeDeclarado()
        }
    }

    /// El campus puede ser referencia también con las rutas urbanas.
    val utpComoReferencia: Boolean
        get() = busesUTPActivos || SedeTrabajoStore.utpComoReferencia

    /// Punto de referencia al abrir el mapa: campus, sede o centro de Trujillo.
    val referenciaInicio: LatLng
        get() = if (utpComoReferencia) GTFSRepository.coordenadaUTP
        else SedeTrabajoStore.sede?.let { LatLng(it.lat, it.lon) } ?: LatLng(-8.1090, -79.0270)

    /// Sede dibujada en el mapa (null = campus UTP como referencia).
    val sedeVisible: SedeTrabajo?
        get() = if (utpComoReferencia) null else SedeTrabajoStore.sede

    /// Sede activa para los selectores, incluido el campus del modo Buses UTP.
    val sedeSeleccionada: SedeTrabajo?
        get() = if (utpComoReferencia) CatalogoSedesTrabajo.campusUTP else SedeTrabajoStore.sede

    fun usarSedeTrabajo(sede: SedeTrabajo) {
        val valida = CatalogoSedesTrabajo.sede(sede.id) ?: return
        seleccionarEmpresa(valida.tematica)
        SedeTrabajoStore.seleccionar(valida.id)
    }

    fun seleccionarEmpresa(empresa: TematicaEmpresa) {
        // Cambiar de empresa vuelve al catálogo urbano, incluso sin una sede
        // disponible (Popeyes) o al volver a tocar la misma temática.
        if (empresa != TematicaEmpresa.UTP && _toggleBusesUTP) {
            _toggleBusesUTP = false
            appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()?.putBoolean(LLAVE_BUSES_UTP, false)?.apply()
        }
        TematicaEmpresaStore.seleccionar(empresa)
    }

    /// El feed institucional (assets/gtfs-utp/routes.txt) aún no existe.
    val rutasUTPPendientes: Boolean
        get() = busesUTPActivos && !hayFeedUTP

    val mensajePendiente: String
        get() = L.t(
            "Las rutas de los buses UTP estarán disponibles próximamente.",
            "UTP bus routes will be available soon."
        )

    private val hayFeedUTP: Boolean
        get() = appContext?.assets?.let { a ->
            try {
                a.open("gtfs-utp/routes.txt").close()
                true
            } catch (e: Exception) {
                false
            }
        } ?: false
}
