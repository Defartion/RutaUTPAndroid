package com.example.rutautpnative.data.gtfs

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

//----Repositorio GTFS----
// Carga el feed GTFS estático embebido (assets/gtfs/*.txt) y lo convierte en
// modelos de dominio `RutaGTFS`. Portado de `GTFSRepository.swift`.
//
// - La carga es async e idempotente (se parsea una sola vez, en un scope
//   propio que sobrevive a la cancelación de quien la pidió).
// - El feed es de Trujillo (-8.04, -79.05).
// - NO incluye GPS en vivo; ver RutaGTFS.kt.
object GTFSRepository {

    //----Campus UTP Trujillo (Av. Nicolás de Piérola 1221)----
    val coordenadaUTP = LatLng(-8.098247879173792, -79.03818104755645)

    //----Estado de carga----
    private var assetManager: AssetManager? = null

    // Scope de larga duración para la carga: no se cancela si el ViewModel que
    // disparó la primera petición se limpia. Equivale al `Task` del actor Swift.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var cache: List<RutaGTFS>? = null
    private var cargaActiva: Deferred<List<RutaGTFS>>? = null

    //----Configuración----
    // Se llama una vez (MainActivity.onCreate) antes de usar el repositorio.
    // Guarda el AssetManager de la aplicación para leer assets/gtfs.
    fun init(context: Context) {
        assetManager = context.applicationContext.assets
    }

    //----Consultas----

    /// Devuelve todas las rutas del feed, ordenadas por cercanía a UTP.
    suspend fun rutas(): List<RutaGTFS> {
        val am = assetManager ?: return emptyList()

        // Resuelve la carga compartida de forma idempotente: si ya hay caché la
        // usa; si hay una carga en curso la reutiliza; si no, arranca una nueva.
        val deferred = mutex.withLock {
            cache?.let { return it }
            cargaActiva ?: scope.async { parsearFeed(am) }.also { cargaActiva = it }
        }

        val resultado = deferred.await()
        mutex.withLock {
            cache = resultado
            cargaActiva = null
        }
        return resultado
    }

    /// Las `n` rutas cuyo recorrido pasa más cerca del campus UTP.
    suspend fun rutasCercaDeUTP(n: Int): List<RutaGTFS> = rutas().take(n)

    /// Rutas cuyo recorrido pasa a `radioMetros` o menos de `punto`, ordenadas
    /// por cercanía. La consulta "¿qué líneas pasan por aquí?".
    suspend fun rutasCercaDe(
        punto: LatLng,
        radioMetros: Double = 400.0
    ): List<RutaGTFS> {
        val todas = rutas()
        return todas
            .map { it to distanciaMinima(it.shape, punto) }
            .filter { it.second <= radioMetros }
            .sortedBy { it.second }
            .map { it.first }
    }

    //----Parseo del feed----

    private fun parsearFeed(am: AssetManager): List<RutaGTFS> = try {
        parsear(am)
    } catch (e: Exception) {
        Log.w("GTFS", "Error parseando feed: ${e.message}", e)
        emptyList()
    }

