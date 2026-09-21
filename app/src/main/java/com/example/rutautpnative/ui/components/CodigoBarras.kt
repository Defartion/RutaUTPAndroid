package com.example.rutautpnative.ui.components

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

//----Generador local de códigos de barras (ZXing)----
// Genera un Code 128 como Bitmap negro sobre blanco, sin red ni backend
// (equivalente a lo que iOS hace con CoreImage en CarneDigitalView).
object CodigoBarras {

    /**
     * Genera un Code 128 para [datos] con tamaño [ancho]x[alto] en píxeles.
     * Conviene pedir el tamaño final en píxeles reales de destino (o un
     * múltiplo entero mayor) para que las barras se vean nítidas, sin blur
     * de reescalado. Devuelve null si los datos no se pueden codificar.
     */
    fun code128(datos: String, ancho: Int, alto: Int): Bitmap? = try {
        val bits = MultiFormatWriter().encode(datos, BarcodeFormat.CODE_128, ancho, alto)
        val bmp = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        for (y in 0 until alto) {
            for (x in 0 until ancho) {
                bmp.setPixel(x, y, if (bits[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        bmp
    } catch (e: Exception) {
        null
    }
}
