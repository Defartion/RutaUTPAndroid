package com.example.rutautpnative.data.monedero

import com.example.rutautpnative.ui.idioma.L
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** La identidad pertenece a una lectura. Un QR estático puede usarse en otro viaje. */
class ResultadoQR(val campos: PagoQR.CamposQR, val id: String = UUID.randomUUID().toString()) {
    private val reservado = AtomicBoolean(false)
    val errorParaPago: String?
        get() = when {
            !campos.esPago -> L.t("El código no tiene un formato de pago reconocido.", "The code does not have a recognized payment format.")
            campos.importeInvalido -> L.t("El código contiene un importe ilegible o ambiguo.", "The code contains an unreadable or ambiguous amount.")
            campos.monedaInvalida -> L.t("El QR repite la moneda.", "The QR repeats the currency.")
            campos.moneda != null && campos.moneda.trim().uppercase(Locale.ROOT) !in setOf("PEN", "604") ->
                L.t("Este monedero demo solo admite soles.", "This demo wallet only accepts soles.")
            campos.moneda == null && campos.importe != null -> L.t("No se puede confirmar la moneda del importe.", "The currency of this amount cannot be confirmed.")
            MonederoStore.importeCentimosValido(campos.importe ?: MonederoStore.TARIFA_CENTIMOS / 100.0) == null ->
                L.t("Usa un importe positivo sin fracciones de céntimo.", "Use a positive amount with whole cents.")
            else -> null
        }
    val importeParaPago: Double? get() = if (errorParaPago == null) campos.importe ?: MonederoStore.TARIFA_CENTIMOS / 100.0 else null
    /** La reserva ocurre antes de suspender: dos pulsaciones nunca generan dos débitos. */
    fun reservarPago(): Boolean = errorParaPago == null && reservado.compareAndSet(false, true)
}
