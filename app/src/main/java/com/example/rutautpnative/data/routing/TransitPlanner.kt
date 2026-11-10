package com.example.rutautpnative.data.routing

import com.example.rutautpnative.data.geo.PolylineMatching
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.ParaderoGTFS
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

//----Planificador de transporte publico (a pie + bus + transbordo)----
// Puerto del `TransitPlanning.swift` de iOS (rama main). Planifica SOLO con el
// feed GTFS: nunca sustituye un micro por una ruta de auto. Politica:
//   - Viajes directos O CON UN TRANSBORDO (dos micros), respetando el orden
//     y el sentido de los paraderos de cada recorrido.
//   - Radio configurable de caminata por extremo (1600 m en Mapa; en Tracking
//     el selector ofrece 200/500/800/1600).
//   - Caminata maxima ENTRE micros: 350 m (`CAMINATA_MAX_TRANSBORDO`).
//   - Espera del segundo micro = headway/2 + 2 min (frecuencia del feed, no
//     una prediccion en vivo) + penalizacion de 180 s en el orden.
//   - Paradero a <= 80 m del shape GTFS; tramo de bus > 50 m.
//   - Velocidad del bus derivada del feed (distancia/duracion), clamp 3-14 m/s.
//   - Caminatas a 1.4 m/s. Las caminatas REALES se piden a un colaborador
//     inyectado (DirectionsService en la app, mock en pruebas); si falla,
//     queda la linea recta marcada como aproximada.
//   - Orden por coste y solo se materializan los 32 mejores candidatos; los
//     consumidores prueban hasta 4 con caminatas reales.
object TransitPlanner {

    //----Parametros (los mismos del iOS)----
    const val RADIO_PIE_METROS = 1600.0
    const val DISTANCIA_MAX_PARADERO_SHAPE = 80.0
    const val CAMINATA_MAX_TRANSBORDO = 350.0
    const val VELOCIDAD_PIE_MS = 1.4
    private const val VELOCIDAD_BUS_FALLBACK_MS = 7.0
    private const val DISTANCIA_MIN_TRAMO_BUS = 50.0
    private const val DISTANCIA_MIN_BUS_COMPARACION = 800.0
    private const val CANDIDATOS_A_PROBAR = 4
    private const val CANDIDATOS_A_MATERIALIZAR = 32
    private const val ESPERA_TRANSBORDO_BASE_S = 120.0
    private const val PENALIZACION_TRANSBORDO_S = 180.0
    private const val PUNTOS_DIBUJO_BUS = 250

    //----Modelo del plan (puerto del TransitItinerary de iOS)----

    data class Caminata(
        val puntos: List<LatLng>,
        val metros: Double,
        val aproximada: Boolean   // true = linea recta porque Directions fallo
    )

    // Tramo actual del viaje (JourneyLeg de ItineraryMetrics.swift).
    enum class TramoViaje { CAMINANDO_A_SUBIDA, EN_BUS, TRANSBORDANDO, EN_SEGUNDO_BUS, CAMINANDO_A_DESTINO }

    // Segundo micro del plan (TransitTransfer de iOS).
    data class Transbordo(
        val ruta: RutaGTFS,
        val board: ParaderoGTFS,
        val alight: ParaderoGTFS,
        val caminata: List<LatLng>,     // bajada 1 -> parada -> subida 2 (recta o Directions)
        val busDibujo: List<LatLng>,    // tramo del shape decimado para dibujar
        val busMetros: Double,
        val busVelocidadMS: Double
    ) {
        val caminataMetros: Double get() = PolylineMatching.totalLengthMeters(caminata)
    }

