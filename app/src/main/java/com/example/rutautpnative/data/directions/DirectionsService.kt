package com.example.rutautpnative.data.directions

import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL

//----Servicio de rutas a pie (Directions API)----
// Calcula la ruta caminando entre dos puntos con la Directions API.
object DirectionsService {

    sealed class Resultado {
        data class Exito(val puntos: List<LatLng>, val distanciaMetros: Int) : Resultado()
        object RespuestaVacia : Resultado()
        object Error : Resultado()
    }

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun rutaPeatonal(origen: LatLng, destino: LatLng): Resultado = withContext(Dispatchers.IO) {
        val apiKey = leerApiKey() ?: return@withContext Resultado.Error
        val url = "https://maps.googleapis.com/maps/api/directions/json" +
            "?origin=${origen.latitude},${origen.longitude}" +
            "&destination=${destino.latitude},${destino.longitude}" +
            "&mode=walking" +
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
            if (raiz["status"]?.jsonPrimitive?.content != "OK") return@withContext Resultado.Error

            val routes = raiz["routes"]?.jsonArray
            if (routes.isNullOrEmpty()) return@withContext Resultado.RespuestaVacia

            val primerRoute = routes[0].jsonObject
            val encoded = primerRoute["overview_polyline"]?.jsonObject?.get("points")
                ?.jsonPrimitive?.content ?: return@withContext Resultado.RespuestaVacia

            val puntos = PolylineDecoder.decode(encoded)
            if (puntos.isEmpty()) return@withContext Resultado.Error

            val distancia = primerRoute["legs"]?.jsonArray?.get(0)
                ?.jsonObject?.get("distance")?.jsonObject?.get("value")
                ?.jsonPrimitive?.longOrNull?.toInt() ?: 0

            Resultado.Exito(puntos = puntos, distanciaMetros = distancia)
        } catch (e: Exception) {
            Resultado.Error
        }
    }

    // No confiamos el API key en el código fuente directo;
    // se lee del AndroidManifest (misma key que usa Maps SDK).
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

//----PolylineDecoder----
// Decodificador del formato encoded polyline de Google, escrito aquí para no
// agregar una dependencia extra solo por PolyUtil.
object PolylineDecoder {
    fun decode(encoded: String): List<LatLng> {
        val puntos = mutableListOf<LatLng>()
        var index = 0
        var lat = 0L
        var lng = 0L
        val n = encoded.length
        while (index < n) {
            var shift = 0
            var acumulado = 0L
            var b: Int
            do {
                b = encoded[index++].code - 63
                acumulado = acumulado or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20 && index < n)
            val dLat = if ((acumulado and 0x1L) != 0L) (acumulado shr 1).inv() else (acumulado shr 1)
            lat += dLat

            shift = 0
            acumulado = 0L
            do {
                b = encoded[index++].code - 63
                acumulado = acumulado or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20 && index < n)
            val dLng = if ((acumulado and 0x1L) != 0L) (acumulado shr 1).inv() else (acumulado shr 1)
            lng += dLng

            puntos.add(LatLng(lat / 1e5, lng / 1e5))
        }
        return puntos
    }
}