package com.example.rutautpnative.data.gtfs

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ResultadoCatalogo {
    data class Disponible(val rutas: List<RutaGTFS>) : ResultadoCatalogo
    data object InstitucionalPendiente : ResultadoCatalogo
    data object Error : ResultadoCatalogo
}

/** Un fallo no se memoriza como catálogo vacío; cada carpeta conserva su estado. */
internal class CargaCatalogo(private val scope: CoroutineScope, private val leer: suspend (String) -> ResultadoCatalogo) {
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, ResultadoCatalogo>()
    private val tareas = mutableMapOf<String, Deferred<ResultadoCatalogo>>()
    private val _estados = MutableStateFlow<Map<String, ResultadoCatalogo>>(emptyMap())
    val estados = _estados.asStateFlow()

    suspend fun cargar(carpeta: String, reintentar: Boolean = false): ResultadoCatalogo {
        val tarea = mutex.withLock {
            if (reintentar) cache.remove(carpeta)
            cache[carpeta]?.let { return it }
            if (tareas[carpeta]?.isCompleted == true) tareas.remove(carpeta)
            tareas.getOrPut(carpeta) {
                scope.async {
                    val resultado = try { leer(carpeta) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { ResultadoCatalogo.Error }
                    mutex.withLock {
                        _estados.value += carpeta to resultado
                        if (resultado != ResultadoCatalogo.Error) cache[carpeta] = resultado
                    }
                    resultado
                }
            }
        }
        return try { tarea.await() }
        finally { mutex.withLock { if (tarea.isCompleted && tareas[carpeta] === tarea) tareas.remove(carpeta) } }
    }
}
