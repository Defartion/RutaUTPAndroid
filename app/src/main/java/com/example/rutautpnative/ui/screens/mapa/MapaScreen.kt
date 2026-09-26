package com.example.rutautpnative.ui.screens.mapa

import com.google.maps.android.compose.MarkerState
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rutautpnative.navigation.AppRouter
import com.example.rutautpnative.navigation.AppScreen
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.data.senias.SeniasOverlay
import com.example.rutautpnative.data.senias.SeniasPrefs
import com.example.rutautpnative.model.TipoReporte
import com.example.rutautpnative.ui.components.BottomNavBar
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import kotlinx.coroutines.launch
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding

@Composable
fun MapaScreen(router: AppRouter, vm: MapaViewModel = viewModel()) {
    val focusManager = LocalFocusManager.current
    var mostrarDrawer by remember { mutableStateOf(false) }
    var showReportarSheet by remember { mutableStateOf(false) }

    // Inicializar destinos con iconos
    LaunchedEffect(Unit) {
        vm.destinos = listOf(
            DestinoChip(1, "Casa",      Icons.Filled.Home,       -8.1180, -79.0350),
            DestinoChip(2, "UTP",       Icons.Filled.School,     GTFSRepository.coordenadaUTP.latitude, GTFSRepository.coordenadaUTP.longitude),
            DestinoChip(3, "Trabajo",   Icons.Filled.Work,       -8.1050, -79.0200),
            DestinoChip(4, "Centro",    Icons.Filled.Business,   -8.1090, -79.0270),
            DestinoChip(5, "Huanchaco", Icons.Filled.BeachAccess,-8.0825, -79.1197),
        )
    }

    DisposableEffect(Unit) {
        onDispose { vm.detenerAnimacion() }
    }

    // Consumir un destino pendiente publicado por otra pestaña (p.ej. zonas de
    // referencia en Seguridad): se limpia y se selecciona como si el usuario lo
    // hubiera buscado manualmente. Convive con rutaPendiente (estado aparte).
    LaunchedEffect(router.destinoPendiente) {
        val pendiente = router.destinoPendiente ?: return@LaunchedEffect
        router.destinoPendiente = null
        vm.seleccionarLugarExterno(pendiente.titulo, pendiente.lat, pendiente.lon)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Mapa
        GoogleMap(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = with(LocalDensity.current) {
                    WindowInsets.statusBars.getTop(this).toDp()
                }),
            cameraPositionState = vm.cameraPositionState,
            onMapClick = { focusManager.clearFocus() },
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
        ) {
            // Marcador UTP
            MarkerComposable(
                state = MarkerState(position = GTFSRepository.coordenadaUTP),
                title = "UTP Trujillo"
            ) {
                MarcadorUTP()
            }

            // Marcador usuario
            MarkerComposable(
                state = MarkerState(position = LatLng(-8.1180, -79.0350)),
                title = "Mi ubicación"
            ) {
                PulsingUserMarker()
            }

            // Buses simulados
            vm.busSimulados.forEach { bus ->
                MarkerComposable(
                    state = MarkerState(position = LatLng(bus.lat, bus.lon)),
                    title = "Línea ${bus.linea}"
                ) {
                    BusMarker(linea = bus.linea)
                }
            }

            // Recorrido de ruta: pospuesto a propósito (ver TODO en MapaViewModel.kt).
        }

        // Ui Flotante
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            MapaHeader(onMenuClick = { mostrarDrawer = true })

            // Search panel
            SearchPanel(
                vm = vm,
                onSearch = { focusManager.clearFocus() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Bottom panel
            BottomPanel(
                router = router,
                rutas = vm.rutasCercanas,
                onReportar = { showReportarSheet = true }
            )
            Spacer(modifier = Modifier.height(100.dp)) // espacio para BottomNavBar
        }

        // Nav boton
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            BottomNavBar(router)
        }

        // del lado
        AnimatedVisibility(
            visible = mostrarDrawer,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it }
        ) {
            SideDrawer(
                router = router,
                onClose = { mostrarDrawer = false }
            )
        }
    }

    if (showReportarSheet) {
        MapaReportarSheet(onDismiss = { showReportarSheet = false })
    }
}

// Header
@Composable
private fun MapaHeader(onMenuClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(AppSurface.copy(alpha = 0.95f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceContainerLow)
                    .clickable { onMenuClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Menu, null, tint = OnSurface, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("Mapa", style = HeadlineLg, color = AppPrimary)
        }
    }
}

