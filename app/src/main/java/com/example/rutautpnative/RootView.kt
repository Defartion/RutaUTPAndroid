package com.example.rutautpnative

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rutautpnative.data.senias.SeniasOverlay
import com.example.rutautpnative.navigation.AppRouter
import com.example.rutautpnative.navigation.AppScreen
import com.example.rutautpnative.ui.components.SeniasVentana
import com.example.rutautpnative.ui.screens.*
import com.example.rutautpnative.ui.screens.mapa.MapaScreen
import com.example.rutautpnative.ui.theme.AppBackground

@Composable
fun RootView(router: AppRouter = viewModel()) {
    // La raíz envuelve todo. El fondo del Box raíz pinta TAMBIÉN la zona tras
    // la status bar (edge-to-edge): en modo oscuro se ve oscuro, en claro claro.
    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Crossfade(
            targetState = router.currentScreen,
            animationSpec = tween(durationMillis = 250),
            label = "screen_transition"
        ) { screen ->
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

        //----Ventana de señas (Fase 6)----
        // Se compone SOLO mientras hay presentación vigente: al crearse se
        // apila sobre las ventanas ya existentes (Dialogs, ModalBottomSheets),
        // como la UIWindow a nivel .alert del iOS.
        val presentacion by SeniasOverlay.presentacion.collectAsState()
        presentacion?.let { SeniasVentana(it) }
    }

    // Cambiar de pantalla cancela la seña en curso y su acción pendiente
    // SIN ejecutarla (iOS: "salir a otra pantalla cancela la anterior").
    LaunchedEffect(router.currentScreen) {
        SeniasOverlay.cancelarPorNavegacion()
    }
}
