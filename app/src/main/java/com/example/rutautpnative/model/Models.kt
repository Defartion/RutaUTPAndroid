package com.example.rutautpnative.model

import androidx.compose.ui.graphics.Color
import com.example.rutautpnative.data.gtfs.ParaderoGTFS
import com.example.rutautpnative.ui.theme.*
import kotlinx.serialization.Serializable
import java.util.UUID

//----Lugar----
@Serializable
enum class CategoriaLugar(val label: String, val icono: String) {
    UNIVERSIDAD("Universidad", "school"),
    HOGAR("Hogar",            "home"),
    TRABAJO("Trabajo", "work"), ESTUDIOS("Estudios", "menu_book"), SALUD("Salud", "medical_services"),
    MERCADO("Mercado", "shopping_basket"), CENTRO_COMERCIAL("Centro comercial", "shopping_bag"),
    CAFETERIA("Cafetería", "local_cafe"), PARQUE("Parque", "park"), GIMNASIO("Gimnasio", "fitness_center"),
    PARADERO("Paradero", "directions_bus"), PERSONALIZADO("Personalizado", "star"),
    TIENDA("Tienda",          "storefront"),
    RESTAURANTE("Restaurante","restaurant"),
    PLAZA("Plaza",            "account_balance"),
    PLAYA("Playa",            "water"),
    OTRO("Otro",              "location_on");
    val etiqueta: String get() = when (this) {
        UNIVERSIDAD -> com.example.rutautpnative.ui.idioma.L.t("Universidad", "University")
        HOGAR -> com.example.rutautpnative.ui.idioma.L.t("Hogar", "Home")
        TRABAJO -> com.example.rutautpnative.ui.idioma.L.t("Trabajo", "Work")
        ESTUDIOS -> com.example.rutautpnative.ui.idioma.L.t("Estudios", "Studies")
        SALUD -> com.example.rutautpnative.ui.idioma.L.t("Salud", "Health")
        MERCADO -> com.example.rutautpnative.ui.idioma.L.t("Mercado", "Market")
        CENTRO_COMERCIAL -> com.example.rutautpnative.ui.idioma.L.t("Centro comercial", "Shopping mall")
        CAFETERIA -> com.example.rutautpnative.ui.idioma.L.t("Cafetería", "Café")
        PARQUE -> com.example.rutautpnative.ui.idioma.L.t("Parque", "Park")
        GIMNASIO -> com.example.rutautpnative.ui.idioma.L.t("Gimnasio", "Gym")
        PARADERO -> com.example.rutautpnative.ui.idioma.L.t("Paradero", "Bus stop")
        PERSONALIZADO -> com.example.rutautpnative.ui.idioma.L.t("Personalizado", "Custom")
        TIENDA -> com.example.rutautpnative.ui.idioma.L.t("Tienda", "Store")
        RESTAURANTE -> com.example.rutautpnative.ui.idioma.L.t("Restaurante", "Restaurant")
        PLAZA -> com.example.rutautpnative.ui.idioma.L.t("Plaza", "Square")
        PLAYA -> com.example.rutautpnative.ui.idioma.L.t("Playa", "Beach")
        OTRO -> com.example.rutautpnative.ui.idioma.L.t("Otro", "Other")
    }
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
    val esFijo: Boolean = false,
    val paraderoID: String? = null,
    val iconoPersonalizado: String? = null
) {
    // Lugares fijos (UTP) no se pueden eliminar desde la UI.
    val esParadero: Boolean get() = categoria == CategoriaLugar.PARADERO || paraderoID != null

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

    /// ¿Es un paradero del catálogo GTFS? Reconocimiento por nombre +
    /// coordenadas a <5 m (el iOS usa tambien el paraderoID persistido;
    /// aqui los paraderos guardados se reconocen igual que los registros
    /// antiguos del iOS, que tampoco lo traian).
    fun esParaderoGuardadoEn(catalogo: List<ParaderoGTFS>): Boolean {
        if (esFijo) return false
        return esParadero || catalogo.any { paraderoID == it.id || coincideCon(it.nombre, it.lat, it.lon) }
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
    val etiqueta: String get() = when (this) {
        ALERTA -> com.example.rutautpnative.ui.idioma.L.t("ALERTA", "ALERT")
        TRAFICO -> com.example.rutautpnative.ui.idioma.L.t("TRÁFICO", "TRAFFIC")
        SUGERENCIA -> com.example.rutautpnative.ui.idioma.L.t("SUGERENCIA", "SUGGESTION")
        OTRO -> com.example.rutautpnative.ui.idioma.L.t("OTRO", "OTHER")
    }

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
    val cuerpoIngles: String? = null,
    val foto: FotoComunidad? = null,
    val utiles: Int,
    val dislikes: Int = 0,
    val comentarios: Int,
    val utilMarcado: Boolean = false,
    val avatarColor: Color = SurfaceContainerHigh,
    val avatarForeground: Color = OnSurfaceVariant
) {
    val cuerpoLocalizado: String get() = com.example.rutautpnative.ui.idioma.L.t(cuerpo, cuerpoIngles ?: cuerpo)

    val tiempoLocalizado: String
        get() {
            val minutos = Regex("HACE ([0-9]+) MIN").matchEntire(hace)?.groupValues?.get(1)
            if (minutos != null) return com.example.rutautpnative.ui.idioma.L.f("Hace {0} min", "{0} min ago", minutos)
            val horas = Regex("HACE ([0-9]+) HORAS?").matchEntire(hace)?.groupValues?.get(1)
            return if (horas != null) com.example.rutautpnative.ui.idioma.L.f("Hace {0} h", "{0} h ago", horas) else hace
        }
}