    data class Plan(
        val ruta: RutaGTFS,                  // primer micro
        val board: ParaderoGTFS,             // parada de subida
        val firstAlight: ParaderoGTFS,       // bajada del primer micro
        val transbordo: Transbordo?,         // segundo micro (null = directo)
        val walkToBoard: List<LatLng>,       // ruta peatonal real (o recta)
        val busDibujo: List<LatLng>,         // tramo del shape decimado (primer micro)
        val walkToDestination: List<LatLng>, // ruta peatonal real (o recta)
        val busMetros: Double,               // primer micro (iOS firstBusMeters)
        val busVelocidadMS: Double,          // primer micro
        val caminataAproximada: Boolean
    ) {
        val optionID: String get() = listOf(ruta.id, transbordo?.ruta?.id.orEmpty(), board.id, firstAlight.id, transbordo?.board?.id.orEmpty(), alight.id, busMetros.toString(), transbordo?.busMetros.toString()).joinToString("|")
        val esperaSubidaSeg: Double get() = max(0, ruta.headwayMin) * 30.0
        val viajeEstimadoSeg: Double get() = totalSegundos + esperaSubidaSeg
        val caminataTotalMetros: Double get() = caminataBoardMetros + caminataDestinoMetros + caminataTransbordoMetros
        val alight: ParaderoGTFS get() = transbordo?.alight ?: firstAlight
        val caminataBoardMetros: Double get() = PolylineMatching.totalLengthMeters(walkToBoard)
        val caminataDestinoMetros: Double get() = PolylineMatching.totalLengthMeters(walkToDestination)
        val caminataTransbordoMetros: Double get() = transbordo?.caminataMetros ?: 0.0
        val dentroDeLimiteTransbordo: Boolean get() = caminataTransbordoMetros <= CAMINATA_MAX_TRANSBORDO

        // Frecuencia del feed: espera media estimada del segundo micro
        // (headway/2 + 2 min), no una prediccion en vivo.
        val esperaTransbordoSeg: Double
            get() = transbordo?.let { max(0, it.ruta.headwayMin) * 30.0 + ESPERA_TRANSBORDO_BASE_S } ?: 0.0

        val busMetrosTotales: Double get() = busMetros + (transbordo?.busMetros ?: 0.0)

        val totalSegundos: Double
            get() = (caminataBoardMetros + caminataDestinoMetros + caminataTransbordoMetros) / VELOCIDAD_PIE_MS +
                busMetros / busVelocidadMS +
                (transbordo?.let { it.busMetros / it.busVelocidadMS } ?: 0.0) +
                esperaTransbordoSeg

        val etaMinutos: Int get() = max(1, ceil(totalSegundos / 60.0).toInt())

        /// Tramo del viaje tras recorrer `metrosRecorridos` (journeyLeg de iOS).
        fun tramoEn(metrosRecorridos: Double): TramoViaje {
            if (metrosRecorridos < caminataBoardMetros) return TramoViaje.CAMINANDO_A_SUBIDA
            val finPrimerBus = caminataBoardMetros + busMetros
            if (metrosRecorridos < finPrimerBus) return TramoViaje.EN_BUS
            transbordo?.let {
                val finCaminata = finPrimerBus + it.caminataMetros
                if (metrosRecorridos < finCaminata) return TramoViaje.TRANSBORDANDO
                if (metrosRecorridos < finCaminata + it.busMetros) return TramoViaje.EN_SEGUNDO_BUS
            }
            return TramoViaje.CAMINANDO_A_DESTINO
        }

        /// Segundos restantes tras avanzar `metrosRecorridos` del plan
        /// (remainingSeconds de ItineraryMetrics.swift).
        fun segundosRestantes(metrosRecorridos: Double): Double {
            var recorridos = max(0.0, metrosRecorridos)
            var segundos = 0.0
            val tramos = mutableListOf<Pair<Double, Double>>()   // (distancia, velocidad)
            tramos.add(caminataBoardMetros to VELOCIDAD_PIE_MS)
            tramos.add(busMetros to busVelocidadMS)
            if (transbordo != null) {
                tramos.add(transbordo.caminataMetros to VELOCIDAD_PIE_MS)
                tramos.add(transbordo.busMetros to transbordo.busVelocidadMS)
                if (metrosRecorridos < caminataBoardMetros + busMetros + transbordo.caminataMetros) {
                    segundos += esperaTransbordoSeg
                }
            }
            tramos.add(caminataDestinoMetros to VELOCIDAD_PIE_MS)
            for ((distancia, velocidad) in tramos) {
                segundos += max(0.0, distancia - recorridos) / velocidad
                recorridos = max(0.0, recorridos - distancia)
            }
            return segundos
        }
    }

    // Una caminata real: devuelve null si no se pudo calcular (sin red,
    // Directions fallo, etc.) — el planificador cae a linea recta.
    fun interface CaminatasReales {
        suspend fun calcular(desde: LatLng, hasta: LatLng): Caminata?
    }

