package com.example.rutautpnative.data.places

import android.content.Context
import android.content.pm.PackageManager
import com.example.rutautpnative.data.gtfs.GTFSRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

//----Servicio de búsqueda de lugares (Places API (New) Text Search)----
// Migrado del Text Search LEGACY (que solo SESGABA por ubicación; caso
// conocido: "Real Plaza, Trujillo" devolvía "Mall Aventura") a la
// Places API (New) con locationRestriction RECTANGULAR: los resultados
// quedan DENTRO del área de Trujillo, sin sorpresas de cercanía.
// POST https://places.googleapis.com/v1/places:searchText
// Header X-Goog-FieldMask define qué campos volverán.
object PlacesService {

    sealed class Resultado {
        data class Exito(val nombre: String, val lat: Double, val lon: Double) : Resultado()
        object SinResultados : Resultado()
        object Error : Resultado()
    }

    // Sugerencia de autocompletado: nombre + direccion legible (para el
    // dropdown del buscador del Mapa, hasta 5 resultados como el
    // MKLocalSearchCompleter de iOS).
    data class Sugerencia(
        val nombre: String,
        val direccion: String,
        val lat: Double,
        val lon: Double
    )

    sealed class ResultadoSugerencias {
        data class Exito(val sugerencias: List<Sugerencia>) : ResultadoSugerencias()
        object SinResultados : ResultadoSugerencias()
        object Error : ResultadoSugerencias()
    }

    private var appContext: Context? = null
    private val json = Json { ignoreUnknownKeys = true }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /// Rectángulo de Trujillo (locationRestriction de la Places API New):
    /// garantiza que TODO resultado caiga dentro del área metropolitana.
    private fun cuerpoRestriccion(): String = """
        {
          "textQuery": "%s",
          "languageCode": "es",
          "locationRestriction": {
            "rectangle": {
              "low": { "latitude": -8.145, "longitude": -79.075 },
              "high": { "latitude": -8.050, "longitude": -78.990 }
            }
          }
        }
    """.trimIndent()

    //----Búsqueda de texto (primer resultado dentro del rectángulo)----
    suspend fun buscarTexto(consulta: String): Resultado = withContext(Dispatchers.IO) {
        val apiKey = leerApiKey() ?: return@withContext Resultado.Error
        val respuesta = consultar(consulta, apiKey) ?: return@withContext Resultado.Error
        val lugares = respuesta["places"]?.jsonArray ?: return@withContext Resultado.SinResultados
        val primero = lugares.firstOrNull()?.jsonObject ?: return@withContext Resultado.SinResultados
        val nombre = primero["displayName"]?.jsonObject
            ?.get("text")?.jsonPrimitive?.content
            ?: return@withContext Resultado.SinResultados
        val loc = primero["location"]?.jsonObject ?: return@withContext Resultado.SinResultados
        val lat = loc["latitude"]?.jsonPrimitive?.double ?: return@withContext Resultado.SinResultados
        val lon = loc["longitude"]?.jsonPrimitive?.double ?: return@withContext Resultado.SinResultados
        Resultado.Exito(nombre = nombre, lat = lat, lon = lon)
    }

    //----Sugerencias de autocompletado (dropdown del buscador del Mapa)----
    suspend fun buscarSugerencias(consulta: String): ResultadoSugerencias = withContext(Dispatchers.IO) {
        val apiKey = leerApiKey() ?: return@withContext ResultadoSugerencias.Error
        val respuesta = consultar(consulta, apiKey) ?: return@withContext ResultadoSugerencias.Error
        val lugares = respuesta["places"]?.jsonArray ?: return@withContext ResultadoSugerencias.SinResultados
        val sugerencias = lugares.take(5).mapNotNull { entrada ->
            val obj = entrada.jsonObject
            val nombre = obj["displayName"]?.jsonObject?.get("text")?.jsonPrimitive?.content
                ?: return@mapNotNull null
            val direccion = obj["formattedAddress"]?.jsonPrimitive?.content
                ?: obj["shortFormattedAddress"]?.jsonPrimitive?.content ?: ""
            val loc = obj["location"]?.jsonObject ?: return@mapNotNull null
            val lat = loc["latitude"]?.jsonPrimitive?.double ?: return@mapNotNull null
            val lon = loc["longitude"]?.jsonPrimitive?.double ?: return@mapNotNull null
            Sugerencia(nombre = nombre, direccion = direccion, lat = lat, lon = lon)
        }
        if (sugerencias.isEmpty()) ResultadoSugerencias.SinResultados
        else ResultadoSugerencias.Exito(sugerencias)
    }

    /// Llamada compartida: POST a searchText con el FieldMask de siempre.
    private fun consultar(consulta: String, apiKey: String): kotlinx.serialization.json.JsonObject? {
        return try {
            val con = URL("https://places.googleapis.com/v1/places:searchText")
                .openConnection() as HttpURLConnection
            con.requestMethod = "POST"
            con.connectTimeout = 10_000
            con.readTimeout = 10_000
            con.doOutput = true
            con.setRequestProperty("X-Goog-Api-Key", apiKey)
            con.setRequestProperty(
                "X-Goog-FieldMask",
                "places.displayName,places.formattedAddress,places.shortFormattedAddress,places.location"
            )
            con.setRequestProperty("Content-Type", "application/json")
            con.connect()
            OutputStreamWriter(con.outputStream).use { salida ->
                salida.write(cuerpoRestriccion().replace("%s", consulta))
            }
            val texto = try {
                con.inputStream.bufferedReader().use { it.readText() }
            } finally {
                con.disconnect()
            }
            json.parseToJsonElement(texto).jsonObject
        } catch (e: Exception) {
            null
        }
    }

    // Misma clave del AndroidManifest que usan Maps y Directions.
    private fun leerApiKey(): String? = try {
        val ctx = appContext
        ctx?.packageManager
            ?.getApplicationInfo(ctx.packageName, PackageManager.GET_META_DATA)
            ?.metaData
            ?.getString("com.google.android.geo.API_KEY")
    } catch (e: Exception) {
        null
    }
}
