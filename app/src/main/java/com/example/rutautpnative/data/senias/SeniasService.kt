package com.example.rutautpnative.data.senias

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

//----Servicio de señas (Modo Señas)----
// Contenido 100% local en assets/senias/ (sin backend, funciona sin conexión).
// Degrada con honestidad: si falta el manifiesto, la clave, la carpeta de
// idioma o el archivo específico, devuelve Pendiente con el motivo — nunca
// crashea ni muestra nada roto.

@Serializable
data class SeniaEntrada(
    val clave: String,
    val archivo: String,
    val fuente: String
)

@Serializable
private data class ManifestSenias(
    @kotlinx.serialization.SerialName("señas") val senias: List<SeniaEntrada>
)

sealed class EstadoSenia {
    // Hay clip real para reproducir (uri lista para Media3: asset:///...).
    data class Clip(val uri: String, val clave: String) : EstadoSenia()
    // No hay seña disponible para esa clave (con motivo para logs/debug).
    data class Pendiente(val motivo: String) : EstadoSenia()
}

object SeniasService {

    private const val CARPETA = "senias"

    private var appContext: Context? = null
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cache: List<SeniaEntrada>? = null
    // Archivos presentes por idioma ("es"/"en"); cacheado por carpeta.
    @Volatile private var archivosPorIdioma: Map<String, Set<String>> = emptyMap()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /// Idioma activo del Modo Señas: SIEMPRE el de la app; si el clip falta
    /// se muestra "pendiente", sin sustituirlo por el otro idioma (iOS idem).
    private fun idiomaActivo(): String =
        com.example.rutautpnative.ui.idioma.L.codigo

    //----Manifiesto: se carga una sola vez; si falta, catálogo vacío----
    private suspend fun entradas(): List<SeniaEntrada> {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            val lista = try {
                val ctx = appContext ?: return@withContext emptyList()
                val texto = ctx.assets.open("$CARPETA/manifest.json").bufferedReader().use { it.readText() }
                json.decodeFromString<ManifestSenias>(texto).senias
            } catch (e: Exception) {
                emptyList()
            }
            cache = lista
            lista
        }
    }

    //----Archivos realmente presentes en clips/<idioma>/----
    private suspend fun archivosDisponibles(idioma: String): Set<String> {
        archivosPorIdioma[idioma]?.let { return it }
        return withContext(Dispatchers.IO) {
            val set = try {
                val ctx = appContext ?: return@withContext emptySet()
                ctx.assets.list("$CARPETA/clips/$idioma")?.toSet() ?: emptySet()
            } catch (e: Exception) {
                emptySet()
            }
            archivosPorIdioma = archivosPorIdioma + (idioma to set)
            set
        }
    }

    //----Estado de una clave: Clip reproducible o Pendiente(motivo)----
    suspend fun estado(clave: String): EstadoSenia {
        val idioma = idiomaActivo()
        val entrada = entradas().firstOrNull { it.clave == clave }
            ?: return EstadoSenia.Pendiente("la clave no está en el manifiesto")
        if (entrada.archivo !in archivosDisponibles(idioma)) {
            return EstadoSenia.Pendiente("el archivo ${entrada.archivo} no está en clips/$idioma")
        }
        return EstadoSenia.Clip(
            uri = "asset:///$CARPETA/clips/$idioma/${entrada.archivo}",
            clave = clave
        )
    }
}