    /// Devuelve el mejor plan `origen -> destino`, directo o con un
    /// transbordo, o null si ninguna combinacion del feed cumple la politica.
    suspend fun plan(
        origen: LatLng,
        destino: LatLng,
        rutas: List<RutaGTFS>,
        radioMetros: Double = RADIO_PIE_METROS,
        caminatas: CaminatasReales
    ): Plan? {
        val candidatos = candidatos(origen, destino, rutas, radioMetros)
        // Los 4 mejores por coste; se acepta el primero cuyas caminatas
        // reales quepan en el radio (y la conexion, en el limite).
        for (candidato in candidatos.take(CANDIDATOS_A_PROBAR)) {
            val resuelto = conCaminatasReales(candidato, caminatas)
            if (resuelto.caminataBoardMetros > radioMetros) continue
            if (resuelto.caminataDestinoMetros > radioMetros) continue
            if (!resuelto.dentroDeLimiteTransbordo) continue
            return resuelto
        }
        return null
    }

    /// Geometria del plan lista para dibujar o recorrer: caminata 1 + bus 1 +
    /// (caminata del transbordo + bus 2) + caminata final.
    fun puntosCombinados(plan: Plan): List<LatLng> = buildList {
        addAll(plan.walkToBoard)
        addAll(plan.busDibujo)
        plan.transbordo?.let {
            addAll(it.caminata)
            addAll(it.busDibujo)
        }
        addAll(plan.walkToDestination)
    }

    enum class Categoria { RECOMENDADA, MAS_RAPIDA, SUBIDA_CERCANA }
    data class Destacada(val plan: Plan, val categorias: Set<Categoria>)
    data class Opciones(val alternativas: List<Plan>, val destacadas: List<Destacada>)

    suspend fun candidatos(origen: LatLng, destino: LatLng, rutas: List<RutaGTFS>, radioMetros: Double): List<Plan> =
        buscar(origen, destino, rutas, radioMetros)

    suspend fun opciones(origen: LatLng, destino: LatLng, rutas: List<RutaGTFS>, radioMetros: Double = RADIO_PIE_METROS): Opciones {
        val todas = buscar(origen, destino, rutas, radioMetros, comparar = true)
        val cercana = todas.minWithOrNull(compareBy({ it.caminataBoardMetros }, { it.viajeEstimadoSeg }, { it.optionID }))
        val rapida = todas.minWithOrNull(compareBy({ it.viajeEstimadoSeg }, { it.optionID }))
        val ganadoras = listOfNotNull(cercana?.let { it to Categoria.RECOMENDADA }, rapida?.let { it to Categoria.MAS_RAPIDA }, cercana?.let { it to Categoria.SUBIDA_CERCANA })
        val destacadas = ganadoras.groupBy { it.first.optionID }.values.map { pares -> Destacada(pares.first().first, pares.map { it.second }.toSet()) }
        return Opciones(todas, destacadas)
    }

    /** Política de iOS para comparar un transbordo con la mejor línea directa.
     * La búsqueda de candidatos de tracking conserva su política independiente.
     */
    internal fun transbordoUtilParaComparar(directoMasRapidoSeg: Double?, transbordoSeg: Double): Boolean {
        if (directoMasRapidoSeg == null) return true
        val ahorro = directoMasRapidoSeg - transbordoSeg
        return directoMasRapidoSeg >= 45 * 60 && ahorro >= max(15 * 60.0, directoMasRapidoSeg * 0.25)
    }

