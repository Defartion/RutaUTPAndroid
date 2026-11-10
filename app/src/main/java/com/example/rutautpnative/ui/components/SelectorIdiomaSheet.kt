package com.example.rutautpnative.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorIdiomaSheet(onCerrar: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onCerrar, containerColor = AppSurface) {
        Text(L.t("Idioma", "Language"), style = HeadlineMd, color = OnSurface, modifier = Modifier.padding(20.dp))
        Column(Modifier.selectableGroup().navigationBarsPadding()) {
            L.idiomas.forEach { idioma ->
                ListItem(headlineContent = { Text(idioma.nombre, color = OnSurface) }, trailingContent = { RadioButton(L.codigo == idioma.codigo, onClick = null) },
                    modifier = Modifier.fillMaxWidth().selectable(L.codigo == idioma.codigo, role = Role.RadioButton) { L.establecer(idioma.codigo); onCerrar() },
                    colors = ListItemDefaults.colors(containerColor = AppSurface))
            }
        }
    }
}
