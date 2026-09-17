package com.example.rutautpnative.ui.screens.mapa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.navigation.AppRouter
import com.example.rutautpnative.navigation.AppScreen
import com.example.rutautpnative.ui.theme.*

// Lo del lado
@Composable
fun SideDrawer(router: AppRouter, onClose: () -> Unit) {
    var activeSheet by remember { mutableStateOf<DrawerSheet?>(null) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable { onClose() }
        )

        // Drawer panel
        Box(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .background(AppSurface)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        if (dragAmount < -30) onClose()
                    }
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppPrimary)
                        .statusBarsPadding()
                        .padding(top = 20.dp, bottom = 20.dp, start = 24.dp, end = 24.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("JD", style = HeadlineMd, color = Color.White)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Ruta UTP Trujillo", style = HeadlineSm, color = Color.White)
                        Text("Menú principal", style = BodyXs, color = Color.White.copy(alpha = 0.75f))
                    }
                }

                // Menu items
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    DrawerRow(Icons.Filled.Notifications, Tertiary, "Notificaciones")    { activeSheet = DrawerSheet.NOTIFICACIONES }
                    DrawerRow(Icons.Filled.LocationCity, Secondary, "Ciudad")             { activeSheet = DrawerSheet.CIUDAD }
                    Divider(modifier = Modifier.padding(start = 56.dp))
                    DrawerRow(Icons.Filled.Settings, OnSurfaceVariant, "Ajustes")         { activeSheet = DrawerSheet.AJUSTES }
                    DrawerRow(Icons.Filled.Headphones, OnSurfaceVariant, "Soporte")       { activeSheet = DrawerSheet.SOPORTE }
                    DrawerRow(Icons.Filled.Info, OnSurfaceVariant, "Sobre Nosotros")      { activeSheet = DrawerSheet.SOBRE_NOSOTROS }
                }

                // Logout
                DrawerRow(Icons.Filled.ExitToApp, AppPrimary, "Cerrar Sesión", destructive = true) {
                    showLogoutConfirm = true
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Active sheet
    activeSheet?.let { sheet ->
        when (sheet) {
            DrawerSheet.NOTIFICACIONES  -> NotificacionesSheet(onDismiss = { activeSheet = null })
            DrawerSheet.CIUDAD          -> CiudadSheet(onDismiss = { activeSheet = null })
            DrawerSheet.AJUSTES         -> AjustesSheet(onDismiss = { activeSheet = null })
            DrawerSheet.SOPORTE         -> SoporteSheet(onDismiss = { activeSheet = null })
            DrawerSheet.SOBRE_NOSOTROS  -> SobreNosotrosSheet(onDismiss = { activeSheet = null })
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("¿Te vas?") },
            text = { Text("Tendrás que volver a iniciar sesión para usar la app.") },
            confirmButton = {
                TextButton(onClick = { showLogoutConfirm = false; onClose() }) {
                    Text("Cerrar sesión", color = AppPrimary)
                }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancelar") } }
        )
    }
}

enum class DrawerSheet { NOTIFICACIONES, CIUDAD, AJUSTES, SOPORTE, SOBRE_NOSOTROS }

@Composable
private fun DrawerRow(icon: ImageVector, iconColor: Color, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = BodySmMedium, color = if (destructive) AppPrimary else OnSurface)
    }
}

