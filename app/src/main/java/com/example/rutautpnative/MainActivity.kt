package com.example.rutautpnative

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rutautpnative.navigation.AppScreen
import com.example.rutautpnative.data.LineasGuardadasStore
import com.example.rutautpnative.data.LugaresStore
import com.example.rutautpnative.data.SedeTrabajoStore
import com.example.rutautpnative.data.SeguridadTilesStore
import com.example.rutautpnative.data.TemaStore
import com.example.rutautpnative.data.directions.DirectionsService
import com.example.rutautpnative.data.gtfs.TransporteApp
import com.example.rutautpnative.data.negocios.CuponesStore
import com.example.rutautpnative.data.negocios.NegociosService
import com.example.rutautpnative.data.places.PlacesService
import com.example.rutautpnative.data.senias.SeniasPrefs
import com.example.rutautpnative.data.senias.SeniasService
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.monedero.MonederoStore
import com.example.rutautpnative.data.Persistencia
import com.example.rutautpnative.data.tarjetas.TarjetasStore
import com.example.rutautpnative.data.tracking.PassiveTrackingCoordinator
import com.example.rutautpnative.data.ubicacion.LocationService
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.navigation.*
import com.example.rutautpnative.ui.theme.RutaUTPNativeTheme
import com.example.rutautpnative.ui.theme.TematicaEmpresaStore

