package com.example.rutautpnative.data.tracking

import android.content.Context

//----Sesion de viaje (puerto del TripSession.swift)----
// Modelo listo para el backend: la sesion NO se persiste; se construye durante
// el viaje y se resume al llegar.
data class PuntoRecorrido(
    val lat: Double,
    val lon: Double,
    val heading: Double,
    val speed: Double,
    val timestamp: Double
)

enum class EstadoViaje {
    EN_PROGRESO, RECALCULANDO, COMPLETADO, CANCELADO
}

data class TripSession(
    val linea: String,
    val empresa: String,
    val origen: PuntoRecorrido,
    val destinoLat: Double,
    val destinoLon: Double,
    var estado: EstadoViaje = EstadoViaje.EN_PROGRESO,
    val startedAt: Double,
    var endedAt: Double? = null,
    val puntosRecorridos: MutableList<PuntoRecorrido> = mutableListOf()
)

//----Preferencias de la demo (tracking.radioParadero / tracking.velocidadDemo)----
// Puerto de las UserDefaults del iOS; mismas claves versionadas al estilo del
// inventario de Persistencia.swift.
object TrackingDemoPrefs {

    private const val PREFS = "rutautp.tracking"
    private const val RADIO = "tracking.radioParadero"
    private const val VELOCIDAD = "tracking.velocidadDemo"

    // Radio de paradero configurable del demo (iOS: 200/500/800/1600 m).
    // 1600 m llega donde el mapa principal ya llega por defecto: con el tope
    // en 800 el Tracking no encontraba rutas que el mapa si mostraba.
    val radiosDisponibles = listOf(200.0, 500.0, 800.0, 1600.0)

    fun leerRadio(context: Context, porDefecto: Double = 500.0): Double {
        val guardado = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(RADIO, porDefecto.toFloat()).toDouble()
        // No reescribe el original si esta danado: solo admite valores del
        // selector antes de usarlos en calculos (iOS idem).
        return if (guardado.isFinite() && guardado in radiosDisponibles) guardado else porDefecto
    }

    fun guardarRadio(context: Context, radio: Double) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat(RADIO, radio.toFloat()).apply()
    }

    // Multiplicador de velocidad del demo (1/3/10).
    val velocidadesDisponibles = listOf(1, 3, 10)

    fun leerVelocidad(context: Context, porDefecto: Int = 1): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(VELOCIDAD, porDefecto)

    fun guardarVelocidad(context: Context, velocidad: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(VELOCIDAD, velocidad).apply()
    }
}
