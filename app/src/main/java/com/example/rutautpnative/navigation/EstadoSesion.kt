package com.example.rutautpnative.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Un resultado solo puede modificar la selección que originó su solicitud. */
internal class ControlSeleccion<T> {
    var seleccion: T? by mutableStateOf(null)
        private set
    var revision: Long = 0
        private set
    fun invalidar(): Long { revision += 1; return revision }
    fun vigente(token: Long): Boolean = token == revision
    fun elegir(value: T) { invalidar(); seleccion = value }
    fun limpiar() { invalidar(); seleccion = null }
    fun aceptar(token: Long, value: T): Boolean {
        if (!vigente(token)) return false
        elegir(value)
        return true
    }
}

enum class TipoViaje { DECLARADO, TRACKING, NAVEGACION }

/** El segundo plano y el cambio de pestaña no terminan un viaje. */
class EstadoViajes {
    private var activos by mutableStateOf<Set<TipoViaje>>(emptySet())
    val enCurso: Boolean get() = activos.isNotEmpty()
    fun actualizar(tipo: TipoViaje, activo: Boolean) {
        activos = if (activo) activos + tipo else activos - tipo
    }
    fun contiene(tipo: TipoViaje): Boolean = tipo in activos
}

object ViajesActivos { val estado = EstadoViajes() }

enum class TipoNotificacion { RECORDATORIO, CAMBIO_RUTA, CONSEJO }
data class AccionNotificacion(
    val id: String,
    val tipo: TipoNotificacion,
    val destinoID: String? = null,
    val rutaID: String? = null,
    val venceEn: Double,
    val consejo: Int? = null,
    val configuracionID: String? = null
)
enum class DecisionNotificacion { ABRIR, VIAJE_EN_CURSO, NO_DISPONIBLE }

fun decidirNotificacion(accion: AccionNotificacion, viajeActivo: Boolean,
                        destinoValido: Boolean, ahora: Double): DecisionNotificacion = when {
    !accion.venceEn.isFinite() || accion.venceEn <= ahora || !destinoValido -> DecisionNotificacion.NO_DISPONIBLE
    viajeActivo -> DecisionNotificacion.VIAJE_EN_CURSO
    else -> DecisionNotificacion.ABRIR
}
