package com.example.rutautpnative.ui.screens.mapa

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.rutautpnative.data.geo.Geocodificacion
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.data.tracking.EstadoOcupacion
import com.example.rutautpnative.data.tracking.GeometriaRutaDeteccion
import com.example.rutautpnative.data.tracking.MQTTObservationPublisher
import com.example.rutautpnative.data.tracking.PassiveTrackingCoordinator
import com.example.rutautpnative.data.tracking.PuntoSubida
import com.example.rutautpnative.data.tracking.RouteCandidateMatcher
import com.example.rutautpnative.data.ubicacion.UbicacionUnaVez
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

//----Flujo "¿Ya subiste? Confirma tu línea" (Fase 9)----
// Puerto de BusOccupancyPanel.swift + MapaViajeOverlay.swift:
//   - TarjetaInvitacionAbordaje: invitacion permanente del resumen del mapa.
//   - HojaConfirmacionAbordaje: la pregunta ("Sí, ya subí" / recordarme /
//     "Todavía no") que continua en el selector de linea.
//   - ContenidoSeleccionViaje: lineas cercanas primero, busqueda, letra del
//     transporte, "¿Dónde subiste?" (texto + mapa) y "¿Qué tan lleno va?".
//   - MapaPuntoSubida: mapa del punto de subida sobre el shape de la linea.
//   - AvisoViajeIniciado + TarjetaContribucionViaje + HojaDetallesViaje:
//     el viaje activo sobre el mapa, con ocupacion, estado, cambio de linea
//     y "Finalizar viaje".

