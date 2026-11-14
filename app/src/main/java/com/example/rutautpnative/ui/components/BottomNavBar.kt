package com.example.rutautpnative.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.rutautpnative.navigation.AppRouter
import com.example.rutautpnative.navigation.AppScreen
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup

//Navtab — etiquetas se traducen inline al renderizar (L.t reactiva al idioma)
private data class NavTab(
    val label: () -> String,
    val iconOutlined: ImageVector,
    val iconFilled: ImageVector,
    val screen: AppScreen
)

private val tabs = listOf(
    NavTab({ L.t("Mapa", "Map") },    Icons.Outlined.Map,           Icons.Filled.Map,           AppScreen.MapaPrincipal),
    NavTab({ L.t("Rutas", "Routes") }, Icons.Outlined.DirectionsBus, Icons.Filled.DirectionsBus, AppScreen.Rutas),
    NavTab({ L.t("Guardado", "Saved") },  Icons.Outlined.Bookmark,      Icons.Filled.Bookmark,      AppScreen.Guardado),
    NavTab({ L.t("Seguridad", "Safety") }, Icons.Outlined.Lock,          Icons.Filled.Lock,          AppScreen.Seguridad),
    NavTab({ L.t("Perfil", "Profile") },Icons.Outlined.Person,        Icons.Filled.Person,        AppScreen.Perfil),
)

//boton de navbar
@Composable
fun BottomNavBar(
    router: AppRouter,
    modifier: Modifier = Modifier
) {
    val borderColor = OutlineVariant.copy(alpha = 0.20f)
    val textoGrande = LocalDensity.current.fontScale >= 1.5f
    val medidor = rememberTextMeasurer()

    // En texto grande se conservan las cinco etiquetas y acciones en dos filas.
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .background(AppSurface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        maxItemsInEachRow = if (textoGrande) 3 else 5
    ) {
        tabs.forEach { tab ->
            val isActive = router.currentScreen == tab.screen
            val label = tab.label()
            val estiloEtiqueta = BodyXs.copy(fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal)
            val peso = if (textoGrande) medidor.measure(label, estiloEtiqueta).size.width.coerceAtLeast(1).toFloat() else 1f
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(peso)
                    .heightIn(min = 64.dp)
                    .selectable(selected = isActive, role = Role.Tab, onClick = { router.navigate(tab.screen) })
                    .padding(horizontal = 2.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = if (isActive) tab.iconFilled else tab.iconOutlined,
                    contentDescription = null,
                    tint = if (isActive) AcentoInterfaz else OnSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = label,
                    style = estiloEtiqueta,
                    color = if (isActive) AcentoInterfaz else OnSurfaceVariant,
                    softWrap = true,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 12.sp
                )
            }
        }
    }
}
