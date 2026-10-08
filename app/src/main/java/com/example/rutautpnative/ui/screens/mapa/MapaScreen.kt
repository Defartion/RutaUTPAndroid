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
import com.example.rutautpnative.data.LugaresStore
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.data.geo.Geocodificacion
import com.example.rutautpnative.data.routing.TransitPlanner
import com.example.rutautpnative.data.senias.SeniasOverlay
import com.example.rutautpnative.data.senias.SeniasPrefs
import com.example.rutautpnative.data.tracking.MQTTObservationPublisher
import com.example.rutautpnative.data.tracking.MQTTConfiguration
import com.example.rutautpnative.data.tracking.OccupancyService
import com.example.rutautpnative.data.tracking.PassiveTrackingCoordinator
import com.example.rutautpnative.data.ubicacion.LocationService
import com.example.rutautpnative.model.LugarGuardado
import com.example.rutautpnative.model.TipoReporte
import com.example.rutautpnative.ui.components.BottomNavBar
import com.example.rutautpnative.ui.components.iconoParaCategoria
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.screens.MapaUbicacionPicker
import com.example.rutautpnative.ui.screens.RouteChangesSheet
import com.example.rutautpnative.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PatternItem
import com.google.maps.android.compose.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapaScreen(router: AppRouter, vm: MapaViewModel = viewModel()) {
    val focusManager = LocalFocusManager.current
    var mostrarDrawer by remember { mutableStateOf(false) }
    var showReportarSheet by remember { mutableStateOf(false) }
    var mostrarCambiosRuta by remember { mutableStateOf(false) }

    // Permiso de ubicacion (pedir solo cuando el usuario toca el boton, como
    // en iOS: no se pide al abrir la pantalla).
    val permisoUbicacion = rememberPermissionState(android.Manifest.permission.ACCESS_FINE_LOCATION)
    val autorizadoGPS = permisoUbicacion.status.isGranted

    // Chips de destino: fijos (UTP/Centro/Huanchaco, con seña) + lugares
    // guardados del usuario (con coordenada), dedup por nombre y tope 6 —
    // el refrescarDestinos del iOS. Reaccionan a cambios en Guardado.
    val lugaresGuardados by LugaresStore.observar().collectAsState(initial = emptyList())
    LaunchedEffect(lugaresGuardados) {
        vm.destinos = chipsDesde(lugaresGuardados)
    }

    // "Elegir en el mapa": picker a pantalla completa + geocodificación inversa.
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var mostrarElegirDestino by remember { mutableStateOf(false) }

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

            // Itinerario a pie + bus (Fase 3): caminatas punteadas y tramo en
            // bus a DOBLE trazo (blanco grueso + color de la linea), como iOS.
            val it = vm.itinerario
            if (it != null) {
                val dens = LocalDensity.current
                val anchoPie = with(dens) { 4.dp.toPx() }
                val patron = listOf(
                    Dash(with(dens) { 3.dp.toPx() }),
                    Gap(with(dens) { 7.dp.toPx() })
                )
                Polyline(points = it.walkToBoard, color = Secondary, width = anchoPie, pattern = patron)
                Polyline(points = it.walkToDestination, color = Secondary, width = anchoPie, pattern = patron)
                Polyline(points = it.busDibujo, color = AppSurface, width = with(dens) { 8.dp.toPx() }, zIndex = 1f)
                Polyline(points = it.busDibujo, color = it.ruta.color, width = with(dens) { 5.dp.toPx() }, zIndex = 2f)

                // Paradas del plan: 1 SUBE (azul) y 2 BAJA (fucsia), ancla abajo.
                MarkerComposable(
                    state = MarkerState(it.board.coordinate),
                    anchor = Offset(0.5f, 1f),
                    title = it.board.nombre
                ) {
                    MarcadorParada("1", L.t("SUBE", "BOARD"), Secondary)
                }
                MarkerComposable(
                    state = MarkerState(it.alight.coordinate),
                    anchor = Offset(0.5f, 1f),
                    title = it.alight.nombre
                ) {
                    MarcadorParada("2", L.t("BAJA", "EXIT"), AppPrimary)
                }
            }

            // Marcador del destino buscado (iOS lo oculta si es la UTP).
            vm.destinoSeleccionado?.let { d ->
                if (d.label != "UTP") {
                    MarkerComposable(
                        state = MarkerState(LatLng(d.lat, d.lon)),
                        anchor = Offset(0.5f, 1f),
                        title = d.label
                    ) {
                        MarcadorDestinoBuscado(d.label)
                    }
                }
            }

            // Recorrido de ruta: pospuesto a propósito (ver TODO en MapaViewModel.kt).
        }

        // Ui Flotante
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            MapaHeader(onMenuClick = { mostrarDrawer = true })

            // Cápsula de estado de la contribución (baliza del pasajero):
            // visible solo si el usuario la activó en Ajustes (Fase 5).
            val contribucionActiva by PassiveTrackingCoordinator.consentimiento.collectAsState()
            if (contribucionActiva) {
                EstadoContribucion(modifier = Modifier.padding(horizontal = 16.dp))
            }

            // Search panel
            SearchPanel(
                vm = vm,
                onSearch = { focusManager.clearFocus() },
                onElegirEnMapa = { mostrarElegirDestino = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            // Resumen del itinerario a pie + bus (Fase 3): pasos, precio y ETA.
            vm.destinoSeleccionado?.let { destino ->
                ResumenItinerario(
                    destino = destino.label,
                    calculando = vm.calculandoItinerario,
                    itinerario = vm.itinerario,
                    mensaje = vm.mensajeRuta,
                    onQuitar = { vm.limpiar() },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                )
            }

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
                fuenteReal = vm.fuenteFlota == FuenteFlota.REAL,
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
        MapaReportarSheet(
            onDismiss = { showReportarSheet = false },
            onAbrirCambios = { showReportarSheet = false; mostrarCambiosRuta = true }
        )
    }

    // Cambios de ruta (Fase 8): obras/cierre/desvio confirmados por la
    // comunidad via MQTT (dos cuentas, 15 min de vigencia).
    if (mostrarCambiosRuta) {
        RouteChangesSheet(onDismiss = { mostrarCambiosRuta = false })
    }

    //----"Elegir en el mapa" (Fase 4): picker a pantalla completa +----
    //----geocodificacion inversa (calle + sublocalidad + ciudad).        ----
    if (mostrarElegirDestino) {
        MapaUbicacionPicker(
            inicial = null,
            titulo = L.t("Arrastra el mapa hasta tu destino", "Drag the map to your destination"),
            textoConfirmar = L.t("Usar este destino", "Use this destination"),
            onConfirmar = { punto ->
                scope.launch {
                    val titulo = Geocodificacion.direccionDe(punto, context)
                        ?: L.t("Punto en el mapa", "Point on the map")
                    vm.seleccionarLugarExterno(titulo, punto.latitude, punto.longitude)
                }
            },
            onCerrar = { mostrarElegirDestino = false }
        )
    }
}

