package com.example.rutautpnative.ui.screens.mapa

import android.os.SystemClock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rutautpnative.data.directions.DirectionsService
import com.example.rutautpnative.data.geo.PolylineMatching
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.data.places.PlacesService
import com.example.rutautpnative.data.routing.TransitPlanner
import com.example.rutautpnative.data.ubicacion.LocationService
import com.example.rutautpnative.ui.idioma.L
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

// Modelos

//----Bus animado sobre ruta real (puerto de `BusAnimado`, iOS rama 3D-BUS)----
// La posicion es SIMULADA (el feed estatico no trae GPS) pero la geometria es
// REAL: cada bus avanza por el shape GTFS de su linea, por DISTANCIA recorrida
// (no por fraccion de segmento) y rebota en los extremos del recorrido.
data class BusAnimado(
    val id: String,                       // "simulated-<route_id>"
    val linea: String,                    // "10", "C-01"
    val rutaId: String,                   // route_id GTFS: enlaza con el detalle de Rutas
    val empresa: String,
    val tipo: String,                     // "Bus"
    val variante: String,                 // variante original del feed, sin traducir
    val minutosLlegada: Int?,            // ETA derivado del headway
    val color: Color,                     // color de la linea GTFS
    val lat: Double,
    val lon: Double,
    val heading: Double,                  // rumbo de brujula [0,360); -1 desconocido
    val rutaCoordenadas: List<LatLng>,    // waypoints decimados
    val acumulados: DoubleArray,          // distancia acumulada por waypoint (m)
    val rumbosSegmento: DoubleArray,       // rumbo forward [0,360) de cada segmento
    val longitudTotalM: Double,
    val distanciaM: Double,               // avance sobre el shape (0...longitudTotalM)
    val velocidadMS: Double,              // crucero propia del vehiculo
    val isMovingForward: Boolean,
    val tramoActual: Int                  // cache del segmento [i, i+1]
) {
    // La variante del feed no se confunde con una matricula ni se traduce.
    val ramalTexto: String
        get() = if (variante.isEmpty()) L.t("S/D", "N/A") else L.t("Ramal $variante", "Branch $variante")

    // Flota simulada: "N MIN" (sin ~; el ~ queda para las posiciones reales).
    val etiquetaLlegada: String
        get() = if (minutosLlegada != null) "$minutosLlegada MIN" else L.t("SIN ETA", "NO ETA")
}

data class DestinoChip(
    val id: Int,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val lat: Double,
    val lon: Double
)

//----Sugerencia de autocompletado del buscador (Fase 4)----
data class SugerenciaLugar(
    val titulo: String,
    val subtitulo: String,
    val lat: Double,
    val lon: Double
)

// Modelo de vista
// NOTA (convención del proyecto): este ViewModel usa mutableStateOf de Compose
// (fue de los primeros escritos). Los ViewModels nuevos usan StateFlow puro
// (ver GuardadoViewModel/SeguridadViewModel). Migrar solo si hay refactor mayor.
class MapaViewModel : ViewModel() {

    var cameraPositionState = CameraPositionState(
        position = CameraPosition.fromLatLngZoom(GTFSRepository.coordenadaUTP, 14f)
    )

    //----Flota animada sobre shapes GTFS (Fase 2)----
    // flotaBuses: estado vivo NO publicado (muta a 20 Hz).
    // busesAnimados: instantanea publicada solo cuando algun bus se movio
    // >= 1.5 m o cambio de sentido (~7 publicaciones/seg a velocidad urbana),
    // para no redibujar el mapa 20 veces por segundo (igual que iOS).
    var busesAnimados by mutableStateOf<List<BusAnimado>>(emptyList())
        private set

    // Popup del bus tocado (marker 3D o BusCard del panel).
    var busSeleccionado by mutableStateOf<BusAnimado?>(null)
        private set

    fun seleccionarBus(bus: BusAnimado?) { busSeleccionado = bus }

    private var flotaBuses: List<BusAnimado> = emptyList()
    private var ultimasPublicadas: List<BusAnimado> = emptyList()

