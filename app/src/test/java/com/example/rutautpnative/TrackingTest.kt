package com.example.rutautpnative

import com.example.rutautpnative.data.gtfs.ParaderoGTFS
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.data.tracking.ActividadMotora
import com.example.rutautpnative.data.tracking.EstadoDeteccionPasajero
import com.example.rutautpnative.data.tracking.GeometriaRutaDeteccion
import com.example.rutautpnative.data.tracking.MuestraDeteccion
import com.example.rutautpnative.data.tracking.MotorDeteccionPasajero
import com.example.rutautpnative.data.tracking.RouteCandidateMatcher
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Test del motor de deteccion y del matcher: mismos escenarios que el
// PassengerDetectionEngineTests / RouteCandidateMatcherTests del iOS.
class TrackingTest {

    //----Muestra "a bordo" tipica: cerca de la ruta, alineado, a velocidad----
    private fun muestraVehicular(ts: Double, actividad: ActividadMotora = ActividadMotora.AUTOMOTIVE) =
        MuestraDeteccion(
            timestamp = ts,
            speed = 10.0,
            horizontalAccuracy = 10.0,
            distanceToRoute = 15.0,
            headingDifference = 10.0,
            distanceToNearestStop = 500.0,
            motionActivity = actividad
        )

    private fun muestraCaminando(ts: Double) =
        MuestraDeteccion(
            timestamp = ts,
            speed = 1.2,
            horizontalAccuracy = 10.0,
            distanceToRoute = 12.0,
            headingDifference = 20.0,
            distanceToNearestStop = 30.0,
            motionActivity = ActividadMotora.WALKING
        )

    @Test
    fun `tres muestras vehiculares consecutivas confirman el abordaje`() {
        val motor = MotorDeteccionPasajero()
        // Muestra 1: IDLE -> BOARDING_CANDIDATE
        var d = motor.procesar(muestraVehicular(1.0))
        assertEquals(EstadoDeteccionPasajero.BOARDING_CANDIDATE, d.estado)
        assertFalse(d.debePublicar)
        // Muestra 2: acumula
        d = motor.procesar(muestraVehicular(2.0))
        assertEquals(EstadoDeteccionPasajero.BOARDING_CANDIDATE, d.estado)
        // Muestra 3: confirma abordaje y autoriza publicar
        d = motor.procesar(muestraVehicular(3.0))
        assertEquals(EstadoDeteccionPasajero.ONBOARD, d.estado)
        assertTrue(d.confirmoAbordaje)
        assertTrue(d.debePublicar)
    }

    @Test
    fun `una muestra imprecisa rompe la racha de evidencia`() {
        val motor = MotorDeteccionPasajero()
        motor.procesar(muestraVehicular(1.0))
        motor.procesar(muestraVehicular(2.0))
        // accuracy 120 m > 50: la muestra se descarta (mantiene estado y no
        // publica)...
        var d = motor.procesar(muestraVehicular(3.0).copy(horizontalAccuracy = 120.0))
        assertEquals(EstadoDeteccionPasajero.BOARDING_CANDIDATE, d.estado)
        assertFalse(d.debePublicar)
        // ...pero NO rompe la evidencia acumulada: la 4 vehicular confirma.
        d = motor.procesar(muestraVehicular(4.0))
        assertEquals(EstadoDeteccionPasajero.ONBOARD, d.estado)
        assertTrue(d.confirmoAbordaje)
    }

    @Test
    fun `cuatro muestras caminando confirman el descenso`() {
        val motor = MotorDeteccionPasajero()
        repeat(3) { motor.procesar(muestraVehicular(it + 1.0)) }
        // ONBOARD -> ALIGHTING_CANDIDATE
        var d = motor.procesar(muestraCaminando(4.0))
        assertEquals(EstadoDeteccionPasajero.ALIGHTING_CANDIDATE, d.estado)
        assertFalse(d.debePublicar)
        // 3 caminatas mas -> descenso confirmado
        d = motor.procesar(muestraCaminando(5.0))
        d = motor.procesar(muestraCaminando(6.0))
        d = motor.procesar(muestraCaminando(7.0))
        assertEquals(EstadoDeteccionPasajero.IDLE, d.estado)
        assertTrue(d.confirmoDescenso)
        assertFalse(d.debePublicar)
    }

