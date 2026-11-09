package com.example.rutautpnative.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ColorReferencia(val hex: String, val nombre: String, val ingles: String)
@Serializable
data class ReferenciaRuta(val routeId: String, val linea: String, val letra: String, val empresa: String,
    val colores: List<ColorReferencia>, val fuente: String? = null, val modelo: String? = null, val orientativa: Boolean = false)

object ReferenciasTransporte {
    @Volatile private var cache: List<ReferenciaRuta>? = null
    fun cargar(ctx: Context): List<ReferenciaRuta> = cache ?: synchronized(this) {
        cache ?: runCatching { Json.decodeFromString<List<ReferenciaRuta>>(ctx.assets.open("referencias_transporte.json").bufferedReader().use { it.readText() }) }
            .getOrDefault(emptyList()).also { cache = it }
    }
    fun referencia(ctx: Context, ruta: RutaGTFS): ReferenciaRuta? = cargar(ctx).firstOrNull {
        it.routeId == ruta.id && it.linea == ruta.linea && it.letra == ruta.variante && it.empresa == ruta.empresa
    }
}

@Composable
fun ReferenciaTransporte(ruta: RutaGTFS) {
    val ctx = LocalContext.current
    val ref by produceState<ReferenciaRuta?>(null, ruta.id) { value = withContext(Dispatchers.IO) { ReferenciasTransporte.referencia(ctx, ruta) } }
    val valor = ref
    if (valor != null) {
        Text(L.t("Colores de referencia", "Reference colors"), style = BodyMdMedium, color = OnSurface)
        valor.colores.chunked(3).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fila.forEach { color -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(16.dp).background(Color(android.graphics.Color.parseColor(color.hex))))
                    Text(L.t(color.nombre, color.ingles), style = BodyXs)
                } }
            }
        }
        if (valor.orientativa) Text(L.t("Referencia orientativa; el aspecto actual está por confirmar.", "Indicative reference; current appearance is unconfirmed."), style = BodyXs)
        valor.fuente?.let { url -> TextButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }) { Text(L.t("Ver fuente de la referencia", "View reference source")) } }
    } else {
        Text(L.t("Sin referencia visual verificada para este ramal.", "No verified visual reference for this branch."), style = BodyXs, color = OnSurfaceVariant)
    }
}
