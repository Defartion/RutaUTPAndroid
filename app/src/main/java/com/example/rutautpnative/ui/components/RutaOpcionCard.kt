package com.example.rutautpnative.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*

enum class AccionTarjetaRuta { DETALLE, ANADIR, GUARDADA }

@Composable
fun InsigniaLinea(ruta: RutaGTFS, modifier: Modifier = Modifier, grande: Boolean = false, compacta: Boolean = false) {
    val tinta = if (ruta.color.luminance() > 0.179) Color.Black else Color.White
    val tamano = if (compacta) 38.dp else if (grande) 76.dp else 58.dp
    Column(modifier.width(tamano).heightIn(min = tamano).clip(RoundedCornerShape(16.dp)).background(ruta.color).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (ruta.letraTransporte.isBlank()) Icon(Icons.Filled.DirectionsBus, null, tint = tinta)
        else {
            if (!compacta) Text(L.t("Letra", "Letter"), fontSize = 10.sp, lineHeight = 12.sp, color = tinta)
            BasicText(ruta.letraTransporte, style = (if (grande) HeadlineLg else HeadlineMd).copy(color = tinta, textAlign = TextAlign.Center), maxLines = 1,
                modifier = Modifier.fillMaxWidth().heightIn(min = if (compacta) 24.dp else if (grande) 40.dp else 32.dp),
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = if (grande) 32.sp else 26.sp))
        }
    }
}

@Composable
fun RutaOpcionCard(ruta: RutaGTFS, distancia: String? = null, accion: AccionTarjetaRuta = AccionTarjetaRuta.DETALLE, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
        contentDescription = L.t("Línea ", "Line ") + ruta.lineaConLetra + ", " + ruta.empresa
    }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.outlinedCardColors(containerColor = SuperficieAgrupada),
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant.copy(alpha = .4f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InsigniaLinea(ruta)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(L.t("Línea ", "Line ") + ruta.codigoTransporte, style = HeadlineBody, color = OnSurface)
                    Text(ruta.empresa, style = BodySm, color = OnSurfaceVariant)
                }
                Icon(when (accion) { AccionTarjetaRuta.DETALLE -> Icons.AutoMirrored.Filled.KeyboardArrowRight; AccionTarjetaRuta.ANADIR -> Icons.Filled.Add; AccionTarjetaRuta.GUARDADA -> Icons.Filled.Bookmark }, null, tint = OnSurfaceVariant)
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceContainerLow).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExtremoRecorrido(ruta.paradaInicioVisible, false)
                ExtremoRecorrido(ruta.paradaFinVisible, true)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DatoBreveRuta(Icons.Filled.Payments, ruta.precioTexto)
                DatoBreveRuta(Icons.Filled.Place, L.f("{0} paraderos", "{0} stops", ruta.paraderos.size.toString()))
                if (ruta.headwayMin > 0) DatoBreveRuta(Icons.Filled.Schedule, ruta.frecuenciaTexto)
            }
            distancia?.let { DatoBreveRuta(Icons.Filled.DirectionsWalk, it) }
            if (accion == AccionTarjetaRuta.GUARDADA) DatoBreveRuta(Icons.Filled.CheckCircle, L.t("Guardada", "Saved"))
        }
    }
}

@Composable
fun ExtremoRecorrido(nombre: String, destino: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
        Icon(if (destino) Icons.Filled.LocationOn else Icons.Filled.RadioButtonUnchecked, null, tint = OnSurfaceVariant, modifier = Modifier.size(17.dp))
        Text(nombre, style = BodySm, color = OnSurface, modifier = Modifier.weight(1f))
    }
}

@Composable
fun DatoBreveRuta(icono: ImageVector, texto: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icono, null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(texto, style = BodyXs, color = OnSurfaceVariant)
    }
}
