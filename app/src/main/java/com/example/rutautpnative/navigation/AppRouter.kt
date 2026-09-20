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
class AppRouter : ViewModel() {
    var currentScreen: AppScreen by mutableStateOf(AppScreen.Bienvenida)
        private set

    // id (o línea) de una ruta pendiente de abrir en detalle tras navegar a Rutas.
    // Público con setter, como @Published en iOS: otras pantallas lo escriben.
    var rutaPendiente: String? by mutableStateOf(null)

    // Lugar pendiente de seleccionar al navegar a Mapa (seguridad: zonas de referencia).
    // Se limpia al consumirse; convive con rutaPendiente sin interferencia.
    var destinoPendiente: DestinoPendiente? by mutableStateOf(null)

    fun navigate(to: AppScreen) {
        currentScreen = to
    }

    fun reset() {
        currentScreen = AppScreen.MapaPrincipal
    }
}