    private fun parsear(am: AssetManager): List<RutaGTFS> {
        // 1. Agencias (agency_id → nombre)
        val agency = GTFSCSV.parsear(leer(am, "agency"))
        val nombresAgencia = diccionario(agency.columna("agency_id"), agency.columna("agency_name"))

        // 2. Rutas
        val routeRows = GTFSParser.routeRows(leer(am, "routes"))

        // 3. Trips (en este feed: 1 trip por ruta, trip_id == route_id)
        val tripRows = GTFSParser.tripRows(leer(am, "trips"))
        val tripShape = tripRows.associate { it.tripId to it.shapeId }
        val routeTrip = tripRows.associate { it.routeId to it.tripId }

        // 4. Shapes agrupados por id y ordenados por secuencia
        val shapeRows = GTFSParser.shapeRows(leer(am, "shapes"))
        val shapesPorId = shapeRows
            .groupBy { it.shapeId }
            .mapValues { (_, pts) -> pts.sortedBy { it.sequence }.map { LatLng(it.lat, it.lon) } }

        // 5. Paraderos (stop_id → ParaderoGTFS)
        val stopRows = GTFSParser.stopRows(leer(am, "stops"))
        val paraderosPorId = stopRows.associate { it.stopId to ParaderoGTFS(it.stopId, it.stopName, it.lat, it.lon) }

        // 6. Stop times agrupados por trip
        val stopTimeRows = GTFSParser.stopTimeRows(leer(am, "stop_times"))
        val paradasPorTrip = stopTimeRows.groupBy { it.tripId }

        // 7. Frecuencias (headway) por trip
        val frequencyRows = GTFSParser.frequencyRows(leer(am, "frequencies"))
        val headwayPorTrip = frequencyRows.associate { it.tripId to it.headwaySecs }

        // 8. Tarifas: route_id → precio
        val fareAttributeRows = GTFSParser.fareAttributeRows(leer(am, "fare_attributes"))
        val precioPorFare = fareAttributeRows.associate { it.fareId to it.price }
        val fareRuleRows = GTFSParser.fareRuleRows(leer(am, "fare_rules"))
        val precioPorRuta = HashMap<String, Double>()
        for (rule in fareRuleRows) {
            precioPorFare[rule.fareId]?.let { precioPorRuta[rule.routeId] = it }
        }

        // 9. Armar el modelo de dominio
        return routeRows.map { r ->
            val (linea, variante) = GTFSNombreParser.lineaYVariante(r.routeShortName).let { it.linea to it.variante }

            val tripId = routeTrip[r.routeId] ?: r.routeId
            val shapeId = tripShape[tripId] ?: r.routeId
            val shapePuntos = shapesPorId[shapeId] ?: emptyList()

            val paradas = (paradasPorTrip[tripId] ?: emptyList()).sortedBy { it.stopSequence }
            val paraderos = paradas.mapNotNull { paraderosPorId[it.stopId] }

            // Duración del viaje según stop_times (última - primera salida)
            val primera = segundos(paradas.firstOrNull()?.departureTime ?: "")
            val ultima = segundos(paradas.lastOrNull()?.departureTime ?: "")
            val duracionMin = if (ultima >= primera) (ultima - primera) / 60 else 0

            val headwayMin = (headwayPorTrip[tripId] ?: 0) / 60

            val colorHex = r.routeColor.ifEmpty { "00CC00" }
            val recorrido = GTFSNombreParser.recorrido(r.routeLongName, r.routeShortName)

            RutaGTFS(
                id = r.routeId,
                linea = linea,
                variante = variante,
                recorrido = recorrido,
                empresa = nombresAgencia[r.agencyId] ?: "Transporte Trujillo",
                colorHex = colorHex,
                color = GTFSColor.colorRuta(colorHex),
                shape = shapePuntos,
                paraderos = paraderos,
                duracionMin = duracionMin,
                headwayMin = headwayMin,
                precio = precioPorRuta[r.routeId] ?: 0.0,
                distanciaKm = longitudTotalKm(shapePuntos),
                distanciaUTPMetros = distanciaMinima(shapePuntos, coordenadaUTP)
            )
        }.sortedBy { it.distanciaUTPMetros }
    }

    //----Helpers----

    private fun leer(am: AssetManager, nombre: String): String =
        am.open("gtfs/$nombre.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun diccionario(claves: List<String>, valores: List<String>): Map<String, String> {
        val resultado = mutableMapOf<String, String>()
        for (i in 0 until min(claves.size, valores.size)) {
            resultado[claves[i]] = valores[i]
        }
        return resultado
    }

    /// "01:17:24" → 4644 segundos (tolera horas > 24 como permite GTFS).
    private fun segundos(hhmmss: String): Int {
        val partes = hhmmss.split(":")
        if (partes.size != 3) return 0
        val h = partes[0].toIntOrNull() ?: return 0
        val m = partes[1].toIntOrNull() ?: return 0
        val s = partes[2].toIntOrNull() ?: return 0
        return h * 3600 + m * 60 + s
    }

    /// Distancia haversine en metros. Pública para que la UI (p.ej. RutasScreen)
    /// pueda calcular distancias a pie hasta un paradero sin duplicar la fórmula.
    fun distanciaMetros(a: LatLng, b: LatLng): Double {
        val radioTierra = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val latA = Math.toRadians(a.latitude)
        val latB = Math.toRadians(b.latitude)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(latA) * cos(latB) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * radioTierra * asin(min(1.0, sqrt(h)))
    }

    private fun distanciaMinima(puntos: List<LatLng>, destino: LatLng): Double {
        var minima = Double.POSITIVE_INFINITY
        for (p in puntos) {
            val d = distanciaMetros(p, destino)
            if (d < minima) minima = d
        }
        return if (minima.isInfinite()) 0.0 else minima
    }

    private fun longitudTotalKm(puntos: List<LatLng>): Double {
        if (puntos.size <= 1) return 0.0
        var total = 0.0
        for (i in 1 until puntos.size) {
            total += distanciaMetros(puntos[i - 1], puntos[i])
        }
        return (total / 1000.0).redondear(1)
    }

    private fun Double.redondear(lugares: Int): Double {
        val divisor = 10.0.pow(lugares)
        return (this * divisor).roundToInt() / divisor
    }
}