/// Chips de destino: fijos (UTP/Centro/Huanchaco, con clave de seña) + lugares
/// guardados del usuario con coordenada, sin duplicar los fijos por nombre,
/// tope 6 — el refrescarDestinos del iOS.
private fun chipsDesde(lugares: List<LugarGuardado>): List<DestinoChip> {
    val fijos = listOf(
        DestinoChip(2, "UTP", Icons.Filled.School, GTFSRepository.coordenadaUTP.latitude, GTFSRepository.coordenadaUTP.longitude),
        DestinoChip(4, L.t("Centro", "Downtown"), Icons.Filled.Business, -8.1090, -79.0270),
        DestinoChip(5, "Huanchaco", Icons.Filled.BeachAccess, -8.0825, -79.1197)
    )
    val nombresFijos = fijos.map { it.label.lowercase() }.toSet()
    val deLugares = lugares
        .filter { it.nombre.lowercase() !in nombresFijos && it.lat != null && it.lon != null }
        .map { lugar ->
            DestinoChip(
                id = "lug|${lugar.nombre}".hashCode(),
                label = lugar.nombre,
                icon = iconoParaCategoria(lugar.categoria),
                lat = lugar.lat!!,
                lon = lugar.lon!!
            )
        }
    return (fijos + deLugares).take(6)
}

