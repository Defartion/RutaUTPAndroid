package com.example.rutautpnative.model

import androidx.compose.ui.graphics.Color
import com.example.rutautpnative.ui.theme.*
import kotlinx.serialization.Serializable
import java.util.UUID

//----Lugar----
@Serializable
enum class CategoriaLugar(val label: String, val icono: String) {
    UNIVERSIDAD("Universidad", "school"),
    HOGAR("Hogar",            "home"),
    TIENDA("Tienda",          "storefront"),
    RESTAURANTE("Restaurante","restaurant"),
    PLAZA("Plaza",            "account_balance"),
    PLAYA("Playa",            "water"),
    OTRO("Otro",              "location_on")
}

//----Lugares Guardados----
@Serializable
data class LugarGuardado(
    val id: String = UUID.randomUUID().toString(),
    val nombre: String,
    val direccion: String,
    val categoria: CategoriaLugar,
    val esFrecuente: Boolean = false,
    val lat: Double? = null,
    val lon: Double? = null
) {
    // Lugares fijos (UTP) no se pueden eliminar desde la UI.
    val esFijo: Boolean get() = nombre.equals("UTP", ignoreCase = true)

    /// ¿Este lugar guardado ES el paradero dado? (mismo nombre y a <5 m).
    /// Antes duplicado 4 veces en ParaderosIluminadosScreen con `!!`.
    fun coincideCon(
        nombreParadero: String,
        latParadero: Double,
        lonParadero: Double
    ): Boolean {
        if (nombre != nombreParadero || lat == null || lon == null) return false
        return com.example.rutautpnative.data.gtfs.GTFSRepository.distanciaMetros(
            com.google.android.gms.maps.model.LatLng(lat, lon),
            com.google.android.gms.maps.model.LatLng(latParadero, lonParadero)
        ) < 5.0
    }
}

//----Referencia a línea guardada----
// Solo persiste el id de la ruta; los datos se resuelven al vuelo contra el feed.
@Serializable
data class LineaGuardadaRef(
    val routeId: String
) {
    val id: String get() = routeId
}

//----Tipo de Reporte----
enum class TipoReporte(val label: String) {
    ALERTA("ALERTA"),
    TRAFICO("TRÁFICO"),
    SUGERENCIA("SUGERENCIA"),
    OTRO("OTRO");

    /// Etiqueta traducida (antes duplicada ×3: tipoLabelMapa, tipoReporteLabel,
    /// tipoParaLabel en MapaScreen, SeguridadScreen y PublicarComunidadSheet).
    val etiqueta: String get() = com.example.rutautpnative.ui.idioma.L.t(
        when (this) {
            ALERTA     -> "ALERTA"
            TRAFICO    -> "TRÁFICO"
            SUGERENCIA -> "SUGERENCIA"
            OTRO       -> "OTRO"
        },
        when (this) {
            ALERTA     -> "ALERT"
            TRAFICO    -> "TRAFFIC"
            SUGERENCIA -> "SUGGESTION"
            OTRO       -> "OTHER"
        }
    )

    val background: Color get() = when (this) {
        ALERTA     -> ErrorContainer
        TRAFICO    -> SecondaryContainer
        SUGERENCIA -> TertiaryContainer
        OTRO       -> SurfaceContainerHigh
    }

    val foreground: Color get() = when (this) {
        ALERTA     -> OnErrorContainer
        TRAFICO    -> OnSecondaryContainer
        SUGERENCIA -> OnTertiaryContainer
        OTRO       -> OnSurfaceVariant
    }
}

//----Reporte de la comunidad----
data class ReporteComunidad(
    val id: UUID = UUID.randomUUID(),
    val iniciales: String,
    val nombre: String,
    val hace: String,
    val tipo: TipoReporte,
    val cuerpo: String,
    val utiles: Int,
    val dislikes: Int = 0,
    val comentarios: Int,
    val utilMarcado: Boolean = false,
    val avatarColor: Color = SurfaceContainerHigh,
    val avatarForeground: Color = OnSurfaceVariant
)