package com.example.rutautpnative.data.negocios

import android.content.Context
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.model.CatalogoNegocios
import com.example.rutautpnative.model.Negocio
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

//----Servicio de negocios promocionados----
// Carga el catálogo UNA sola vez desde assets y lo mantiene en caché
// (mismo patrón singleton que GTFSRepository). Si el archivo falta o está
// corrupto, degrada en silencio a un catálogo vacío (igual que iOS).
object NegociosService {

    private const val ARCHIVO = "negocios_promocionados.json"

    private var appContext: Context? = null
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cache: List<Negocio>? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private suspend fun cargar(): List<Negocio> {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            val lista = try {
                val ctx = appContext ?: return@withContext emptyList<Negocio>()
                val texto = ctx.assets.open(ARCHIVO).bufferedReader().use { it.readText() }
                json.decodeFromString<CatalogoNegocios>(texto).negocios
            } catch (e: Exception) {
                emptyList() // degradación silenciosa
            }
            cache = lista
            lista
        }
    }

    //----Todo el catálogo----
    suspend fun todos(): List<Negocio> = cargar()

    //----Los más cercanos a un origen, dentro de un radio----
    // Filtra por radio, ordena por distancia, limita cantidad.
    suspend fun cerca(origen: LatLng, radioMetros: Double = 600.0, limite: Int = 5): List<Negocio> {
        return cargar()
            .filter { GTFSRepository.distanciaMetros(origen, LatLng(it.latitud, it.longitud)) <= radioMetros }
            .sortedBy { GTFSRepository.distanciaMetros(origen, LatLng(it.latitud, it.longitud)) }
            .take(limite)
    }

    //----Versión "espaciada": evita negocios muy pegados entre sí----
    // Recorre por distancia y descarta los que queden a menos de la
    // separación mínima (max(110, radio/5) metros) de uno ya elegido.
    suspend fun distribuidos(centro: LatLng, radioMetros: Double, limite: Int): List<Negocio> {
        val separacionMinima = maxOf(110.0, radioMetros / 5)
        val seleccionados = mutableListOf<Negocio>()
        val candidatos = cargar()
            .filter { GTFSRepository.distanciaMetros(centro, LatLng(it.latitud, it.longitud)) <= radioMetros }
            .sortedBy { GTFSRepository.distanciaMetros(centro, LatLng(it.latitud, it.longitud)) }

        for (cand in candidatos) {
            if (seleccionados.size >= limite) break
            val muyPegado = seleccionados.any {
                GTFSRepository.distanciaMetros(
                    LatLng(it.latitud, it.longitud),
                    LatLng(cand.latitud, cand.longitud)
                ) < separacionMinima
            }
            if (!muyPegado) seleccionados.add(cand)
        }
        return seleccionados
    }
}
