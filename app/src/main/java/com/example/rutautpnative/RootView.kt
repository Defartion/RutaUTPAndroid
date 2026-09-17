package com.example.rutautpnative

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rutautpnative.navigation.AppRouter
import com.example.rutautpnative.navigation.AppScreen
import com.example.rutautpnative.ui.screens.*
import com.example.rutautpnative.ui.screens.mapa.MapaScreen

@Composable
fun RootView(router: AppRouter = viewModel()) {
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
        }
    }
}