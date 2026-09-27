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
import com.example.rutautpnative.data.TemaStore
import com.example.rutautpnative.navigation.AppRouter
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import kotlinx.coroutines.launch

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
                        Text(L.t("Menú principal", "Main menu"), style = BodyXs, color = Color.White.copy(alpha = 0.75f))
                    }
                }

                // Menu items
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    DrawerRow(Icons.Filled.Notifications, Tertiary, L.t("Notificaciones", "Notifications")) { activeSheet = DrawerSheet.NOTIFICACIONES }
                    DrawerRow(Icons.Filled.LocationCity, Secondary, L.t("Ciudad", "City")) { activeSheet = DrawerSheet.CIUDAD }
                    Divider(modifier = Modifier.padding(start = 56.dp))
                    DrawerRow(Icons.Filled.Settings, OnSurfaceVariant, L.t("Ajustes", "Settings")) { activeSheet = DrawerSheet.AJUSTES }
                    DrawerRow(Icons.Filled.Headphones, OnSurfaceVariant, L.t("Soporte", "Support")) { activeSheet = DrawerSheet.SOPORTE }
                    DrawerRow(Icons.Filled.Info, OnSurfaceVariant, L.t("Sobre Nosotros", "About Us")) { activeSheet = DrawerSheet.SOBRE_NOSOTROS }
                }

                // Logout
                DrawerRow(Icons.Filled.ExitToApp, AppPrimary, L.t("Cerrar Sesión", "Log Out"), destructive = true) {
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
            title = { Text(L.t("¿Te vas?", "Leaving?")) },
            text = { Text(L.t("Tendrás que volver a iniciar sesión para usar la app.", "You'll need to log in again to use the app.")) },
            confirmButton = {
                TextButton(onClick = { showLogoutConfirm = false; onClose() }) {
                    Text(L.t("Cerrar sesión", "Log out"), color = AppPrimary)
                }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text(L.t("Cancelar", "Cancel")) } }
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
    val pausas = listOf(
        L.t("30 minutos", "30 minutes"),
        L.t("1 hora", "1 hour"),
        L.t("3 horas", "3 hours"),
        L.t("Indefinido", "Indefinitely")
    )

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(modifier = Modifier.padding(20.dp)) {
            SheetHeader(Icons.Filled.Notifications, Tertiary, L.t("Notificaciones", "Notifications"))
            Spacer(Modifier.height(16.dp))
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(L.t("Activar notificaciones", "Enable notifications"), style = BodyMdMedium, color = OnSurface)
                        Text(L.t("Recibe alertas de rutas y reportes", "Get route and report alerts"), style = BodySm, color = OnSurfaceVariant)
                    }
                    Switch(checked = notifOn, onCheckedChange = { notifOn = it }, colors = SwitchDefaults.colors(checkedTrackColor = AppPrimary))
                }
            }
            if (notifOn) {
                Spacer(Modifier.height(16.dp))
                Text(L.t("PAUSAR NOTIFICACIONES", "PAUSE NOTIFICATIONS"), style = LabelCapsMd, color = OnSurfaceVariant)
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
                        if (pausa == L.t("Indefinido", "Indefinitely")) Icon(Icons.Filled.Bedtime, null, tint = Secondary, modifier = Modifier.size(16.dp))
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
            SheetHeader(Icons.Filled.LocationCity, Secondary, L.t("Ciudad", "City"))
            Spacer(Modifier.height(8.dp))
            Text(L.t("Selecciona tu ciudad para ver rutas y paraderos actualizados.", "Select your city to see updated routes and stops."), style = BodySm, color = OnSurfaceVariant)
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
                Text(L.t("Pronto añadiremos más ciudades.", "We'll add more cities soon."), style = BodyXs, color = OnSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AjustesSheet(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    // Conectado a TemaStore (persistido): es el ÚNICO control de tema real,
    // igual que en iOS (SideDrawer.swift). Lee el espejo en memoria para
    // reflejar el cambio al instante (sin depender del timing DataStore→Flow).
    val isDark = TemaStore.oscuroActual

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(modifier = Modifier.padding(20.dp)) {
            SheetHeader(Icons.Filled.Settings, OnSurfaceVariant, L.t("Ajustes", "Settings"))
            Spacer(Modifier.height(16.dp))
            Text(L.t("APARIENCIA", "APPEARANCE"), style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(false to L.t("Claro", "Light") to Icons.Filled.LightMode, true to L.t("Oscuro", "Dark") to Icons.Filled.DarkMode).forEach { (pair, icon) ->
                    val (mode, label) = pair
                    val isSelected = isDark == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) PrimaryContainer else SurfaceContainerLow)
                            .clickable { scope.launch { TemaStore.establecerOscuro(mode) } }
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
        Triple(Icons.Filled.Warning,    L.t("¿Cómo reporto un incidente?", "How do I report an incident?"),
                                            L.t("Toca el botón REPORTAR en el mapa o en Seguridad y describe la situación.", "Tap the REPORT button on the map or in Safety and describe the situation.")),
        Triple(Icons.Filled.Bookmark,   L.t("¿Cómo guardo un lugar?", "How do I save a place?"),
                                            L.t("En la pantalla de Guardado, presiona + Añadir y completa los datos.", "On the Saved screen, tap + Add and fill in the details.")),
        Triple(Icons.Filled.LocationOn, L.t("¿Cómo cambio mi destino?", "How do I change my destination?"),
                                            L.t("En el mapa, toca los chips de Casa / UTP / Trabajo para cambiar rápido.", "On the map, tap the Home / UTP / Work chips to change quickly.")),
        Triple(Icons.Filled.Refresh,    L.t("¿Cómo actualizo una ruta?", "How do I update a route?"),
                                            L.t("Las rutas se actualizan automáticamente cada pocos segundos.", "Routes update automatically every few seconds."))
    )

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            SheetHeader(Icons.Filled.Headphones, Secondary, L.t("Soporte", "Support"))
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)) {
                Icon(Icons.Filled.Message, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(L.t("Contactar Soporte Técnico", "Contact Technical Support"), style = HeadlineSm, color = Color.White)
            }
            Spacer(Modifier.height(16.dp))
            Text(L.t("PREGUNTAS FRECUENTES", "FREQUENTLY ASKED QUESTIONS"), style = LabelCapsMd, color = OnSurfaceVariant)
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
                Text(L.t("SOBRE LA APP", "ABOUT THE APP"), style = LabelCapsMd, color = OnSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(L.t("Aplicación que ayuda a los estudiantes de la UTP Trujillo a encontrar rutas de micros y combis hacia el campus. Incluye lugares guardados, reportes comunitarios y seguimiento en tiempo real.", "An app that helps UTP Trujillo students find bus and combi routes toward campus. Includes saved places, community reports and real-time tracking."), style = BodyMd, color = OnSurface)
                Spacer(Modifier.height(16.dp))
                Text(L.t("EQUIPO DE DESARROLLO", "DEVELOPMENT TEAM"), style = LabelCapsMd, color = OnSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                listOf(L.t("Diseño y desarrollo", "Design & development") to "Joaquín Díaz", L.t("Curso", "Course") to "Productos y Servicios - Ciclo 7", L.t("Institución", "Institution") to "UTP Trujillo").forEach { (rol, nombre) ->
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