/// Invitacion permanente del panel de resumen (invitacionConfirmarViaje iOS):
/// accesible aunque se cierre el aviso automatico.
@Composable
fun TarjetaInvitacionAbordaje(onConfirmarAbordo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerLow)
            .clickable { onConfirmarAbordo() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(Icons.Filled.DirectionsBus, null, tint = AppPrimary, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(L.t("¿Ya subiste?", "Already on board?"), style = BodySmMedium, color = OnSurface)
            Text(L.t("Confirma tu línea", "Confirm your line"), style = BodyXs, color = OnSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

/// Pregunta tras encontrar una ruta; responder no conserva el itinerario.
/// "Sí, ya subí" continua en el selector de linea dentro de la misma hoja.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaConfirmacionAbordaje(
    rutaSugeridaID: String?,
    onRecordarMasTarde: () -> Unit,
    onCerrar: () -> Unit
) {
    var eligiendoLinea by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onCerrar, containerColor = AppSurface) {
        if (eligiendoLinea) {
            ContenidoSeleccionViaje(
                rutaSugeridaID = rutaSugeridaID,
                onComenzar = { onCerrar() },
                onCancelar = { eligiendoLinea = false }
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(PrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.DirectionsBus, null, tint = OnPrimaryContainer, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    L.t("¿Ya te encuentras en el transporte público?", "Are you already on public transport?"),
                    style = HeadlineSm, color = OnSurface,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    L.t(
                        "Si ya subiste, confirma la línea que tomaste. Si aún no, puedes seguir viendo tu ruta.",
                        "If you've boarded, confirm your line. Otherwise, keep viewing your route."
                    ),
                    style = BodySm, color = OnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { eligiendoLinea = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
                ) {
                    Text(L.t("Sí, ya subí", "Yes, I'm on board"), style = HeadlineXs, color = Color.White)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { onRecordarMasTarde(); onCerrar() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.AccessTime, null, tint = AppPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(L.t("Recordarme en 2 minutos", "Remind me in 2 minutes"), style = BodyMdMedium, color = AppPrimary)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onCerrar, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    Text(L.t("Todavía no", "Not yet"), style = BodyMdMedium)
                }
            }
        }
    }
}

/// Selector de linea (TripSelectionSheet iOS): cercanas primero cuando hay
/// ubicacion, busqueda por linea/empresa/ramal, y el formulario del viaje
/// con lugar de subida y ocupacion.
@Composable
fun ContenidoSeleccionViaje(
    rutaSugeridaID: String?,
    onComenzar: () -> Unit,
    onCancelar: () -> Unit
) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    val consentimiento by PassiveTrackingCoordinator.consentimiento.collectAsState()
    val configurado by PassiveTrackingCoordinator.publicadorConfigurado.collectAsState()
    val ubicacion by PassiveTrackingCoordinator.ubicacionReciente.collectAsState()

    var rutas by remember { mutableStateOf<List<RutaGTFS>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var errorCarga by remember { mutableStateOf<String?>(null) }
    var seleccionada by remember { mutableStateOf<RutaGTFS?>(null) }
    var busqueda by remember { mutableStateOf("") }

    // Formulario del viaje declarado.
    var lugarSubida by remember { mutableStateOf("") }
    var puntoSubida by remember { mutableStateOf<PuntoSubida?>(null) }
    var ocupacionElegida by remember { mutableStateOf("") }   // "" = prefiero no indicar
    var mostrarMapaSubida by remember { mutableStateOf(false) }
    var mostrarConsentimiento by remember { mutableStateOf(false) }
    var tareaDireccion by remember { mutableStateOf<Job?>(null) }

    /// La calle del punto marcado es un RELLENO, no un cierre: el campo sigue
    /// editable porque la calle del sistema no siempre es la que el usuario
    /// reconoce (iOS idem). Se cancela la geocodificacion anterior al mover el
    /// punto otra vez.
    fun escribirDireccion(punto: PuntoSubida) {
        tareaDireccion?.cancel()
        tareaDireccion = scope.launch {
            val nombre = Geocodificacion.direccionDe(LatLng(punto.latitude, punto.longitude), contexto)
            if (nombre != null) lugarSubida = nombre.take(120)
        }
    }

    LaunchedEffect(Unit) {
        cargando = true
        errorCarga = null
        val feed = withContext(Dispatchers.Default) { GTFSRepository.rutas() }
        rutas = if (ubicacion != null) {
            val ubic = ubicacion!!
            withContext(Dispatchers.Default) {
                val distancias = feed.associate { ruta ->
                    ruta.id to (GeometriaRutaDeteccion.desde(ruta)?.let { geo ->
                        RouteCandidateMatcher.emparejamientoMasCercano(ubic.latitude, ubic.longitude, -1.0, listOf(geo))
                            ?.distanciaRuta
                    } ?: Double.MAX_VALUE)
                }
                feed.sortedBy { distancias[it.id] ?: Double.MAX_VALUE }
            }
        } else {
            feed.sortedBy { it.linea }
        }
        if (rutaSugeridaID != null) seleccionada = rutas.firstOrNull { it.id == rutaSugeridaID }
        cargando = false
    }

    DisposableEffect(Unit) {
        onDispose { tareaDireccion?.cancel() }
    }

    val ruta = seleccionada
    if (ruta == null) {
        //----Lista de lineas: cercanas primero + busqueda----
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceContainerLow)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Filled.Search, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                OutlinedTextField(
                    value = busqueda,
                    onValueChange = { busqueda = it },
                    placeholder = { Text(L.t("Línea o empresa", "Line or operator"), style = BodyMd) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = BodyMd.copy(color = OnSurface)
                )
                TextButton(onClick = onCancelar) { Text(L.t("Cancelar", "Cancel")) }
            }
            Text(
                if (ubicacion == null) L.t("Elige la línea que tomaste", "Choose your line")
                else L.t("Líneas cercanas primero", "Nearby lines first"),
                style = LabelCapsMd, color = OnSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            when {
                cargando -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(20.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AppPrimary)
                    Text(L.t("Cargando líneas…", "Loading lines…"), style = BodySm, color = OnSurfaceVariant)
                }
                errorCarga != null -> Column(Modifier.padding(20.dp)) {
                    Text(errorCarga ?: "", style = BodySm, color = OnSurfaceVariant)
                }
                rutas.isEmpty() -> Text(
                    L.t("El catálogo no contiene líneas.", "The catalog contains no lines."),
                    style = BodySm, color = OnSurfaceVariant, modifier = Modifier.padding(20.dp)
                )
                else -> {
                    val consulta = busqueda.trim()
                    val filtradas = rutas.filter { r ->
                        consulta.isEmpty() || "${r.linea} ${r.empresa} ${r.variante}"
                            .contains(consulta, ignoreCase = true)
                    }
                    if (filtradas.isEmpty()) {
                        Text(L.t("No encontramos esa línea.", "No matching line found."),
                            style = BodySm, color = OnSurfaceVariant, modifier = Modifier.padding(20.dp))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                            items(filtradas.size) { i ->
                                val r = filtradas[i]
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { seleccionada = r }
                                        .padding(horizontal = 20.dp, vertical = 12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(r.color)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(L.t("Línea", "Line") + " ${r.linea}", style = BodyMdMedium, color = OnSurface)
                                        Text("${r.empresa} · ${r.variante}", style = BodyXs, color = OnSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    } else {
        //----Formulario del viaje declarado----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L.t("Estoy en un micro", "I'm on a bus"), style = HeadlineSm, color = OnSurface, modifier = Modifier.weight(1f))
                TextButton(onClick = onCancelar) { Text(L.t("Cancelar", "Cancel")) }
            }
            Spacer(Modifier.height(12.dp))

            // Contribucion apagada: ofrecer activarla con consentimiento.
            if (!consentimiento) {
                if (configurado) {
                    Button(
                        onClick = { mostrarConsentimiento = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
                    ) {
                        Text(L.t("Activar Ayudar con ubicaciones", "Turn on Help with locations"), style = BodyMdMedium, color = Color.White)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        L.t(
                            "Para contribuir con tu viaje, activa esta opción y concede los permisos de ubicación y movimiento.",
                            "To contribute your trip, enable this option and grant location and motion permissions."
                        ),
                        style = BodyXs, color = OnSurfaceVariant
                    )
                } else {
                    Text(
                        L.t(
                            "Esta instalación aún no tiene habilitada la contribución de viajes. Puedes seguir consultando la ruta en el mapa.",
                            "Trip contributions aren't configured on this installation yet. You can still view your route on the map."
                        ),
                        style = BodySm, color = OnSurfaceVariant
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            Text(L.t("TU TRANSPORTE PÚBLICO", "YOUR PUBLIC TRANSPORT"), style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(L.t("Línea", "Line") + " ${ruta.linea}", style = HeadlineSm, color = OnSurface)
            Text(ruta.empresa, style = BodySm, color = OnSurfaceVariant)
            Text(
                L.t("Letra del transporte:", "Transport letter:") + " " +
                    (ruta.letraTransporte.ifEmpty { L.t("No especificada", "Not specified") }),
                style = BodySm, color = OnSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { seleccionada = null }) {
                Text(L.t("Elegir otra línea", "Choose another line"), style = BodySmMedium, color = AppPrimary)
            }
            Spacer(Modifier.height(14.dp))

            Text(L.t("¿DÓNDE SUBISTE? (OPCIONAL)", "WHERE DID YOU BOARD? (OPTIONAL)"), style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { mostrarMapaSubida = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Map, null, tint = AppPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (puntoSubida == null) L.t("Marcar en el mapa", "Mark on the map")
                    else L.t("Ver o cambiar el punto", "View or change point"),
                    style = BodyMdMedium, color = AppPrimary
                )
            }
            if (puntoSubida != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = AppPrimary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(L.t("Punto seleccionado", "Point selected"), style = BodyXs, color = AppPrimary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { puntoSubida = null; lugarSubida = "" }) {
                        Text(L.t("Quitar", "Remove"), style = BodyXs, color = AppError)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = lugarSubida,
                onValueChange = { lugarSubida = it.take(120) },
                placeholder = { Text(L.t("Calle o referencia", "Street or landmark"), style = BodyMd) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceContainerLow,
                    unfocusedContainerColor = SurfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
                textStyle = BodyMd.copy(color = OnSurface)
            )
            Text(
                L.t(
                    "Marca el lugar y la dirección se escribe sola; puedes corregirla. Es opcional y se guarda solo en tu teléfono.",
                    "Mark the spot and the address is written for you; you can edit it. This is optional and stays on your phone."
                ),
                style = BodyXs, color = OnSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(14.dp))

            Text(L.t("¿QUÉ TAN LLENO VA? (OPCIONAL)", "HOW FULL IS IT? (OPTIONAL)"), style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            val opciones: List<Pair<String, Pair<String, ImageVector>>> = listOf(
                "" to (L.t("Prefiero no indicar", "Skip for now") to Icons.Filled.RemoveCircle),
                "empty" to (L.t("Vacío", "Empty") to Icons.Filled.Person),
                "space" to (L.t("Con espacio", "Room available") to Icons.Filled.People),
                "full" to (L.t("Lleno", "Full") to Icons.Filled.Groups)
            )
            opciones.forEach { (valor, par) ->
                val (titulo, icono) = par
                val activo = ocupacionElegida == valor
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { ocupacionElegida = valor }
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Icon(icono, null, tint = if (activo) AppPrimary else OnSurfaceVariant, modifier = Modifier.size(22.dp))
                    Text(titulo, style = BodyMd, color = OnSurface, modifier = Modifier.weight(1f))
                    Icon(
                        if (activo) Icons.Filled.CheckCircle else Icons.Filled.RemoveCircle,
                        null,
                        tint = if (activo) AppPrimary else OnSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                L.t(
                    "Elige cómo va ahora. Puedes actualizarlo durante el viaje; el reporte vence en 3 minutos y necesita corroboración.",
                    "Choose how full it is now. You can update it during the trip; the report expires in 3 minutes and needs corroboration."
                ),
                style = BodyXs, color = OnSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(18.dp))

            Button(
                onClick = {
                    val geo = GeometriaRutaDeteccion.desde(ruta) ?: return@Button
                    val ocupacion = when (ocupacionElegida) {
                        "empty" -> EstadoOcupacion.VACIO
                        "space" -> EstadoOcupacion.CON_ESPACIO
                        "full" -> EstadoOcupacion.LLENO
                        else -> null
                    }
                    PassiveTrackingCoordinator.iniciarViajeDeclarado(geo, ocupacion, lugarSubida, puntoSubida)
                    onComenzar()
                },
                enabled = consentimiento && configurado,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
            ) {
                Icon(Icons.Filled.DirectionsBus, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(L.t("Comenzar a ayudar", "Start helping"), style = HeadlineXs, color = Color.White)
            }
        }
    }

    //----Mapa del punto de subida----
    if (mostrarMapaSubida && seleccionada != null) {
        MapaPuntoSubida(
            ruta = seleccionada!!,
            puntoInicial = puntoSubida,
            onConfirmar = { nuevo ->
                puntoSubida = nuevo
                mostrarMapaSubida = false
                escribirDireccion(nuevo)
            },
            onCerrar = { mostrarMapaSubida = false }
        )
    }

    //----Consentimiento (mismo texto que el toggle de Ajustes)----
    if (mostrarConsentimiento) {
        DialogoConsentimientoContribucion(
            onAceptar = { mostrarConsentimiento = false },
            onCancelar = { mostrarConsentimiento = false }
        )
    }
}

/// Dialogo de consentimiento + permisos, compartido con el drawer de Ajustes
/// (TextoConsentimientoContribucion.confirmacion del iOS).
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DialogoConsentimientoContribucion(onAceptar: () -> Unit, onCancelar: () -> Unit) {
    // Permisos juntos (ubicacion + actividad fisica): la baliza necesita la
    // ubicacion; la actividad degrada a "unknown", no bloquea.
    val lanzadorPermisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permisos ->
        val ubicacion = permisos[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permisos[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        PassiveTrackingCoordinator.setContribucionActivada(ubicacion)
        onAceptar()
    }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(L.t("Ayudar con ubicaciones en tiempo real", "Help with real-time locations")) },
        text = {
            Text(
                L.t(
                    "La app usa tu ubicación y actividad física para detectar un viaje. Comparte sesión, ruta, coordenadas, hora, velocidad, rumbo, precisión y actividad con el servidor, mediante tu cuenta MQTT. No envía nombre ni correo, pero el broker y el backend pueden relacionar viajes. El envío se pausa en segundo plano. Terminar el viaje no borra los datos del servidor ni garantiza anonimato.",
                    "The app uses location and physical activity to detect a trip. It shares session, route, coordinates, time, speed, heading, accuracy and activity through your MQTT account. It sends no name or email, but the broker and backend can link trips. Sending pauses in the background. Ending a trip does not delete server data or guarantee anonymity."
                ),
                style = BodySm
            )
        },
        confirmButton = {
            TextButton(onClick = {
                lanzadorPermisos.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ))
            }) {
                Text(L.t("Aceptar y activar", "Accept and turn on"))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text(L.t("Cancelar", "Cancel")) }
        }
    )
}

/// Mapa del punto de subida (BoardingPointMap iOS): shape de la linea, tocar
/// para colocar el punto, "marcar mi ubicacion actual" y "Usar este punto".
/// Solo «Usar este punto» confirma donde subio; la ubicacion actual solo propone.
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapaPuntoSubida(
    ruta: RutaGTFS,
    puntoInicial: PuntoSubida?,
    onConfirmar: (PuntoSubida) -> Unit,
    onCerrar: () -> Unit
) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    val permisoUbicacion = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    val centroInicial = puntoInicial?.let { LatLng(it.latitude, it.longitude) }
    val camara = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(centroInicial ?: GTFSRepository.coordenadaUTP, 16f)
    }
    var punto by remember { mutableStateOf(puntoInicial) }
    var buscandoUbicacion by remember { mutableStateOf(true) }
    var mensajeUbicacion by remember { mutableStateOf<String?>(null) }
    var mostrarAjustes by remember { mutableStateOf(false) }
    var peticionUbicacion by remember { mutableStateOf(0) }

    fun pedirUbicacion(reemplazar: Boolean) {
        buscandoUbicacion = true
        mensajeUbicacion = null
        mostrarAjustes = false
        val solicitud = peticionUbicacion
        UbicacionUnaVez.solicitar(contexto, scope) { resultado ->
            if (solicitud != peticionUbicacion) return@solicitar
            buscandoUbicacion = false
            val coordenada = resultado ?: run {
                when {
                    !permisoUbicacion.status.isGranted -> {
                        mostrarAjustes = true
                        mensajeUbicacion = L.t(
                            "La ubicación está desactivada para esta app. Actívala en Ajustes o marca el lugar manualmente.",
                            "Location is turned off for this app. Enable it in Settings or mark the spot manually."
                        )
                    }
                    else -> mensajeUbicacion = L.t(
                        "No pudimos obtener una ubicación reciente y precisa. Vuelve a intentarlo o marca el lugar manualmente.",
                        "Couldn't get a recent, accurate location. Try again or mark the spot manually."
                    )
                }
                null
            }
            if (coordenada != null && (punto == null || reemplazar)) {
                punto = PuntoSubida(coordenada.latitude, coordenada.longitude)
                scope.launch { camara.animate(CameraUpdateFactory.newLatLngZoom(coordenada, 16f)) }
            }
        }
    }

    // Al abrir: proponer la ubicacion actual como punto (solo si no habia).
    LaunchedEffect(Unit) {
        if (permisoUbicacion.status.isGranted) {
            val solicitud = ++peticionUbicacion
            UbicacionUnaVez.solicitar(contexto, scope) { resultado ->
                if (solicitud != peticionUbicacion) return@solicitar
                buscandoUbicacion = false
                if (resultado != null && punto == null) {
                    punto = PuntoSubida(resultado.latitude, resultado.longitude)
                    scope.launch { camara.animate(CameraUpdateFactory.newLatLngZoom(resultado, 16f)) }
                }
            }
        } else {
            buscandoUbicacion = false
        }
    }

    fun encuadrarRecorrido() {
        if (ruta.shape.size < 2) return
        scope.launch {
            val builder = LatLngBounds.builder()
            ruta.shape.forEach(builder::include)
            camara.animate(CameraUpdateFactory.newLatLngBounds(builder.build(), 96))
        }
    }

    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(AppSurface)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = camara,
                uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
                onMapClick = { coordenada ->
                    punto = PuntoSubida(coordenada.latitude, coordenada.longitude)
                }
            ) {
                Polyline(points = ruta.shape, color = ruta.color, width = 10f)
                punto?.let { p ->
                    MarkerComposable(
                        state = MarkerState(LatLng(p.latitude, p.longitude)),
                        title = L.t("Aquí subí", "I boarded here")
                    ) {
                        Icon(Icons.Filled.Place, null, tint = AppPrimary, modifier = Modifier.size(40.dp))
                    }
                }
            }

            //----Barra superior----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(AppSurface)
                        .clickable(onClick = onCerrar),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, L.t("Cerrar", "Close"), tint = OnSurface, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    L.t("¿Dónde subiste?", "Where did you board?"),
                    style = BodyMdMedium, color = OnSurface,
                    modifier = Modifier
                        .shadow(2.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(AppSurface)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
                Spacer(Modifier.weight(1f))
                // "Ver el recorrido": encuadra el shape completo.
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(AppSurface)
                        .clickable { encuadrarRecorrido() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Map, L.t("Ver el recorrido", "Show route"), tint = OnSurface, modifier = Modifier.size(20.dp))
                }
            }

            //----Controles flotantes: mi ubicacion----
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 70.dp, end = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(AppPrimary)
                        .clickable {
                            if (permisoUbicacion.status.isGranted) {
                                peticionUbicacion++
                                pedirUbicacion(reemplazar = true)
                            } else {
                                permisoUbicacion.launchPermissionRequest()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (buscandoUbicacion) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Filled.MyLocation, L.t("Marcar mi ubicación actual", "Mark my current location"), tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }

            //----Panel inferior: mensaje + confirmacion----
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(AppSurface)
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                mensajeUbicacion?.let { mensaje ->
                    Text(mensaje, style = BodyXs, color = OnSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                }
                if (mostrarAjustes) {
                    TextButton(onClick = {
                        contexto.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }) {
                        Text(L.t("Abrir Ajustes de ubicación", "Open location settings"))
                    }
                }
                Text(
                    L.t(
                        "Revisa el punto donde subiste antes de confirmarlo. La flecha marca tu ubicación actual; toca el mapa para elegir otro lugar.",
                        "Review where you boarded before confirming. The arrow marks your current location; tap the map to choose another spot."
                    ),
                    style = BodyXs, color = OnSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val p = punto ?: return@Button
                        onConfirmar(p)
                    },
                    enabled = punto != null && !buscandoUbicacion,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
                ) {
                    Text(L.t("Usar este punto", "Use this point"), style = HeadlineXs, color = Color.White)
                }
            }
        }
    }
}

/// Aviso "Viaje iniciado" con cuenta atras de 6 s (AvisoViajeIniciado iOS):
/// se desliza hacia arriba o lateral para cerrarlo; no se repite al volver
/// al mapa durante un viaje existente.
@Composable
fun AvisoViajeIniciado(
    destino: String?,
    viajeIniciadoEn: Double,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val duracionS = 6.0
    var segundosRestantes by remember { mutableStateOf(6) }
    var progreso by remember { mutableStateOf(1f) }
    var desplazamientoY by remember { mutableStateOf(0f) }
    var desplazamientoX by remember { mutableStateOf(0f) }
    val progresoAnimado by animateFloatAsState(progreso, tween(300), label = "avisoViaje")

    LaunchedEffect(viajeIniciadoEn) {
        val cierre = viajeIniciadoEn + duracionS
        while (true) {
            val restante = cierre - System.currentTimeMillis() / 1000.0
            if (restante <= 0) { onCerrar(); break }
            segundosRestantes = ceil(restante).toInt()
            progreso = (restante / duracionS).toFloat()
            delay(((restante * 1000).coerceAtMost(1000.0)).toLong().coerceAtLeast(50L))
        }
    }

    val titulo = destino?.let { L.t("Hacia ", "To ") + it } ?: L.t("Viaje iniciado", "Trip started")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(AppSurface)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (desplazamientoY < -40f || abs(desplazamientoX) > 60f) onCerrar()
                        desplazamientoX = 0f; desplazamientoY = 0f
                    },
                    onDragCancel = { desplazamientoX = 0f; desplazamientoY = 0f }
                ) { change, amount ->
                    change.consume()
                    desplazamientoX += amount.x
                    desplazamientoY = desplazamientoY.coerceAtMost(0f) + minOf(amount.y, 0f)
                }
            }
            .padding(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = BodyMdMedium, color = OnSurface, maxLines = 1)
            Text(
                L.t("Viaje iniciado · Se ocultará", "Trip started · Closing soon"),
                style = BodyXs, color = OnSurfaceVariant
            )
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(30.dp)) {
            CircularProgressIndicator(
                progress = { progresoAnimado },
                modifier = Modifier.size(30.dp),
                strokeWidth = 3.dp,
                color = AppPrimary,
                trackColor = OnSurfaceVariant.copy(alpha = 0.2f)
            )
            Text("$segundosRestantes", style = BodyXs, color = OnSurface)
        }
    }
}

/// Tarjeta del viaje activo (TripContributionPanel iOS): abre los detalles
/// con la ocupacion, el estado y las opciones del viaje.
@Composable
fun TarjetaContribucionViaje(onAbrirDetalles: () -> Unit, modifier: Modifier = Modifier) {
    val ruta by PassiveTrackingCoordinator.rutaViajeSeleccionada.collectAsState()
    val r = ruta ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(AppSurface)
            .clickable { onAbrirDetalles() }
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PrimaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.DirectionsBus, null, tint = OnPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(L.t("Tu transporte público", "Your public transport"), style = BodyXs, color = OnSurfaceVariant)
            Text(L.t("Línea", "Line") + " ${r.lineaConLetra}", style = BodySmMedium, color = OnSurface, maxLines = 1)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

/// Detalles del viaje activo (tripDetails iOS): linea y letra, lugar de
/// subida, ocupacion del micro, estado del publicador, cambio de linea y
/// "Finalizar viaje".
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaDetallesViaje(onCerrar: () -> Unit) {
    var cambiandoLinea by remember { mutableStateOf(false) }
    var confirmarFin by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onCerrar, containerColor = AppSurface) {
        if (cambiandoLinea) {
            ContenidoSeleccionViaje(
                rutaSugeridaID = null,
                onComenzar = onCerrar,
                onCancelar = { cambiandoLinea = false }
            )
        } else {
            DetallesViajeContenido(
                onCerrar = onCerrar,
                onCambiarLinea = { cambiandoLinea = true },
                onPedirFin = { confirmarFin = true }
            )
        }
    }

    if (confirmarFin) {
        AlertDialog(
            onDismissRequest = { confirmarFin = false },
            title = { Text(L.t("¿Finalizar este viaje?", "End this trip?")) },
            text = {
                Text(
                    L.t(
                        "Se dejará de compartir la ubicación de este micro y volverás al mapa principal.",
                        "Sharing this bus's location will stop and you'll return to the main map."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmarFin = false
                    PassiveTrackingCoordinator.terminarViajeDeclarado()
                    onCerrar()
                }) {
                    Text(L.t("Finalizar viaje", "End trip"), color = RojoPeligro)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmarFin = false }) { Text(L.t("Cancelar", "Cancel")) }
            }
        )
    }
}

@Composable
private fun DetallesViajeContenido(
    onCerrar: () -> Unit,
    onCambiarLinea: () -> Unit,
    onPedirFin: () -> Unit
) {
    val ruta by PassiveTrackingCoordinator.rutaViajeSeleccionada.collectAsState()
    val lineaConfirmada by PassiveTrackingCoordinator.lineaConfirmada.collectAsState()
    val lugarSubida by PassiveTrackingCoordinator.lugarSubida.collectAsState()
    val puntoSubida by PassiveTrackingCoordinator.puntoSubida.collectAsState()
    val mensaje by PassiveTrackingCoordinator.mensajeEstado.collectAsState()
    val estadoPublicador by PassiveTrackingCoordinator.estadoPublicador.collectAsState()
    val consentimiento by PassiveTrackingCoordinator.consentimiento.collectAsState()
    val ejecutando by PassiveTrackingCoordinator.ejecutando.collectAsState()

    val r = ruta ?: return

    // Ocupacion del viaje: el servicio vive con la sesion de observacion.
    val servicio = remember { PassiveTrackingCoordinator.ocupacionViaje }
    val enviando by (servicio?.enviando ?: remember { MutableStateFlow(false) }).collectAsState()
    val resultado by (servicio?.resultado ?: remember { MutableStateFlow<String?>(null) }).collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(L.t("Tu viaje", "Your trip"), style = HeadlineSm, color = OnSurface, modifier = Modifier.weight(1f))
            TextButton(onClick = onCerrar) { Text(L.t("Listo", "Done")) }
        }
        Spacer(Modifier.height(12.dp))

        Text(L.t("TU TRANSPORTE PÚBLICO", "YOUR PUBLIC TRANSPORT"), style = LabelCapsMd, color = OnSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        DetalleFila(L.t("Línea", "Line"), r.linea)
        DetalleFila(
            L.t("Letra del transporte", "Transport letter"),
            r.letraTransporte.ifEmpty { L.t("No especificada", "Not specified") }
        )
        lugarSubida?.let { lugar ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Icon(Icons.Filled.Place, null, tint = AppPrimary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(L.t("Subiste en: ", "Boarded at: ") + lugar, style = BodySm, color = OnSurface)
            }
        }
        if (puntoSubida != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Icon(Icons.Filled.CheckCircle, null, tint = OnSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(L.t("Punto de subida guardado", "Boarding point saved"), style = BodySm, color = OnSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))

        Text(L.t("OCUPACIÓN DEL MICRO", "BUS OCCUPANCY"), style = LabelCapsMd, color = OnSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(L.t("¿Qué tan lleno va ahora?", "How full is it now?"), style = BodySm, color = OnSurface)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                EstadoOcupacion.VACIO to L.t("Vacío", "Empty"),
                EstadoOcupacion.CON_ESPACIO to L.t("Con espacio", "Room available"),
                EstadoOcupacion.LLENO to L.t("Lleno", "Full")
            ).forEach { (estado, titulo) ->
                OutlinedButton(
                    onClick = { PassiveTrackingCoordinator.actualizarOcupacionViaje(estado) },
                    enabled = !enviando,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(titulo, style = BodyXs, maxLines = 1)
                }
            }
        }
        resultado?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = BodyXs, color = OnSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))

        Text(L.t("ESTADO DEL VIAJE", "TRIP STATUS"), style = LabelCapsMd, color = OnSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            if (lineaConfirmada != null) L.t("Estás ayudando en esta línea.", "You're helping on this line.")
            else L.t("Esperando detectar movimiento compatible con tu línea.", "Waiting for movement matching your line."),
            style = BodySm, color = OnSurface
        )
        Spacer(Modifier.height(6.dp))
        val colorEstado = when (estadoPublicador) {
            MQTTObservationPublisher.EstadoPublicador.INACTIVO -> OnSurfaceVariant
            MQTTObservationPublisher.EstadoPublicador.CONECTANDO -> NaranjaAdvertencia
            MQTTObservationPublisher.EstadoPublicador.CONECTADO -> VerdeExito
            MQTTObservationPublisher.EstadoPublicador.FALLO -> AppError
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colorEstado))
            Spacer(Modifier.width(8.dp))
            Text(mensaje, style = BodyXs, color = OnSurfaceVariant)
        }
        if (consentimiento && !ejecutando) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { PassiveTrackingCoordinator.setContribucionActivada(true) }) {
                Text(L.t("Reintentar detección", "Retry detection"), style = BodySmMedium, color = AppPrimary)
            }
        }
        Spacer(Modifier.height(14.dp))

        OutlinedButton(
            onClick = onCambiarLinea,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.SwapHoriz, null, tint = AppPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(L.t("Cambiar de línea", "Change line"), style = BodyMdMedium, color = AppPrimary)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onPedirFin,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppError)
        ) {
            Icon(Icons.Filled.Close, null, tint = AppError, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(L.t("Finalizar viaje", "End trip"), style = BodyMdMedium)
        }
    }
}

@Composable
private fun DetalleFila(etiqueta: String, valor: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(etiqueta, style = BodySm, color = OnSurfaceVariant, modifier = Modifier.weight(1f))
        Text(valor, style = BodySmMedium, color = OnSurface)
    }
}