// Panel de busqueda
@Composable
private fun SearchPanel(vm: MapaViewModel, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val modoSenias by SeniasPrefs.observarActivo().collectAsState(initial = false)
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface.copy(alpha = 0.92f)),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // TextField
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceContainerLow)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                TextField(
                    value = vm.textoBusqueda,
                    onValueChange = {
                        vm.textoBusqueda = it
                        vm.buscarTexto(it)
                    },
                    placeholder = { Text(L.t("¿A dónde vas hoy?", "Where are you going today?"), style = BodyMd, color = OnSurfaceVariant) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(0.dp),
                    textStyle = BodyMd.copy(color = OnSurface)
                )
                if (vm.textoBusqueda.isNotEmpty()) {
                    IconButton(onClick = { vm.limpiar(); onSearch() }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Filled.Cancel, null, tint = OnSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // Chips
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                vm.destinos.forEach { destino ->
                        // Las claves de señas se mapean por ID (no por label) para no
                        // romperse al traducir; el texto mostrado va por L.t.
                        val claveSenia = when (destino.id) {
                            2 -> "mapa.destino.utp"
                            4 -> "mapa.destino.centro"
                            5 -> "mapa.destino.huanchaco"
                            else -> null
                        }
                        val etiqueta = when (destino.id) {
                            1 -> L.t("Casa", "Home")
                            2 -> "UTP"
                            3 -> L.t("Trabajo", "Work")
                            4 -> L.t("Centro", "Downtown")
                            5 -> "Huanchaco"
                            else -> destino.label
                        }
                        DestinoChipItem(
                            destino = destino.copy(label = etiqueta),
                            isActive = vm.destinoSeleccionado?.id == destino.id,
                            onClick = {
                                if (modoSenias && claveSenia != null) {
                                    scope.launch { SeniasOverlay.mostrar(claveSenia) }
                                } else {
                                    onSearch(); vm.seleccionar(destino)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

@Composable
private fun DestinoChipItem(destino: DestinoChip, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isActive) SecondaryContainer else SurfaceContainerHighest)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(destino.icon, null, tint = if (isActive) OnSecondaryContainer else OnSurface, modifier = Modifier.size(12.dp))
            Text(destino.label, style = BodyXsMedium, color = if (isActive) OnSecondaryContainer else OnSurface)
        }
    }
}

// Boton del panel
@Composable
private fun BottomPanel(router: AppRouter, rutas: List<RutaGTFS>, onReportar: () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AppPrimary)
                    .clickable { onReportar() }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text("REPORTAR", style = LabelCapsMd, color = Color.White)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                if (rutas.size == 1) "1 línea operando ahora" else "${rutas.size} líneas operando ahora",
                style = HeadlineBody,
                color = OnSurface.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryFixed)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(AppPrimary))
                    Text("En vivo", style = LabelCapsSm, color = AppPrimary)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            rutas.forEach { ruta ->
                BusCard(ruta = ruta) {
                    router.rutaPendiente = ruta.id
                    router.navigate(AppScreen.Rutas)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BusCard(ruta: RutaGTFS, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface.copy(alpha = 0.85f)),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.width(256.dp).clickable { onClick() }
    ) {
        Box {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("LÍNEA ${ruta.linea}", style = LabelCapsMd, color = OnSurfaceVariant)
                Text(ruta.empresa, style = HeadlineSm, color = OnSurface, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(PrimaryContainer).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text(ruta.precioTexto, style = LabelCapsMd, color = OnPrimaryContainer)
                    }
                    Text(ruta.frecuenciaTexto, style = BodySm, color = OnSurfaceVariant, maxLines = 1)
                }
            }
            Box(modifier = Modifier.width(4.dp).height(56.dp).clip(RoundedCornerShape(2.dp)).background(ruta.color).align(Alignment.CenterStart))
        }
    }
}

// Panel de reporte
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapaReportarSheet(onDismiss: () -> Unit) {
    var tipo by remember { mutableStateOf(TipoReporte.ALERTA) }
    var descripcion by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Reportar incidente", style = HeadlineMd, color = OnSurface)
            Spacer(Modifier.height(20.dp))
            Text("TIPO DE REPORTE", style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TipoReporte.ALERTA, TipoReporte.TRAFICO, TipoReporte.SUGERENCIA).forEach { t ->
                    FilterChip(selected = tipo == t, onClick = { tipo = t }, label = { Text(t.label, style = BodySm) })
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("DESCRIPCIÓN", style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                placeholder = { Text("¿Qué sucede?") },
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
                Text("Enviar reporte", style = HeadlineSm, color = Color.White)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showSuccess) {
        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = { Text("Reporte enviado") },
            text = { Text("Tu reporte fue enviado a la comunidad. Gracias por colaborar.") },
            confirmButton = { TextButton(onClick = { onDismiss() }) { Text("OK") } }
        )
    }
}