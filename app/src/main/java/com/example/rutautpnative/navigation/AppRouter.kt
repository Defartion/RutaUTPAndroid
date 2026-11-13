package com.example.rutautpnative.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle

//----Pantallas----
sealed class AppScreen {
    object Bienvenida    : AppScreen()
    object MapaPrincipal : AppScreen()
    object Rutas         : AppScreen()
    object Guardado      : AppScreen()
    object Seguridad     : AppScreen()
    object Perfil        : AppScreen()
    object TrackingDemo  : AppScreen()   // Pantalla demo (no entra por BottomNavBar)
}

val AppScreen.clave: String get() = when (this) {
    AppScreen.Bienvenida -> "bienvenida"
    AppScreen.MapaPrincipal -> "mapa"
    AppScreen.Rutas -> "rutas"
    AppScreen.Guardado -> "guardado"
    AppScreen.Seguridad -> "seguridad"
    AppScreen.Perfil -> "perfil"
    AppScreen.TrackingDemo -> "tracking"
}

//----Destino pendiente de seleccionar en el Mapa----
// Estado genérico del router: cualquier pestaña (Seguridad, Guardado, ...)
// publica un lugar y al llegar a Mapa se consume y se selecciona como si
// el usuario lo hubiera buscado manualmente.
data class DestinoPendiente(
    val titulo: String,
    val lat: Double,
    val lon: Double
)

//----Rutas centrales----
class AppRouter(
    private val estadoGuardado: SavedStateHandle = SavedStateHandle(),
    private val viajes: EstadoViajes = ViajesActivos.estado
) : ViewModel() {
    constructor(handle: SavedStateHandle) : this(handle, ViajesActivos.estado)
    var currentScreen: AppScreen by mutableStateOf(AppScreen.Bienvenida)
        private set

    // id (o línea) de una ruta pendiente de abrir en detalle tras navegar a Rutas.
    // Público con setter, como @Published en iOS: otras pantallas lo escriben.
    var rutaPendiente: String? by mutableStateOf(null)

    // Lugar pendiente de seleccionar al navegar a Mapa (seguridad: zonas de referencia).
    // Se limpia al consumirse; convive con rutaPendiente sin interferencia.
    var destinoPendiente: DestinoPendiente? by mutableStateOf(null)

    // Consume RutasScreen: "transporte cerca de este lugar" (Guardado, paraderos):
    // filtra las líneas con paradero a menos de 300 m del punto.
    var lugarCercanoPendiente: DestinoPendiente? by mutableStateOf(null)

    var consultaCambiosPendiente by mutableStateOf(false)
    var consejoAbierto: Int? by mutableStateOf(null)
        private set
    val puedeNavegarConsejo: Boolean get() = !viajes.enCurso

    var cambioRutaPendiente: String? by mutableStateOf(null)
    var lugarGuardadoPendiente: String? by mutableStateOf(null)
    private var acciones by mutableStateOf<List<AccionNotificacion>>(emptyList())
    val notificacionPendiente: AccionNotificacion? get() = acciones.firstOrNull()
    var aviso: String? by mutableStateOf(null)
        private set

    var revisionCatalogo = 0L
        private set

    fun recibirNotificacion(accion: AccionNotificacion) {
        val vistas = estadoGuardado.get<ArrayList<String>>("avisos_consumidos").orEmpty()
        if (accion.id !in vistas && acciones.none { it.id == accion.id }) acciones = acciones + accion
    }

    fun procesarNotificacion(accion: AccionNotificacion, destino: DestinoPendiente?,
                            destinoValido: Boolean, ahora: Double, contexto: Long = revisionCatalogo) {
        if (notificacionPendiente?.id != accion.id) return
        // Se consulta al terminar las lecturas asíncronas, no antes de ellas.
        val decision = decidirNotificacion(accion, viajes.enCurso, destinoValido && contexto == revisionCatalogo, ahora)
        if (accion.tipo == TipoNotificacion.CONSEJO && decision != DecisionNotificacion.NO_DISPONIBLE) {
            // El consejo se puede leer durante un viaje; su botón consulta de nuevo el registro.
            consejoAbierto = accion.consejo
        } else when (decision) {
            DecisionNotificacion.VIAJE_EN_CURSO -> aviso = com.example.rutautpnative.ui.idioma.L.t(
                "Tienes un viaje en curso. Termínalo antes de abrir otro destino.",
                "You have a trip in progress. Finish it before opening another destination.")
            DecisionNotificacion.NO_DISPONIBLE -> aviso = com.example.rutautpnative.ui.idioma.L.t(
                "Este aviso ya no está disponible. Revisa la información actual.",
                "This notification is no longer available. Check the current information.")
            DecisionNotificacion.ABRIR -> when (accion.tipo) {
                TipoNotificacion.RECORDATORIO -> {
                    destinoPendiente = destino
                    navigate(AppScreen.MapaPrincipal)
                }
                TipoNotificacion.CAMBIO_RUTA -> {
                    cambioRutaPendiente = accion.rutaID
                    navigate(AppScreen.MapaPrincipal)
                }
                TipoNotificacion.CONSEJO -> Unit
            }
        }
        val vistos = estadoGuardado.get<ArrayList<String>>("avisos_consumidos").orEmpty()
        estadoGuardado["avisos_consumidos"] = ArrayList((vistos + accion.id).takeLast(128))
        acciones = acciones.filterNot { it.id == accion.id }
    }
    fun cerrarAviso() { aviso = null }
    fun cerrarConsejo() { consejoAbierto = null }
    fun abrirConsejo() {
        val tipo = consejoAbierto ?: return
        if (!puedeNavegarConsejo) return
        if (tipo == 1) consultaCambiosPendiente = true
        navigate(when (tipo) {
            1 -> AppScreen.MapaPrincipal
            2 -> AppScreen.Perfil
            3 -> AppScreen.Guardado
            else -> AppScreen.Rutas
        })
        consejoAbierto = null
    }
    fun invalidarCatalogo() {
        revisionCatalogo += 1
        rutaPendiente = null
        destinoPendiente = null
        lugarCercanoPendiente = null
        cambioRutaPendiente = null
        consultaCambiosPendiente = false
        lugarGuardadoPendiente = null
    }

    fun navigate(to: AppScreen) {
        currentScreen = to
    }
}
