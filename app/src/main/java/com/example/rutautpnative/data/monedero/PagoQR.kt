package com.example.rutautpnative.data.monedero

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.text.Normalizer
import java.util.Locale

//----QR de cobro del monedero (puerto del PagoQR.swift)----
// TODO demo por diseno: las marcas son parodias ("Yapo"/"Plun"), la cuenta es
// inventada y ninguna app de pagos reales leera esto como un cobro.
//
// Payload demo: TEXTO PLANO ASCII legible (NO es TLV EMVCo, a proposito —
// igual que el iOS), una linea por campo "clave: valor". El lector entiende
// DOS formatos: este, y el TLV EMVCo real que generan Yape/Plin.

object PagoQR {

    enum class Billetera(val nombreMarca: String, val colorMarca: Color, val colorTextoMarca: Color) {
        YAPO("Yapo", Color(0xFF7C1FA0), Color(0xFFC77CE0)),
        PLUN("Plun", Color(0xFF00939F), Color(0xFF5FD9E4))
    }

    data class CuentaCobro(
        val titular: String,
        val celular: String,
        val ciudad: String,
        val billetera: Billetera
    ) {
        /// "999888777" -> "999 888 777" (legible para el usuario).
        val celularLegible: String
            get() = celular.chunked(3).joinToString(" ")
    }

    /// Cuenta DEMO (inventada, igual que la del iOS).
    val cuentaDemo = CuentaCobro(
        titular = "Joaquín Díaz",
        celular = "999888777",
        ciudad = "Trujillo",
        billetera = Billetera.YAPO
    )

    //----Generacion del payload----

    /// ASCII plano: quita tildes/filtra no-ASCII para que lectores con otra
    /// codificacion no muestren "Joaqu铆n".
    private fun ascii(texto: String): String {
        val normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD)
        return normalizado.filter { it.code in 32..126 }
    }

    fun cargaUtil(cuenta: CuentaCobro, importe: Double? = null): String {
        val lineas = mutableListOf(
            "DEMO ${cuenta.billetera.nombreMarca.uppercase()}",
            "titular: ${ascii(cuenta.titular)}",
            "celular: ${ascii(cuenta.celular)}",
            "ciudad: ${ascii(cuenta.ciudad)}",
            "moneda: PEN"
        )
        // El importe solo viaja cuando es mayor que cero (QR con monto).
        if (importe != null && importe.isFinite() && importe > 0) {
            lineas.add(String.format(Locale.US, "importe: %.2f", importe))
        }
        return lineas.joinToString("\n")
    }

    /// QR como Bitmap con nivel de correccion Q (25%, como el iOS) y margen.
    /// Usa IntArray + setPixels (UNA llamada JNI en bloque) en vez de
    /// setPixel por píxel (262k llamadas JNI — jank visible en main thread).
    fun imagen(cargaUtil: String, tamano: Int = 512): Bitmap? = try {
        val pistas = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q,
            EncodeHintType.MARGIN to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matriz = QRCodeWriter().encode(cargaUtil, BarcodeFormat.QR_CODE, tamano, tamano, pistas)

        // Construye todos los pixels en un IntArray y vuelcalos de una vez:
        // ~100x mas rapido que el bucle setPixel doble.
        val negro = android.graphics.Color.BLACK
        val blanco = android.graphics.Color.WHITE
        val pixels = IntArray(tamano * tamano) { i ->
            val x = i % tamano
            val y = i / tamano
            if (matriz[x, y]) negro else blanco
        }
        Bitmap.createBitmap(pixels, tamano, tamano, Bitmap.Config.RGB_565)
    } catch (e: Exception) {
        null
    }

    //----Lectura dual: formato demo + TLV EMVCo real----

    data class CamposQR(
        val billetera: Billetera?,
        val titular: String?,
        val ciudad: String?,
        val celular: String?,
        val importe: Double?,
        val moneda: String?,
        val esDemo: Boolean,
        val esPago: Boolean,
        val importeInvalido: Boolean = false,
        val monedaInvalida: Boolean = false
    ) {
        /// Prefija "S/ " solo si la moneda es PEN (604) o "PEN".
        val importeTexto: String
            get() {
                val valor = importe ?: return ""
                val prefijo = if (moneda?.trim()?.uppercase(Locale.ROOT) in setOf("604", "PEN")) "S/ " else ""
                return prefijo + "%.2f".format(valor)
            }
    }

    fun leer(cargaUtil: String): CamposQR {
        val vacio = CamposQR(null, null, null, null, null, null, false, false)
        val lineas = cargaUtil.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lineas.firstOrNull() in setOf("DEMO YAPO", "DEMO PLUN")) {
            val campos = mutableMapOf<String, String>()
            var importeInvalido = false
            var monedaInvalida = false
            for (linea in lineas.drop(1)) {
                val partes = linea.split(':', limit = 2)
                if (partes.size != 2 || partes[0].isBlank()) return vacio
                val clave = partes[0].trim().lowercase(Locale.ROOT)
                val valor = partes[1].trim()
                if (clave == "importe" && (clave in campos || valor.toDoubleOrNull() == null)) importeInvalido = true
                if (clave == "moneda" && clave in campos) monedaInvalida = true
                campos[clave] = valor
            }
            return CamposQR(
                Billetera.entries.firstOrNull { "DEMO ${it.nombreMarca.uppercase(Locale.ROOT)}" == lineas.first() },
                campos["titular"], campos["ciudad"], campos["celular"], campos["importe"]?.toDoubleOrNull(),
                campos["moneda"], true, true, importeInvalido, monedaInvalida
            )
        }
        val tlv = parsearTLVBytes(cargaUtil.toByteArray(Charsets.UTF_8)) ?: return vacio
        if (tlv["00"] != "01" || tlv["63"]?.matches(Regex("[0-9a-fA-F]{4}")) != true) return vacio
        val cuenta = tlv["26"]?.let { parsearTLVBytes(it.toByteArray(Charsets.UTF_8)) ?: return vacio }
        return CamposQR(null, tlv["59"], tlv["60"], cuenta?.get("01"), tlv["54"]?.toDoubleOrNull(),
            tlv["53"], false, true, "54" in tlv && tlv["54"]?.toDoubleOrNull() == null)
    }

    /** Consume TODO el registro UTF-8: un prefijo, duplicado o campo truncado nunca autoriza cobrar. */
    private fun parsearTLVBytes(bytes: ByteArray): Map<String, String>? {
        val campos = mutableMapOf<String, String>()
        var i = 0
        while (i < bytes.size) {
            if (i + 4 > bytes.size || (i until i + 4).any { bytes[it].toInt() !in 48..57 }) return null
            val id = String(bytes, i, 2, Charsets.US_ASCII)
            val n = (bytes[i + 2] - 48) * 10 + (bytes[i + 3] - 48)
            if (i + 4 + n > bytes.size || id in campos) return null
            val valor = try {
                Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes, i + 4, n)).toString()
            } catch (_: Exception) { return null }
            campos[id] = valor
            i += 4 + n
        }
        return campos
    }
}
