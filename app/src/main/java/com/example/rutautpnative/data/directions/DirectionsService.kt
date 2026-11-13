package com.example.rutautpnative.data.directions

import android.content.Context
import com.example.rutautpnative.data.google.ServiciosGoogle
import com.example.rutautpnative.data.google.GoogleHTTP
import com.example.rutautpnative.data.conexion.ConexionInstalacionStore
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

//----Servicio de rutas a pie (Directions API)----
// Calcula la ruta caminando entre dos puntos con la Directions API.
object DirectionsService {

    sealed class Resultado {
        data class Exito(val puntos: List<LatLng>, val distanciaMetros: Int) : Resultado()
        object RespuestaVacia : Resultado()
        object Error : Resultado()
        object SinConfiguracion : Resultado()
        object NoAutorizado : Resultado()
    }


    fun init(context: Context) {
        ServiciosGoogle.init(context)
    }

    private data class Key(val origen: LatLng, val destino: LatLng, val revision: Int, val idioma: String)
    private val cache = RouteCalculationCache<Key, Resultado>({ it is Resultado.Exito })
    suspend fun rutaPeatonal(origen: LatLng, destino: LatLng): Resultado = try {
        cache.get(Key(origen, destino, ConexionInstalacionStore.revisionWeb, com.example.rutautpnative.ui.idioma.L.codigoGoogle)) { withTimeout(4_000) { pedir(origen, destino) } }
    } catch (_: TimeoutCancellationException) { Resultado.Error }
      catch (e: CancellationException) { throw e }
      catch (_: Exception) { Resultado.Error }

    private suspend fun pedir(origen: LatLng, destino: LatLng): Resultado = withContext(Dispatchers.IO) {
        val apiKey = ServiciosGoogle.claveWeb() ?: return@withContext Resultado.SinConfiguracion
        val identidad = ServiciosGoogle.identidad() ?: return@withContext Resultado.SinConfiguracion
        val url = "https://maps.googleapis.com/maps/api/directions/json" +
            "?origin=${origen.latitude},${origen.longitude}" +
            "&destination=${destino.latitude},${destino.longitude}" +
            "&mode=walking" +
            "&language=${com.example.rutautpnative.ui.idioma.L.codigoGoogle}" +
            "&key=${java.net.URLEncoder.encode(apiKey, "UTF-8")}"

        try {
            val respuesta = GoogleHTTP.ejecutar(url, identidad)
            if (respuesta.codigo == 401 || respuesta.codigo == 403) return@withContext Resultado.NoAutorizado
            if (respuesta.codigo !in 200..299) return@withContext Resultado.Error
            val texto = respuesta.cuerpo

            val raiz = Json.parseToJsonElement(texto).jsonObject
            if (raiz["status"]?.jsonPrimitive?.content == "REQUEST_DENIED") return@withContext Resultado.NoAutorizado
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
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Resultado.Error }
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
