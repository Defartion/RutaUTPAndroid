package com.example.rutautpnative.data.ubicacion

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

//----Servicio de ubicacion COMPARTIDO----
// Puerto de `Services/Location/LocationService.swift` (iOS, rama 3D-BUS).
//
// Pieza clave del diseno iOS: una UNICA instancia en toda la app con CONTEO DE
// CONSUMIDORES. Lo usan a la vez el mapa, el rastreo pasivo (fase 5) y quien
// haga falta; el GPS solo se apaga cuando se va el ULTIMO consumidor. Con una
// instancia por pantalla habria dos clientes de ubicacion en paralelo pidiendo
// la misma precision.
//
// Equivalencias con iOS:
//   - distanceFilter 5 m / kCLLocationAccuracyBest
//       -> PRIORITY_HIGH_ACCURACY + minUpdateDistanceMeters 5f
//   - currentLocation() -> AsyncStream    -> currentLocation() -> Flow (callbackFlow)
//   - stopUpdating() no apaga con consumidores vivos -> pararSiNoHayConsumidores()
//   - al denegar el permiso se fuerza el apagado   -> onPermissionRevoked()
//
// El permiso NO se pide desde aqui (en Android el dialogo sale de un
// Activity/Composable con Accompanist); el flujo simplemente no emite hasta
// que `authorizationStatus` sea Autorizado.
object LocationService {

    //----Estado de autorizacion (reactivo para la UI)----
    sealed interface EstadoAutorizacion {
        data object SinDeterminar : EstadoAutorizacion
        data object Autorizado : EstadoAutorizacion
        data object Denegado : EstadoAutorizacion
    }

    //----Estado interno----
    private var appContext: Context? = null
    private val lock = Any()

    // Consumidores vivos: cada coleccionista del Flow registra un emisor.
    // CopyOnWriteArrayList porque el callback del GPS llega por el hilo del
    // looper principal y el alta/baja llega por hilos de corrutinas.
    private data class Consumidor(val id: UUID, val emitir: (Location) -> Unit)

    private val consumidores = CopyOnWriteArrayList<Consumidor>()

    private var pidiendoUpdates = false
    private var ultimaPosicion: Location? = null

    private val _authorizationStatus =
        MutableStateFlow<EstadoAutorizacion>(EstadoAutorizacion.SinDeterminar)

    val authorizationStatus: StateFlow<EstadoAutorizacion> get() = _authorizationStatus

    //----Configuracion (se llama en MainActivity.onCreate, como el resto de stores)----
    fun init(context: Context) {
        appContext = context.applicationContext
        refrescarEstadoAutorizacion()
    }

    //----Consultas----

    val estaAutorizado: Boolean
        get() = _authorizationStatus.value == EstadoAutorizacion.Autorizado

    /// Refresca el estado del permiso (llamar en Activity.onResume: el usuario
    /// puede haber concedido/denegado desde Ajustes con la app en segundo plano).
    fun refrescarEstadoAutorizacion() {
        val ctx = appContext ?: return
        val fino = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION)
        val grueso = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        val estado = if (fino == PackageManager.PERMISSION_GRANTED || grueso == PackageManager.PERMISSION_GRANTED) {
            EstadoAutorizacion.Autorizado
        } else {
            EstadoAutorizacion.Denegado
        }
        if (estado == EstadoAutorizacion.Denegado) onPermissionRevoked()
        _authorizationStatus.value = estado
    }

    //----Flujo continuo de posiciones----
    //
    // Como el AsyncStream de iOS: cada coleccionista recibe su propio flujo,
    // la ultima posicion conocida se emite DE INMEDIATO al suscribirse, y el
    // GPS se enciende con el primero y se apaga con el ultimo en irse
    // (awaitClose baja el consumidor; pararSiNoHayConsumidores decide).
    fun currentLocation(): Flow<Location> = callbackFlow {
        val id = UUID.randomUUID()
        synchronized(lock) {
            consumidores.add(Consumidor(id) { l -> trySend(l) })
            // Emite lo conocido al instante: la UI no espera al primer fix
            // para dibujar el marcador (igual que lastKnownLocation en iOS).
            // DENTRO del lock: ultimaPosicion la escribe el callback del GPS
            // en otro hilo; leerla sin lock es una carrera benigna pero
            // inconsistente con el resto del archivo.
            ultimaPosicion?.let { trySend(it) }
        }

        arrancarSiHaceFalta()

        awaitClose {
            synchronized(lock) { consumidores.removeAll { it.id == id } }
            pararSiNoHayConsumidores()
        }
    }

    //----Arranque/parada con conteo----

    /// Arranca las actualizaciones si no estaban activas (idempotente).
    @SuppressLint("MissingPermission") // la guarda esta en tienePermiso()
    private fun arrancarSiHaceFalta() {
        if (pidiendoUpdates || !tienePermiso()) return
        val ctx = appContext ?: return
        val fused = LocationServices.getFusedLocationProviderClient(ctx)

        // distanceFilter 5 m del iOS; el intervalo corto deja que el filtro
        // de distancia manda (FusedLocation ya agrupa igual que CoreLocation).
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateDistanceMeters(5f)
            .build()

        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
            pidiendoUpdates = true
        } catch (_: SecurityException) {
            // El permiso se revoco entre la guarda y la peticion: no crash,
            // el flujo queda vivo pero en silencio hasta que se autorice.
        }
    }

    /// Apaga el GPS SOLO si no queda nadie escuchando (piezas clave del iOS).
    private fun pararSiNoHayConsumidores() {
        if (consumidores.isEmpty() && pidiendoUpdates) {
            appContext?.let { LocationServices.getFusedLocationProviderClient(it).removeLocationUpdates(callback) }
            pidiendoUpdates = false
        }
    }

    /// Permiso revocado o denegado: apagado total, como `forceStopUpdating()`
    /// de iOS (alli cierra los streams con finish; aqui los emisores quedan
    /// registrados pero sin fuente, y la UI reacciona al estado de autorizacion).
    private fun onPermissionRevoked() {
        if (pidiendoUpdates) {
            appContext?.let { LocationServices.getFusedLocationProviderClient(it).removeLocationUpdates(callback) }
            pidiendoUpdates = false
        }
    }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            ultimaPosicion = loc
            // Copia defensiva: los consumidores se dan de baja desde otros hilos.
            val vivos = synchronized(lock) { consumidores.toList() }
            vivos.forEach { it.emitir(loc) }
        }
    }

    private fun tienePermiso(): Boolean {
        val ctx = appContext ?: return false
        val fino = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION)
        val grueso = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        return fino == PackageManager.PERMISSION_GRANTED || grueso == PackageManager.PERMISSION_GRANTED
    }
}
