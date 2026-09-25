package com.example.rutautpnative.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.data.senias.SeniasOverlay
import com.example.rutautpnative.data.senias.SeniasPrefs
import com.example.rutautpnative.navigation.AppRouter
import com.example.rutautpnative.ui.components.BottomNavBar
import com.example.rutautpnative.ui.components.Signable
import com.example.rutautpnative.ui.theme.*
import kotlinx.coroutines.launch
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt

// Estimación genérica de referencia (5 km/h) para estimar tiempos a pie,
// mientras no haya un motor de rutas peatonales real integrado.
private const val VELOCIDAD_CAMINATA_KMH = 5.0

//----Main Screen----
@Composable
fun RutasScreen(router: AppRouter, viewModel: RutasViewModel = viewModel()) {
    val rutas by viewModel.rutas.collectAsState()
    val textoBusqueda by viewModel.textoBusqueda.collectAsState()
    val rutasFiltradas by viewModel.rutasFiltradas.collectAsState()

    var rutaSeleccionada by remember { mutableStateOf<RutaGTFS?>(null) }
    var mostrarNavegacion by remember { mutableStateOf(false) }
    var rutaNavegando by remember { mutableStateOf<RutaGTFS?>(null) }

    // Consume una ruta pendiente enviada desde otra pantalla (p.ej. "Ver ruta"
    // del panel de Mapa) y abre directamente su detalle. Clave en `rutas` para
    // re-chequear cuando el feed termine de cargar.
    LaunchedEffect(router.rutaPendiente, rutas) {
        val id = router.rutaPendiente ?: return@LaunchedEffect
        val ruta = rutas.firstOrNull { it.id == id } ?: rutas.firstOrNull { it.linea == id }
        if (ruta != null) {
            router.rutaPendiente = null
            rutaSeleccionada = ruta
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = rutaSeleccionada,
            transitionSpec = {
                if (targetState != null) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
                } else {
                    fadeIn() togetherWith fadeOut()
                }
            },
            label = "ruta_transition"
        ) { ruta ->
            if (ruta != null) {
                DetalleRutaScreen(
                    ruta = ruta,
                    router = router,
                    onBack = { rutaSeleccionada = null },
                    onIniciarNavegacion = { rutaNavegando = ruta; mostrarNavegacion = true }
                )
            } else {
                ListaRutasScreen(
                    router = router,
                    rutas = rutasFiltradas,
                    textoBusqueda = textoBusqueda,
                    onSearchChange = viewModel::actualizarBusqueda,
                    onClearSearch = viewModel::limpiarBusqueda,
                    onSelectRuta = { rutaSeleccionada = it }
                )
            }
        }

        if (mostrarNavegacion && rutaNavegando != null) {
            NavegacionScreen(
                rutaNombre = "Línea ${rutaNavegando!!.linea} - ${rutaNavegando!!.empresa}",
                onFinish = { mostrarNavegacion = false; rutaNavegando = null }
            )
        }
    }
}