    var textoBusqueda by mutableStateOf("")

    //----Sugerencias del buscador (Fase 4)----
    var sugerencias by mutableStateOf<List<SugerenciaLugar>>(emptyList())
        private set

    var buscandoSugerencias by mutableStateOf(false)
        private set

    private var sugerenciasJob: Job? = null

    /// Consulta sugerencias con debounce. SOLO con el campo enfocado (igual
    /// que iOS): el texto que pone un chip, una sugerencia o un lugar externo
    /// no dispara consultas.
    fun actualizarTextoBusqueda(texto: String, campoEnfocado: Boolean) {
        textoBusqueda = texto
        sugerenciasJob?.cancel()
        val t = texto.trim()
        if (!campoEnfocado || t.length < 3) {
            sugerencias = emptyList()
            buscandoSugerencias = false
            return
        }
        buscandoSugerencias = true
        sugerenciasJob = viewModelScope.launch {
            delay(400)   // debounce
            when (val r = PlacesService.buscarSugerencias(t)) {
                is PlacesService.ResultadoSugerencias.Exito ->
                    sugerencias = r.sugerencias.map {
                        SugerenciaLugar(it.nombre, it.direccion, it.lat, it.lon)
                    }
                else -> sugerencias = emptyList()
            }
            buscandoSugerencias = false
        }
    }

    fun limpiarSugerencias() {
        sugerenciasJob?.cancel()
        sugerencias = emptyList()
        buscandoSugerencias = false
    }

    /// Tap en una sugerencia: entra por el mismo flujo que un lugar externo
    /// (camara + flota + itinerario). Equivale a seleccionarSugerencia iOS.
    fun seleccionarSugerencia(s: SugerenciaLugar) {
        limpiarSugerencias()
        seleccionarLugarExterno(titulo = s.titulo, lat = s.lat, lon = s.lon)
    }

    var destinoSeleccionado by mutableStateOf<DestinoChip?>(null)
        private set

    private var animacionJob: Job? = null

    // Destinos conocidos
    // Los iconos se asignan en MapaScreen para evitar dependencia de Compose aquí
    var destinos by mutableStateOf<List<DestinoChip>>(emptyList())

    // Rutas reales del feed cuyo recorrido pasa cerca del punto de anclaje actual
    // (destino seleccionado o campus UTP por defecto).
    var rutasCercanas by mutableStateOf<List<RutaGTFS>>(emptyList())
        private set

    //----Itinerario a pie + bus (Fase 3)----
    // Puerto del bloque `itinerario` del MapaViewModel.swift de iOS: plan
    // puerta-a-puerta caminata -> bus GTFS -> caminata.
    var itinerario by mutableStateOf<TransitPlanner.Plan?>(null)
        private set

    var calculandoItinerario by mutableStateOf(false)
        private set

    // "Necesitamos tu ubicacion..." / "No encontramos una linea directa...".
    var mensajeRuta by mutableStateOf<String?>(null)
        private set

    private var itinerarioJob: Job? = null

    //----GPS real (Fase 1)----
    // Posicion del usuario en vivo: nula hasta el primer fix. Dibuja el
    // marcador del mapa y sera el origen de rutas/itinerarios (fases 3+).
    var userRealCoordinate: LatLng? by mutableStateOf(null)
        private set

    private var gpsJob: Job? = null

    // Enciende el GPS si hay permiso (idempotente). El conteo de consumidores
    // del LocationService apaga el hardware cuando nadie escucha.
    fun iniciarGPS() {
        if (gpsJob?.isActive == true) return
        if (!LocationService.estaAutorizado) return
        gpsJob = viewModelScope.launch {
            var primerFixPendiente = userRealCoordinate == null
            LocationService.currentLocation().collect { loc ->
                userRealCoordinate = LatLng(loc.latitude, loc.longitude)
                // El destino pudo elegirse antes del primer fix (con el mensaje
                // de "necesitamos tu ubicacion"): al llegar el GPS se recalcula.
                if (primerFixPendiente) {
                    primerFixPendiente = false
                    destinoSeleccionado?.let { calcularRutaHacia(LatLng(it.lat, it.lon)) }
                }
            }
        }
    }

