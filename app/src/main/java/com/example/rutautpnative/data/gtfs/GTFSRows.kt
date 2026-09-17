package com.example.rutautpnative.data.gtfs

//----Filas tipadas del feed----
// Una data class por archivo CSV del feed. Solo se modelan las columnas que el
// repositorio realmente consume (misma filosofía que el parseo por columnas de
// iOS): las columnas innecesarias (route_type, timepoint, exact_times, etc.)
// se ignoran.

data class RouteRow(
    val routeId: String,
    val agencyId: String,
    val routeShortName: String,
    val routeLongName: String,
    val routeColor: String
)

data class TripRow(
    val tripId: String,
    val routeId: String,
    val shapeId: String
)

data class ShapeRow(
    val shapeId: String,
    val lat: Double,
    val lon: Double,
    val sequence: Int
)

data class StopRow(
    val stopId: String,
    val stopName: String,
    val lat: Double,
    val lon: Double
)

data class StopTimeRow(
    val tripId: String,
    val stopSequence: Int,
    val stopId: String,
    val departureTime: String
)

data class FrequencyRow(
    val tripId: String,
    val headwaySecs: Int
)

data class FareAttributeRow(
    val fareId: String,
    val price: Double
)

data class FareRuleRow(
    val fareId: String,
    val routeId: String
)

//----Parser tipado----
// Convierte el texto crudo de cada archivo en listas de filas tipadas.
// Las filas cuyo campo numérico requerido no puede parsearse se descartan
// (equivale al `guard ... else { continue }` del parseo Swift).
object GTFSParser {

    fun routeRows(texto: String): List<RouteRow> {
        val t = GTFSCSV.parsear(texto)
        return (0 until t.rowCount).map { i ->
            RouteRow(
                routeId = t.valor(i, "route_id"),
                agencyId = t.valor(i, "agency_id"),
                routeShortName = t.valor(i, "route_short_name"),
                routeLongName = t.valor(i, "route_long_name"),
                routeColor = t.valor(i, "route_color")
            )
        }
    }

    fun tripRows(texto: String): List<TripRow> {
        val t = GTFSCSV.parsear(texto)
        return (0 until t.rowCount).map { i ->
            TripRow(
                tripId = t.valor(i, "trip_id"),
                routeId = t.valor(i, "route_id"),
                shapeId = t.valor(i, "shape_id")
            )
        }
    }

    fun shapeRows(texto: String): List<ShapeRow> {
        val t = GTFSCSV.parsear(texto)
        return (0 until t.rowCount).mapNotNull { i ->
            val lat = t.valor(i, "shape_pt_lat").toDoubleOrNull() ?: return@mapNotNull null
            val lon = t.valor(i, "shape_pt_lon").toDoubleOrNull() ?: return@mapNotNull null
            val seq = t.valor(i, "shape_pt_sequence").toIntOrNull() ?: return@mapNotNull null
            ShapeRow(shapeId = t.valor(i, "shape_id"), lat = lat, lon = lon, sequence = seq)
        }
    }

    fun stopRows(texto: String): List<StopRow> {
        val t = GTFSCSV.parsear(texto)
        return (0 until t.rowCount).mapNotNull { i ->
            val lat = t.valor(i, "stop_lat").toDoubleOrNull() ?: return@mapNotNull null
            val lon = t.valor(i, "stop_lon").toDoubleOrNull() ?: return@mapNotNull null
            StopRow(
                stopId = t.valor(i, "stop_id"),
                stopName = t.valor(i, "stop_name"),
                lat = lat,
                lon = lon
            )
        }
    }

    fun stopTimeRows(texto: String): List<StopTimeRow> {
        val t = GTFSCSV.parsear(texto)
        return (0 until t.rowCount).mapNotNull { i ->
            val seq = t.valor(i, "stop_sequence").toIntOrNull() ?: return@mapNotNull null
            StopTimeRow(
                tripId = t.valor(i, "trip_id"),
                stopSequence = seq,
                stopId = t.valor(i, "stop_id"),
                departureTime = t.valor(i, "departure_time")
            )
        }
    }

    fun frequencyRows(texto: String): List<FrequencyRow> {
        val t = GTFSCSV.parsear(texto)
        return (0 until t.rowCount).mapNotNull { i ->
            val headway = t.valor(i, "headway_secs").toIntOrNull() ?: return@mapNotNull null
            FrequencyRow(tripId = t.valor(i, "trip_id"), headwaySecs = headway)
        }
    }

    fun fareAttributeRows(texto: String): List<FareAttributeRow> {
        val t = GTFSCSV.parsear(texto)
        return (0 until t.rowCount).map { i ->
            FareAttributeRow(
                fareId = t.valor(i, "fare_id"),
                price = t.valor(i, "price").toDoubleOrNull() ?: 0.0
            )
        }
    }

    fun fareRuleRows(texto: String): List<FareRuleRow> {
        val t = GTFSCSV.parsear(texto)
        return (0 until t.rowCount).map { i ->
            FareRuleRow(
                fareId = t.valor(i, "fare_id"),
                routeId = t.valor(i, "route_id")
            )
        }
    }
}