    //----Candidatos puros sobre el feed (sin red)----
    // Puerto de `TransitItinerary.candidates` / `search` del iOS: barridos de
    // mejor subida/bajada por linea + rejilla espacial 3x3 para emparejar la
    // bajada del primer micro con la subida del segundo.
    private suspend fun buscar(
        origen: LatLng,
        destino: LatLng,
        rutas: List<RutaGTFS>,
        radioMetros: Double,
        comparar: Boolean = false
    ): List<Plan> = withContext(Dispatchers.Default) {
        if (!radioMetros.isFinite() || radioMetros <= 0.0 ||
            !origen.latitude.isFinite() || !origen.longitude.isFinite() ||
            !destino.latitude.isFinite() || !destino.longitude.isFinite()
        ) {
            return@withContext emptyList()
        }
        podarCache(rutas.map { it.id }.toSet())

        val preparadas = ArrayList<RutaPreparada>()
        for (ruta in rutas) {
            currentCoroutineContext().ensureActive()
            if (ruta.shape.size < 2 || ruta.paraderos.size < 2) continue
            val startWalk = DoubleArray(ruta.paraderos.size) {
                GTFSRepository.distanciaMetros(origen, ruta.paraderos[it].coordinate)
            }
            val endWalk = DoubleArray(ruta.paraderos.size) {
                GTFSRepository.distanciaMetros(ruta.paraderos[it].coordinate, destino)
            }
            // Lineas que no pasan cerca de NINGUN extremo quedan fuera.
            if (!(startWalk.any { it <= radioMetros } || endWalk.any { it <= radioMetros })) continue
            preparadas.add(RutaPreparada(ruta, geometriaDe(ruta), startWalk, endWalk, radioMetros))
        }

        // Celdas del tamano del limite de transbordo (+2% de margen): el
        // vecindario 3x3 garantiza cubrir toda conexion <= 350 m (iOS idem,
        // con MKMapPointsPerMeterAtLatitude; aqui la proyeccion es planar en
        // metros, misma garantia a la latitud de Trujillo).
        val tamanioCelda = CAMINATA_MAX_TRANSBORDO * 1.02
        val minimoBus = if (comparar) DISTANCIA_MIN_BUS_COMPARACION else DISTANCIA_MIN_TRAMO_BUS
        val salidas = preparadas.map { (it.mejoresSalidas(minimoBus) + if (comparar) it.salidasCercanas(minimoBus) else emptyList()).distinct() }
        val llegadas = preparadas.map { ruta ->
            ruta.mejoresLlegadas(minimoBus).groupBy { celdaDe(ruta.puntos[it.first], tamanioCelda) }
        }

        val elecciones = ArrayList<Eleccion>()
        val eleccionesCercanas = ArrayList<Eleccion>()
        for ((firstIndex, first) in preparadas.withIndex()) {
            currentCoroutineContext().ensureActive()
            // Directos: enumeracion completa de subidas x bajadas validas.
            for (board in first.boarding) {
                for (alight in first.arriving) {
                    if (!first.tramoValido(board, alight)) continue
                    val score = first.costoInicio(board, alight) + first.endWalk[alight] / VELOCIDAD_PIE_MS
                    elecciones.add(
                        Eleccion(firstIndex, board, alight, null, 0, alight, score, first.ruta.id, "$board:$alight")
                    )
                }
            }
            if (salidas[firstIndex].isEmpty()) continue
            // Transbordos: mejor combinacion por pareja de lineas, buscando
            // la subida del segundo micro en las celdas vecinas a la bajada.
            for ((secondIndex, second) in preparadas.withIndex()) {
                if (second.ruta.id == first.ruta.id || second.arriving.isEmpty()) continue
                var hayMejor = false
                var mejorBoard = 0; var mejorExit = 0; var mejorEnter = 0; var mejorAlight = 0
                var mejorScore = 0.0
                var cercana: Eleccion? = null
                var menorCaminata = Double.POSITIVE_INFINITY
                val cercanas = llegadas[secondIndex]
                for ((board, exit) in salidas[firstIndex]) {
                    currentCoroutineContext().ensureActive()
                    val costoPrimero = first.costoInicio(board, exit)
                    val celda = celdaDe(first.puntos[exit], tamanioCelda)
                    for (dx in -1..1) {
                        for (dy in -1..1) {
                            val entradas = cercanas[Celda(celda.x + dx, celda.y + dy)] ?: continue
                            for ((enter, alight) in entradas) {
                                val cotaInferior = costoPrimero + second.costoFin(enter, alight)
                                if (!comparar && hayMejor && cotaInferior >= mejorScore) continue
                                val caminata = GTFSRepository.distanciaMetros(
                                    first.ruta.paraderos[exit].coordinate,
                                    second.ruta.paraderos[enter].coordinate
                                )
                                if (caminata + first.conectores[exit] + second.conectores[enter] > CAMINATA_MAX_TRANSBORDO) continue
                                val score = cotaInferior + caminata / VELOCIDAD_PIE_MS
                                val aPie = first.startWalk[board] + first.conectores[board]
                                if (comparar && (aPie < menorCaminata || (aPie == menorCaminata && score < (cercana?.score ?: Double.POSITIVE_INFINITY)))) {
                                    menorCaminata = aPie
                                    cercana = Eleccion(firstIndex, board, exit, secondIndex, enter, alight, score, first.ruta.id + second.ruta.id, "$board:$exit:$enter:$alight")
                                }
                                if (!hayMejor || score < mejorScore) {
                                    hayMejor = true
                                    mejorBoard = board; mejorExit = exit; mejorEnter = enter; mejorAlight = alight
                                    mejorScore = score
                                }
                            }
                        }
                    }
                }
                cercana?.let { opcion ->
                    val espera = max(0, second.ruta.headwayMin) * 30.0 + ESPERA_TRANSBORDO_BASE_S
                    eleccionesCercanas.add(opcion.copy(score = opcion.score + espera + PENALIZACION_TRANSBORDO_S))
                }
                if (hayMejor) {
                    // Espera media del segundo micro + penalizacion fija.
                    val espera = max(0, second.ruta.headwayMin) * 30.0 + ESPERA_TRANSBORDO_BASE_S
                    elecciones.add(
                        Eleccion(
                            firstIndex, mejorBoard, mejorExit, secondIndex, mejorEnter, mejorAlight,
                            mejorScore + espera + PENALIZACION_TRANSBORDO_S,
                            first.ruta.id + second.ruta.id,
                            "$mejorBoard:$mejorExit:$mejorEnter:$mejorAlight"
                        )
                    )
                }
            }
        }

        // El orden usa las mismas distancias y penalizacion que el plan final.
        // Solo se materializan 32 opciones; los consumidores prueban hasta 4.
        val ordenadas = elecciones.sortedWith(compareBy({ it.score }, { it.order }, { it.tie }))
        if (!comparar) return@withContext ordenadas.take(CANDIDATOS_A_MATERIALIZAR).map { materializar(it, preparadas, origen, destino) }
        // El score incluye 180 s para ordenar transbordos. Ese margen no es
        // tiempo de viaje; la espera inicial sí lo es, igual que journeyTime en iOS.
        fun tiempoViaje(e: Eleccion): Double = e.score -
            (if (e.second == null) 0.0 else PENALIZACION_TRANSBORDO_S) +
            max(0, preparadas[e.first].ruta.headwayMin) * 30.0
        val directoMasRapido = ordenadas.filter { it.second == null }.minOfOrNull(::tiempoViaje)
        fun util(e: Eleccion): Boolean = e.second == null || transbordoUtilParaComparar(directoMasRapido, tiempoViaje(e))
        val elegibles = ordenadas.filter(::util)
        fun combinacion(e: Eleccion) = e.first to e.second
        val porLinea = elegibles.groupBy(::combinacion)
        val mejores = porLinea.values.map { grupo -> grupo.minBy { it.score + preparadas[it.first].ruta.headwayMin.coerceAtLeast(0) * 30.0 } }
        // Mantener cada línea directa y hasta 32 combinaciones con transbordo.
        // Añadir solo la subida global más cercana como destacado adicional.
        val seleccionadas = (mejores.filter { it.second == null } + mejores.filter { it.second != null }.take(32))
            .sortedWith(compareBy({ it.score }, { it.order }, { it.tie }))
        val cercana = (elegibles + eleccionesCercanas.filter(::util)).minWithOrNull(compareBy(
            { preparadas[it.first].startWalk[it.board] + preparadas[it.first].conectores[it.board] },
            { tiempoViaje(it) }, { it.order }, { it.tie }))
        (seleccionadas + listOfNotNull(cercana))
            .distinctBy { listOf(it.first, it.board, it.exit, it.second, it.enter, it.alight) }
            .map { materializar(it, preparadas, origen, destino) }
    }

