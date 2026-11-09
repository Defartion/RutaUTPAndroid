package com.example.rutautpnative.data.senias

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

//----Presentador global del Modo Señas (puerto del SeniasPresenter.swift)----
// Tres presentaciones, cada una con identidad propia (cambiar de clave o de
// pantalla descarta finales y acciones de la anterior):
//   Mini: mini-reproductor en bucle (señas consultadas desde TEXTOS).
//   Accion: tarjeta 3 s y luego ejecuta la acción UNA sola vez (BOTONES
//           señables). Cerrar la tarjeta ADELANTA la acción; una segunda
//           pulsación no reinicia la cuenta; elegir otra acción o salir de la
//           pantalla cancela la anterior SIN ejecutarla.
//   Activacion: video de encendido del modo (media pantalla, cierra al
//           terminar; el modo queda activo y el contexto visible alrededor).
// Solo hace algo si el Modo Señas está activo (persistido).
object SeniasOverlay {

    //----Presentación vigente (la ventana de UI la dibuja la raíz)----
    sealed class Presentacion {
        abstract val clave: String
        /** Mini-reproductor en bucle (texto señable). */
        data class Mini(override val clave: String) : Presentacion()
        /** Tarjeta de 3 s -> acción (botón señable). */
        data class Accion(override val clave: String) : Presentacion()
        /** Video de activación del modo (una vez, al terminar se cierra). */
        data class Activacion(override val clave: String) : Presentacion()
    }

    private const val DURACION_TARJETA_MS = 3_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _presentacion = MutableStateFlow<Presentacion?>(null)
    val presentacion: StateFlow<Presentacion?> = _presentacion.asStateFlow()

    private var tareaAccion: Job? = null
    private var accionPendiente: (() -> Unit)? = null
    private var claveAccionActiva: String? = null
    private var resolucionEnCurso = false

    /** Seña de texto: mini-reproductor en bucle (sin acción). */
    fun mostrarTexto(clave: String) {
        if (!SeniasPrefs.estaActivoSync()) return
        // Elegir otra seña cancela una acción en curso, sin ejecutarla.
        cancelarAccion()
        _presentacion.value = Presentacion.Mini(clave)
    }

    /**
     * Botón señable: muestra la tarjeta con el clip 3 s y luego ejecuta la
     * acción UNA sola vez. Sin Modo Señas, ejecuta la acción directamente.
     * Si el clip está PENDIENTE, muestra el aviso y NO ejecuta (no inventa).
     */
    fun ejecutarTrasVerSenia(clave: String, accion: () -> Unit) {
        if (!SeniasPrefs.estaActivoSync()) {
            accion()
            return
        }
        // Segunda pulsación con la MISMA tarjeta ya resuelta: no reinicia.
        if (_presentacion.value is Presentacion.Accion && claveAccionActiva == clave &&
            !resolucionEnCurso
        ) return
        // Otra acción distinta cancela la anterior sin ejecutarla.
        cancelarAccion()
        _presentacion.value = Presentacion.Accion(clave)
        resolucionEnCurso = true
        scope.launch {
            // Resuelve el clip ANTES de armar el temporizador: con pendiente,
            // la acción muere aquí y el usuario solo ve el aviso con su X.
            val estado = SeniasService.estado(clave)
            resolucionEnCurso = false
            if (_presentacion.value !is Presentacion.Accion) return@launch  // se canceló en el camino
            claveAccionActiva = clave
            if (estado !is EstadoSenia.Clip) {
                accionPendiente = null
                return@launch
            }
            accionPendiente = accion
            tareaAccion = scope.launch {
                delay(DURACION_TARJETA_MS)
                if (_presentacion.value is Presentacion.Accion && claveAccionActiva == clave) {
                    ejecutarAccionPendiente()
                }
            }
        }
    }

    /** Video de activación del modo (al encender el interruptor de Perfil). */
    fun mostrarActivacionDelModo() {
        if (!SeniasPrefs.estaActivoSync()) return
        cancelarAccion()
        _presentacion.value = Presentacion.Activacion("perfil.modo_senias")
    }

    /** Cierre por el usuario (X): en la tarjeta de acción ADELANTA la acción. */
    fun cerrarPorUsuario() {
        if (_presentacion.value is Presentacion.Accion) {
            tareaAccion?.cancel()
            tareaAccion = null
            ejecutarAccionPendiente()
        } else {
            _presentacion.value = null
        }
    }

    /** Fin natural del video de activación: solo cierra (el modo queda activo). */
    fun activacionTerminada() {
        if (_presentacion.value is Presentacion.Activacion) _presentacion.value = null
    }

    /**
     * Cambio de pantalla o paso a segundo plano: cancela la presentación y
     * la acción pendiente SIN ejecutarla (iOS idem).
     */
    fun cancelarPorNavegacion() {
        cancelarAccion()
        _presentacion.value = null
    }

    private fun ejecutarAccionPendiente() {
        val accion = accionPendiente
        accionPendiente = null
        claveAccionActiva = null
        _presentacion.value = null
        accion?.invoke()
    }

    private fun cancelarAccion() {
        tareaAccion?.cancel()
        tareaAccion = null
        accionPendiente = null
        claveAccionActiva = null
    }
}
