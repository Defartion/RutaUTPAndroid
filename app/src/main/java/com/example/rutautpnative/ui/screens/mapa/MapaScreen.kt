package com.example.rutautpnative.ui.screens.mapa

import com.google.maps.android.compose.MarkerState
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
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
import com.example.rutautpnative.data.ubicacion.LocationService
import com.example.rutautpnative.model.TipoReporte
import com.example.rutautpnative.ui.components.BottomNavBar
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapaScreen(router: AppRouter, vm: MapaViewModel = viewModel()) {
    val focusManager = LocalFocusManager.current
    var mostrarDrawer by remember { mutableStateOf(false) }
    var showReportarSheet by remember { mutableStateOf(false) }

    // Permiso de ubicacion (pedir solo cuando el usuario toca el boton, como
    // en iOS: no se pide al abrir la pantalla).
    val permisoUbicacion = rememberPermissionState(android.Manifest.permission.ACCESS_FINE_LOCATION)
    val autorizadoGPS = permisoUbicacion.status.isGranted

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

    // Enciende el GPS en cuanto haya permiso (al concederlo o al entrar con
    // permiso ya dado). El conteo de consumidores apaga el hardware al salir.
    LaunchedEffect(autorizadoGPS) {
        if (autorizadoGPS) vm.iniciarGPS()
    }

    // Al volver de otra pestaña (p.ej. tras "Ver Ruta Completa") el bucle de
    // la flota quedo detenido en onDispose: reanudar flota + GPS aqui.
    LaunchedEffect(Unit) {
        vm.reanudar()
    }

    DisposableEffect(Unit) {
        onDispose {
            vm.detenerAnimacion()
            vm.detenerGPS()
        }
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
            onMapClick = {
                focusManager.clearFocus()
                vm.seleccionarBus(null)
            },
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

            // Marcador usuario: GPS REAL (nulo hasta el primer fix; antes era
            // una posicion fija de demostracion).
            vm.userRealCoordinate?.let { pos ->
                MarkerComposable(
                    state = MarkerState(position = pos),
                    title = L.t("Mi ubicación", "My location")
                ) {
                    PulsingUserMarker()
                }
            }

            // Buses animados sobre shapes GTFS REALES. Tope de 8 marcadores
            // en el mapa por rendimiento; las cards del panel muestran TODAS
            // las líneas (igual que iOS). Tocar uno abre su popup de detalle.
            vm.busesAnimados.take(8).forEach { bus ->
                val seleccionado = vm.busSeleccionado?.id == bus.id
                MarkerComposable(
                    // IMPORTANTE (maps-compose): el contenido de un
                    // MarkerComposable se dibuja a UN BITMAP que solo se
                    // re-renderiza cuando cambia una de estas claves. La
                    // POSICION actualiza aparte (via state), pero el giro del
                    // bus y la flechita del rumbo viajan POR AQUI: sin el
                    // fotograma como key, el bitmap queda congelado en el
                    // rumbo del nacimiento del marcador. Con la key = frame,
                    // en avenidas rectas no se re-renderiza nada (frame
                    // constante) aunque el bus avance.
                    keys = arrayOf<Any>(bus.id, indiceFotogramaBus(bus.heading), seleccionado),
                    state = MarkerState(position = LatLng(bus.lat, bus.lon)),
                    anchor = BusMarkerAncla,
                    title = L.t("Línea", "Route") + " ${bus.linea}",
                    onClick = {
                        vm.seleccionarBus(bus)
                        true
                    }
                ) {
                    BusMarker3D(
                        linea = bus.linea,
                        color = bus.color,
                        heading = bus.heading,
                        seleccionado = seleccionado
                    )
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

            // Boton "Mi Ubicacion" GPS: siempre en la misma posicion (derecha,
            // sobre el panel inferior), como en iOS.
            // - Sin permiso: gris, lo pide al tocar.
            // - Con permiso sin fix: primario, recentra al tocar (relanza GPS).
            // - Con posicion: primario, recentra la camara sobre el usuario.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .padding(end = 20.dp, bottom = 8.dp)
                        .size(48.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(AppSurface)
                        .clickable {
                            if (!autorizadoGPS) permisoUbicacion.launchPermissionRequest()
                            else vm.recenterOnUser()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (autorizadoGPS) Icons.Filled.MyLocation else Icons.Filled.LocationOff,
                        contentDescription = L.t("Mi ubicación", "My location"),
                        tint = if (autorizadoGPS) AppPrimary else OnSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Bottom panel
            BottomPanel(
                rutas = vm.rutasCercanas,
                buses = vm.busesAnimados,
                onReportar = { showReportarSheet = true },
                onSeleccionarBus = { vm.seleccionarBus(it) }
            )
            Spacer(modifier = Modifier.height(100.dp)) // espacio para BottomNavBar
        }

        // POPUP DETALLE DE BUS (encima del panel inferior, como el
        // BusDetailPopup de iOS). Tocar el mapa o la X lo cierra.
        AnimatedVisibility(
            visible = vm.busSeleccionado != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 258.dp)
        ) {
            vm.busSeleccionado?.let { bus ->
                BusDetailPopup(
                    bus = bus,
                    onClose = { vm.seleccionarBus(null) },
                    onVerRuta = {
                        vm.seleccionarBus(null)
                        // Abre el detalle de ESA línea en Rutas, no la lista.
                        router.rutaPendiente = bus.rutaId
                        router.navigate(AppScreen.Rutas)
                    }
                )
            }
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
            Text(L.t("Mapa", "Map"), style = HeadlineLg, color = AppPrimary)
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
private fun BottomPanel(
    rutas: List<RutaGTFS>,
    buses: List<BusAnimado>,
    onReportar: () -> Unit,
    onSeleccionarBus: (BusAnimado) -> Unit
) {
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
                    Text(L.t("REPORTAR", "REPORT"), style = LabelCapsMd, color = Color.White)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                if (rutas.size == 1) L.t("1 línea operando ahora", "1 route operating now") else L.t("${rutas.size} líneas operando ahora", "${rutas.size} routes operating now"),
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
                    Text(L.t("En vivo", "Live"), style = LabelCapsSm, color = AppPrimary)
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
            // Cards con TODAS las líneas que pasan por el punto (el tope de 8
            // es solo para los marcadores del mapa). Tap → popup del bus.
            buses.forEach { bus ->
                BusCard(bus = bus) { onSeleccionarBus(bus) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

//----Popup de detalle del bus (puerto del BusDetailPopup de iOS)----
@Composable
private fun BusDetailPopup(
    bus: BusAnimado,
    onClose: () -> Unit,
    onVerRuta: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(10.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(AppSurface)
            .border(1.dp, bus.color.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Ícono circular con el color de la línea (fondo tintado al 18%).
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(bus.color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.DirectionsBus, null, tint = bus.color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(L.t("LÍNEA", "ROUTE") + " ${bus.linea}", style = HeadlineXs, color = OnSurface)
                    Spacer(Modifier.width(8.dp))
                    // Cápsula de llegada con el color de la línea.
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(bus.color)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(bus.etiquetaLlegada, style = LabelCapsSm, color = Color.White)
                    }
                }
                Text(
                    "${bus.empresa} • ${bus.tipo} (${bus.ramalTexto})",
                    style = BodySm, color = OnSurfaceVariant, maxLines = 1
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        // CTA con el color del bus: abre el detalle de ESA línea en Rutas.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bus.color)
                .clickable { onVerRuta() },
            contentAlignment = Alignment.Center
        ) {
            Text(L.t("Ver Ruta Completa", "View Full Route"), style = HeadlineXs, color = Color.White)
        }
    }
}

// Card de línea del panel inferior (puerto del BusCard de iOS): línea,
// empresa, cápsula de llegada y "tipo • ramal". Tap → popup del bus.
@Composable
private fun BusCard(bus: BusAnimado, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface.copy(alpha = 0.85f)),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.width(256.dp).clickable { onClick() }
    ) {
        Box {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(L.t("LÍNEA", "ROUTE") + " ${bus.linea}", style = LabelCapsMd, color = OnSurfaceVariant)
                Text(bus.empresa, style = HeadlineSm, color = OnSurface, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(bus.color)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(bus.etiquetaLlegada, style = LabelCapsSm, color = Color.White)
                    }
                    Text(
                        "${bus.tipo} • ${bus.ramalTexto}",
                        style = BodySm, color = OnSurfaceVariant, maxLines = 1
                    )
                }
            }
            Box(modifier = Modifier.width(4.dp).height(56.dp).clip(RoundedCornerShape(2.dp)).background(bus.color).align(Alignment.CenterStart))
        }
    }
}

// Panel de reporte
// Etiqueta del tipo de reporte según idioma (el enum guarda etiquetas en español).
private fun tipoLabelMapa(tipo: TipoReporte): String = when (tipo) {
    TipoReporte.ALERTA     -> L.t("ALERTA", "ALERT")
    TipoReporte.TRAFICO    -> L.t("TRÁFICO", "TRAFFIC")
    TipoReporte.SUGERENCIA -> L.t("SUGERENCIA", "SUGGESTION")
    TipoReporte.OTRO       -> L.t("OTRO", "OTHER")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapaReportarSheet(onDismiss: () -> Unit) {
    var tipo by remember { mutableStateOf(TipoReporte.ALERTA) }
    var descripcion by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(L.t("Reportar incidente", "Report incident"), style = HeadlineMd, color = OnSurface)
            Spacer(Modifier.height(20.dp))
            Text(L.t("TIPO DE REPORTE", "REPORT TYPE"), style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TipoReporte.ALERTA, TipoReporte.TRAFICO, TipoReporte.SUGERENCIA).forEach { t ->
                    FilterChip(selected = tipo == t, onClick = { tipo = t }, label = { Text(tipoLabelMapa(t), style = BodySm) })
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
            text = { Text(L.t("Tu reporte fue enviado a la comunidad. Gracias por colaborar.", "Your report was sent to the community. Thanks for contributing.")) },
            confirmButton = { TextButton(onClick = { onDismiss() }) { Text("OK") } }
        )
    }
}