    //----Resolucion de caminatas reales (withWalkingDirections de iOS)----
    // Como maximo tres peticiones simultaneas, solo para el candidato actual.
    suspend fun conCaminatasReales(plan: Plan, caminatas: CaminatasReales): Plan = coroutineScope {
        val origen = plan.walkToBoard.firstOrNull() ?: return@coroutineScope plan
        val destino = plan.walkToDestination.lastOrNull() ?: return@coroutineScope plan
        val primera = async { caminatas.calcular(origen, plan.board.coordinate) }
        val ultima = async { caminatas.calcular(plan.alight.coordinate, destino) }
        val transbordoReq = plan.transbordo?.let {
            async { caminatas.calcular(plan.firstAlight.coordinate, it.board.coordinate) }
        }
        val w1 = primera.await()
        val w2 = ultima.await()
        val wt = transbordoReq?.await()

        var resultado = plan
        if (w1 != null && w1.puntos.size >= 2) {
            resultado = resultado.copy(
                walkToBoard = listOf(origen) + w1.puntos + listOf(plan.board.coordinate, plan.busDibujo.first())
            )
        }
        if (w2 != null && w2.puntos.size >= 2) {
            val inicioBusFinal = plan.transbordo?.busDibujo?.lastOrNull() ?: plan.busDibujo.last()
            resultado = resultado.copy(
                walkToDestination = listOf(inicioBusFinal, plan.alight.coordinate) + w2.puntos + listOf(destino)
            )
        }
        var aproximada = w1 == null || w1.puntos.size < 2 || w2 == null || w2.puntos.size < 2
        if (plan.transbordo != null) {
            val t = plan.transbordo
            if (wt != null && wt.puntos.size >= 2) {
                resultado = resultado.copy(
                    transbordo = t.copy(
                        caminata = listOf(plan.busDibujo.last(), plan.firstAlight.coordinate) +
                            wt.puntos + listOf(t.board.coordinate, t.busDibujo.first())
                    )
                )
            } else {
                aproximada = true
            }
        }
        resultado.copy(caminataAproximada = aproximada)
    }