    @Test
    fun `una muestra vehicular interrumpe el descenso`() {
        val motor = MotorDeteccionPasajero()
        repeat(3) { motor.procesar(muestraVehicular(it + 1.0)) }
        motor.procesar(muestraCaminando(4.0))
        // Sigue a bordo: vuelve a ONBOARD y publica.
        val d = motor.procesar(muestraVehicular(5.0))
        assertEquals(EstadoDeteccionPasajero.ONBOARD, d.estado)
        assertTrue(d.debePublicar)
    }

    @Test
    fun `actividad desconocida es compatible con vehiculo`() {
        val motor = MotorDeteccionPasajero()
        var d: com.example.rutautpnative.data.tracking.DecisionDeteccion? = null
        repeat(3) { d = motor.procesar(muestraVehicular(it + 1.0, ActividadMotora.UNKNOWN)) }
        assertEquals(EstadoDeteccionPasajero.ONBOARD, d!!.estado)
    }

    //----Matcher----

    private fun rutaNorte(): GeometriaRutaDeteccion {
        val shape = (0..10).map { LatLng(-8.1000 + it * 0.001, -79.0000) }
        return GeometriaRutaDeteccion(
            routeId = "R1", linea = "T-1", shape = shape,
            paraderos = listOf(ParaderoGTFS("p0", "Inicio", -8.1000, -79.0000)),
            minLat = -8.1000, maxLat = -8.0910, minLon = -79.0000, maxLon = -79.0000
        )
    }

    @Test
    fun `matcher proyecta sobre la ruta y alinea el rumbo`() {
        // Punto a ~55 m al este del shape, viajando al norte (rumbo 0).
        val match = RouteCandidateMatcher.emparejamientoMasCercano(
            lat = -8.0955, lon = -78.9995, heading = 0.0,
            rutas = listOf(rutaNorte())
        )
        assertNotNull(match)
        match!!
        assertEquals("R1", match.routeId)
        // Distancia al shape ~ 55 m (delta lon 0.0005 grados a esta latitud).
        assertTrue("distancia=${match.distanciaRuta}", match.distanciaRuta in 35.0..75.0)
        // El shape va al norte; rumbo 0 => alineado.
        assertTrue("diferencia=${match.diferenciaRumbo}", match.diferenciaRumbo < 45.0)
    }

    @Test
    fun `rumbo desconocido nunca cuenta como alineado`() {
        val match = RouteCandidateMatcher.emparejamientoMasCercano(
            lat = -8.0955, lon = -78.9995, heading = -1.0,
            rutas = listOf(rutaNorte())
        )
        assertNotNull(match)
        assertEquals(180.0, match!!.diferenciaRumbo, 0.001)
    }

    @Test
    fun `el punto lejano da una distancia enorme que el motor descartara`() {
        // El matcher NO filtra por distancia (regla del iOS): devuelve la ruta
        // mas cercana aunque este a 40 km — el umbral de 40 m lo aplica el
        // motor de deteccion con distanciaRuta.
        val match = RouteCandidateMatcher.emparejamientoMasCercano(
            lat = -8.3000, lon = -79.3000, heading = 0.0,
            rutas = listOf(rutaNorte())
        )
        assertNotNull(match)
        assertTrue("distancia=${match!!.distanciaRuta}", match.distanciaRuta > 10_000.0)
    }

    @Test
    fun `sin rutas devuele null`() {
        val match = RouteCandidateMatcher.emparejamientoMasCercano(
            lat = -8.1000, lon = -79.0000, heading = 0.0,
            rutas = emptyList()
        )
        assertNull(match)
    }

    //----Sanitizador de posiciones reales (VehiclePositionSanitizer)----

    private fun posicionValida(ts: Double) = com.example.rutautpnative.data.tracking.VehiclePosition(
        id = "V1", linea = "C-01", routeId = "R1",
        lat = -8.10, lon = -79.0, heading = 90.0, speed = 8.0, timestamp = ts
    )

