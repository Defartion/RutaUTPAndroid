package com.example.rutautpnative.ui.screens

import android.Manifest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.ParaderoGTFS
import com.example.rutautpnative.data.gtfs.ParaderosIluminados
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.ui.screens.mapa.MarcadorUTP
import com.example.rutautpnative.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

//----Paraderos iluminados (pantalla)----
// Mapa con paraderos + búsqueda/filtro de radio + carrusel + "buscar cerca de mí".
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ParaderosIluminadosScreen(rutas: List<RutaGTFS>, onCerrar: () -> Unit) {
    val context = LocalContext.current
    val paraderos = remember(rutas) { ParaderosIluminados.seleccionar(rutas) }
    var ubicacionUsuario by remember { mutableStateOf<LatLng?>(null) }
    var locating by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf<String?>(null) }

    // La ancla es UTP por defecto; cuando se obtenga la ubicación real, reemplaza.
    val anchor: LatLng = ubicacionUsuario ?: GTFSRepository.coordenadaUTP

    val distancias = remember(paraderos, anchor) {
        paraderos.associate { it.id to GTFSRepository.distanciaMetros(it.coordinate, anchor) }
    }
    val lineasPorParadero = remember(paraderos, rutas) {
        paraderos.associate { p -> p.id to lineasDe(p, rutas) }
    }

    var query by remember { mutableStateOf("") }
    var radioMetros by remember { mutableStateOf(0) }

    val paraderosVisibles = remember(paraderos, query, radioMetros, anchor) {
        paraderos.filter { p ->
            (query.isEmpty() || p.nombre.contains(query, ignoreCase = true)) &&
                (radioMetros == 0 || GTFSRepository.distanciaMetros(p.coordinate, anchor) <= radioMetros)
        }.sortedWith { a, b ->
            val da = GTFSRepository.distanciaMetros(a.coordinate, anchor)
            val db = GTFSRepository.distanciaMetros(b.coordinate, anchor)
            if (abs(da - db) < 0.1) a.id.compareTo(b.id) else da.compareTo(db)
        }
    }

    var selectedId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(anchor, 13f)
    }
    val pagerState = rememberPagerState(pageCount = { maxOf(1, paraderosVisibles.size) })

    fun limpiarFiltro() {
        query = ""
        radioMetros = 0
        selectedId = null
        scope.launch { pagerState.scrollToPage(0) }
    }

    // pin -> carrusel + cámara
    LaunchedEffect(selectedId) {
        val id = selectedId ?: return@LaunchedEffect
        val idx = paraderosVisibles.indexOfFirst { it.id == id }
        if (idx >= 0) {
            if (pagerState.currentPage != idx) pagerState.animateScrollToPage(idx)
            cameraState.animate(CameraUpdateFactory.newLatLngZoom(paraderosVisibles[idx].coordinate, 16f))
        }
    }

    // carrusel -> pin + cámara (solo cuando el usuario deja de deslizar)
    LaunchedEffect(pagerState, paraderosVisibles) {
        snapshotFlow { pagerState.settledPage }
            .drop(1) // ignora la página inicial para no auto-seleccionar al abrir
            .collect { page ->
                paraderosVisibles.getOrNull(page)?.let { p ->
                    if (p.id != selectedId) selectedId = p.id
                }
            }
    }

    // Al reducir el filtro, vuelve al inicio para no quedar en una página fuera de rango.
    LaunchedEffect(paraderosVisibles.size) {
        pagerState.scrollToPage(0)
    }

    // Manejo del permiso: cuando se resuelve a concedido, se inicia la ubicación;
    // si no, se muestra un mensaje sin bloquear la pantalla.
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    fun localizar() {
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val cancelacion = CancellationTokenSource()
        val timeoutJob = scope.launch {
            delay(12_000)
            locationMessage = "No recibimos señal GPS. Inténtalo de nuevo."
            locating = false
            cancelacion.cancel()
        }
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancelacion.token)
            .addOnSuccessListener { location ->
                timeoutJob.cancel()
                if (location != null) {
                    ubicacionUsuario = LatLng(location.latitude, location.longitude)
                } else {
                    locationMessage = "No recibimos señal GPS. Inténtalo de nuevo."
                }
                locating = false
            }
            .addOnFailureListener {
                timeoutJob.cancel()
                locationMessage = "No recibimos señal GPS. Inténtalo de nuevo."
                locating = false
            }
            .addOnCanceledListener {
                // Llamado por el timeout; ya se registró el mensaje ahí. Solo limpiamos.
                locating = false
            }
    }

    LaunchedEffect(locationPermission.status) {
        if (!locating) return@LaunchedEffect
        if (locationPermission.status.isGranted) {
            localizar()
        } else {
            locating = false
            locationMessage = "Activa Ubicación en Ajustes. Las distancias usan UTP como referencia."
        }
    }

    // Cuando la ancla está con la ubicación real, se reselecciona la más cercana.
    LaunchedEffect(ubicacionUsuario) {
        if (ubicacionUsuario != null && paraderosVisibles.isNotEmpty()) {
            selectedId = paraderosVisibles.first().id
        }
    }

    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraState,
                uiSettings = MapUiSettings(
                    compassEnabled = false,
                    mapToolbarEnabled = false,
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false
                )
            ) {
                MarkerComposable(
                    state = MarkerState(position = anchor),
                    title = "UTP Trujillo"
                ) {
                    MarcadorUTP()
                }
                paraderosVisibles.forEach { paradero ->
                    val seleccionado = paradero.id == selectedId
                    MarkerComposable(
                        state = MarkerState(position = paradero.coordinate),
                        title = paradero.nombre,
                        onClick = { _ -> selectedId = paradero.id; true }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (seleccionado) 40.dp else 30.dp)
                                .clip(CircleShape)
                                .background(if (seleccionado) AppPrimary else SecondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.DirectionsBus,
                                null,
                                tint = if (seleccionado) Color.White else OnSecondaryContainer,
                                modifier = Modifier.size(if (seleccionado) 20.dp else 16.dp)
                            )
                        }
                    }
                }
            }

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(AppSurface.copy(alpha = 0.92f)).clickable { onCerrar() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.ArrowBack, null, tint = OnSurface, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Explora paraderos", style = HeadlineBody, color = OnSurface)
                    Text("Tu próxima parada, más cerca", style = BodySm, color = OnSurfaceVariant)
                }
            }

            // Panel inferior
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(AppSurface.copy(alpha = 0.97f))
                    .navigationBarsPadding()
                    .padding(vertical = 12.dp)
            ) {
                Column {
                    // Campo de búsqueda
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Busca una avenida o paradero", style = BodySm, color = OnSurfaceVariant) },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = OnSurfaceVariant) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { limpiarFiltro() }) {
                                    Icon(Icons.Filled.Close, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    // Control segmentado de radio
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    ) {
                        listOf(0 to "Todos", 1000 to "1 km", 3000 to "3 km").forEachIndexed { index, (metros, etiqueta) ->
                            SegmentedButton(
                                selected = radioMetros == metros,
                                onClick = { radioMetros = metros },
                                shape = SegmentedButtonDefaults.itemShape(index, count = 3),
                                label = { Text(etiqueta, style = BodySm) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // Contador + indicador de ancla + ubicación
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PARADEROS ${paraderosVisibles.size}", style = LabelCapsMd, color = AppPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (ubicacionUsuario != null) "CERCA DE TI" else "DESDE UTP",
                            style = LabelCapsSm, color = OnSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(SecondaryContainer)
                                .clickable {
                                    if (!locating) {
                                        locationMessage = null
                                        if (locationPermission.status.isGranted) {
                                            localizar()
                                        } else {
                                            locationPermission.launchPermissionRequest()
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (locating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = OnSecondaryContainer
                                )
                            } else {
                                Icon(
                                    Icons.Filled.MyLocation,
                                    null,
                                    tint = OnSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    if (locationMessage != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            locationMessage!!,
                            style = BodyXs, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (paraderosVisibles.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No hay paraderos en esta búsqueda", style = BodyMdMedium, color = OnSurface)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { limpiarFiltro() }) {
                                Text("Ver todos", style = BodyMdMedium, color = AppPrimary)
                            }
                        }
                    } else {
                        HorizontalPager(state = pagerState, modifier = Modifier.height(140.dp)) { page ->
                            paraderosVisibles.getOrNull(page)?.let { paradero ->
                                ParaderoCard(
                                    paradero = paradero,
                                    distancia = distancias[paradero.id] ?: 0.0,
                                    lineas = lineasPorParadero[paradero.id] ?: emptyList(),
                                    seleccionado = paradero.id == selectedId,
                                    onClick = { selectedId = paradero.id },
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Iluminación y vigilancia sin verificar", style = BodyXs, color = OnSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        }
    }
}

private fun lineasDe(paradero: ParaderoGTFS, rutas: List<RutaGTFS>): List<String> =
    rutas.filter { ruta ->
        ruta.paraderos.any { p ->
            p.id == paradero.id ||
                GTFSRepository.distanciaMetros(p.coordinate, paradero.coordinate) < 20.0
        }
    }.map { it.linea }

@Composable
private fun ParaderoCard(
    paradero: ParaderoGTFS,
    distancia: Double,
    lineas: List<String>,
    seleccionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (seleccionado) PrimaryContainer else SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(paradero.nombre, style = BodyMdMedium, color = OnSurface, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text("${distancia.roundToInt()} m", style = BodySm, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                lineas.take(3).forEach { linea ->
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(PrimaryContainer).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(linea, style = LabelCapsSm, color = OnPrimaryContainer)
                    }
                }
                if (lineas.size > 3) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(SurfaceContainerHigh).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("+${lineas.size - 3}", style = LabelCapsSm, color = OnSurfaceVariant)
                    }
                }
            }
        }
    }
}