    /// Velocidad de crucero del bus derivada del feed: distancia/duracion,
    /// clamp 3-14 m/s. Sin duracion (o absurda) -> fallback 7 m/s.
    internal fun velocidadBus(ruta: RutaGTFS): Double {
        if (ruta.duracionMin <= 0 || ruta.distanciaKm <= 0) return VELOCIDAD_BUS_FALLBACK_MS
        val v = ruta.distanciaKm * 1000.0 / (ruta.duracionMin * 60.0)
        if (!v.isFinite()) return VELOCIDAD_BUS_FALLBACK_MS
        return v.coerceIn(3.0, 14.0)
    }

    /// Caminata real via Google Directions; si falla devuelve null y el
    /// planificador cae a linea recta marcandola aproximada.
    suspend fun caminataReal(a: LatLng, b: LatLng): Caminata? {
        return when (val r = com.example.rutautpnative.data.directions.DirectionsService.rutaPeatonal(a, b)) {
            is com.example.rutautpnative.data.directions.DirectionsService.Resultado.Exito -> {
                val metros = if (r.distanciaMetros > 0) r.distanciaMetros.toDouble()
                else GTFSRepository.distanciaMetros(a, b)
                Caminata(r.puntos, metros, aproximada = false)
            }
            else -> null
        }
    }

    //----Piezas internas del buscador----

    private data class Eleccion(
        val first: Int,
        val board: Int,
        val exit: Int,
        val second: Int?,
        val enter: Int,
        val alight: Int,
        val score: Double,
        val order: String,
        val tie: String
    )

    private data class Celda(val x: Int, val y: Int)

    // Punto proyectado a un plano local en metros (proyeccion planar por
    // grado; misma para todas las busquedas para poder cachear la geometria).
    private class PuntoProyectado(val x: Double, val y: Double)

    private fun celdaDe(p: PuntoProyectado, tamanio: Double) =
        Celda(floor(p.x / tamanio).toInt(), floor(p.y / tamanio).toInt())

    // Geometria inmutable reutilizable entre busquedas, invalidada si cambia
    // el feed aunque se repita el route_id (TransitRouteGeometry de iOS).
    private class GeometriaRuta(val ruta: RutaGTFS) {
        val indices: IntArray          // paradero -> waypoint mas cercano del shape
        val distancias: DoubleArray    // distancia acumulada del shape
        val conectores: DoubleArray    // paradero -> shape (m)
        val puntos: List<PuntoProyectado>

        init {
            val shape = ruta.shape
            indices = IntArray(ruta.paraderos.size)
            var lower = 0
            for (i in ruta.paraderos.indices) {
                val paradero = ruta.paraderos[i].coordinate
                var nearest = lower
                var mejorD = Double.MAX_VALUE
                for (j in lower until shape.size) {
                    val d = GTFSRepository.distanciaMetros(shape[j], paradero)
                    if (d < mejorD) {
                        mejorD = d
                        nearest = j
                    }
                }
                lower = nearest
                indices[i] = lower
            }
            conectores = DoubleArray(ruta.paraderos.size) {
                GTFSRepository.distanciaMetros(ruta.paraderos[it].coordinate, shape[indices[it]])
            }
            distancias = PolylineMatching.distanciasAcumuladas(shape)
            puntos = ruta.paraderos.map { PuntoProyectado(it.lon * PolylineMatching.METROS_POR_GRADO, it.lat * PolylineMatching.METROS_POR_GRADO) }
        }