// Paneles de notificaciones
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificacionesSheet(onDismiss: () -> Unit) {
    var notifOn by remember { mutableStateOf(true) }
    var pausaSeleccionada by remember { mutableStateOf<String?>(null) }
    val pausas = listOf("30 minutos", "1 hora", "3 horas", "Indefinido")

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(modifier = Modifier.padding(20.dp)) {
            SheetHeader(Icons.Filled.Notifications, Tertiary, "Notificaciones")
            Spacer(Modifier.height(16.dp))
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Activar notificaciones", style = BodyMdMedium, color = OnSurface)
                        Text("Recibe alertas de rutas y reportes", style = BodySm, color = OnSurfaceVariant)
                    }
                    Switch(checked = notifOn, onCheckedChange = { notifOn = it }, colors = SwitchDefaults.colors(checkedTrackColor = AppPrimary))
                }
            }
            if (notifOn) {
                Spacer(Modifier.height(16.dp))
                Text("PAUSAR NOTIFICACIONES", style = LabelCapsMd, color = OnSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                pausas.forEach { pausa ->
                    val isSelected = pausaSeleccionada == pausa
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) PrimaryContainer.copy(alpha = 0.3f) else SurfaceContainerLow)
                            .clickable { pausaSeleccionada = if (isSelected) null else pausa }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            null, tint = if (isSelected) AppPrimary else OnSurfaceVariant, modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(pausa, style = BodyMd, color = OnSurface, modifier = Modifier.weight(1f))
                        if (pausa == "Indefinido") Icon(Icons.Filled.Bedtime, null, tint = Secondary, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CiudadSheet(onDismiss: () -> Unit) {
    var ciudadSeleccionada by remember { mutableStateOf("Trujillo") }
    val ciudades = listOf("Lima", "Chiclayo", "Piura", "Cuzco", "Arequipa")

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(modifier = Modifier.padding(20.dp)) {
            SheetHeader(Icons.Filled.LocationCity, Secondary, "Ciudad")
            Spacer(Modifier.height(8.dp))
            Text("Selecciona tu ciudad para ver rutas y paraderos actualizados.", style = BodySm, color = OnSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            ciudades.forEach { ciudad ->
                val isSelected = ciudad == ciudadSeleccionada
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) PrimaryContainer.copy(alpha = 0.2f) else SurfaceContainerLow)
                        .clickable { ciudadSeleccionada = ciudad }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Business, null, tint = if (isSelected) AppPrimary else OnSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(ciudad, style = BodyMdMedium, color = OnSurface, modifier = Modifier.weight(1f))
                    if (isSelected) Icon(Icons.Filled.CheckCircle, null, tint = AppPrimary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, tint = OnSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Pronto añadiremos más ciudades.", style = BodyXs, color = OnSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AjustesSheet(onDismiss: () -> Unit) {
    var isDark by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(modifier = Modifier.padding(20.dp)) {
            SheetHeader(Icons.Filled.Settings, OnSurfaceVariant, "Ajustes")
            Spacer(Modifier.height(16.dp))
            Text("APARIENCIA", style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(false to "Claro" to Icons.Filled.LightMode, true to "Oscuro" to Icons.Filled.DarkMode).forEach { (pair, icon) ->
                    val (mode, label) = pair
                    val isSelected = isDark == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) PrimaryContainer else SurfaceContainerLow)
                            .clickable { isDark = mode }
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(icon, null, tint = if (isSelected) OnPrimaryContainer else OnSurface, modifier = Modifier.size(28.dp))
                            Text(label, style = BodyMdMedium, color = if (isSelected) OnPrimaryContainer else OnSurface)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoporteSheet(onDismiss: () -> Unit) {
    val faqs = listOf(
        Triple(Icons.Filled.Warning,    "¿Cómo reporto un incidente?",   "Toca el botón REPORTAR en el mapa o en Seguridad y describe la situación."),
        Triple(Icons.Filled.Bookmark,   "¿Cómo guardo un lugar?",        "En la pantalla de Guardado, presiona + Añadir y completa los datos."),
        Triple(Icons.Filled.LocationOn, "¿Cómo cambio mi destino?",      "En el mapa, toca los chips de Casa / UTP / Trabajo para cambiar rápido."),
        Triple(Icons.Filled.Refresh,    "¿Cómo actualizo una ruta?",     "Las rutas se actualizan automáticamente cada pocos segundos.")
    )

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            SheetHeader(Icons.Filled.Headphones, Secondary, "Soporte")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)) {
                Icon(Icons.Filled.Message, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Contactar Soporte Técnico", style = HeadlineSm, color = Color.White)
            }
            Spacer(Modifier.height(16.dp))
            Text("PREGUNTAS FRECUENTES", style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            faqs.forEach { (icon, pregunta, respuesta) ->
                var expanded by remember { mutableStateOf(false) }
                Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow), modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, tint = AppPrimary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(pregunta, style = BodyMdMedium, color = OnSurface, modifier = Modifier.weight(1f))
                            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = OnSurfaceVariant)
                        }
                        if (expanded) {
                            Spacer(Modifier.height(8.dp))
                            Text(respuesta, style = BodySm, color = OnSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SobreNosotrosSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(96.dp).clip(CircleShape).background(Brush.linearGradient(listOf(AppPrimary, PrimaryContainer, Tertiary))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.DirectionsBus, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Ruta UTP Trujillo", style = HeadlineMd, color = OnSurface)
            Box(modifier = Modifier.clip(CircleShape).background(SurfaceContainerLow).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("v1.0.0", style = LabelCapsMd, color = OnSurfaceVariant)
            }
            Spacer(Modifier.height(20.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text("SOBRE LA APP", style = LabelCapsMd, color = OnSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Aplicación que ayuda a los estudiantes de la UTP Trujillo a encontrar rutas de micros y combis hacia el campus. Incluye lugares guardados, reportes comunitarios y seguimiento en tiempo real.", style = BodyMd, color = OnSurface)
                Spacer(Modifier.height(16.dp))
                Text("EQUIPO DE DESARROLLO", style = LabelCapsMd, color = OnSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                listOf("Diseño y desarrollo" to "Joaquín Díaz", "Curso" to "Productos y Servicios - Ciclo 7", "Institución" to "UTP Trujillo").forEach { (rol, nombre) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(rol, style = BodySm, color = OnSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(nombre, style = BodySmMedium, color = OnSurface)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SheetHeader(icon: ImageVector, iconColor: Color, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Text(title, style = HeadlineMd, color = OnSurface)
    }
}