class MainActivity : ComponentActivity() {
    private var notificacionIntent by androidx.compose.runtime.mutableStateOf<android.content.Intent?>(null)
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificacionIntent = intent
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // L y Tema VAN PRIMERO: L configura el idioma antes de que cualquier
        // servicio publique mensajes (la baliza usa L.t para sus estados), y
        // Tema hidrata sincronamente el espejo del tema para que el primer
        // frame de setContent ya vea el valor correcto (sin flash).
        L.init(this)
        TemaStore.init(this)
        // Temáticas y sede ANTES de setContent: la paleta de la empresa
        // guardada debe resolver el primer frame (sin flash de colores UTP),
        // igual que el espejo sincrono del tema oscuro.
        TematicaEmpresaStore.init(this)
        com.example.rutautpnative.model.CatalogoSedesTrabajo.init(this)
        SedeTrabajoStore.init(this)
        TransporteApp.init(this)
        com.example.rutautpnative.data.tracking.MapVehiclePreferences.init(this)
        com.example.rutautpnative.ui.theme.aplicarTemaOscuro(TemaStore.oscuroActual)
        Persistencia.migrarSiHaceFalta(this)
        // Datos del perfil: telefono/correo, emergencia, preferencias y
        // visibilidad de los carnets por empresa.
        com.example.rutautpnative.data.PreferenciasPerfilStore.init(this)
        com.example.rutautpnative.data.PreferenciasVisualesPerfilStore.init(this)
        GTFSRepository.init(this)
        LugaresStore.init(this)
        SeguridadTilesStore.init(this)
        LineasGuardadasStore.init(this)
        DirectionsService.init(this)
        PlacesService.init(this)
        NegociosService.init(this)
        CuponesStore.init(this)
        MonederoStore.init(this)
        TarjetasStore.init(this)
        SeniasService.init(this)
        SeniasPrefs.init(this)
        LocationService.init(this)
        com.example.rutautpnative.data.tracking.TrackingProviderFactory.init(this)
        PassiveTrackingCoordinator.init(this)
        // Hapticos compartidos (AppHaptics del iOS): el TalkBack check lee el
        // servicio de accesibilidad, se inicializa una sola vez aqui.
        com.example.rutautpnative.ui.components.AppHaptics.init(this)
        // Si el usuario ya habia consentido, la baliza se reanuda al arrancar
        // (startIfConsented del iOS); sin consentimiento no hace nada.
        // Ahora va DESPUES de L.init: los mensajes salen en el idioma correcto.
        PassiveTrackingCoordinator.reanudarSiConsentido()
        com.example.rutautpnative.data.notificaciones.NotificacionesService.init(this)
        com.example.rutautpnative.data.notificaciones.PushRutasService.init(this)
        notificacionIntent = intent
        setContent {
            // Tema oscuro manual (persistido): la raíz lee el espejo reactivo
            // del TemaStore — ya hidratado SINCRONAMENTE en init(): el primer
            // frame ve el valor correcto, sin flash de tema claro.
            val modoOscuro = TemaStore.oscuroActual
            RutaUTPNativeTheme(modoOscuro = modoOscuro) {
                val router: AppRouter = viewModel()
                //----Argumentos de depuración (Fase 8)----
                // Puerto del `--pantalla` de iOS (DEBUG only): solo en
                // compilaciones de desarrollo, se puede abrir directo a una
                // pantalla pasando el extra "pantalla" en el intent:
                //   adb shell am start -n com.example.rutautpnative/.MainActivity \
                //     --es pantalla tracking
                // Valores: mapa | rutas | guardado | seguridad | perfil | tracking.
                // "ruta" con un id abre el detalle de ESA línea en Rutas.
                if (BuildConfig.DEBUG && savedInstanceState == null) {
                    LaunchedEffect(Unit) {
                        when (intent?.getStringExtra("pantalla")?.lowercase()) {
                            "mapa" -> router.navigate(AppScreen.MapaPrincipal)
                            "rutas" -> router.navigate(AppScreen.Rutas)
                            "guardado" -> router.navigate(AppScreen.Guardado)
                            "seguridad" -> router.navigate(AppScreen.Seguridad)
                            "perfil" -> router.navigate(AppScreen.Perfil)
                            "tracking" -> router.navigate(AppScreen.TrackingDemo)
                        }
                        intent?.getStringExtra("ruta")?.let { id ->
                            router.rutaPendiente = id
                            router.navigate(AppScreen.Rutas)
                        }
                    }
                }
                LaunchedEffect(notificacionIntent) {
                    val llegada = notificacionIntent ?: return@LaunchedEffect
                    val destino = llegada.getStringExtra("notificacionDestinoID")
                    val ruta = llegada.getStringExtra("pushRouteID")
                    val consejo = if (llegada.hasExtra("notificacionDiaria")) llegada.getIntExtra("notificacionDiaria", -1) else null
                    val tipo = when {
                        destino != null -> TipoNotificacion.RECORDATORIO
                        ruta != null -> TipoNotificacion.CAMBIO_RUTA
                        consejo != null -> TipoNotificacion.CONSEJO
                        else -> null
                    }
                    if (tipo != null) {
                        val vence = llegada.getDoubleExtra("notificacionVenceEn", llegada.getDoubleExtra("pushExpiresAt", 0.0))
                        // Los avisos anteriores sin vigencia abren únicamente el aviso de información no disponible.
                        val id = llegada.getStringExtra("notificacionAccionID") ?: "legacy:$tipo:$destino:$ruta:$consejo:$vence"
                        router.recibirNotificacion(AccionNotificacion(id, tipo, destino, ruta, vence, consejo, llegada.getStringExtra("notificacionConfiguracionID")))
                    }
                    listOf("notificacionDestinoID", "pushRouteID", "pushExpiresAt", "notificacionDiaria",
                        "notificacionAccionID", "notificacionVenceEn", "notificacionConfiguracionID").forEach(llegada::removeExtra)
                }
                LaunchedEffect(router.notificacionPendiente) {
                    val accion = router.notificacionPendiente ?: return@LaunchedEffect
                    val catalogoInicial = TransporteApp.busesUTPActivos
                    val contextoInicial = router.revisionCatalogo
                    var destino: DestinoPendiente? = null
                    val valido = when (accion.tipo) {
                        TipoNotificacion.RECORDATORIO -> {
                            val lugar = LugaresStore.cargar().firstOrNull { "lugar:${it.id}" == accion.destinoID }
                            val sede = com.example.rutautpnative.model.CatalogoSedesTrabajo.todas.firstOrNull { "sede:${it.id}" == accion.destinoID }
                            val lat = lugar?.lat ?: sede?.lat
                            val lon = lugar?.lon ?: sede?.lon
                            if (lat != null && lon != null && lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) {
                                destino = DestinoPendiente(lugar?.nombre ?: sede!!.nombre, lat, lon)
                            }
                            destino != null
                        }
                        TipoNotificacion.CAMBIO_RUTA -> {
                            val guardada = LineasGuardadasStore.cargar().any { it.routeId == accion.rutaID }
                            guardada && GTFSRepository.rutas().any { it.id == accion.rutaID }
                        }
                        TipoNotificacion.CONSEJO -> accion.consejo in 0..3
                    }
                    val configVigente = accion.tipo == TipoNotificacion.CAMBIO_RUTA ||
                        (!com.example.rutautpnative.data.notificaciones.NotificacionesService.datosInvalidos &&
                            com.example.rutautpnative.data.notificaciones.NotificacionesService.configuracion.aceptaAviso(
                                accion.configuracionID, accion.destinoID, accion.consejo))
                    router.procesarNotificacion(accion, destino, valido && configVigente && catalogoInicial == TransporteApp.busesUTPActivos,
                        System.currentTimeMillis() / 1000.0, contextoInicial)
                }
                RootView(router)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // El permiso puede haber cambiado en Ajustes con la app en segundo plano.
        LocationService.refrescarEstadoAutorizacion()
        com.example.rutautpnative.data.notificaciones.NotificacionesService.refrescar()
        // Vuelve del segundo plano: reanuda la publicacion del viaje vigente
        // con el MISMO sessionId (resumeObservationSessionIfNeeded del iOS).
        PassiveTrackingCoordinator.reanudarSesionSiPosible()
        com.example.rutautpnative.data.notificaciones.PushRutasService.primerPlano(true)
    }

    override fun onStop() {
        super.onStop()
        // iOS suspende el proceso poco despues de esto: el socket y el GPS
        // mueren sin despedida. Se cierra la publicacion de forma limpia.
        PassiveTrackingCoordinator.pausarParaSegundoPlano()
        com.example.rutautpnative.data.notificaciones.PushRutasService.primerPlano(false)
        // Paso a segundo plano: la seña en curso y su acción pendiente se
        // cancelan SIN ejecutarla (iOS idem: "pasar a background detiene la
        // presentación").
        com.example.rutautpnative.data.senias.SeniasOverlay.cancelarPorNavegacion()
    }
}