        fun coincide(otra: RutaGTFS): Boolean {
            if (ruta.paraderos != otra.paraderos) return false
            if (ruta.shape.size != otra.shape.size) return false
            return ruta.shape.zip(otra.shape).all { (a, b) -> a.latitude == b.latitude && a.longitude == b.longitude }
        }
    }

    // Cache de geometria por route_id (equivalente del diccionario del actor
    // TransitPlanner de iOS). Se poda a las lineas de la busqueda actual y se
    // valida por contenido: no reutilizar paraderos proyectados sobre un shape
    // anterior.
    private val cacheGeometrias = HashMap<String, GeometriaRuta>()

    private fun geometriaDe(ruta: RutaGTFS): GeometriaRuta = synchronized(cacheGeometrias) {
        val existente = cacheGeometrias[ruta.id]
        if (existente != null && existente.coincide(ruta)) return existente
        val nueva = GeometriaRuta(ruta)
        cacheGeometrias[ruta.id] = nueva
        nueva
    }

    private fun podarCache(ids: Set<String>) {
        synchronized(cacheGeometrias) { cacheGeometrias.keys.retainAll(ids) }
    }

    private class PlanParcial(
        val board: ParaderoGTFS,
        val alight: ParaderoGTFS,
        val walkToBoard: List<LatLng>,
        val bus: List<LatLng>,            // tramo del shape entre ambos paraderos
        val walkToDestination: List<LatLng>
    )

    private fun materializar(
        eleccion: Eleccion,
        preparadas: List<RutaPreparada>,
        origen: LatLng,
        destino: LatLng
    ): Plan {
        val first = preparadas[eleccion.first]
        val primerPlan = first.planParcial(eleccion.board, eleccion.exit, origen, destino)
        var transbordo: Transbordo? = null
        var walkToDestination = primerPlan.walkToDestination
        if (eleccion.second != null) {
            val second = preparadas[eleccion.second]
            val next = second.planParcial(eleccion.enter, eleccion.alight, origen, destino)
            transbordo = Transbordo(
                ruta = second.ruta,
                board = next.board,
                alight = next.alight,
                caminata = listOf(
                    primerPlan.bus.last(),
                    primerPlan.alight.coordinate,
                    next.board.coordinate,
                    next.bus.first()
                ),
                busDibujo = PolylineMatching.decimate(next.bus, PUNTOS_DIBUJO_BUS),
                busMetros = PolylineMatching.totalLengthMeters(next.bus),
                busVelocidadMS = velocidadBus(second.ruta)
            )
            walkToDestination = next.walkToDestination
        }
        return Plan(
            ruta = first.ruta,
            board = primerPlan.board,
            firstAlight = primerPlan.alight,
            transbordo = transbordo,
            walkToBoard = primerPlan.walkToBoard,
            busDibujo = PolylineMatching.decimate(primerPlan.bus, PUNTOS_DIBUJO_BUS),
            walkToDestination = walkToDestination,
            busMetros = PolylineMatching.totalLengthMeters(primerPlan.bus),
            busVelocidadMS = velocidadBus(first.ruta),
            caminataAproximada = true   // recta hasta resolver Directions
        )
    }

