package com.example.rutautpnative.data.ubicacion

import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

//----GPS de una sola lectura----
// Antes duplicado en ParaderosIluminadosScreen y MapaUbicacionPicker.
// Lee una única posición con timeout; el llamador decide qué mostrar
// (mensajes de error, rencentrado, etc.) según el resultado.
//
// ANTI DOBLE CALLBACK: el timeout puede disparar justo cuando llega el
// success (compiten por onResultado). Con AtomicBoolean garantizamos que
// el callback se invoca UNA sola vez.
object UbicacionUnaVez {

    fun solicitar(
        context: Context,
        scope: CoroutineScope,
        timeoutMs: Long = 10_000,
        onResultado: (LatLng?) -> Unit
    ) {
        val resuelto = AtomicBoolean(false)
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val cancelacion = CancellationTokenSource()

        fun entregar(punto: LatLng?) {
            if (resuelto.compareAndSet(false, true)) onResultado(punto)
        }

        val timeout = scope.launch {
            delay(timeoutMs)
            cancelacion.cancel()
            entregar(null)
        }
        try {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancelacion.token)
            .addOnSuccessListener { location ->
                timeout.cancel()
                entregar(location?.let { LatLng(it.latitude, it.longitude) })
            }
            .addOnFailureListener {
                timeout.cancel()
                entregar(null)
            }
            .addOnCanceledListener { }
        } catch (_: SecurityException) {
            timeout.cancel()
            cancelacion.cancel()
            entregar(null)
        }
    }
}
