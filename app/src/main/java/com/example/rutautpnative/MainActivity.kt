package com.example.rutautpnative

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rutautpnative.data.LineasGuardadasStore
import com.example.rutautpnative.data.LugaresStore
import com.example.rutautpnative.data.SeguridadTilesStore
import com.example.rutautpnative.data.TemaStore
import com.example.rutautpnative.data.directions.DirectionsService
import com.example.rutautpnative.data.negocios.CuponesStore
import com.example.rutautpnative.data.negocios.NegociosService
import com.example.rutautpnative.data.places.PlacesService
import com.example.rutautpnative.data.senias.SeniasPrefs
import com.example.rutautpnative.data.senias.SeniasService
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.tracking.PassiveTrackingCoordinator
import com.example.rutautpnative.data.ubicacion.LocationService
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.navigation.AppRouter
import com.example.rutautpnative.ui.theme.RutaUTPNativeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        GTFSRepository.init(this)
        LugaresStore.init(this)
        SeguridadTilesStore.init(this)
        LineasGuardadasStore.init(this)
        DirectionsService.init(this)
        PlacesService.init(this)
        NegociosService.init(this)
        CuponesStore.init(this)
        SeniasService.init(this)
        SeniasPrefs.init(this)
        TemaStore.init(this)
        LocationService.init(this)
        PassiveTrackingCoordinator.init(this)
        L.init(this)
        // Si el usuario ya habia consentido, la baliza se reanuda al arrancar
        // (startIfConsented del iOS); sin consentimiento no hace nada.
        PassiveTrackingCoordinator.reanudarSiConsentido()
        setContent {
            // Tema oscuro manual (persistido): la raíz lee el espejo reactivo
            // del TemaStore — cambia al instante sin depender del timing del flow.
            val modoOscuro = TemaStore.oscuroActual
            RutaUTPNativeTheme(modoOscuro = modoOscuro) {
                val router: AppRouter = viewModel()
                RootView(router)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // El permiso puede haber cambiado en Ajustes con la app en segundo plano.
        LocationService.refrescarEstadoAutorizacion()
        // Vuelve del segundo plano: reanuda la publicacion del viaje vigente
        // con el MISMO sessionId (resumeObservationSessionIfNeeded del iOS).
        PassiveTrackingCoordinator.reanudarSesionSiPosible()
    }

    override fun onStop() {
        super.onStop()
        // iOS suspende el proceso poco despues de esto: el socket y el GPS
        // mueren sin despedida. Se cierra la publicacion de forma limpia.
        PassiveTrackingCoordinator.pausarParaSegundoPlano()
    }
}