    // Cada linea preparada con sus paraderos viables y sus costos
    // (PreparedTransitRoute de iOS).
    private class RutaPreparada(
        val ruta: RutaGTFS,
        geometria: GeometriaRuta,
        val startWalk: DoubleArray,
        val endWalk: DoubleArray,
        radio: Double
    ) {
        val indices = geometria.indices
        val distancias = geometria.distancias
        val conectores = geometria.conectores
        val puntos = geometria.puntos
        val velocidad = velocidadBus(ruta)
        val boarding: IntArray
        val arriving: IntArray

        init {
            boarding = ruta.paraderos.indices.filter { startWalk[it] + conectores[it] <= radio }.toIntArray()
            arriving = ruta.paraderos.indices.filter { endWalk[it] + conectores[it] <= radio }.toIntArray()
        }

        /// Barrido ordenado: mantener la mejor subida anterior evita volver a
        /// comparar todas las subidas en cada paradero y en cada pareja de
        /// lineas. Devuelve pares (mejor subida, bajada).
        fun mejoresSalidas(minimoMetros: Double = DISTANCIA_MIN_TRAMO_BUS): List<Pair<Int, Int>> {
            val elegibles = boarding.filter { conectores[it] < DISTANCIA_MAX_PARADERO_SHAPE }
            var cursor = 0
            var mejorParada = -1
            var mejorCosto = 0.0
            val resultado = ArrayList<Pair<Int, Int>>()
            for (exit in ruta.paraderos.indices) {
                while (cursor < elegibles.size) {
                    val board = elegibles[cursor]
                    if (!(board < exit && distancias[indices[exit]] - distancias[indices[board]] > minimoMetros)) break
                    val costo = (startWalk[board] + conectores[board]) / VELOCIDAD_PIE_MS - distancias[indices[board]] / velocidad
                    if (mejorParada < 0 || costo < mejorCosto) {
                        mejorParada = board
                        mejorCosto = costo
                    }
                    cursor++
                }
                if (conectores[exit] < DISTANCIA_MAX_PARADERO_SHAPE && mejorParada >= 0) {
                    resultado.add(mejorParada to exit)
                }
            }
            return resultado
        }

        fun salidasCercanas(minimoMetros: Double = DISTANCIA_MIN_TRAMO_BUS): List<Pair<Int, Int>> {
            val resultado = ArrayList<Pair<Int, Int>>()
            for (exit in ruta.paraderos.indices) {
                if (conectores[exit] >= DISTANCIA_MAX_PARADERO_SHAPE) continue
                val board = boarding.filter { tramoValido(it, exit) && distancias[indices[exit]] - distancias[indices[it]] > minimoMetros }
                    .minWithOrNull(compareBy({ startWalk[it] + conectores[it] }, { costoInicio(it, exit) }))
                if (board != null) resultado.add(board to exit)
            }
            return resultado
        }

        /// Espejo de mejoresSalidas hacia atras: pares (subida, mejor bajada).
        fun mejoresLlegadas(minimoMetros: Double = DISTANCIA_MIN_TRAMO_BUS): List<Pair<Int, Int>> {
            val elegibles = arriving.filter { conectores[it] < DISTANCIA_MAX_PARADERO_SHAPE }.reversed()
            var cursor = 0
            var mejorParada = -1
            var mejorCosto = 0.0
            val resultado = ArrayList<Pair<Int, Int>>()
            for (enter in ruta.paraderos.indices.reversed()) {
                while (cursor < elegibles.size) {
                    val alight = elegibles[cursor]
                    if (!(alight > enter && distancias[indices[alight]] - distancias[indices[enter]] > minimoMetros)) break
                    val costo = (endWalk[alight] + conectores[alight]) / VELOCIDAD_PIE_MS + distancias[indices[alight]] / velocidad
                    if (mejorParada < 0 || costo <= mejorCosto) {
                        mejorParada = alight
                        mejorCosto = costo
                    }
                    cursor++
                }
                if (conectores[enter] < DISTANCIA_MAX_PARADERO_SHAPE && mejorParada >= 0) {
                    resultado.add(enter to mejorParada)
                }
            }
            return resultado.asReversed()
        }

        fun tramoValido(board: Int, alight: Int): Boolean =
            alight > board &&
                distancias[indices[alight]] - distancias[indices[board]] > DISTANCIA_MIN_TRAMO_BUS &&
                conectores[board] < DISTANCIA_MAX_PARADERO_SHAPE &&
                conectores[alight] < DISTANCIA_MAX_PARADERO_SHAPE

        fun costoInicio(board: Int, alight: Int): Double =
            (startWalk[board] + conectores[board] + conectores[alight]) / VELOCIDAD_PIE_MS +
                (distancias[indices[alight]] - distancias[indices[board]]) / velocidad

        fun costoFin(board: Int, alight: Int): Double =
            (endWalk[alight] + conectores[board] + conectores[alight]) / VELOCIDAD_PIE_MS +
                (distancias[indices[alight]] - distancias[indices[board]]) / velocidad

        fun planParcial(board: Int, alight: Int, origen: LatLng, destino: LatLng): PlanParcial {
            val segmento = ruta.shape.subList(indices[board], indices[alight] + 1)
            return PlanParcial(
                board = ruta.paraderos[board],
                alight = ruta.paraderos[alight],
                walkToBoard = listOf(origen, ruta.paraderos[board].coordinate, segmento.first()),
                bus = segmento,
                walkToDestination = listOf(segmento.last(), ruta.paraderos[alight].coordinate, destino)
            )
        }
    }
}
