package com.example.rutautpnative.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.navigation.AppRouter
import com.example.rutautpnative.navigation.AppScreen
import com.example.rutautpnative.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

//Navtab
private data class NavTab(
    val label: String,
    val iconOutlined: ImageVector,
    val iconFilled: ImageVector,    
    val screen: AppScreen
)

private val tabs = listOf(
    NavTab("Mapa",      Icons.Outlined.Map,          Icons.Filled.Map,          AppScreen.MapaPrincipal),
    NavTab("Rutas",     Icons.Outlined.DirectionsBus, Icons.Filled.DirectionsBus, AppScreen.Rutas),
    NavTab("Guardado",  Icons.Outlined.Bookmark,      Icons.Filled.Bookmark,     AppScreen.Guardado),
    NavTab("Seguridad", Icons.Outlined.Lock,           Icons.Filled.Lock,         AppScreen.Seguridad),
    NavTab("Perfil",    Icons.Outlined.Person,         Icons.Filled.Person,       AppScreen.Perfil),
)

//boton de navbar
@Composable
fun BottomNavBar(
    router: AppRouter,
    modifier: Modifier = Modifier
) {
    val borderColor = OutlineVariant.copy(alpha = 0.20f)

    Row(
        modifier = modifier
            .fillMaxWidth()
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val isActive = router.currentScreen == tab.screen
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { router.navigate(tab.screen) }
                    .padding(vertical = 6.dp)
            ) {
                Icon(
                    imageVector = if (isActive) tab.iconFilled else tab.iconOutlined,
                    contentDescription = tab.label,
                    tint = if (isActive) AppPrimary else OnSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = tab.label,
                    style = LabelCapsSm,
                    color = if (isActive) AppPrimary else OnSurfaceVariant.copy(alpha = 0.65f),
                    maxLines = 1,
                    softWrap = false,
                    fontSize = 9.sp
                )
            }
        }
    }
}