//----Lista de rutas----
@Composable
private fun ListaRutasScreen(
    router: AppRouter,
    rutas: List<RutaGTFS>,
    textoBusqueda: String,
    onSearchChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSelectRuta: (RutaGTFS) -> Unit
) {
    val borderColor = OutlineVariant.copy(alpha = 0.25f)
    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(AppSurface)
                    .drawBehind {
                        drawLine(borderColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.DirectionsBus, null, tint = AppPrimary, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Rutas", style = HeadlineLg, color = AppPrimary, modifier = Modifier.weight(1f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(PrimaryFixed)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(AppPrimary))
                        Spacer(Modifier.width(5.dp))
                        Text("EN VIVO", style = LabelCapsSm, color = AppPrimary)
                    }
                }
            }

            // LazyColumn: el catálogo tiene ~102 rutas; con Column+verticalScroll se
            // renderizarían todas de golpe. El mapa y el buscador van como `item {}`
            // para conservar el mismo scroll global y el mismo layout visual.
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Mapa overview + encabezado + búsqueda
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                        val utpLatLng = LatLng(-8.1116, -79.0287)
                        val origenLatLng = LatLng(-8.1180, -79.0350)
                        val cameraState = rememberCameraPositionState {
                            position = CameraPosition.fromLatLngZoom(utpLatLng, 13f)
                        }
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraState,
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = false,
                                scrollGesturesEnabled = false,
                                zoomGesturesEnabled = false
                            )
                        ) {
                            Polyline(points = listOf(origenLatLng, utpLatLng), color = AppPrimary, width = 8f)
                            Marker(state = MarkerState(position = utpLatLng), title = "UTP Trujillo")
                            Marker(state = MarkerState(position = origenLatLng), title = "Mi ubicación")
                        }
                    }
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Spacer(Modifier.height(20.dp))
                        Signable(clave = "rutas.elegir") {
                            Text("Elige tu ruta", style = HeadlineSm, color = OnSurface)
                        }
                        Text("Toca una ruta para ver el detalle", style = BodySm, color = OnSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        // Campo de búsqueda
                        OutlinedTextField(
                            value = textoBusqueda,
                            onValueChange = onSearchChange,
                            placeholder = { Text("Buscar línea, empresa o avenida", style = BodyMd, color = OnSurfaceVariant) },
                            leadingIcon = { Icon(Icons.Filled.Search, null, tint = OnSurfaceVariant) },
                            trailingIcon = {
                                if (textoBusqueda.isNotEmpty()) {
                                    IconButton(onClick = onClearSearch) {
                                        Icon(Icons.Filled.Close, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }

                if (rutas.isEmpty() && textoBusqueda.isNotBlank()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No hay rutas que coincidan con \"${textoBusqueda.trim()}\"",
                                style = BodyMd,
                                color = OnSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(rutas, key = { it.id }) { ruta ->
                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                            RutaOpcionCard(ruta = ruta, onClick = { onSelectRuta(ruta) })
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            BottomNavBar(router)
        }
    }
}

//----Card de ruta----
@Composable
private fun RutaOpcionCard(ruta: RutaGTFS, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(4.dp).height(56.dp).clip(RoundedCornerShape(2.dp)).background(ruta.color))
            Spacer(Modifier.width(14.dp))
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(ruta.color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(ruta.linea, color = ruta.color, fontSize = 16.sp, style = HeadlineSm)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(ruta.empresa, style = BodyMdMedium, color = OnSurface)
                Text(ruta.recorrido, style = BodySm, color = OnSurfaceVariant, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(ruta.frecuenciaTexto, style = BodyMdMedium, color = ruta.color)
                Text("frecuencia", style = LabelCapsSm, color = OnSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
        }
    }
}

//----Detalle de ruta----
@Composable
private fun DetalleRutaScreen(
    ruta: RutaGTFS,
    router: AppRouter,
    onBack: () -> Unit,
    onIniciarNavegacion: () -> Unit
) {
    val borderColor = OutlineVariant.copy(alpha = 0.25f)
    var mostrarExplorador by remember { mutableStateOf(false) }
    val modoSenias by SeniasPrefs.observarActivo().collectAsState(initial = false)
    val scope = rememberCoroutineScope()

    // --- Datos para la guía paso a paso ---
    // Ubicación del usuario (mock) mientras no haya GPS real en esta pantalla;
    // misma coordenada "Mi ubicación" que el mapa overview.
    val origenUsuario = LatLng(-8.1180, -79.0350)
    val paraderoSubida = ruta.paraderos.minByOrNull { GTFSRepository.distanciaMetros(origenUsuario, it.coordinate) }
    val paraderoDestino = ruta.paraderos.lastOrNull()
    val distanciaOrigenParadero = paraderoSubida?.let { GTFSRepository.distanciaMetros(origenUsuario, it.coordinate) }
    val metrosCaminata = distanciaOrigenParadero?.roundToInt() ?: 0
    val minutosCaminata = distanciaOrigenParadero?.let { (it / (VELOCIDAD_CAMINATA_KMH * 1000.0 / 60.0)).roundToInt() } ?: 0

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(AppSurface)
                    .drawBehind {
                        drawLine(borderColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(SurfaceContainerLow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.ArrowBack, null, tint = OnSurface)
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.DirectionsBus, null, tint = AppPrimary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ruta ${ruta.linea}", style = HeadlineLg, color = AppPrimary, modifier = Modifier.weight(1f))
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(16.dp))

                // Mapa con el recorrido real
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    val shape = ruta.shape
                    val utpLatLng = LatLng(-8.1116, -79.0287)
                    val origenLatLng = shape.firstOrNull() ?: utpLatLng
                    val cameraState = rememberCameraPositionState {
                        position = CameraPosition.fromLatLngZoom(utpLatLng, 14f)
                    }
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraState,
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = false,
                            scrollGesturesEnabled = false,
                            zoomGesturesEnabled = false,
                            tiltGesturesEnabled = false,
                            rotationGesturesEnabled = false,
                            myLocationButtonEnabled = false,
                            mapToolbarEnabled = false
                        )
                    ) {
                        if (shape.size >= 2) {
                            Polyline(points = shape, color = ruta.color, width = 10f)
                        } else {
                            Polyline(points = listOf(origenLatLng, utpLatLng), color = ruta.color, width = 10f)
                        }
                        Marker(state = MarkerState(position = utpLatLng), title = "UTP Trujillo", onClick = { true })
                        Marker(state = MarkerState(position = origenLatLng), title = ruta.empresa, onClick = { true })
                    }
                    // Overlay transparente: captura el toque en toda el área del mapa.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { mostrarExplorador = true }
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Tertiary)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Ruta Segura", style = LabelCapsMd, color = Color.White)
                    }
                    // Overlay: indicación de que el mapa es tocable
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(AppSurface.copy(alpha = 0.85f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.OpenInFull, null, tint = OnSurface, modifier = Modifier.size(14.dp))
                        Text("Toca para ver el recorrido completo", style = BodyXsMedium, color = OnSurface)
                    }
                }
                Spacer(Modifier.height(20.dp))

                // Info card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
                        Box(modifier = Modifier.width(6.dp).height(48.dp).clip(RoundedCornerShape(3.dp)).background(ruta.color))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ruta.empresa, style = HeadlineSm, color = OnSurface)
                            Text("Destino: UTP Trujillo", style = BodySm, color = OnSurfaceVariant)
                        }
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(PrimaryContainer).padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("FRECUENCIA", style = LabelCapsMd, color = OnPrimaryContainer)
                                Text(if (ruta.headwayMin > 0) "${ruta.headwayMin} min" else "—", style = DisplayNumberMd, color = OnPrimaryContainer)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Stats grid
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("TIEMPO", if (ruta.duracionMin > 0) "${ruta.duracionMin} min" else "—", Icons.Filled.Schedule, AppPrimary, Modifier.weight(1f))
                    StatTile("COSTO", ruta.precioTexto, Icons.Filled.Payments, AppPrimary, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("TRANSBORDOS", "0", Icons.Filled.SwapCalls, AppPrimary, Modifier.weight(1f))
                    StatTile("CONGESTIÓN", "—", Icons.Filled.BarChart, Secondary, Modifier.weight(1f))
                }
                Spacer(Modifier.height(20.dp))

                // Pasos
                Text("Guía paso a paso", style = HeadlineXs, color = OnSurface)
                Spacer(Modifier.height(16.dp))
                PasoRow(
                    "1",
                    "Camina al paradero ${paraderoSubida?.nombre ?: "más cercano"}",
                    if (paraderoSubida != null) "$metrosCaminata metros • $minutosCaminata min aprox." else "A pie a tu paradero",
                    Icons.Filled.DirectionsWalk, SurfaceContainerHighest, OnSurface, isLast = false
                )
                PasoRow("2", "Sube a la línea ${ruta.linea}", "${ruta.empresa} • ${ruta.duracionMin} min de viaje", Icons.Filled.DirectionsBus, AppPrimary, Color.White, isLast = false)
                PasoRow("3", "Baja en ${paraderoDestino?.nombre ?: "destino final"}", "Llegada a destino final", Icons.Filled.School, Tertiary, Color.White, isLast = true)
                Spacer(Modifier.height(20.dp))

                // Botón Iniciar Navegación (señable: con el Modo Señas activo
                // muestra la seña en vez de iniciar el tracking).
                Button(
                    onClick = {
                        if (modoSenias) {
                            scope.launch { SeniasOverlay.mostrar("nav.iniciar") }
                        } else {
                            onIniciarNavegacion()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer)
                ) {
                    Icon(Icons.Filled.LocationOn, null, tint = OnPrimaryContainer)
                    Spacer(Modifier.width(10.dp))
                    Text("Iniciar Navegación", style = HeadlineSm, color = OnPrimaryContainer)
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    if (mostrarExplorador) {
        ExploradorRutaScreen(ruta = ruta, onCerrar = { mostrarExplorador = false })
    }
}

//----Componentes compartidos----
@Composable
private fun StatTile(label: String, value: String, icon: ImageVector, iconColor: Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
        modifier = modifier
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = LabelCapsMd, color = OnSurfaceVariant)
                Text(value, style = HeadlineXs, color = if (iconColor == Secondary) Secondary else OnSurface)
            }
        }
    }
}

@Composable
private fun PasoRow(n: String, title: String, subtitle: String, icon: ImageVector, bg: Color, fg: Color, isLast: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(12.dp))
            }
            if (!isLast) {
                Box(modifier = Modifier.width(2.dp).height(36.dp).background(SurfaceContainerHighest))
            }
        }
        Column(modifier = Modifier.padding(top = 1.dp)) {
            Text("$n. $title", style = BodyMd, color = OnSurface)
            Text(subtitle, style = BodySm, color = OnSurfaceVariant)
        }
    }
}