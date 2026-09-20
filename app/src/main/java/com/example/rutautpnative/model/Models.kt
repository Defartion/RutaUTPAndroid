package com.example.rutautpnative.model

import androidx.compose.ui.graphics.Color
import com.example.rutautpnative.ui.theme.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

// ----Tipo de Vehiculo----
enum class TipoVehiculo(val label: String) {
    MICRO("Micro"),
    COMBI("Combi"),
    BUS("Bus")
}

//----Ruta----
data class Ruta(
    val id: String,
    val linea: String,
    val nombre: String,
    val empresa: String,
    val tipo: TipoVehiculo,
    val placa: String,
    val minutosLlegada: Int,
    val colorIdentificador: Color
)

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
    val lon: Double? = null,
    @Transient val colorBadge: Color = AppPrimary
) {
    // Lugares fijos (UTP) no se pueden eliminar desde la UI.
    val esFijo: Boolean get() = nombre.equals("UTP", ignoreCase = true)
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

//-----Reporte de la comunidad----
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