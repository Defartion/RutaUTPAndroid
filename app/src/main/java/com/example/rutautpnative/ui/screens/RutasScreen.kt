package com.example.rutautpnative.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rutautpnative.data.gtfs.*
import com.example.rutautpnative.data.senias.SeniasOverlay
import com.example.rutautpnative.navigation.AppRouter
import com.example.rutautpnative.navigation.DestinoPendiente
import com.example.rutautpnative.ui.components.*
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun RutasScreen(router: AppRouter, viewModel: RutasViewModel = viewModel()) {
    val rutas by viewModel.rutas.collectAsState()
    val cargando by viewModel.cargando.collectAsState()
    val error by viewModel.errorCarga.collectAsState()
    val busqueda by viewModel.textoBusqueda.collectAsState()
    val filtro by viewModel.rutasFiltradas.collectAsState()
    val cerca by viewModel.lugarCercano.collectAsState()
    var seleccionada by viewModel::seleccionada
    val lista = rememberLazyListState()
    var galeria by viewModel::galeria
    var modelo by viewModel::modelo
    var navegando by viewModel::navegando
    LaunchedEffect(router.rutaPendiente, rutas) {
        val id = router.rutaPendiente ?: return@LaunchedEffect
        (rutas.firstOrNull { it.id == id } ?: rutas.firstOrNull { it.linea == id })?.let {
            seleccionada = it; router.rutaPendiente = null
        }
    }
    LaunchedEffect(router.lugarCercanoPendiente) {
        router.lugarCercanoPendiente?.let { viewModel.limpiarBusqueda(); viewModel.filtrarCercaDe(it); router.lugarCercanoPendiente = null }
    }
    BackHandler(seleccionada != null) { seleccionada = null }
    Scaffold(containerColor = AppBackground, contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = { BottomNavBar(router) }) { padding ->
        AnimatedContent(seleccionada, modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding(), label = "ruta_detalle") { ruta ->
            if (ruta == null) RutasCatalogo(filtro.rutas, filtro.distancias, cerca, cargando, error, busqueda,
                viewModel::actualizarBusqueda, { viewModel.cargar(true) }, { viewModel.filtrarCercaDe(null) },
                { seleccionada = it }, { galeria = true }, lista)
            else DetalleRutaActual(ruta, { seleccionada = null }, { navegando = ruta }, { modelo = ruta })
        }
    }
    if (galeria) Transportes3DScreen(onCerrar = { galeria = false })
    modelo?.let { Transportes3DScreen(it) { modelo = null } }
    navegando?.let { NavegacionScreen(it) { navegando = null } }
}

