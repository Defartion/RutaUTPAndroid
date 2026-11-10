package com.example.rutautpnative.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.rutautpnative.data.gtfs.*
import com.example.rutautpnative.data.geo.PolylineMatching
import com.example.rutautpnative.data.ubicacion.LocationService
import com.example.rutautpnative.ui.components.*
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploradorRutaScreen(ruta: RutaGTFS, onCerrar: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    val camera = rememberCameraPositionState()
    val puntos = remember(ruta.id) { (ruta.shape + ruta.paraderos.map { it.coordinate }).filter { it.latitude.isFinite() && it.longitude.isFinite() } }
    val trazado = remember(ruta.id) { PolylineMatching.decimate(ruta.shape, 300) }
    val paraderosVisibles = remember(ruta.id) {
        if (ruta.paraderos.size <= 70) ruta.paraderos else (0 until 70).map { ruta.paraderos[it * ruta.paraderos.lastIndex / 69] }
    }
    var mapaListo by remember { mutableStateOf(false) }
    var mostrarLista by remember { mutableStateOf(false) }
    var buscando by remember { mutableStateOf(false) }
    var siguiendo by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var permiso by remember { mutableStateOf(LocationService.estaAutorizado) }
    var solicitud by remember { mutableStateOf<Job?>(null) }
    val autorizacion by LocationService.authorizationStatus.collectAsState()
    LaunchedEffect(autorizacion) {
        permiso = autorizacion == LocationService.EstadoAutorizacion.Autorizado
        if (!permiso) { solicitud?.cancel(); siguiendo = false }
    }

    fun centrarUbicacion() {
        solicitud?.cancel()
        solicitud = scope.launch {
            buscando = true
            try {
                LocationService.refrescarEstadoAutorizacion()
                val pos = withTimeout(10_000) { LocationService.currentLocation().first() }
                camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(pos.latitude, pos.longitude), 16f))
                siguiendo = true
            } catch (e: TimeoutCancellationException) { error = true }
              catch (e: CancellationException) { throw e }
              catch (_: Exception) { error = true }
            finally { buscando = false }
        }
    }
    val pedirPermisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultado ->
        permiso = resultado.values.any { it }
        if (permiso) centrarUbicacion() else error = true
    }
    fun encuadrar() {
        solicitud?.cancel(); siguiendo = false
        if (!mapaListo || puntos.isEmpty()) return
        scope.launch {
            if (puntos.distinct().size == 1) camera.animate(CameraUpdateFactory.newLatLngZoom(puntos.first(), 15f))
            else camera.animate(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().apply { puntos.forEach(::include) }.build(), 36))
        }
    }
    LaunchedEffect(mapaListo, ruta.id) { if (mapaListo) encuadrar() }
    LaunchedEffect(siguiendo) {
        if (siguiendo) LocationService.currentLocation().collect { posicion ->
            camera.animate(CameraUpdateFactory.newLatLng(LatLng(posicion.latitude, posicion.longitude)))
        }
    }
    LaunchedEffect(camera) {
        snapshotFlow { camera.isMoving && camera.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE }.collect { moviendo ->
            if (moviendo) { siguiendo = false; solicitud?.cancel() }
        }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { solicitud?.cancel(); siguiendo = false } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); solicitud?.cancel() }
    }
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding().navigationBarsPadding()) {
            val alturaPanel = maxHeight * .36f
            GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = camera, onMapLoaded = { mapaListo = true },
                contentPadding = PaddingValues(top = 100.dp, start = 24.dp, end = 24.dp, bottom = alturaPanel + 76.dp),
                properties = MapProperties(isMyLocationEnabled = permiso),
                uiSettings = MapUiSettings(compassEnabled = false, mapToolbarEnabled = false, zoomControlsEnabled = false, myLocationButtonEnabled = false)) {
                if (trazado.size >= 2) Polyline(trazado, color = ruta.color, width = 9f)
                paraderosVisibles.forEachIndexed { index, paradero ->
                    MarkerComposable(state = MarkerState(paradero.coordinate), title = if (index == 0) L.t("Inicio del recorrido", "Route start") else nombreParaderoVisible(paradero.nombre), snippet = if (index == 0) nombreParaderoVisible(paradero.nombre) else null) {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            if (index == 0) Surface(shape = CircleShape, color = ruta.color) { Icon(Icons.Filled.DirectionsBus, null, tint = if (ruta.color.luminance() > .179) Color.Black else Color.White, modifier = Modifier.size(26.dp).padding(5.dp)) }
                            else Box(Modifier.size(8.dp).clip(CircleShape).background(ruta.color))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Surface(shape = CircleShape, color = AppSurface.copy(alpha = .96f)) {
                    IconButton(onClick = onCerrar) { Icon(Icons.Filled.Close, L.t("Cerrar recorrido", "Close route"), tint = OnSurface) }
                }
                Surface(shape = RoundedCornerShape(20.dp), color = AppSurface.copy(alpha = .96f)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (ruta.letraTransporte.isNotEmpty()) InsigniaLinea(ruta, compacta = true)
                        Column(Modifier.weight(1f)) { Text(L.t("Línea ", "Line ") + ruta.codigoTransporte, style = BodyMdMedium, color = OnSurface); Text(ruta.empresa, style = BodyXs, color = OnSurfaceVariant) }
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.align(Alignment.End), shape = RoundedCornerShape(18.dp), color = AppSurface.copy(alpha = .96f)) {
                    Column {
                        IconButton(onClick = {
                            permiso = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (permiso) centrarUbicacion() else pedirPermisos.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }, enabled = !buscando) { if (buscando) CircularProgressIndicator(Modifier.size(24.dp)) else Icon(if (siguiendo) Icons.Filled.MyLocation else Icons.Filled.LocationSearching, L.t("Ir a mi ubicación", "Go to my location"), tint = OnSurface) }
                        HorizontalDivider(Modifier.width(48.dp).padding(horizontal = 10.dp))
                        IconButton(onClick = { encuadrar() }) { Icon(Icons.Filled.Route, L.t("Ver recorrido completo", "Show entire route"), tint = OnSurface) }
                    }
                }
                Surface(shape = RoundedCornerShape(22.dp), color = AppSurface.copy(alpha = .97f), shadowElevation = 6.dp) {
                    Column(Modifier.heightIn(max = alturaPanel).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(L.t("Recorrido", "Route"), style = HeadlineXs, color = OnSurface)
                            Text(ruta.frecuenciaTexto, style = BodySmMedium, color = OnSurfaceVariant)
                        }
                        Text(ruta.recorrido, style = BodySm, color = OnSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DatoBreveRuta(Icons.Filled.Schedule, ruta.tiempoTexto)
                            DatoBreveRuta(Icons.Filled.Payments, ruta.precioTexto)
                            DatoBreveRuta(Icons.Filled.Place, ruta.paraderos.size.toString())
                            DatoBreveRuta(Icons.Filled.Route, String.format(Locale.getDefault(), "%.1f km", ruta.distanciaKm))
                        }
                        HorizontalDivider(color = OutlineVariant.copy(alpha = .3f))
                        TextButton(onClick = { mostrarLista = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.List, null); Spacer(Modifier.width(8.dp)); Text(L.t("Ver lista de paraderos", "View stop list"), modifier = Modifier.weight(1f)); Icon(Icons.Filled.ChevronRight, null)
                        }
                    }
                }
            }
        }
        if (mostrarLista) ModalBottomSheet(onDismissRequest = { mostrarLista = false }, containerColor = AppSurface) {
            Text(L.t("Paraderos de ", "Stops for ") + ruta.lineaConLetra, style = HeadlineSm, modifier = Modifier.padding(20.dp), color = OnSurface)
            LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.85f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                itemsIndexed(ruta.paraderos) { index, paradero ->
                    ListItem(headlineContent = { Text(nombreParaderoVisible(paradero.nombre)) }, supportingContent = { if (index == 0) Text(L.t("Inicio del recorrido", "Route start")) }, leadingContent = { Text((index + 1).toString(), color = AcentoInterfaz) }, colors = ListItemDefaults.colors(containerColor = AppSurface))
                    HorizontalDivider(color = OutlineVariant.copy(alpha = .2f))
                }
            }
        }
        if (error) AlertDialog(onDismissRequest = { error = false }, title = { Text(L.t("Tu ubicación", "Your location")) },
            text = { Text(L.t("No pudimos obtener tu ubicación. Revisa el permiso de RutaUTP y vuelve a intentarlo.", "We could not get your location. Check RutaUTP's permission and try again.")) },
            confirmButton = { TextButton(onClick = { error = false }) { Text(L.t("Aceptar", "OK")) } },
            dismissButton = { TextButton(onClick = { error = false; context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text(L.t("Abrir ajustes", "Open settings")) } })
    }
}
