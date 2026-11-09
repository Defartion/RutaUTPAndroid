package com.example.rutautpnative.data.imagenes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlin.math.max

//----Persistencia de fotos (puerto de ProfileImageStore / CarnetImageStore)----
// Fuente UNICA de la foto de perfil: la leen el drawer, Perfil, el Carné
// Digital y Datos Personales. CarnetImageStore conserva POR SEPARADO y por
// EMPRESA la foto del carné físico (utp -> carnet-utp.jpg, etc.).

/// Revision observable: subir el numero tras guardar invalida los
/// produceState que cargan la foto y recomponen al instante.
object ProfileImageStore {
    var revision by mutableIntStateOf(0)
        private set

    fun archivo(context: Context): File = File(context.filesDir, "perfil_foto.jpg")

    fun existe(context: Context): Boolean = archivo(context).exists()

    fun guardar(context: Context, bitmap: Bitmap): Boolean {
        return try {
            // Tope de 1200 px por lado: suficiente para un avatar y evita
            // amplificar el almacenamiento con fotos de camara completas.
            val maxLado = 1200
            val mayor = max(bitmap.width, bitmap.height)
            val final = if (mayor > maxLado) {
                val escala = maxLado.toFloat() / mayor
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * escala).toInt().coerceAtLeast(1),
                    (bitmap.height * escala).toInt().coerceAtLeast(1),
                    true
                )
            } else bitmap
            archivo(context).outputStream().use { salida ->
                final.compress(Bitmap.CompressFormat.JPEG, 80, salida)
            }
            revision++
            true
        } catch (e: Exception) {
            false
        }
    }

    fun cargar(context: Context): Bitmap? = try {
        BitmapFactory.decodeFile(archivo(context).absolutePath)
    } catch (e: Exception) {
        null
    }
}

/// Foto del carné físico por empresa (utp-card.jpg / company-card-<id>.jpg
/// del iOS). Ocultar el carné en preferencias visuales NUNCA la borra.
object CarnetImageStore {
    var revision by mutableIntStateOf(0)
        private set

    private const val MAX_LADO = 2400   // iOS idem; JPEG 90

    fun archivo(context: Context, empresaId: String): File =
        File(context.filesDir, "carnet-$empresaId.jpg")

    fun existe(context: Context, empresaId: String): Boolean =
        archivo(context, empresaId).exists()

    fun guardar(context: Context, empresaId: String, bitmap: Bitmap): Boolean {
        return try {
            val mayor = max(bitmap.width, bitmap.height)
            val final = if (mayor > MAX_LADO) {
                val escala = MAX_LADO.toFloat() / mayor
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * escala).toInt().coerceAtLeast(1),
                    (bitmap.height * escala).toInt().coerceAtLeast(1),
                    true
                )
            } else bitmap
            archivo(context, empresaId).outputStream().use { salida ->
                final.compress(Bitmap.CompressFormat.JPEG, 90, salida)
            }
            revision++
            true
        } catch (e: Exception) {
            false
        }
    }

    fun cargar(context: Context, empresaId: String): Bitmap? = try {
        BitmapFactory.decodeFile(archivo(context, empresaId).absolutePath)
    } catch (e: Exception) {
        null
    }
}
