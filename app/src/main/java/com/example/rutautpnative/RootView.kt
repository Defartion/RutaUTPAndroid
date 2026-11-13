package com.example.rutautpnative

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rutautpnative.data.senias.SeniasOverlay
import com.example.rutautpnative.navigation.AppRouter
import com.example.rutautpnative.navigation.AppScreen
import com.example.rutautpnative.navigation.ModelosCatalogo
import com.example.rutautpnative.navigation.clave
import com.example.rutautpnative.data.gtfs.TransporteApp
import com.example.rutautpnative.data.tracking.PassiveTrackingCoordinator
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.components.SeniasVentana
import com.example.rutautpnative.ui.screens.*
import com.example.rutautpnative.ui.screens.mapa.MapaScreen
import com.example.rutautpnative.ui.theme.AppBackground

@Composable
fun RootView(router: AppRouter = viewModel(), modelos: ModelosCatalogo = viewModel()) {
    val app = LocalContext.current.applicationContext as Application
    val factory = remember(app) { ViewModelProvider.AndroidViewModelFactory.getInstance(app) }
    val owner = modelos.obtener(TransporteApp.busesUTPActivos, factory)
    val estados = rememberSaveableStateHolder()
    LaunchedEffect(TransporteApp.busesUTPDisponibles) {
        if (!TransporteApp.busesUTPDisponibles) TransporteApp.establecerBusesUTP(false)
    }
    LaunchedEffect(owner) {
        modelos.consumirCambio()?.let { anterior ->
            router.invalidarCatalogo()
            PassiveTrackingCoordinator.actualizarModoTransporte()
            listOf("bienvenida", "mapa", "rutas", "guardado", "seguridad", "perfil", "tracking")
                .forEach { estados.removeState(anterior.toString() + ":" + it) }
        }
    }
    BackHandler(router.currentScreen !in listOf(AppScreen.Bienvenida, AppScreen.MapaPrincipal, AppScreen.TrackingDemo)) {
        router.navigate(AppScreen.MapaPrincipal)
    }
    // La raíz envuelve todo. El fondo del Box raíz pinta TAMBIÉN la zona tras
    // la status bar (edge-to-edge): en modo oscuro se ve oscuro, en claro claro.
    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            estados.SaveableStateProvider(modelos.revision.toString() + ":" + router.currentScreen.clave) {
                val screen = router.currentScreen
                when (screen) {
                    is AppScreen.Bienvenida    -> BienvenidaScreen(router)
                    is AppScreen.MapaPrincipal -> MapaScreen(router)
                    is AppScreen.Rutas         -> RutasScreen(router)
                    is AppScreen.Guardado      -> GuardadoScreen(router)
                    is AppScreen.Seguridad     -> SeguridadScreen(router)
                    is AppScreen.Perfil        -> PerfilScreen(router)
                    is AppScreen.TrackingDemo  -> TrackingDemoScreen(router)
                }
            }
        }

        //----Ventana de señas (Fase 6)----
        // Se compone SOLO mientras hay presentación vigente: al crearse se
        // apila sobre las ventanas ya existentes (Dialogs, ModalBottomSheets),
        // como la UIWindow a nivel .alert del iOS.
        val presentacion by SeniasOverlay.presentacion.collectAsState()
        presentacion?.let { SeniasVentana(it) }
        router.consejoAbierto?.let { tipo ->
            com.example.rutautpnative.ui.components.AvisoDiarioSheet(tipo, router.puedeNavegarConsejo,
                router::abrirConsejo, router::cerrarConsejo)
        }
        router.aviso?.let { mensaje ->
            AlertDialog(onDismissRequest = router::cerrarAviso,
                title = { Text(L.t("Aviso", "Notice")) },
                text = { Text(mensaje) },
                confirmButton = { TextButton(onClick = router::cerrarAviso) { Text(L.t("Listo", "Done")) } })
        }
    }

    // Cambiar de pantalla cancela la seña en curso y su acción pendiente
    // SIN ejecutarla (iOS: "salir a otra pantalla cancela la anterior").
    LaunchedEffect(router.currentScreen) {
        SeniasOverlay.cancelarPorNavegacion()
    }
}