@Composable
fun RutasCatalogo(rutas: List<RutaGTFS>, distancias: Map<String, Int>, cerca: DestinoPendiente?, cargando: Boolean,
    error: String?, texto: String, onBusqueda: (String) -> Unit, onReintentar: () -> Unit, onQuitarCerca: () -> Unit,
    onRuta: (RutaGTFS) -> Unit, on3D: () -> Unit, lista: LazyListState = rememberLazyListState()) {
    val foco = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        CabeceraSeccion(if (TransporteApp.busesUTPActivos) L.t("Rutas UTP", "UTP routes") else L.t("Rutas", "Routes"), Icons.Filled.DirectionsBus)
        OutlinedTextField(texto, onBusqueda, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            placeholder = { Text(L.t("Buscar rutas", "Search routes")) }, leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (texto.isNotEmpty()) IconButton(onClick = { onBusqueda("") }) { Icon(Icons.Filled.Cancel, L.t("Borrar búsqueda de rutas", "Clear route search")) } },
            singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { foco.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = AcentoInterfaz,
                unfocusedContainerColor = SuperficieAgrupada, focusedContainerColor = SuperficieAgrupada))
        if (cerca != null) Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(14.dp), color = SuperficieAgrupada) {
            Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.NearMe, null, tint = AcentoInterfaz)
                Column(Modifier.weight(1f).padding(12.dp)) {
                    Text(L.t("Cerca de ", "Near ") + cerca.titulo, style = BodyMdMedium)
                    Text(L.t("Hasta 300 m del lugar", "Within 300 m of this place"), style = BodyXs, color = OnSurfaceVariant)
                }
                IconButton(onClick = onQuitarCerca) { Icon(Icons.Filled.Cancel, L.t("Quitar filtro de cercanía", "Clear nearby filter")) }
            }
        }
        LazyColumn(Modifier.weight(1f), state = lista, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Card(onClick = on3D, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = SuperficieAgrupada)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ViewInAr, null, tint = AcentoInterfaz, modifier = Modifier.size(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(L.t("Transportes en 3D", "Transport in 3D"), style = BodyMdMedium, color = OnSurface)
                        Text(L.t("Micro, combi y colectivo", "Bus, minibus and shared taxi"), style = BodyXs, color = OnSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant)
                }
            } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(L.t("Elige tu ruta", "Pick your route"), style = HeadlineXs, color = OnSurface, modifier = Modifier.semantics { heading() }.signable("rutas.elegir"))
                    if (!cargando && error == null) Text(L.f("{0} rutas encontradas", "{0} routes found", rutas.size.toString()), style = BodyXs, color = OnSurfaceVariant)
                }
            }
            when {
                cargando -> item { EstadoCatalogo(L.t("Cargando rutas…", "Loading routes…"), cargando = true) }
                error != null -> item { EstadoCatalogo(L.t("No se pudo cargar el catálogo de rutas.", "Could not load the route catalog."), onRetry = onReintentar) }
                rutas.isEmpty() -> item { EstadoCatalogo(when {
                    TransporteApp.busesUTPActivos -> L.t("Buses UTP · rutas próximamente", "UTP buses · routes coming soon")
                    texto.isNotBlank() -> L.t("No encontramos rutas para ", "No routes found for ") + "“$texto”"
                    cerca != null -> L.t("No hay líneas con paradero a menos de 300 m de este lugar.", "No routes have a stop within 300 m of this place.")
                    else -> L.t("No hay rutas disponibles en este catálogo.", "No routes are available in this catalog.")
                }) }
                else -> items(rutas, key = { it.id }) { ruta -> RutaOpcionCard(ruta, distancias[ruta.id]?.let { L.f("A {0} m del lugar", "{0} m from this place", it.toString()) }) { foco.clearFocus(); onRuta(ruta) } }
            }
        }
    }
}

@Composable
private fun EstadoCatalogo(texto: String, cargando: Boolean = false, onRetry: (() -> Unit)? = null) {
    Surface(shape = RoundedCornerShape(20.dp), color = SuperficieAgrupada) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (cargando) CircularProgressIndicator(color = AcentoInterfaz) else Icon(Icons.Filled.DirectionsBus, null, tint = OnSurfaceVariant)
            Text(texto, style = BodySm, color = OnSurfaceVariant)
            onRetry?.let { OutlinedButton(onClick = it) { Text(L.t("Reintentar", "Try again")) } }
        }
    }
}

