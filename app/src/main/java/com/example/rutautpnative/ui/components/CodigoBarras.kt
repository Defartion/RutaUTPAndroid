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
    /**
     * Genera un Code 128 para [datos] con tamaño [ancho]x[alto] en píxeles.
     * Usa IntArray + setPixels (UNA llamada JNI) en vez de setPixel por
     * píxel (272k llamadas JNI para un carnet de 1080×252).
     */
    fun code128(datos: String, ancho: Int, alto: Int): Bitmap? = try {
        val bits = MultiFormatWriter().encode(datos, BarcodeFormat.CODE_128, ancho, alto)
        val negro = 0xFF000000.toInt()
        val blanco = 0xFFFFFFFF.toInt()
        val pixels = IntArray(ancho * alto) { i ->
            val x = i % ancho
            val y = i / ancho
            if (bits[x, y]) negro else blanco
        }
        Bitmap.createBitmap(pixels, ancho, alto, Bitmap.Config.ARGB_8888)
    } catch (e: Exception) {
        null
    }
}
