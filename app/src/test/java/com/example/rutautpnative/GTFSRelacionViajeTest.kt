package com.example.rutautpnative

import com.example.rutautpnative.data.gtfs.GTFSRepository
import org.junit.Assert.*
import org.junit.Test

class GTFSRelacionViajeTest {
    private fun fuente(trips: String) = mapOf(
        "agency" to "agency_id,agency_name\nag,Empresa",
        "routes" to "route_id,agency_id,route_short_name,route_long_name,route_color\nr,ag,C-01,Centro,FF0000",
        "trips" to "trip_id,route_id,shape_id\n$trips",
        "shapes" to "shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence\nr,-8.1,-79.0,1\nr,-8.11,-79.01,2\najena,-7.0,-78.0,1\najena,-7.1,-78.1,2",
        "stops" to "stop_id,stop_name,stop_lat,stop_lon\np1,Paradero,-8.1,-79.0",
        "stop_times" to "trip_id,stop_sequence,stop_id,departure_time\nr,1,p1,08:00:00\nr,2,p1,09:00:00",
        "frequencies" to "trip_id,headway_secs\nr,600",
        "fare_attributes" to "fare_id,price\nf,2.50",
        "fare_rules" to "fare_id,route_id\nf,r"
    )

    @Test fun rutaSinViajePropioNoHeredaUnTripHomonimoDeOtraRuta() {
        val tablas = fuente("r,otra,ajena")
        val ruta = GTFSRepository.parsearFuente(tablas::getValue).single()
        assertEquals(-8.1, ruta.shape.first().latitude, .00001)
        assertTrue(ruta.paraderos.isEmpty())
        assertEquals(0, ruta.headwayMin)
        assertEquals(0, ruta.duracionMin)
    }

    @Test fun shapeDeclaradoAusenteUsaElShapeDeRouteIdYConservaSusParaderos() {
        val tablas = fuente("r,r,no-existe")
        val ruta = GTFSRepository.parsearFuente(tablas::getValue).single()
        assertEquals(2, ruta.shape.size)
        assertEquals(-8.1, ruta.shape.first().latitude, .00001)
        assertEquals(2, ruta.paraderos.size)
        assertEquals(10, ruta.headwayMin)
        assertEquals(60, ruta.duracionMin)
    }
}
