package com.example.rutautpnative

import com.example.rutautpnative.data.tracking.BoardingPlaceStore
import com.example.rutautpnative.data.tracking.EstadoOcupacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Test del almacen de referencias de subida (BoardingPlaceStore del iOS):
// la normalizacion es pura (recorte a 120 + trim; vacio -> null).
class BoardingPlaceStoreTest {

    @Test
    fun normalizadoRecortaA120() {
        val texto = "x".repeat(130)
        assertEquals("x".repeat(120), BoardingPlaceStore.normalizado("  $texto  "))
    }

    @Test
    fun normalizadoVacioDevuelveNull() {
        assertNull(BoardingPlaceStore.normalizado("   "))
        assertNull(BoardingPlaceStore.normalizado(""))
    }

    @Test
    fun normalizadoConservaElTexto() {
        assertEquals("Av. España 1450", BoardingPlaceStore.normalizado(" Av. España 1450 "))
    }

    @Test
    fun estadosDeOcupacionIncluyenConEspacio() {
        // El contrato con el backend: empty/space/full (BusOccupancyState iOS).
        assertEquals("empty", EstadoOcupacion.VACIO.valorPayload)
        assertEquals("space", EstadoOcupacion.CON_ESPACIO.valorPayload)
        assertEquals("full", EstadoOcupacion.LLENO.valorPayload)
    }
}