    fun detenerGPS() {
        gpsJob?.cancel()
        gpsJob = null
    }

    // Boton "Mi Ubicacion": recentra la camara sobre el usuario (o relanza el
    // GPS si aun no hay fix). Equivale a recenterOnUser() en iOS.
    fun recenterOnUser() {
        val pos = userRealCoordinate
        if (pos != null) {
            viewModelScope.launch {
                cameraPositionState.animate(
                    com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(pos, 16f)
                )
            }
        } else {
            iniciarGPS()
        }
    }

    // Al abrir el mapa (sin destino), muestra las líneas cercanas al campus UTP.
    init {
        viewModelScope.launch {
            actualizarRutasCercanas(GTFSRepository.coordenadaUTP)
        }
    }

    // Seleccion
    fun seleccionar(destino: DestinoChip) {
        if (destinoSeleccionado?.id == destino.id) return
        destinoSeleccionado = destino
        textoBusqueda = destino.label
        viewModelScope.launch {
            cameraPositionState.animate(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                    LatLng(destino.lat, destino.lon), 15f
                )
            )
        }
        // La flota nace de las lineas cercanas al nuevo ancla.
        viewModelScope.launch {
            actualizarRutasCercanas(LatLng(destino.lat, destino.lon))
        }
        // Itinerario puerta-a-puerta desde la posicion real del usuario.
        calcularRutaHacia(LatLng(destino.lat, destino.lon))
    }

    //----Calculo del itinerario (puerto de calcularRutaHacia, iOS)----
    fun calcularRutaHacia(destino: LatLng) {
        val origen = userRealCoordinate
        if (origen == null) {
            itinerario = null
            mensajeRuta = L.t(
                "Necesitamos tu ubicación para calcular la ruta. Activa la ubicación con el botón de la derecha.",
                "We need your location to calculate the route. Enable it with the button on the right."
            )
            return
        }
        itinerarioJob?.cancel()
        itinerarioJob = viewModelScope.launch {
            calculandoItinerario = true
            mensajeRuta = null
            itinerario = null
            try {
                val rutas = GTFSRepository.rutas()
                val plan = TransitPlanner.plan(origen, destino, rutas) { a, b -> caminataReal(a, b) }
                itinerario = plan
                if (plan == null) {
                    mensajeRuta = L.t(
                        "No encontramos una línea directa con paraderos a menos de 800 m de ambos extremos.",
                        "No direct line found with stops within 800 m of both ends."
                    )
                } else {
                    enfocarItinerario(plan)
                }
            } finally {
                calculandoItinerario = false
            }
        }
    }

    // Caminata real via Google Directions; si falla el planificador cae a
    // linea recta (aproximada). Distancia 0 = campo ausente -> recta.
    private suspend fun caminataReal(a: LatLng, b: LatLng): TransitPlanner.Caminata? {
        return when (val r = DirectionsService.rutaPeatonal(a, b)) {
            is DirectionsService.Resultado.Exito -> {
                val metros = if (r.distanciaMetros > 0) r.distanciaMetros.toDouble()
                else GTFSRepository.distanciaMetros(a, b)
                TransitPlanner.Caminata(r.puntos, metros, aproximada = false)
            }
            else -> null
        }
    }

    // Encuadra la camara en el plan completo (equivalente al
    // itinerarioFocusTick de iOS, que ajusta el boundingRect del itinerario).
    private fun enfocarItinerario(plan: TransitPlanner.Plan) {
        val builder = LatLngBounds.builder()
        plan.walkToBoard.forEach(builder::include)
        plan.busDibujo.forEach(builder::include)
        plan.walkToDestination.forEach(builder::include)
        viewModelScope.launch {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(builder.build(), 120))
        }
    }

    // Selecciona un lugar que llega de fuera del mapa (router.destinoPendiente,
    // p.ej. zonas de Seguridad). Se trata como si el usuario lo hubiera buscado
    // manualmente: mismo flujo de seleccionar(DestinoChip).
    fun seleccionarLugarExterno(titulo: String, lat: Double, lon: Double) {
        seleccionar(
            DestinoChip(
                id = "ext|$titulo|$lat|$lon".hashCode(),
                label = titulo,
                icon = Icons.Filled.Place,
                lat = lat,
                lon = lon
            )
        )
    }

    fun buscarTexto(texto: String) {
        val t = texto.trim()
        if (t.isEmpty()) return
        // 1. Coincidencia directa con un chip.
        destinos.firstOrNull { it.label.lowercase().contains(t.lowercase()) }
            ?.let { seleccionar(it); return }
        // 2. Sugerencia ya cargada en el dropdown.
        sugerencias.firstOrNull()?.let { seleccionarSugerencia(it); return }
        // 3. Consulta directa a Places (submit del teclado).
        viewModelScope.launch {
            when (val r = PlacesService.buscarTexto(t)) {
                is PlacesService.Resultado.Exito ->
                    seleccionarLugarExterno(r.nombre, r.lat, r.lon)
                else -> Unit  // sin resultados: el usuario sigue viendo el dropdown
            }
        }
    }

    fun limpiar() {
        textoBusqueda = ""
        destinoSeleccionado = null
        limpiarSugerencias()
        detenerAnimacion()
        flotaBuses = emptyList()
        busesAnimados = emptyList()
        busSeleccionado = null
        itinerarioJob?.cancel()
        itinerario = null
        mensajeRuta = null
        calculandoItinerario = false
        viewModelScope.launch {
            actualizarRutasCercanas(GTFSRepository.coordenadaUTP)
        }
        viewModelScope.launch {
            cameraPositionState.animate(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(GTFSRepository.coordenadaUTP, 14f)
            )
        }
    }

    // Carga las rutas cercanas a un punto: prueba 400 m y, si no encuentra,
    // amplía a 800 m para evitar "sin rutas" cuando el punto quedó algo lejos
    // del recorrido real. La flota se reconstruye sobre esas lineas.
    private suspend fun actualizarRutasCercanas(punto: LatLng) {
        var rutas = GTFSRepository.rutasCercaDe(punto, 400.0)
        if (rutas.isEmpty()) {
            rutas = GTFSRepository.rutasCercaDe(punto, 800.0)
        }
        rutasCercanas = rutas
        // La flota nace de estas lineas: un bus por linea cerca del ancla.
        reconstruirFlota(rutas, punto)
    }

    // TODO(fase-mejoras-futuras): en iOS el mapa principal no dibuja el shape de una
    // línea específica (solo la ruta de navegación al destino, vía Directions). Si en el
    // futuro se quiere mostrar el recorrido de una línea elegida directamente aquí, retomar
    // desde el historial de este archivo (se implementó y luego se revirtió a propósito).

    //----Simulacion de la flota (puerto del MapaViewModel.swift, rama 3D-BUS)----

    /// Reconstruye la flota a partir de las lineas que pasan cerca del ancla:
    /// un bus por linea, nacido repartido por el corredor alrededor del punto.
    private fun reconstruirFlota(rutas: List<RutaGTFS>, ancla: LatLng) {
        detenerAnimacion()
        busSeleccionado = null
        flotaBuses = busesDesde(rutas, ancla)
        ultimasPublicadas = emptyList()
        publicarSiCambio(forzar = true)
        if (flotaBuses.isNotEmpty()) iniciarAnimacion()
    }

    private fun busesDesde(rutas: List<RutaGTFS>, ancla: LatLng): List<BusAnimado> {
        val n = rutas.size.coerceAtLeast(1)
        return rutas.mapIndexedNotNull { index, ruta ->
            // 480 puntos (mejora intencional sobre los 240 del iOS): cuerdas de
            // ~18 m que siguen las curvas reales del feed sin cortar esquinas.
            val shape = PolylineMatching.decimate(ruta.shape, 480)
            if (shape.size < 2) return@mapIndexedNotNull null
            val acumulados = PolylineMatching.distanciasAcumuladas(shape)
            val total = acumulados.last()
            if (total <= 0.0) return@mapIndexedNotNull null

            // Rumbo de cada segmento (sentido forward). -1 = segmento degenerado.
            val rumbos = DoubleArray(shape.size - 1)
            for (i in rumbos.indices) {
                rumbos[i] = PolylineMatching.headingDegrees(shape[i], shape[i + 1])
            }

            // Nacimiento repartido por el corredor: waypoint mas cercano al
            // ancla + offsets escalonados por fracciones de Fibonacci (+-1.8 km)
            // + jitter aleatorio, envuelto modulo la longitud total. Las lineas
            // que comparten avenida quedan escalonadas, no amontonadas.
            var idxAncla = 0
            var minD = Double.MAX_VALUE
            for (i in shape.indices) {
                val d = GTFSRepository.distanciaMetros(shape[i], ancla)
                if (d < minD) { minD = d; idxAncla = i }
            }
            val fraccion = ((index + 1) * 0.6180339887) % 1.0
            val offsetM = (fraccion - 0.5) * 3600.0
            val jitter = Random.nextDouble(-120.0, 120.0)
            val nacimiento = (((acumulados[idxAncla] + offsetM + jitter) % total) + total) % total

            val velocidad = Random.nextDouble(7.0, 12.0)   // 25-43 km/h urbanos
            val haciaAdelante = Random.nextBoolean()

            // ETA simulado: derivado del headway del feed y el orden de la linea.
            val paso = max(1.0, ruta.headwayMin / n.toDouble())
            val minutos = max(1, (2 + index * paso).roundToInt())

            val p = proyectarEnShape(shape, acumulados, rumbos, nacimiento, haciaAdelante, 0)
            BusAnimado(
                id = "simulated-${ruta.id}",
                linea = ruta.linea,
                rutaId = ruta.id,
                empresa = ruta.empresa,
                tipo = "Bus",
                variante = ruta.variante,
                minutosLlegada = minutos,
                color = ruta.color,
                lat = p.lat,
                lon = p.lon,
                heading = p.heading,
                rutaCoordenadas = shape,
                acumulados = acumulados,
                rumbosSegmento = rumbos,
                longitudTotalM = total,
                distanciaM = nacimiento,
                velocidadMS = velocidad,
                isMovingForward = haciaAdelante,
                tramoActual = p.tramo
            )
        }
    }

    /// Interpolacion dentro del segmento donde cae `distancia` (busqueda
    /// incremental desde `tramoInicial`, no desde cero). El rumbo se
    /// INTERPOLA del segmento actual hacia el siguiente conforme avanza:
    /// el bus gira gradualmente al entrar en una curva en vez de mantenerse
    /// estatico y saltar 90 de golpe en la esquina.
    private data class PosicionInterpolada(
        val lat: Double,
        val lon: Double,
        val heading: Double,
        val tramo: Int
    )

    private fun proyectarEnShape(
        shape: List<LatLng>,
        acumulados: DoubleArray,
        rumbos: DoubleArray,
        distancia: Double,
        haciaAdelante: Boolean,
        tramoInicial: Int
    ): PosicionInterpolada {
        var i = tramoInicial.coerceIn(0, shape.size - 2)
        while (i < shape.size - 2 && distancia > acumulados[i + 1]) i++
        while (i > 0 && distancia < acumulados[i]) i--
        val a = acumulados[i]
        val b = acumulados[i + 1]
        val f = if (b > a) ((distancia - a) / (b - a)).coerceIn(0.0, 1.0) else 0.0
        val lat = shape[i].latitude + (shape[i + 1].latitude - shape[i].latitude) * f
        val lon = shape[i].longitude + (shape[i + 1].longitude - shape[i].longitude) * f

        val hActual = rumbos[i]
        val hSiguiente = if (i + 1 < rumbos.size) rumbos[i + 1] else hActual
        val rumboForward = when {
            hActual >= 0 && hSiguiente >= 0 -> lerpAngulo(hActual, hSiguiente, f)
            hActual >= 0 -> hActual
            hSiguiente >= 0 -> hSiguiente
            else -> -1.0
        }
        // La vuelta (rebote en el extremo) invierte el sentido de la marcha.
        val heading = if (haciaAdelante) rumboForward else ((rumboForward + 180.0) % 360.0)

        return PosicionInterpolada(lat, lon, heading, i)
    }

    /// Interpolacion de rumbos por el camino corto (maneja el salto 359->0:
    /// girar de 350 a 10 debe ser +20, no -340).
    private fun lerpAngulo(desde: Double, hasta: Double, t: Double): Double {
        val delta = ((hasta - desde + 540.0) % 360.0) - 180.0
        return (((desde + delta * t) % 360.0) + 360.0) % 360.0
    }

    /// Avanza la flota `dt` segundos y REBOTA en los extremos del recorrido
    /// (ida y vuelta por el mismo corredor, sin saltos ni congelamiento).
    private fun actualizarPosicionBuses(dt: Double) {
        if (flotaBuses.isEmpty()) return
        flotaBuses = flotaBuses.map { bus ->
            var haciaAdelante = bus.isMovingForward
            var distancia = bus.distanciaM + bus.velocidadMS * dt * (if (haciaAdelante) 1.0 else -1.0)
            if (distancia >= bus.longitudTotalM) {
                distancia = bus.longitudTotalM
                haciaAdelante = false
            } else if (distancia <= 0.0) {
                distancia = 0.0
                haciaAdelante = true
            }
            val p = proyectarEnShape(bus.rutaCoordenadas, bus.acumulados, bus.rumbosSegmento, distancia, haciaAdelante, bus.tramoActual)
            bus.copy(
                lat = p.lat,
                lon = p.lon,
                heading = p.heading,
                distanciaM = distancia,
                isMovingForward = haciaAdelante,
                tramoActual = p.tramo
            )
        }
        publicarSiCambio()
    }

    /// Publica la instantanea solo si algun bus se movio >= 1.5 m o cambio de
    /// sentido: sin esto, el mapa recompondria 20 veces por segundo.
    private fun publicarSiCambio(forzar: Boolean = false) {
        if (!forzar && flotaBuses.size == ultimasPublicadas.size) {
            val movio = flotaBuses.zip(ultimasPublicadas).any { (nueva, vieja) ->
                nueva.isMovingForward != vieja.isMovingForward ||
                        GTFSRepository.distanciaMetros(LatLng(nueva.lat, nueva.lon), LatLng(vieja.lat, vieja.lon)) >= 1.5
            }
            if (!movio) return
        }
        busesAnimados = flotaBuses
        ultimasPublicadas = flotaBuses
    }

    // Bucle de animacion a 20 Hz con dt real (cap 1 s tras pausas largas).
    private fun iniciarAnimacion() {
        detenerAnimacion()
        var ultimoTick: Long? = null
        animacionJob = viewModelScope.launch {
            while (isActive) {
                delay(50)
                val ahora = SystemClock.elapsedRealtime()
                val dt = ultimoTick?.let { ((ahora - it) / 1000.0).coerceAtMost(1.0) } ?: 0.0
                ultimoTick = ahora
                actualizarPosicionBuses(dt)
            }
        }
    }

    fun detenerAnimacion() {
        animacionJob?.cancel()
        animacionJob = null
    }

    /// Reanuda la flota y el GPS al volver a la pantalla. El ViewModel
    /// SOBREVIVE a la navegacion (la flota queda intacta), pero el bucle se
    /// detiene en onDispose al irse a otra pestaña (bateria); al recomponer
    /// la pantalla hay que encenderlo otra vez. Idempotente.
    fun reanudar() {
        if (flotaBuses.isNotEmpty() && animacionJob?.isActive != true) {
            iniciarAnimacion()
        }
        iniciarGPS()
    }

    override fun onCleared() {
        super.onCleared()
        detenerAnimacion()
        detenerGPS()
    }
}