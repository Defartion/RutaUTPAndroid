package com.example.rutautpnative.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.model.CategoriaLugar
import com.example.rutautpnative.model.LugarGuardado
import com.example.rutautpnative.navigation.AppRouter
import com.example.rutautpnative.navigation.AppScreen
import com.example.rutautpnative.ui.components.BottomNavBar
import com.example.rutautpnative.ui.components.iconoParaCategoria
import com.example.rutautpnative.ui.theme.*

// Pantalla
@Composable
fun GuardadoScreen(router: AppRouter, viewModel: GuardadoViewModel = viewModel()) {
    val lugares by viewModel.lugares.collectAsState()
    val lineas by viewModel.lineas.collectAsState()
    val catalogo by viewModel.catalogo.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showAddLineaSheet by remember { mutableStateOf(false) }
    var selectedLugar by remember { mutableStateOf<LugarGuardado?>(null) }
    var selectedLinea by remember { mutableStateOf<RutaGTFS?>(null) }
    var exploradorRuta by remember { mutableStateOf<RutaGTFS?>(null) }

    val borderColor = OutlineVariant.copy(alpha = 0.25f)
    val tabs = listOf("Lugares", "Líneas")

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(AppSurface)
                    .drawBehind { drawLine(borderColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Bookmark, null, tint = AppPrimary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text("Guardado", style = HeadlineLg, color = AppPrimary, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier.clip(CircleShape).background(PrimaryContainer)
                        .clickable { showAddSheet = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Add, null, tint = OnPrimaryContainer, modifier = Modifier.size(12.dp))
                        Text("Añadir", style = LabelCapsMd, color = OnPrimaryContainer)
                    }
                }
            }

            // Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth().background(AppSurface)
                    .drawBehind { drawLine(borderColor, Offset(0f, size.height), Offset(size.width, size.height), 0.5.dp.toPx()) }
            ) {
                tabs.forEachIndexed { idx, label ->
                    Column(
                        modifier = Modifier.weight(1f).clickable { selectedTab = idx }.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(label, style = BodyMdMedium, color = if (selectedTab == idx) AppPrimary else OnSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(if (selectedTab == idx) AppPrimary else Color.Transparent))
                    }
                }
            }

            // Content
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
            ) {
                when (selectedTab) {
                    0 -> {
                        if (lugares.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.BookmarkBorder, null, tint = OnSurfaceVariant, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(14.dp))
                                    Text("Aún no tienes lugares guardados", style = BodyMdMedium, color = OnSurface)
                                }
                            }
                        } else {
                            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                                Spacer(Modifier.height(8.dp))
                                Text("Toca un lugar para ver más opciones.", style = BodySm, color = OnSurfaceVariant)
                                Spacer(Modifier.height(12.dp))
                                lugares.forEach { lugar ->
                                    LugarRow(lugar = lugar, onClick = { selectedLugar = lugar })
                                    Spacer(Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                    1 -> {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Líneas guardadas", style = BodyMdMedium, color = OnSurface, modifier = Modifier.weight(1f))
                                IconButton(onClick = { showAddLineaSheet = true }) {
                                    Icon(Icons.Filled.Add, null, tint = AppPrimary)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            if (lineas.isEmpty()) {
                                Text("Aún no guardas ninguna línea", style = BodySm, color = OnSurfaceVariant)
                            } else {
                                lineas.forEach { ruta ->
                                    LineaRow(ruta = ruta, onClick = { selectedLinea = ruta })
                                    Spacer(Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(90.dp))
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            BottomNavBar(router)
        }
    }

    // Sheets
    selectedLugar?.let { lugar ->
        LugarDetailSheet(
            lugar = lugar,
            router = router,
            onEliminar = { viewModel.eliminar(lugar); selectedLugar = null },
            onDismiss = { selectedLugar = null }
        )
    }

    selectedLinea?.let { ruta ->
        LineaDetailSheet(
            ruta = ruta,
            onQuitar = { viewModel.quitarLinea(ruta.id); selectedLinea = null },
            onVerRutaCompleta = { exploradorRuta = ruta; selectedLinea = null },
            onDismiss = { selectedLinea = null }
        )
    }

    if (showAddSheet) {
        AddLugarSheet(
            onSave = { nuevo -> viewModel.agregar(nuevo); showAddSheet = false },
            onDismiss = { showAddSheet = false }
        )
    }

    if (showAddLineaSheet) {
        AddLineaSheet(
            catalogo = catalogo,
            idsGuardados = lineas.map { it.id }.toSet(),
            onAgregar = { ruta -> viewModel.agregarLinea(ruta) },
            onDismiss = { showAddLineaSheet = false }
        )
    }

    exploradorRuta?.let { ruta ->
        ExploradorRutaScreen(ruta = ruta, onCerrar = { exploradorRuta = null })
    }
}

@Composable
private fun LugarRow(lugar: LugarGuardado, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val isUTP = lugar.nombre == "UTP"
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(if (isUTP) AppPrimary else PrimaryContainer.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(iconoParaCategoria(lugar.categoria), null, tint = if (isUTP) Color.White else AppPrimary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(lugar.nombre, style = BodyMdMedium, color = OnSurface)
                    if (lugar.esFrecuente) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Tertiary).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("FRECUENTE", style = LabelCapsMd, color = OnTertiary)
                        }
                    }
                }
                Text(lugar.direccion, style = BodySm, color = OnSurfaceVariant, maxLines = 1)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun LineaRow(ruta: RutaGTFS, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(ruta.color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Text(ruta.linea, style = HeadlineSm, color = ruta.color)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(ruta.empresa, style = BodyMdMedium, color = OnSurface)
                Text(ruta.recorrido, style = BodySm, color = OnSurfaceVariant, maxLines = 1)
            }
            Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Tertiary).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(ruta.frecuenciaTexto, style = LabelCapsMd, color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LugarDetailSheet(lugar: LugarGuardado, router: AppRouter, onEliminar: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(modifier = Modifier.padding(20.dp)) {
            val isUTP = lugar.nombre == "UTP"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(if (isUTP) AppPrimary else PrimaryContainer.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(iconoParaCategoria(lugar.categoria), null, tint = if (isUTP) Color.White else AppPrimary, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(lugar.nombre, style = HeadlineMd, color = OnSurface)
                    Text(lugar.direccion, style = BodySm, color = OnSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))
            Button(onClick = { router.navigate(AppScreen.MapaPrincipal); onDismiss() }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)) {
                Icon(Icons.Filled.Map, null); Spacer(Modifier.width(8.dp)); Text("Ver ruta desde mi posición", style = BodyMdMedium, color = Color.White)
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { router.navigate(AppScreen.Rutas); onDismiss() }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer)) {
                Icon(Icons.Filled.DirectionsBus, null, tint = OnPrimaryContainer); Spacer(Modifier.width(8.dp)); Text("Buscar transporte cercano", style = BodyMdMedium, color = OnPrimaryContainer)
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onEliminar, enabled = !lugar.esFijo, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = ErrorContainer)) {
                Icon(Icons.Filled.Delete, null, tint = OnErrorContainer); Spacer(Modifier.width(8.dp)); Text("Eliminar de guardados", style = BodyMdMedium, color = OnErrorContainer)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LineaDetailSheet(ruta: RutaGTFS, onQuitar: () -> Unit, onVerRutaCompleta: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(ruta.color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Text(ruta.linea, style = DisplayNumberMd, color = ruta.color)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(ruta.empresa, style = HeadlineSm, color = OnSurface)
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(TertiaryContainer).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(ruta.frecuenciaTexto, style = LabelCapsMd, color = Tertiary)
                    }
                }
            }
            Spacer(Modifier.height(16.dp)); Divider(); Spacer(Modifier.height(16.dp))
            Text("RECORRIDO", style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(ruta.recorrido, style = BodyMd, color = OnSurface)
            Spacer(Modifier.height(16.dp))
            Text("PARADAS PRINCIPALES", style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            ruta.paraderos.take(5).forEach { paradero ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AppPrimary))
                    Spacer(Modifier.width(10.dp))
                    Text(paradero.nombre, style = BodyMd, color = OnSurface)
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onVerRutaCompleta, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)) {
                Icon(Icons.Filled.LocationOn, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text("Ver ruta completa", style = HeadlineSm, color = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onQuitar, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = ErrorContainer)) {
                Icon(Icons.Filled.Delete, null, tint = OnErrorContainer); Spacer(Modifier.width(8.dp)); Text("Quitar de guardados", style = BodyMdMedium, color = OnErrorContainer)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cerrar", style = BodyMdMedium, color = OnSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLineaSheet(
    catalogo: List<RutaGTFS>,
    idsGuardados: Set<String>,
    onAgregar: (RutaGTFS) -> Unit,
    onDismiss: () -> Unit
) {
    var textoBusqueda by remember { mutableStateOf("") }

    val disponibles = remember(catalogo, idsGuardados, textoBusqueda) {
        val q = textoBusqueda.trim()
        catalogo
            .filter { it.id !in idsGuardados }
            .filter {
                q.isEmpty() ||
                    it.linea.contains(q, ignoreCase = true) ||
                    it.empresa.contains(q, ignoreCase = true) ||
                    it.recorrido.contains(q, ignoreCase = true) ||
                    it.variante.contains(q, ignoreCase = true)
            }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Añadir línea", style = HeadlineMd, color = OnSurface)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = textoBusqueda,
                onValueChange = { textoBusqueda = it },
                placeholder = { Text("Buscar línea, empresa o avenida", style = BodySm, color = OnSurfaceVariant) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = OnSurfaceVariant) },
                trailingIcon = {
                    if (textoBusqueda.isNotEmpty()) {
                        IconButton(onClick = { textoBusqueda = "" }) {
                            Icon(Icons.Filled.Close, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            if (disponibles.isEmpty()) {
                Text(
                    if (textoBusqueda.isNotBlank()) "No hay líneas que coincidan" else "No hay más líneas para agregar",
                    style = BodySm,
                    color = OnSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    disponibles.forEach { ruta ->
                        LineaRow(ruta = ruta, onClick = { onAgregar(ruta) })
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLugarSheet(onSave: (LugarGuardado) -> Unit, onDismiss: () -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var direccion by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf(CategoriaLugar.OTRO) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Guardar lugar", style = HeadlineMd, color = OnSurface)
            Spacer(Modifier.height(16.dp))
            Text("NOMBRE", style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                placeholder = { Text("Ej. Mi trabajo") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("DIRECCIÓN", style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = direccion,
                onValueChange = { direccion = it },
                placeholder = { Text("Ej. Av. España 123") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onSave(LugarGuardado(
                        nombre = nombre.ifBlank { "Nuevo lugar" },
                        direccion = direccion.ifBlank { "Sin dirección" },
                        categoria = categoria
                    ))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
            ) {
                Text("Guardar", style = HeadlineSm, color = Color.White)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}