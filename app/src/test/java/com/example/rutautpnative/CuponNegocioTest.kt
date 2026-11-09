package com.example.rutautpnative

import com.example.rutautpnative.ui.components.cuponVigente
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Test de vigencia de cupones (puerto de CuponNegocioTests iOS): fecha ISO
// "yyyy-MM-dd", sin vencimiento = vigente, fecha pasada = vencido, fecha
// invalida NO se interpreta como vencida (no inventa).
class CuponNegocioTest {

    @Test
    fun sinVencimientoEstaVigente() {
        assertTrue(cuponVigente(null))
        assertTrue(cuponVigente(""))
    }

    @Test
    fun fechaPasadaNoEstaVigente() {
        assertFalse(cuponVigente("2020-01-01"))
    }

    @Test
    fun fechaFuturaEstaVigente() {
        assertTrue(cuponVigente("2099-12-31"))
    }

    @Test
    fun fechaInvalidaNoSeInterpretaComoVencida() {
        // Un formato inesperado no debe retirar el cupón: sin fecha válida
        // el cupón sigue vigente (misma regla del iOS con fechaVencimiento nil).
        assertTrue(cuponVigente("no-es-una-fecha"))
    }

    @Test
    fun fechaDeHoySigueVigente() {
        // El día de hoy inclusive (isBefore excluye hoy).
        val hoy = java.time.LocalDate.now().toString()
        assertTrue(cuponVigente(hoy))
    }
}