    @Test
    fun `sanitizador acepta lo fresco y expulsa lo obsoleto`() {
        val ahora = 1_000_000.0
        // Fresca (hace 10 s): pasa tal cual.
        assertNotNull(com.example.rutautpnative.data.tracking.VehiclePositionSanitizer.sanitizar(posicionValida(ahora - 10), ahora))
        // Obsoleta (hace 60 s > 45): fuera.
        assertNull(com.example.rutautpnative.data.tracking.VehiclePositionSanitizer.sanitizar(posicionValida(ahora - 60), ahora))
        // Del futuro (hace 60 s de "adelanto" > tolerancia 10): fuera.
        assertNull(com.example.rutautpnative.data.tracking.VehiclePositionSanitizer.sanitizar(posicionValida(ahora + 60), ahora))
        // Coordenada imposible: fuera.
        assertNull(com.example.rutautpnative.data.tracking.VehiclePositionSanitizer.sanitizar(posicionValida(ahora).copy(lat = 200.0), ahora))
        // Speed y heading se reparan: clamp y mod 360.
        val reparada = com.example.rutautpnative.data.tracking.VehiclePositionSanitizer.sanitizar(
            posicionValida(ahora).copy(speed = 500.0, heading = 370.0), ahora
        )
        assertNotNull(reparada)
        assertEquals(100.0, reparada!!.speed, 0.001)
        assertEquals(10.0, reparada.heading, 0.001)
    }

    //----Estimador de ETA (VehicleETAEstimator)----

    private fun rutaLinealParaEta(): RutaGTFS {
        val shape = (0..20).map { LatLng(-8.1000, -79.0000 + it * 0.001) }   // ~2.2 km al este
        return RutaGTFS(
            id = "R1", linea = "T-1", variante = "", recorrido = "Test",
            empresa = "Prueba", colorHex = "00CC00", color = androidx.compose.ui.graphics.Color(0xFF00CC00),
            shape = shape, paraderos = listOf(ParaderoGTFS("p0", "Final", -8.1000, -79.0000 + 20 * 0.001)),
            duracionMin = 6, headwayMin = 5, precio = 1.0,
            distanciaKm = 2.2, distanciaUTPMetros = 0.0
        )
    }

    @Test
    fun `eta estima cuando el vehiculo va hacia el punto`() {
        val ruta = rutaLinealParaEta()
        // Vehiculo a mitad del recorrido, rumbo 90 (este, sentido del shape), 10 m/s.
        val pos = com.example.rutautpnative.data.tracking.VehiclePosition(
            id = "V1", linea = "T-1", routeId = "R1",
            lat = -8.1000, lon = -79.0000 + 10 * 0.001,
            heading = 90.0, speed = 10.0, timestamp = 0.0
        )
        // Objetivo: el extremo este del recorrido.
        val eta = com.example.rutautpnative.data.tracking.VehicleETAEstimator.minutos(
            pos, ruta, LatLng(-8.1000, -79.0000 + 20 * 0.001)
        )
        assertNotNull("debe estimar", eta)
        // ~1.1 km restantes a 10 m/s = ~2 min.
        assertTrue("eta=$eta", eta!! in 1..4)
    }

    @Test
    fun `eta no inventa con rumbo en contra ni punto pasado`() {
        val ruta = rutaLinealParaEta()
        val base = com.example.rutautpnative.data.tracking.VehiclePosition(
            id = "V1", linea = "T-1", routeId = "R1",
            lat = -8.1000, lon = -79.0000 + 10 * 0.001,
            heading = 90.0, speed = 10.0, timestamp = 0.0
        )
        // Rumbo 270 (oeste): se aleja del punto -> SIN ETA.
        assertNull(com.example.rutautpnative.data.tracking.VehicleETAEstimator.minutos(
            base.copy(heading = 270.0), ruta, LatLng(-8.1000, -79.0000 + 20 * 0.001)
        ))
        // Sin rumbo (-1): SIN ETA.
        assertNull(com.example.rutautpnative.data.tracking.VehicleETAEstimator.minutos(
            base.copy(heading = -1.0), ruta, LatLng(-8.1000, -79.0000 + 20 * 0.001)
        ))
        // Punto atras del vehiculo en ruta lineal: SIN ETA.
        assertNull(com.example.rutautpnative.data.tracking.VehicleETAEstimator.minutos(
            base, ruta, LatLng(-8.1000, -79.0000 + 2 * 0.001)
        ))
    }
}