//----Cápsula de estado de la contribución (estadoContribucion del iOS)----
@Composable
private fun EstadoContribucion(modifier: Modifier = Modifier) {
    val estadoPublicador by PassiveTrackingCoordinator.estadoPublicador.collectAsState()
    val mensaje by PassiveTrackingCoordinator.mensajeEstado.collectAsState()

    val (color, etiqueta) = when (estadoPublicador) {
        MQTTObservationPublisher.EstadoPublicador.CONECTADO ->
            Color(0xFF43A047) to L.t("Conectado", "Connected")
        MQTTObservationPublisher.EstadoPublicador.CONECTANDO ->
            Color(0xFFFB8C00) to L.t("Conectando…", "Connecting…")
        MQTTObservationPublisher.EstadoPublicador.FALLO ->
            AppError to L.t("Sin conexión", "Offline")
        MQTTObservationPublisher.EstadoPublicador.INACTIVO ->
            OnSurfaceVariant.copy(alpha = 0.6f) to L.t("Inactivo", "Inactive")
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AppSurface.copy(alpha = 0.92f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            if (mensaje.isNotBlank()) mensaje else etiqueta,
            style = BodyXs, color = OnSurfaceVariant, maxLines = 1
        )
    }
}

// Header
@Composable
private fun MapaHeader(onMenuClick: () -> Unit) {    Column(
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
private fun SearchPanel(
    vm: MapaViewModel,
    onSearch: () -> Unit,
    onElegirEnMapa: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val modoSenias by SeniasPrefs.observarActivo().collectAsState(initial = false)
    var campoEnfocado by remember { mutableStateOf(false) }

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
                    onValueChange = { vm.actualizarTextoBusqueda(it, campoEnfocado) },
                    placeholder = { Text(L.t("¿A dónde vas hoy?", "Where are you going today?"), style = BodyMd, color = OnSurfaceVariant) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        vm.buscarTexto(vm.textoBusqueda)
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .padding(0.dp)
                        .onFocusChanged { campoEnfocado = it.isFocused },
                    textStyle = BodyMd.copy(color = OnSurface)
                )
                if (vm.textoBusqueda.isNotEmpty()) {
                    IconButton(onClick = { vm.limpiar(); onSearch() }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Filled.Cancel, null, tint = OnSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
                Spacer(Modifier.width(8.dp))
                // "Elegir en el mapa" (círculo gris con brújula, como iOS).
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainerHighest)
                        .clickable { onSearch(); onElegirEnMapa() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Explore,
                        contentDescription = L.t("Elegir en el mapa", "Pick on map"),
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Sugerencias de autocompletado (Places, hasta 5 — como el
            // MKLocalSearchCompleter del iOS). Solo con el campo enfocado.
            if (campoEnfocado && (vm.sugerencias.isNotEmpty() || vm.buscandoSugerencias)) {
                Spacer(Modifier.height(8.dp))
                if (vm.buscandoSugerencias && vm.sugerencias.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AppPrimary)
                        Text(L.t("Buscando lugares…", "Searching places…"), style = BodySm, color = OnSurfaceVariant)
                    }
                }
                vm.sugerencias.take(5).forEach { s ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                vm.seleccionarSugerencia(s)
                                focusManager.clearFocus()
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Filled.Place, null, tint = AppPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.titulo, style = BodySmMedium, color = OnSurface, maxLines = 1)
                            if (s.subtitulo.isNotBlank()) {
                                Text(s.subtitulo, style = BodyXs, color = OnSurfaceVariant, maxLines = 1)
                            }
                        }
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
                            2 -> "UTP"
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
    fuenteReal: Boolean,
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
            // Badge "En vivo": SOLO con flota real del broker (igual que iOS,
            // que lo enciende con fuenteFlota == .real).
            if (fuenteReal) {
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

//----Resumen del itinerario (puerto del resumenItinerario de iOS)----
@Composable
private fun ResumenItinerario(
    destino: String,
    calculando: Boolean,
    itinerario: TransitPlanner.Plan?,
    mensaje: String?,
    onQuitar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface.copy(alpha = 0.92f)),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Place, null, tint = AppPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    L.t("Hacia", "To") + " $destino",
                    style = HeadlineSm, color = OnSurface,
                    maxLines = 1, modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onQuitar, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Filled.Close, null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }

            when {
                calculando -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = AppPrimary
                    )
                    Text(
                        L.t("Buscando paradero y transporte…", "Finding stop and route…"),
                        style = BodySm, color = OnSurfaceVariant
                    )
                }

                itinerario != null -> {
                    val plan = itinerario
                    Spacer(Modifier.height(10.dp))
                    PasoItinerario(
                        icon = Icons.Filled.DirectionsWalk,
                        texto = L.t(
                            "Camina ${plan.caminataBoardMetros.roundToInt()} m · ${plan.board.nombre}",
                            "Walk ${plan.caminataBoardMetros.roundToInt()} m · ${plan.board.nombre}"
                        )
                    )
                    PasoItinerario(
                        icon = Icons.Filled.DirectionsBus,
                        color = plan.ruta.color,
                        texto = L.t(
                            "Toma la línea ${plan.ruta.linea} · ${plan.ruta.precioTexto}",
                            "Take line ${plan.ruta.linea} · ${plan.ruta.precioTexto}"
                        )
                    )
                    PasoItinerario(
                        icon = Icons.Filled.Flag,
                        texto = L.t("Baja en ${plan.alight.nombre}", "Get off at ${plan.alight.nombre}")
                    )
                    if (plan.caminataDestinoMetros.roundToInt() > 0) {
                        Text(
                            L.t(
                                "Luego camina ${plan.caminataDestinoMetros.roundToInt()} m hasta tu destino",
                                "Then walk ${plan.caminataDestinoMetros.roundToInt()} m to your destination"
                            ),
                            style = BodySm, color = OnSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(L.t("··· A pie", "··· Walking"), style = BodyXs, color = OnSurfaceVariant)
                        Text(L.t("━━ En bus", "━━ By bus"), style = BodyXs, color = plan.ruta.color)
                        Text("· ~${plan.etaMinutos} min", style = BodyXs, color = OnSurface)
                    }
                    if (plan.caminataAproximada) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.Warning, null, tint = AppError, modifier = Modifier.size(14.dp))
                            Text(
                                L.t("Caminata estimada en línea recta", "Walking legs estimated as straight lines"),
                                style = BodyXs, color = OnSurfaceVariant
                            )
                        }
                    }
                }

                mensaje != null -> Text(mensaje, style = BodySm, color = OnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PasoItinerario(icon: ImageVector, texto: String, color: Color = OnSurface) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(texto, style = BodySm, color = OnSurface, maxLines = 1)
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
                // Solo para vehiculos REALES: aclarar que la llegada es una
                // estimacion (o por que no hay) — texto del BusDetailPopup iOS.
                if (bus.fuente == FuenteFlota.REAL) {
                    Text(
                        if (bus.minutosLlegada == null)
                            L.t(
                                "Llegada no disponible: faltan datos suficientes del vehículo.",
                                "Arrival unavailable: not enough vehicle data."
                            )
                        else
                            L.t(
                                "Llegada aproximada al punto consultado de la ruta. Puede variar por tráfico y paradas.",
                                "Estimated arrival to the point on the route. May vary with traffic and stops."
                            ),
                        style = BodyXs, color = OnSurfaceVariant, maxLines = 2
                    )
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }

        // Panel de ocupacion (Fase 7): SOLO vehiculos reales; los de demo
        // no reciben reportes (la identidad es el vehicleID del backend,
        // nunca el numero de linea).
        if (bus.fuente == FuenteFlota.REAL) {
            Spacer(Modifier.height(12.dp))
            BusOccupancyPanel(vehicleId = bus.id.removePrefix("real-"))
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                L.t(
                    "La ocupación estará disponible en los buses en vivo. Los buses de demostración no reciben reportes.",
                    "Occupancy will be available on live buses. Demo buses don't receive reports."
                ),
                style = BodyXs, color = OnSurfaceVariant
            )
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

//----Panel de ocupacion del bus (puerto del BusOccupancyPanel.swift)----
@Composable
private fun BusOccupancyPanel(vehicleId: String) {
    // El servicio vive y muere con el panel (onAppear/onDisappear del iOS):
    // una conexion MQTT propia, apagada al cerrar el popup.
    val config = remember { MQTTConfiguration.desdeBuildConfig() }
    val servicio = remember(config) { config?.let { OccupancyService(it) } }

    DisposableEffect(servicio) {
        servicio?.start()
        onDispose { servicio?.stop() }
    }

    val listo by (servicio?.listo ?: remember { MutableStateFlow(false) }).collectAsState()
    val lecturas by (servicio?.lecturas ?: remember { MutableStateFlow(emptyMap<String, OccupancyService.LecturaOcupacion>()) }).collectAsState()
    val enviando by (servicio?.enviando ?: remember { MutableStateFlow(false) }).collectAsState()
    val resultado by (servicio?.resultado ?: remember { MutableStateFlow<String?>(null) }).collectAsState()

    val lectura = lecturas[vehicleId]
    var estadoPendiente by remember { mutableStateOf<OccupancyService.EstadoOcupacion?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerLow)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Groups, null, tint = OnSurface, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(L.t("Ocupación", "Occupancy"), style = BodyMdMedium, color = OnSurface, modifier = Modifier.weight(1f))
            Text(
                when {
                    lectura?.estado != null -> lectura.estado.etiqueta()
                    !listo -> L.t("No disponible", "Unavailable")
                    else -> L.t("Sin confirmar", "Unconfirmed")
                },
                style = BodyXs,
                color = if (lectura?.estado == OccupancyService.EstadoOcupacion.LLENO) Color(0xFFFB8C00)
                else OnSurfaceVariant
            )
        }
        lectura?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                L.t("${it.confirmaciones} cuentas coinciden", "${it.confirmaciones} accounts agree") +
                    " · " + L.t("vence", "expires") + " ${it.venceTexto}",
                style = BodyXs, color = OnSurfaceVariant
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { estadoPendiente = OccupancyService.EstadoOcupacion.VACIO },
                enabled = listo && !enviando,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.Person, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(L.t("Vacío", "Empty"), style = BodySm)
            }
            OutlinedButton(
                onClick = { estadoPendiente = OccupancyService.EstadoOcupacion.LLENO },
                enabled = listo && !enviando,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.Groups, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(L.t("Lleno", "Full"), style = BodySm)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            when {
                enviando -> L.t("Esperando confirmación…", "Waiting for confirmation…")
                resultado != null -> resultado!!
                listo -> L.t(
                    "Reporta solo si estás a bordo: se necesitan dos cuentas que coincidan; los reportes duran 3 minutos.",
                    "Report only if you're onboard: two matching accounts are needed; reports last 3 minutes."
                )
                else -> L.t(
                    "No hay conexión con la ocupación en este momento.",
                    "No connection with occupancy right now."
                )
            },
            style = BodyXs, color = OnSurfaceVariant
        )
    }

    // Confirmación antes de reportar (evita toques accidentales, como el iOS).
    estadoPendiente?.let { estado ->
        AlertDialog(
            onDismissRequest = { estadoPendiente = null },
            title = { Text(L.t("Confirmar ocupación", "Confirm occupancy")) },
            text = {
                Text(
                    L.t(
                        "¿Estás en este bus y confirmas que está ${estado.etiqueta().lowercase()}?",
                        "Are you on this bus and do you confirm it's ${estado.etiqueta().lowercase()}?"
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    servicio?.reportar(vehicleId, estado)
                    estadoPendiente = null
                }) {
                    Text(L.t("Sí, reportar", "Yes, report"))
                }
            },
            dismissButton = {
                TextButton(onClick = { estadoPendiente = null }) {
                    Text(L.t("Cancelar", "Cancel"))
                }
            }
        )
    }
}

private fun OccupancyService.EstadoOcupacion.etiqueta(): String = when (this) {
    OccupancyService.EstadoOcupacion.VACIO -> L.t("Vacío", "Empty")
    OccupancyService.EstadoOcupacion.LLENO -> L.t("Lleno", "Full")
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
private fun MapaReportarSheet(onDismiss: () -> Unit, onAbrirCambios: () -> Unit) {
    var tipo by remember { mutableStateOf(TipoReporte.ALERTA) }
    var descripcion by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(L.t("Reportar incidente", "Report incident"), style = HeadlineMd, color = OnSurface)
            Spacer(Modifier.height(16.dp))

            // Boton destacado a cambios de ruta (Fase 8), como el ReportarSheet iOS.
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