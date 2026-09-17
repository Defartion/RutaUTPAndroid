package com.example.rutautpnative.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

//----Pantallas----
sealed class AppScreen {
    object Bienvenida    : AppScreen()
    object MapaPrincipal : AppScreen()
    object Rutas         : AppScreen()
    object Guardado      : AppScreen()
    object Seguridad     : AppScreen()
    object Perfil        : AppScreen()
}

//----Rutas centrales----
class AppRouter : ViewModel() {
    var currentScreen: AppScreen by mutableStateOf(AppScreen.Bienvenida)
        private set

    // id (o línea) de una ruta pendiente de abrir en detalle tras navegar a Rutas.
    // Público con setter, como @Published en iOS: otras pantallas lo escriben.
    var rutaPendiente: String? by mutableStateOf(null)

    fun navigate(to: AppScreen) {
        currentScreen = to
    }

    fun reset() {
        currentScreen = AppScreen.MapaPrincipal
    }
}