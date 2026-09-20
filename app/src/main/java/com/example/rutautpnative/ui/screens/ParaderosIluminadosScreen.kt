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
import com.example.rutautpnative.data.LugaresStore
import com.example.rutautpnative.data.directions.DirectionsService
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.ParaderoGTFS
import com.example.rutautpnative.data.gtfs.ParaderosIluminados
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.model.CategoriaLugar
import com.example.rutautpnative.model.LugarGuardado
import com.example.rutautpnative.ui.screens.mapa.MarcadorUTP
import com.example.rutautpnative.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// Color fijo de la ruta a pie (claro). No existe tema oscuro en este proyecto
// todavía, así que se usa directamente el valor claro fijo: #1669A8.
private val ColorCaminata = Color(0xFF1669A8)

//----Paraderos iluminados (pantalla)----
// Mapa + búsqueda/filtro de radio + "cerca de mí" + carrusel + distancia real a pie.
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ParaderosIluminadosScreen(rutas: List<RutaGTFS>, onCerrar: () -> Unit) {
    val context = LocalContext.current
    val paraderos = remember(rutas) { ParaderosIluminados.seleccionar(rutas) }

    var ubicacionUsuario by remember { mutableStateOf<LatLng?>(null) }
    var locating by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf<String?>(null) }

    // La ancla (por defecto UTP, o la ubicación real del usuario si hay permiso+señal)
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
    var guardados by remember { mutableStateOf<List<LugarGuardado>>(emptyList()) }

    // Caminata a pie (Directions API)
    var walkingLoading by remember { mutableStateOf(false) }
    var walkingLine by remember { mutableStateOf<List<LatLng>?>(null) }
    var walkingDistance by remember { mutableStateOf<Int?>(null) }
    var walkingMessage by remember { mutableStateOf<String?>(null) }
    var walkingJob by remember { mutableStateOf<Job?>(null) }

    val scope = rememberCoroutineScope()
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(anchor, 13f)
    }
    val pagerState = rememberPagerState(pageCount = { maxOf(1, paraderosVisibles.size) })

    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    fun limpiarFiltro() {
        query = ""
        radioMetros = 0
        selectedId = null
        scope.launch { pagerState.scrollToPage(0) }
    }

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
                locating = false
            }
    }

    fun alternarGuardado(paradero: ParaderoGTFS) {
        val existente = guardados.firstOrNull { g ->
            g.nombre == paradero.nombre &&
                g.lat != null && g.lon != null &&
                GTFSRepository.distanciaMetros(LatLng(g.lat!!, g.lon!!), paradero.coordinate) < 5.0
        }
        guardados = when {
            existente == null -> guardados + LugarGuardado(
                nombre = paradero.nombre,
                direccion = "Trujillo",
                categoria = CategoriaLugar.OTRO,
                lat = paradero.lat,
                lon = paradero.lon
            )
            existente.esFijo -> guardados
            else -> guardados.filter { it.id != existente.id }
        }
        scope.launch { LugaresStore.guardar(guardados) }
    }

    // Carga los lugares guardados una vez (para íconos de bookmark en los pins).
    LaunchedEffect(Unit) {
        guardados = LugaresStore.cargar()
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

    // Al seleccionar un paradero: mueve carrusel+ cámara y calcula la caminata.
    LaunchedEffect(selectedId) {
        val id = selectedId ?: return@LaunchedEffect
        val paradero = paraderosVisibles.firstOrNull { p -> p.id == id } ?: return@LaunchedEffect

        // Carrusel: selecciona la tarjeta
        val idx = paraderosVisibles.indexOfFirst { it.id == id }
        if (idx >= 0 && pagerState.currentPage != idx) {
            pagerState.animateScrollToPage(idx)
        }

        // Caminata
        val anchorActual = anchor
        val destino = paradero.coordinate
        walkingJob?.cancel()
        walkingLoading = true
        walkingMessage = null
        walkingLine = null
        walkingDistance = null
        walkingJob = scope.launch {
            val resultado = DirectionsService.rutaPeatonal(origen = anchorActual, destino = destino)
            if (!isActive) return@launch
            walkingLoading = false
            when (resultado) {
                is DirectionsService.Resultado.Exito -> {
                    walkingLine = resultado.puntos
                    walkingDistance = resultado.distanciaMetros
                    val bounds = LatLngBounds.Builder().apply {
                        resultado.puntos.forEach { include(it) }
                        include(anchorActual)
                    }.build()
                    cameraState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                }
                else -> {
                    walkingMessage = "No se pudo calcular la caminata. Revisa tu conexión."
                }
            }
        }
    }

    // Cuando la ubicación real llega, se reposiciona la ancla y se muestra el pin cerca.
    LaunchedEffect(ubicacionUsuario) {
        if (ubicacionUsuario != null && paraderosVisibles.isNotEmpty()) {
            selectedId = paraderosVisibles.first().id
        }
    }

    LaunchedEffect(pagerState, paraderosVisibles) {
        snapshotFlow { pagerState.settledPage }
            .drop(1) // ignora la página inicial para no auto-seleccionar al abrir
            .collect { page ->
                paraderosVisibles.getOrNull(page)?.let { p ->
                    if (p.id != selectedId) selectedId = p.id
                }
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
                    title = if (ubicacionUsuario != null) "Mi ubicación" else "UTP Trujillo"
                ) {
                    if (ubicacionUsuario != null) {
                        Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF1A73E8)))
                    } else {
                        MarcadorUTP()
                    }
                }
                paraderosVisibles.forEach { paradero ->
                    val seleccionado = paradero.id == selectedId
                    val esGuardado = guardados.any { g ->
                        g.nombre == paradero.nombre &&
                            g.lat != null && g.lon != null &&
                            GTFSRepository.distanciaMetros(LatLng(g.lat!!, g.lon!!), paradero.coordinate) < 5.0
                    }
                    MarkerComposable(
                        state = MarkerState(position = paradero.coordinate),
                        title = paradero.nombre,
                        onClick = { _ -> selectedId = paradero.id; true }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (seleccionado) 40.dp else 30.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        seleccionado -> AppPrimary
                                        esGuardado -> Tertiary
                                        else -> SecondaryContainer
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (esGuardado) Icons.Filled.Bookmark else Icons.Filled.DirectionsBus,
                                null,
                                tint = if (seleccionado || esGuardado) Color.White else OnSecondaryContainer,
                                modifier = Modifier.size(if (seleccionado) 20.dp else 16.dp)
                            )
                        }
                    }
                }
                // Línea de la ruta a pie calculada
                walkingLine?.let { linea ->
                    if (linea.size >= 2) {
                        // Estilo punteado con guiones para distinguir de la ruta de bus
                        Polyline(
                            points = linea,
                            color = ColorCaminata,
                            width = 4f,
                            pattern = listOf(Dash(20f), Gap(12f))
                        )
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
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(AppSurface.copy(alpha = 0.92f))
                        .clickable {
                            if (!locating) {
                                locationMessage = null
                                if (locationPermission.status.isGranted) {
                                    locating = true
                                    localizar()
                                } else {
                                    locationPermission.launchPermissionRequest()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (locating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AppPrimary)
                    } else {
                        Icon(Icons.Filled.MyLocation, null, tint = OnSurface, modifier = Modifier.size(20.dp))
                    }
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
                    // Control de radio
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
                    // Fila estado
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
                        // Botón de guardar el paradero actualmente seleccionado
                        if (selectedId != null) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(TertiaryContainer)
                                    .clickable {
                                        paraderos.firstOrNull { it.id == selectedId }?.let { alternarGuardado(it) }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                val guardadoSel = paraderos.firstOrNull { it.id == selectedId }
                                val yaGuardado = guardadoSel != null && guardados.any { g ->
                                    g.nombre == guardadoSel.nombre && g.lat != null && g.lon != null &&
                                        GTFSRepository.distanciaMetros(LatLng(g.lat!!, g.lon!!), guardadoSel.coordinate) < 5.0
                                }
                                Icon(
                                    if (yaGuardado) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    null,
                                    tint = OnTertiaryContainer,
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
                    if (walkingMessage != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            walkingMessage!!,
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
                        HorizontalPager(state = pagerState, modifier = Modifier.height(130.dp)) { page ->
                            paraderosVisibles.getOrNull(page)?.let { paradero ->
                                ParaderoCard(
                                    paradero = paradero,
                                    distancia = if (paradero.id == selectedId && walkingDistance != null) walkingDistance!!.toDouble() else distancias[paradero.id] ?: 0.0,
                                    lineas = lineasPorParadero[paradero.id] ?: emptyList(),
                                    seleccionado = paradero.id == selectedId,
                                    esGuardado = guardados.any { g -> g.nombre == paradero.nombre && g.lat != null && g.lon != null && GTFSRepository.distanciaMetros(LatLng(g.lat!!, g.lon!!), paradero.coordinate) < 5.0 },
                                    onClick = { selectedId = paradero.id },
                                    onGuardar = { alternarGuardado(paradero) },
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
    esGuardado: Boolean,
    onClick: () -> Unit,
    onGuardar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (seleccionado) PrimaryContainer else SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { onGuardar() }) {
                Icon(
                    if (esGuardado) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    null,
                    tint = if (esGuardado) Tertiary else OnSurfaceVariant
                )
            }
        }
    }
}