@Composable
internal fun DetalleRutaActual(ruta: RutaGTFS, onBack: () -> Unit, onNavegar: () -> Unit, on3D: () -> Unit) {
    var explorador by remember { mutableStateOf(false) }
    var mapaListo by remember(ruta.id) { mutableStateOf(false) }
    LaunchedEffect(ruta.id) { delay(450); mapaListo = true }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(AppSurface).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.clip(CircleShape).background(SurfaceContainerLow)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, L.t("Volver", "Back"), tint = OnSurface) }
            Text(L.t("Ruta ", "Route ") + ruta.codigoTransporte, style = HeadlineMd, color = OnSurface, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            Icon(Icons.Filled.DirectionsBus, null, tint = AcentoInterfaz)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Surface(shape = RoundedCornerShape(20.dp), color = SuperficieAgrupada) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        InsigniaLinea(ruta, grande = true)
                        Column(Modifier.weight(1f)) { Text(ruta.empresa, style = HeadlineSm, color = OnSurface); Text(L.t("Línea ", "Line ") + ruta.lineaConLetra, style = BodySm, color = OnSurfaceVariant) }
                    }
                    Text(ruta.recorrido, style = BodySm, color = OnSurfaceVariant)
                    HorizontalDivider(color = OutlineVariant.copy(alpha = .3f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { DatoBreveRuta(Icons.Filled.Schedule, L.t("Frecuencia", "Frequency")); Text(ruta.frecuenciaTexto, style = BodyMdMedium, color = OnSurface) }
                }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = SuperficieAgrupada) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row { DatoViaje(Icons.Filled.Schedule, L.t("Tiempo de viaje", "Travel time"), ruta.tiempoTexto, Modifier.weight(1f)); DatoViaje(Icons.Filled.Payments, L.t("Pasaje", "Fare"), ruta.precioTexto, Modifier.weight(1f)) }
                    Row { DatoViaje(Icons.Filled.Place, L.t("Paraderos", "Stops"), ruta.paraderos.size.toString(), Modifier.weight(1f)); DatoViaje(Icons.Filled.Route, L.t("Distancia", "Distance"), String.format(Locale.getDefault(), "%.1f km", ruta.distanciaKm), Modifier.weight(1f)) }
                }
            }
            Card(onClick = { explorador = true }, colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(L.t("Ver el recorrido", "Show route"), style = HeadlineSm, color = OnSurface, modifier = Modifier.weight(1f)); Icon(Icons.Filled.OpenInFull, L.t("Explorar recorrido", "Explore route"), tint = AcentoInterfaz) }
                    if (mapaListo || androidx.compose.ui.platform.LocalInspectionMode.current) MapaVistaPreviaRuta(ruta, Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(20.dp))) { explorador = true }
                    else Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(20.dp)).background(SurfaceContainerLow))
                }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = SuperficieAgrupada) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(L.t("Reconoce tu transporte", "Recognize your vehicle"), style = HeadlineSm, color = OnSurface, modifier = Modifier.weight(1f)); IconButton(onClick = on3D) { Icon(Icons.Filled.OpenInFull, L.t("Ampliar modelo 3D", "Expand 3D model"), tint = AcentoInterfaz) } }
                    ReferenciaTransporte(ruta)
                    ModeloRuta3DDiferido(ruta)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(L.t("Guía paso a paso", "Step-by-step guide"), style = HeadlineSm, color = OnSurface, modifier = Modifier.signable("rutas.guia"))
                Surface(shape = RoundedCornerShape(20.dp), color = SuperficieAgrupada) {
                    Column {
                        PasoRuta(1, L.f("Ve al paradero {0}", "Go to {0} stop", ruta.paradaInicioVisible), L.f("Sale uno {0}", "One departs {0}", ruta.frecuenciaTexto), Icons.Filled.DirectionsWalk)
                        HorizontalDivider(Modifier.padding(start = 68.dp), color = OutlineVariant.copy(alpha = .3f))
                        PasoRuta(2, L.f("Sube a la línea {0}", "Board line {0}", ruta.lineaConLetra), ruta.empresa + " · " + ruta.tiempoTexto, Icons.Filled.DirectionsBus)
                        HorizontalDivider(Modifier.padding(start = 68.dp), color = OutlineVariant.copy(alpha = .3f))
                        PasoRuta(3, L.f("Baja en {0}", "Get off at {0}", ruta.paradaFinVisible), L.t("Fin del recorrido", "End of the route"), Icons.Filled.Flag)
                    }
                }
            }
        }
        Surface(color = AppSurface) {
            Button(onClick = { SeniasOverlay.ejecutarTrasVerSenia("nav.iniciar", onNavegar) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = PrimaryFill, contentColor = OnPrimaryFill)) {
                Icon(Icons.Filled.Navigation, null); Spacer(Modifier.width(10.dp)); Text(L.t("Iniciar navegación", "Start navigation"), style = BodyMdMedium)
            }
        }
    }
    if (explorador) ExploradorRutaScreen(ruta) { explorador = false }
}

