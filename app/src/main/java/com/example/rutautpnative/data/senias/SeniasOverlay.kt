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

    private val controlador = PresentadorSenias(
        CoroutineScope(SupervisorJob() + Dispatchers.Main),
        { SeniasPrefs.estaActivoSync() }, { SeniasService.estado(it) })
    val presentacion get() = controlador.presentacion
    fun mostrarTexto(clave: String) = controlador.mostrarTexto(clave)
    fun ejecutarTrasVerSenia(clave: String, accion: () -> Unit) = controlador.ejecutarTrasVerSenia(clave, accion)
    fun mostrarActivacionDelModo() = controlador.mostrarActivacionDelModo()
    fun cerrarPorUsuario() = controlador.cerrarPorUsuario()
    fun activacionTerminada() = controlador.activacionTerminada()
    fun cancelarPorNavegacion() = controlador.cancelarPorNavegacion()
}

/** Generaciones impiden que resolver un clip antiguo habilite una accion nueva. */
internal class PresentadorSenias(
    private val scope: CoroutineScope,
    private val activo: () -> Boolean,
    private val resolver: suspend (String) -> EstadoSenia
) {
    private val _presentacion = MutableStateFlow<SeniasOverlay.Presentacion?>(null)
    val presentacion = _presentacion.asStateFlow()
    private var tarea: Job? = null
    private var generacion = 0L
    private var accionPendiente: (() -> Unit)? = null

    fun mostrarTexto(clave: String) {
        if (!activo()) return
        cancelarPorNavegacion()
        _presentacion.value = SeniasOverlay.Presentacion.Mini(clave)
    }
    fun ejecutarTrasVerSenia(clave: String, accion: () -> Unit) {
        if (!activo()) { accion(); return }
        if ((_presentacion.value as? SeniasOverlay.Presentacion.Accion)?.clave == clave) return
        cancelarPorNavegacion()
        val revision = generacion
        _presentacion.value = SeniasOverlay.Presentacion.Accion(clave)
        tarea = scope.launch {
            val estado = resolver(clave)
            if (revision != generacion || estado !is EstadoSenia.Clip) return@launch
            accionPendiente = accion
            delay(3_000)
            if (revision == generacion) cerrarPorUsuario()
        }
    }
    fun mostrarActivacionDelModo() {
        if (!activo()) return
        cancelarPorNavegacion()
        _presentacion.value = SeniasOverlay.Presentacion.Activacion("perfil.modo_senias")
    }
    fun cerrarPorUsuario() {
        val accion = accionPendiente
        cancelarPorNavegacion()
        accion?.invoke()
    }
    fun activacionTerminada() {
        if (_presentacion.value is SeniasOverlay.Presentacion.Activacion) cancelarPorNavegacion()
    }
    fun cancelarPorNavegacion() {
        generacion++
        tarea?.cancel(); tarea = null
        accionPendiente = null
        _presentacion.value = null
    }
}
