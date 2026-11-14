package com.example.rutautpnative.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.ui.theme.*

/** Cabecera compartida; el área de sistema la reserva cada pantalla una sola vez. */
@Composable
fun CabeceraSeccion(titulo: String, icono: ImageVector) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(min = 60.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(titulo, style = HeadlineLg, color = OnSurface,
            modifier = Modifier.weight(1f).semantics { heading() })
        Icon(icono, null, tint = AcentoInterfaz)
    }
}

data class OpcionSeccion(val titulo: String, val icono: ImageVector)

/** Mismo indicador inferior para Guardado y Seguridad; etiquetas sin altura fija. */
@Composable
fun SelectorSeccion(opciones: List<OpcionSeccion>, seleccion: Int, onSeleccionar: (Int) -> Unit) {
    val escala = LocalDensity.current.fontScale
    val apilar = escala >= 1.5f
    if (escala >= 1.8f) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).selectableGroup()) {
            opciones.forEachIndexed { indice, opcion ->
                val tinta = if (seleccion == indice) AcentoInterfaz else OnSurfaceVariant
                Column(Modifier.fillMaxWidth().selectable(seleccion == indice, role = Role.Tab,
                    onClick = { onSeleccionar(indice) })) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(opcion.icono, null, tint = tinta, modifier = Modifier.size(20.dp))
                        Text(opcion.titulo, style = BodyMdMedium, color = tinta, modifier = Modifier.weight(1f))
                    }
                    Box(Modifier.fillMaxWidth().height(3.dp).background(if (seleccion == indice) AcentoInterfaz else OutlineVariant.copy(alpha = .25f)))
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup()) {
            opciones.forEachIndexed { indice, opcion ->
                Column(Modifier.weight(1f).fillMaxHeight().selectable(seleccion == indice, role = Role.Tab,
                    onClick = { onSeleccionar(indice) }), horizontalAlignment = Alignment.CenterHorizontally) {
                    val tinta = if (seleccion == indice) AcentoInterfaz else OnSurfaceVariant
                    if (apilar) Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(opcion.icono, null, tint = tinta, modifier = Modifier.size(20.dp))
                        Text(opcion.titulo, style = BodyMdMedium, color = tinta, textAlign = TextAlign.Center)
                    } else Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(opcion.icono, null, tint = tinta, modifier = Modifier.size(20.dp))
                        Text(opcion.titulo, style = BodyMdMedium, color = tinta, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.fillMaxWidth().height(3.dp).background(if (seleccion == indice) AcentoInterfaz else OutlineVariant.copy(alpha = .25f)))
                }
            }
        }
    }
}
