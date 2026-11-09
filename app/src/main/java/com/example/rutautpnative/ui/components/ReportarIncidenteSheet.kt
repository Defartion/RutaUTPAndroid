package com.example.rutautpnative.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.model.TipoReporte
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*

//----Sheet de reporte de incidente (puerto del ReportarSheet.swift)----
// Compartido por el Mapa (boton REPORTAR) y Seguridad (boton Reportar).
// Antes duplicado con 72 lineas identicas en MapaScreen.kt y SeguridadScreen.kt;
// unificado en la limpieza L10 con el texto de exito del iOS ("Gracias por
// colaborar con la comunidad").

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportarIncidenteSheet(
    onDismiss: () -> Unit,
    onAbrirCambios: () -> Unit
) {
    var tipo by remember { mutableStateOf(TipoReporte.ALERTA) }
    var descripcion by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(L.t("Reportar incidente", "Report incident"), style = HeadlineMd, color = OnSurface)
            Spacer(Modifier.height(16.dp))

            // Botón destacado a cambios de ruta (Fase 8), como el iOS.
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryContainer.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().clickable { onAbrirCambios() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    Icon(Icons.Filled.AltRoute, null, tint = AppPrimary, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(L.t("Obras, cierres o cambios de ruta", "Roadworks, closures or route changes"), style = BodyMdMedium, color = OnSurface)
                        Text(L.t("Confirmados por la comunidad, vigentes 15 min", "Community-confirmed, valid for 15 min"), style = BodyXs, color = OnSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(16.dp))

            Text(L.t("TIPO DE REPORTE", "REPORT TYPE"), style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TipoReporte.ALERTA, TipoReporte.TRAFICO, TipoReporte.SUGERENCIA).forEach { t ->
                    FilterChip(selected = tipo == t, onClick = { tipo = t }, label = { Text(t.etiqueta, style = BodySm) })
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(L.t("DESCRIPCIÓN", "DESCRIPTION"), style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                placeholder = { Text(L.t("¿Qué sucede?", "What happened?")) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { showSuccess = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                enabled = descripcion.isNotBlank()
            ) {
                Text(L.t("Enviar reporte", "Send report"), style = HeadlineSm, color = Color.White)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showSuccess) {
        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = { Text(L.t("Reporte enviado", "Report sent")) },
            text = { Text(L.t("Gracias por colaborar con la comunidad.", "Thanks for contributing to the community.")) },
            confirmButton = { TextButton(onClick = { onDismiss() }) { Text("OK") } }
        )
    }
}
