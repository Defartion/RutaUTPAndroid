package com.example.rutautpnative.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.model.TipoReporte
import com.example.rutautpnative.ui.theme.*

//----Formulario de publicar en la comunidad----
// Demo (igual que en iOS): NO inserta el reporte en el feed. Publicar solo
// muestra la confirmación y cierra. Sin foto ni ubicación (pasos aparte).

private const val MAX_CARACTERES = 200

//----Textos contextuales según el tipo elegido----
private fun detallePara(tipo: TipoReporte) = when (tipo) {
    TipoReporte.ALERTA     -> "Cuenta qué pasó: robo, acoso, persona sospechosa o accidente. Indica el lugar aproximado."
    TipoReporte.TRAFICO    -> "Reporta congestión, choques o desvíos que estén afectando tu ruta ahora mismo."
    TipoReporte.SUGERENCIA -> "Propón mejoras: frecuencias, limpieza, nuevos paraderos o precios justos."
    TipoReporte.OTRO       -> "Cualquier otra cosa que la comunidad deba saber."
}

private fun placeholderPara(tipo: TipoReporte) = when (tipo) {
    TipoReporte.ALERTA     -> "Ej. Vi a una persona sospechosa cerca del paradero…"
    TipoReporte.TRAFICO    -> "Ej. Choque en Av. España, tráfico detenido…"
    TipoReporte.SUGERENCIA -> "Ej. La línea B debería pasar más seguido…"
    TipoReporte.OTRO       -> "¿Qué sucede?"
}

private fun chipsPara(tipo: TipoReporte): List<String> = when (tipo) {
    TipoReporte.ALERTA     -> listOf("Robo en el paradero", "Persona sospechosa", "Accidente")
    TipoReporte.TRAFICO    -> listOf("Tráfico detenido", "Choque", "Desvío en la ruta")
    TipoReporte.SUGERENCIA -> listOf("Más frecuencia", "Nuevo paradero", "Mejor limpieza")
    TipoReporte.OTRO       -> emptyList()
}

private fun iconoPara(tipo: TipoReporte): ImageVector = when (tipo) {
    TipoReporte.ALERTA     -> Icons.Filled.Warning
    TipoReporte.TRAFICO    -> Icons.Filled.DirectionsCar
    TipoReporte.SUGERENCIA -> Icons.Filled.Lightbulb
    TipoReporte.OTRO       -> Icons.Filled.ChatBubbleOutline
}

//----Tarjeta de tipo de reporte (selector de 4)----
@Composable
private fun TipoCard(tipo: TipoReporte, seleccionado: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fondo = if (seleccionado) tipo.background else SurfaceContainerLowest
    val contenido = if (seleccionado) tipo.foreground else OnSurfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(fondo)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(iconoPara(tipo), null, tint = contenido, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(tipo.label, style = LabelCapsSm, color = contenido, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicarComunidadSheet(onDismiss: () -> Unit) {
    var tipo by remember { mutableStateOf(TipoReporte.ALERTA) }
    var descripcion by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            //----Barra superior: Cancelar----
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar", style = BodyMdMedium, color = OnSurfaceVariant)
                }
            }

            //----Encabezado----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(PrimaryFixed), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Group, null, tint = AppPrimary, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Publicar en la comunidad", style = HeadlineMd, color = OnSurface)
                    Text("Comparte algo útil con otros estudiantes", style = BodySm, color = OnSurfaceVariant)
                }
            }
            Spacer(Modifier.height(20.dp))

            //----Selector de tipo (4 tarjetas)----
            Text("TIPO", style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TipoReporte.entries.forEach { t ->
                    TipoCard(
                        tipo = t,
                        seleccionado = tipo == t,
                        onClick = { tipo = t },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            //----Detalle contextual----
            Spacer(Modifier.height(12.dp))
            Text(detallePara(tipo), style = BodySm, color = OnSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            //----Descripción con contador regresivo y truncado----
            Text("DESCRIPCIÓN", style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            val restantes = MAX_CARACTERES - descripcion.length
            OutlinedTextField(
                value = descripcion,
                onValueChange = { nuevo ->
                    descripcion = nuevo.take(MAX_CARACTERES) // trunca lo pegado de más
                },
                placeholder = { Text(placeholderPara(tipo), style = BodySm) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 7,
                shape = RoundedCornerShape(12.dp),
                supportingText = {
                    Text(
                        "$restantes / $MAX_CARACTERES",
                        style = LabelCapsSm,
                        color = if (restantes <= 20) AppError else OnSurfaceVariant
                    )
                }
            )

            //----Chips de sugerencia rápida (solo si está vacío)----
            val chips = chipsPara(tipo)
            if (descripcion.isBlank() && chips.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chips.forEach { chip ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceContainerHigh)
                                .clickable { descripcion = chip }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(chip, style = BodyXsMedium, color = OnSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            //----Botón Publicar (gradiente del color tertiary)----
            val habilitado = descripcion.trim().isNotEmpty()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (habilitado) Brush.horizontalGradient(listOf(Tertiary, TertiaryFixedDim))
                        else Brush.horizontalGradient(listOf(SurfaceContainerHigh, SurfaceContainerHigh))
                    )
                    .clickable(enabled = habilitado) { showSuccess = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Publicar",
                    style = HeadlineSm,
                    color = if (habilitado) Color.White else OnSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    //----Confirmación de publicación (demo: no se inserta nada en el feed)----
    // Igual que en iOS: solo muestra el éxito y al tocar "Listo" cierra TODO.
    if (showSuccess) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Publicado en la comunidad") },
            text = { Text("Gracias por aportar a la comunidad UTP.") },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Listo", style = BodyMdMedium, color = AppPrimary)
                }
            }
        )
    }
}
