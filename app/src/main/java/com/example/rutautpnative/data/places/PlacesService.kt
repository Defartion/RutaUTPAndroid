package com.example.rutautpnative.data.places

import android.content.Context
import android.content.pm.PackageManager
import com.example.rutautpnative.data.gtfs.GTFSRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

//----Servicio de búsqueda de lugares (Places API, Text Search legacy)----
// Busca un lugar por texto cerca del campus UTP y devuelve el primer resultado
// con su nombre real y coordenada. Sin librerías nuevas: HTTP directo, igual
// que DirectionsService.
object PlacesService {

    sealed class Resultado {
        data class Exito(val nombre: String, val lat: Double, val lon: Double) : Resultado()
        object SinResultados : Resultado()
        object Error : Resultado()
    }

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    //----Búsqueda de texto centrada en el campus UTP----
    suspend fun buscarTexto(consulta: String): Resultado = withContext(Dispatchers.IO) {
        val apiKey = leerApiKey() ?: return@withContext Resultado.Error
        val centro = GTFSRepository.coordenadaUTP
        val url = "https://maps.googleapis.com/maps/api/place/textsearch/json" +
            "?query=" + URLEncoder.encode(consulta, "UTF-8") +
            "&location=${centro.latitude},${centro.longitude}" +
            "&radius=8000" +
            "&language=es" +
            "&key=$apiKey"

        try {
            val con = URL(url).openConnection() as HttpURLConnection
            con.connectTimeout = 10_000
            con.readTimeout = 10_000
            val texto = try {
                con.inputStream.bufferedReader().use { it.readText() }
            } finally {
                con.disconnect()
            }

            val raiz = Json.parseToJsonElement(texto).jsonObject
            when (raiz["status"]?.jsonPrimitive?.content) {
                "OK" -> {
                    val resultados = raiz["results"]?.jsonArray
                    val primero = resultados?.firstOrNull()?.jsonObject
                        ?: return@withContext Resultado.SinResultados
                    val nombre = primero["name"]?.jsonPrimitive?.content
                        ?: return@withContext Resultado.SinResultados
                    val loc = primero["geometry"]?.jsonObject
                        ?.get("location")?.jsonObject
                        ?: return@withContext Resultado.SinResultados
                    val lat = loc["lat"]?.jsonPrimitive?.double
                        ?: return@withContext Resultado.SinResultados
                    val lon = loc["lng"]?.jsonPrimitive?.double
                        ?: return@withContext Resultado.SinResultados
                    Resultado.Exito(nombre = nombre, lat = lat, lon = lon)
                }
                "ZERO_RESULTS" -> Resultado.SinResultados
                else -> Resultado.Error
            }
        } catch (e: Exception) {
            Resultado.Error
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
