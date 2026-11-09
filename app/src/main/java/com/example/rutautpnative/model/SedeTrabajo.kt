package com.example.rutautpnative.model

import android.content.Context
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.ui.theme.TematicaEmpresa
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

//----Sede de referencia (puerto del SedeTrabajo.swift)----
// Campus UTP o local de la empresa elegida. La `empresa` de la sede elegida
// decide la temática de colores de toda la app.
@Serializable
data class SedeTrabajo(
    val id: String,
    val empresa: String,          // id de TematicaEmpresa ("utp", "interbank"...)
    val nombre: String,
    val direccion: String,
    val lat: Double,
    val lon: Double,
    val googlePlaceID: String = ""
) {
    // Empresa resuelta contra el enum; UTP si el id no existe (dato viejo).
    val tematica: TematicaEmpresa
        get() = TematicaEmpresa.desdeId(empresa) ?: TematicaEmpresa.UTP
}

object CatalogoSedesTrabajo {

    /// Reutiliza la referencia institucional de la app, sin inventar un Place ID.
    val campusUTP = SedeTrabajo(
        id = "utp-trujillo", empresa = "utp",
        nombre = "Campus UTP Trujillo", direccion = "Av. Nicolás de Piérola 1221, Trujillo",
        lat = GTFSRepository.coordenadaUTP.latitude,
        lon = GTFSRepository.coordenadaUTP.longitude
    )

    // Pins y estado de las ocho sedes empresariales contrastados el 09/10/2026.
    // Procedencia y descartes: ThirdPartyNotices/Marcas/sedes-trujillo.json
    // (repo iOS). Son pins del establecimiento; el acceso peatonal queda por
    // verificar. Real Plaza está cerrado. Popeyes queda «Próximamente», sin
    // sede inventada.
    private val sedesBase = listOf(
        campusUTP,
        SedeTrabajo("interbank-centro-gamarra", "interbank",
            "Interbank Centro — Gamarra", "Jr. Gamarra 450, Trujillo",
            -8.1103596, -79.0267624, "ChIJdTe2doQ9rZERjFx-9JTDaA0"),
        SedeTrabajo("interbank-larco", "interbank",
            "Interbank Larco", "Av. Larco 780, Trujillo",
            -8.1211306, -79.0362330, "ChIJVyHXAXQ9rZERLcd2T3GuRkw"),
        SedeTrabajo("interbank-mallplaza", "interbank",
            "Interbank Mallplaza", "Av. Mansiche y América Oeste, locales B-1253 / B-1257, Trujillo",
            -8.1018948, -79.0467764, "ChIJE0idAbc9rZERjkZ9p_va72k"),
        SedeTrabajo("interbank-el-chacarero", "interbank",
            "Interbank El Chacarero", "Prol. Unión 2218, Trujillo",
            -8.0906329, -79.0066038, "ChIJG2HANogXrZERTIWcQVy_RsY"),
        SedeTrabajo("plazavea-espana", "plaza_vea",
            "Plaza Vea España", "Av. España, Trujillo",
            -8.1159412, -79.0261629, "ChIJaebOUTE9rZERtp1ChS0rYrU"),
        SedeTrabajo("plazavea-espana-780", "plaza_vea",
            "Plaza Vea España 780", "Av. España 780, Trujillo",
            -8.1076368, -79.0299941, "ChIJFy-0KIU9rZERvkTRFnzVzy8"),
        SedeTrabajo("plazavea-primavera", "plaza_vea",
            "Plaza Vea Primavera", "Av. Teodoro Valcárcel 266–268, Trujillo",
            -8.1014367, -79.0355399, "ChIJl_9I_rI9rZERRueGGAB6uA4"),
        SedeTrabajo("plazavea-el-chacarero", "plaza_vea",
            "Plaza Vea El Chacarero", "Prol. Unión 2218, Trujillo",
            -8.0900189, -79.0062619, "ChIJ0Ub_bbcXrZERU3K1JkFHb64")
    )

    /// Catálogo local cargado una vez (assets/sedes-empresas-trujillo.json):
    /// ampliar las sedes no introduce búsquedas de red al abrir el mapa.
    private var _todas: List<SedeTrabajo> = sedesBase

    val todas: List<SedeTrabajo> get() = _todas

    fun init(context: Context) {
        try {
            val texto = context.assets.open("sedes-empresas-trujillo.json")
                .bufferedReader().use { it.readText() }
            val json = Json { ignoreUnknownKeys = true }
            val sedes = json.decodeFromString<List<SedeTrabajo>>(texto)
            val ids = sedesBase.map { it.id }.toMutableSet()
            val validas = sedes.filter {
                it.empresa in TematicaEmpresa.empresas.map { e -> e.id } &&
                    it.lat.isFinite() && it.lon.isFinite() &&
                    it.lat >= -90.0 && it.lat <= 90.0 &&
                    it.lon >= -180.0 && it.lon <= 180.0 &&
                    ids.add(it.id)
            }
            _todas = sedesBase + validas
        } catch (e: Exception) {
            // Sin catálogo adicional o JSON corrupto: solo las sedes base.
        }
    }

    fun sede(id: String): SedeTrabajo? = todas.firstOrNull { it.id == id }

    fun sedes(empresa: TematicaEmpresa): List<SedeTrabajo> =
        todas.filter { it.tematica == empresa }
}