@Composable
private fun DatoViaje(icono: ImageVector, titulo: String, valor: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) { DatoBreveRuta(icono, titulo); Text(valor, style = HeadlineSm, color = OnSurface) }
}

@Composable
private fun PasoRuta(numero: Int, titulo: String, detalle: String, icono: ImageVector) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icono, null, tint = AcentoInterfaz, modifier = Modifier.size(40.dp).clip(CircleShape).background(SurfaceContainerLow).padding(9.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("$numero. $titulo", style = BodyMdMedium, color = OnSurface); Text(detalle, style = BodySm, color = OnSurfaceVariant) }
    }
}

@Composable
private fun MapaVistaPreviaRuta(ruta: RutaGTFS, modifier: Modifier, onAbrir: () -> Unit) {
    if (androidx.compose.ui.platform.LocalInspectionMode.current) {
        // La vista previa dibuja solo la geometría local; no inicializa Google Maps.
        androidx.compose.foundation.Canvas(modifier.background(SurfaceContainerLow)) {
            val puntos = ruta.shape
            if (puntos.size > 1) {
                val minLat = puntos.minOf { it.latitude }; val minLon = puntos.minOf { it.longitude }
                val alto = (puntos.maxOf { it.latitude } - minLat).coerceAtLeast(.0001)
                val ancho = (puntos.maxOf { it.longitude } - minLon).coerceAtLeast(.0001)
                val path = androidx.compose.ui.graphics.Path()
                puntos.forEachIndexed { i, p ->
                    val x = (size.width * (.1 + .8 * (p.longitude - minLon) / ancho)).toFloat()
                    val y = (size.height * (.9 - .8 * (p.latitude - minLat) / alto)).toFloat()
                    if (i == 0) path.moveTo(x,y) else path.lineTo(x,y)
                }
                drawPath(path, ruta.color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx()))
            }
        }
        return
    }
    val camara = rememberCameraPositionState()
    var listo by remember(ruta.id) { mutableStateOf(false) }
    LaunchedEffect(listo, ruta.id) {
        if (listo) {
            val puntos = ruta.shape + ruta.paraderos.map { it.coordinate }
            if (puntos.distinct().size > 1) camara.move(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().apply { puntos.forEach(::include) }.build(), 40))
            else puntos.firstOrNull()?.let { camara.move(CameraUpdateFactory.newLatLngZoom(it, 15f)) }
        }
    }
    GoogleMap(modifier = modifier, cameraPositionState = camara, onMapLoaded = { listo = true }, onMapClick = { onAbrir() }, uiSettings = MapUiSettings(scrollGesturesEnabled = false, zoomGesturesEnabled = false, tiltGesturesEnabled = false, rotationGesturesEnabled = false, zoomControlsEnabled = false, mapToolbarEnabled = false)) {
        if (ruta.shape.size > 1) Polyline(ruta.shape, color = ruta.color, width = 8f)
        ruta.paraderos.firstOrNull()?.let { Marker(state = MarkerState(it.coordinate), title = ruta.paradaInicioVisible, onClick = { onAbrir(); true }) }
        ruta.paraderos.lastOrNull()?.let { Marker(state = MarkerState(it.coordinate), title = ruta.paradaFinVisible, onClick = { onAbrir(); true }) }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RutasActualizadasPreview() {
    val ruta = RutaGTFS("preview", "C-01", "B", "Av. Miguel Grau → Av. Libertad", "El Cortijo", "00A850", Color(0xFF00A850), emptyList(), listOf(ParaderoGTFS("a", "Av. Miguel Grau", -8.1, -79.0), ParaderoGTFS("b", "Av. Libertad", -8.11, -79.02)), 48, 5, 2.5, 12.4, 150.0)
    RutaUTPNativeTheme { RutasCatalogo(listOf(ruta, ruta.copy(id = "2", linea = "M-34", variante = "A", empresa = "Nuevo California", color = Color(0xFFEEB633))), emptyMap(), null, false, null, "", {}, {}, {}, {}, {}) }
}
