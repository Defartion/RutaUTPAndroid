package com.example.rutautpnative.data.places

import android.content.Context
import com.example.rutautpnative.data.google.GoogleHTTP
import com.example.rutautpnative.data.google.ServiciosGoogle
import com.example.rutautpnative.model.CatalogoSedesTrabajo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

object PlacesService {
    sealed class Resultado {
        data class Exito(val nombre: String, val lat: Double, val lon: Double) : Resultado()
        object SinResultados : Resultado()
        object Error : Resultado()
        object SinConfiguracion : Resultado()
        object NoAutorizado : Resultado()
    }
    data class Sugerencia(val nombre: String, val direccion: String, val lat: Double, val lon: Double)
    sealed class ResultadoSugerencias {
        data class Exito(val sugerencias: List<Sugerencia>) : ResultadoSugerencias()
        object SinResultados : ResultadoSugerencias()
        object Error : ResultadoSugerencias()
        object SinConfiguracion : ResultadoSugerencias()
        object NoAutorizado : ResultadoSugerencias()
    }
    private var appContext: Context? = null
    @Volatile private var area: CoberturaBusqueda? = null
    fun init(context: Context) { appContext = context.applicationContext; ServiciosGoogle.init(context) }
    private fun cobertura(): CoberturaBusqueda = area ?: synchronized(this) {
        area ?: CoberturaBusqueda.desdeGTFS(requireNotNull(appContext).assets.open("gtfs/stops.txt").bufferedReader().use { it.readText() },
            CatalogoSedesTrabajo.todas.map { it.lat to it.lon }).also { area = it }
    }
    internal fun solicitud(consulta: String, area: CoberturaBusqueda): String = buildJsonObject {
        put("textQuery", consulta); put("languageCode", "es"); put("pageSize", 5)
        putJsonObject("locationRestriction") { putJsonObject("rectangle") {
            putJsonObject("low") { put("latitude",area.sur); put("longitude",area.oeste) }
            putJsonObject("high") { put("latitude",area.norte); put("longitude",area.este) }
        } }
    }.toString()
    private sealed class Consulta {
        data class Lugares(val lista: List<Sugerencia>) : Consulta()
        object FaltaConfiguracion : Consulta()
        object Autorizacion : Consulta()
        object Fallo : Consulta()
    }
    private suspend fun consultar(texto: String): Consulta {
        val key = ServiciosGoogle.claveWeb() ?: return Consulta.FaltaConfiguracion
        val identidad = ServiciosGoogle.identidad() ?: return Consulta.FaltaConfiguracion
        val area = cobertura()
        val r = GoogleHTTP.ejecutar("https://places.googleapis.com/v1/places:searchText", identidad, "POST", solicitud(texto,area),
            mapOf("X-Goog-Api-Key" to key, "X-Goog-FieldMask" to "places.displayName,places.formattedAddress,places.location"))
        if (r.codigo == 401 || r.codigo == 403) return Consulta.Autorizacion
        if (r.codigo !in 200..299) return Consulta.Fallo
        return Consulta.Lugares(leerLugares(r.cuerpo, area))
    }
    internal fun leerLugares(texto: String, area: CoberturaBusqueda): List<Sugerencia> {
        val lugares = Json.parseToJsonElement(texto).jsonObject["places"]?.jsonArray.orEmpty()
        return lugares.mapNotNull { value ->
            val obj=value.jsonObject
            val loc=obj["location"]?.jsonObject ?: return@mapNotNull null
            val lat=loc["latitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lon=loc["longitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            if (!area.contiene(lat,lon)) return@mapNotNull null
            val nombre=obj["displayName"]?.jsonObject?.get("text")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Sugerencia(nombre,obj["formattedAddress"]?.jsonPrimitive?.content.orEmpty(),lat,lon)
        }.take(5)
    }
    suspend fun buscarTexto(consulta: String): Resultado = withContext(Dispatchers.IO) {
        try { when (val r=consultar(consulta)) {
            is Consulta.Lugares -> r.lista.firstOrNull()?.let { Resultado.Exito(it.nombre,it.lat,it.lon) } ?: Resultado.SinResultados
            Consulta.FaltaConfiguracion -> Resultado.SinConfiguracion
            Consulta.Autorizacion -> Resultado.NoAutorizado
            Consulta.Fallo -> Resultado.Error
        } } catch (e: CancellationException) { throw e } catch (_: Exception) { Resultado.Error }
    }
    suspend fun buscarSugerencias(consulta: String): ResultadoSugerencias = withContext(Dispatchers.IO) {
        try { when (val r=consultar(consulta)) {
            is Consulta.Lugares -> if (r.lista.isEmpty()) ResultadoSugerencias.SinResultados else ResultadoSugerencias.Exito(r.lista)
            Consulta.FaltaConfiguracion -> ResultadoSugerencias.SinConfiguracion
            Consulta.Autorizacion -> ResultadoSugerencias.NoAutorizado
            Consulta.Fallo -> ResultadoSugerencias.Error
        } } catch (e: CancellationException) { throw e } catch (_: Exception) { ResultadoSugerencias.Error }
    }
}
