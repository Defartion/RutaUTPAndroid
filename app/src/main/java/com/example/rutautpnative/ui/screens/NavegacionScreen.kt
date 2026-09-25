package com.example.rutautpnative.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.negocios.CuponesStore
import com.example.rutautpnative.data.negocios.NegociosService
import com.example.rutautpnative.model.Negocio
import com.example.rutautpnative.ui.components.cuponVigente
import com.example.rutautpnative.ui.components.formatoVenceCupon
import com.example.rutautpnative.ui.components.iconoParaCategoria
import com.example.rutautpnative.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Instrucciones de navegación ───────────────────────────────────────────────
data class NavInstruccion(
    val id: Int,
    val texto: String,
    val distancia: String,
    val icono: ImageVector
)

private val instrucciones = listOf(
    NavInstruccion(0, "Camina 250m hasta Av. España",       "250 m",  Icons.Filled.DirectionsWalk),
    NavInstruccion(1, "Sube al bus en el paradero",          "15 min", Icons.Filled.DirectionsBus),
    NavInstruccion(2, "Continúa por Av. España 1.5 km",     "1.5 km", Icons.Filled.ArrowUpward),
    NavInstruccion(3, "Baja en el frontis de UTP Trujillo", "200 m",  Icons.Filled.ArrowDownward),
    NavInstruccion(4, "¡Llegaste a tu destino!",             "",       Icons.Filled.CheckCircle),
)

private val tiempos   = listOf("4 min", "3 min", "2 min", "1 min", "0 min")
private val distancias = listOf("2.0 km", "1.8 km", "1.5 km", "0.5 km", "0 m")

private val routePoints = listOf(
    LatLng(-8.1180, -79.0350),
    LatLng(-8.1140, -79.0320),
    LatLng(-8.1116, -79.0287)
)

// Punto sobre la polilínea para un progreso [0,1].
private fun puntoEnRuta(progreso: Float): LatLng {
    if (routePoints.size < 2) return routePoints.first()
    val totalTramos = routePoints.size - 1
    val posicion = (progreso.coerceIn(0f, 1f)) * totalTramos
    val tramo = posicion.toInt().coerceAtMost(totalTramos - 1)
    val f = posicion - tramo
    val a = routePoints[tramo]
    val b = routePoints[tramo + 1]
    return LatLng(
        a.latitude + (b.latitude - a.latitude) * f,
        a.longitude + (b.longitude - a.longitude) * f
    )
}

// ── Main Screen ───────────────────────────────────────────────────────────────
@Composable
fun NavegacionScreen(
    rutaNombre: String,
    onFinish: () -> Unit
) {
    var instruccionIndex by remember { mutableIntStateOf(0) }
    val instruccionActual = instrucciones[instruccionIndex]
    val progreso = instruccionIndex.toFloat() / (instrucciones.size - 1).toFloat()

    // Auto-advance instructions every 4 seconds
    LaunchedEffect(Unit) {
        while (instruccionIndex < instrucciones.size - 1) {
            delay(4000)
            instruccionIndex++
        }
    }

    //----Posición animada del bus sobre la polilínea----
    // Se interpola suavemente cada vez que avanza la instrucción.
    var posicionBus by remember { mutableStateOf(routePoints.first()) }
    LaunchedEffect(instruccionIndex) {
        val desde = posicionBus
        val hasta = puntoEnRuta(progreso)
        val pasos = 24
        for (i in 1..pasos) {
            val f = i.toFloat() / pasos
            posicionBus = LatLng(
                desde.latitude + (hasta.latitude - desde.latitude) * f,
                desde.longitude + (hasta.longitude - desde.longitude) * f
            )
            delay(40)
        }
    }

    //----Negocios visibles durante el tracking----
    // Recalcula SOLO si la posición se movió al menos 120 m desde el último
    // cálculo (no en cada frame de la animación).
    var negociosVisibles by remember { mutableStateOf<List<Negocio>>(emptyList()) }
    var ultimoCalculo by remember { mutableStateOf<LatLng?>(null) }
    var negocioSeleccionado by remember { mutableStateOf<Negocio?>(null) }
    LaunchedEffect(posicionBus) {
        val prev = ultimoCalculo
        if (prev == null || GTFSRepository.distanciaMetros(prev, posicionBus) >= 120.0) {
            ultimoCalculo = posicionBus
            negociosVisibles = NegociosService.distribuidos(
                centro = posicionBus,
                radioMetros = 900.0,
                limite = 14
            )
        }
    }

    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(-8.1116, -79.0287), 14f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0a0a0a))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            TopBar(rutaNombre = rutaNombre, onFinish = onFinish)

            // Map (60% height)
            Box(modifier = Modifier.weight(1f)) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraState,
                    properties = MapProperties(mapStyleOptions = null),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        scrollGesturesEnabled = false,
                        zoomGesturesEnabled = false,
                        rotationGesturesEnabled = false
                    )
                ) {
                    Polyline(
                        points = routePoints,
                        color = AppPrimary,
                        width = 12f
                    )
                    MarkerComposable(
                        state = MarkerState(position = routePoints.last()),
                        title = "UTP Trujillo"
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(AppPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.School, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    MarkerComposable(
                        state = MarkerState(position = routePoints.first()),
                        title = "Mi ubicación"
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Secondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.MyLocation, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                    //----Burbujas de negocios cercanos al trayecto----
                    negociosVisibles.forEach { negocio ->
                        MarkerComposable(
                            state = MarkerState(LatLng(negocio.latitud, negocio.longitud)),
                            anchor = Offset(0.5f, 1f),
                            onClick = {
                                negocioSeleccionado =
                                    if (negocioSeleccionado?.id == negocio.id) null else negocio
                                true
                            }
                        ) {
                            NegocioBubbleMarker(
                                negocio = negocio,
                                seleccionado = negocioSeleccionado?.id == negocio.id
                            )
                        }
                    }

                    // Bus animado sobre el trayecto
                    MarkerComposable(
                        state = MarkerState(position = posicionBus),
                        anchor = Offset(0.5f, 0.5f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1a1a1a)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.DirectionsBus, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Bottom panel
            BottomPanel(
                instruccion = instruccionActual,
                progreso = progreso,
                tiempo = tiempos[instruccionIndex.coerceAtMost(4)],
                distancia = distancias[instruccionIndex.coerceAtMost(4)]
            )
        }

        //----Detalle del negocio seleccionado----
        negocioSeleccionado?.let { negocio ->
            Box(modifier = Modifier.fillMaxSize()) {
                NegocioDetailCard(
                    negocio = negocio,
                    desde = posicionBus,
                    onCerrar = { negocioSeleccionado = null },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                )
            }
        }
    }
}

// Ícono por categoría y vigencia de cupón: viven compartidas en
// ui/components/IconosCategorias.kt y FormatoCupones.kt (están importadas).

// ── Burbuja de negocio (NegocioBubbleMarker) ──────────────────────────────────
@Composable
private fun NegocioBubbleMarker(negocio: Negocio, seleccionado: Boolean) {
    val color = negocio.categoria.color
    val escala by animateFloatAsState(
        targetValue = if (seleccionado) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "burbujaEscala"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer { scaleX = escala; scaleY = escala }
    ) {
        // Etiqueta con el nombre (solo si está seleccionado)
        if (seleccionado) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(negocio.nombre, style = LabelCapsSm, color = Color.Black, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
        }

        Box {
            // Cuadrado 44dp con gradiente del color de la categoría y borde blanco
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(listOf(color, color.copy(alpha = 0.75f)))
                    )
                    .border(3.dp, Color.White, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(iconoParaCategoria(negocio.categoria), null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            // Insignia de ticket si tiene cupón vigente
            val cupon = negocio.cupon
            if (cupon != null && cuponVigente(cupon.vence)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.ConfirmationNumber, null, tint = color, modifier = Modifier.size(11.dp))
                }
            }
        }

        // Cola (triángulo) del globo, del color de la categoría.
        Canvas(modifier = Modifier.size(width = 14.dp, height = 7.dp)) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(path, color)
        }
    }
}

// ── Tarjeta de detalle de negocio (NegocioDetailCard) ─────────────────────────
@Composable
private fun NegocioDetailCard(
    negocio: Negocio,
    desde: LatLng,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    // Cupones guardados (reactivo: "Guardar cupón" → "Guardado" sin reiniciar).
    val idsGuardados by CuponesStore.observarIds().collectAsState(initial = emptySet())
    val guardado = negocio.id in idsGuardados

    val cupon = negocio.cupon
    val vigente = cuponVigente(cupon?.vence)

    val metros = GTFSRepository.distanciaMetros(desde, LatLng(negocio.latitud, negocio.longitud))
    val textoDistancia = if (metros >= 1000) "%.1f km".format(metros / 1000) else "${metros.toInt()} m"

    // Confirmación de copiado (~1.6 s, como la referencia).
    var copiado by remember { mutableStateOf(false) }
    LaunchedEffect(copiado) {
        if (copiado) { delay(1600); copiado = false }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            //----Encabezado: ícono, nombre, rating, cerrar----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(negocio.categoria.color),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(iconoParaCategoria(negocio.categoria), null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(negocio.nombre, style = HeadlineSm, color = OnSurface, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(negocio.categoria.label, style = BodySm, color = OnSurfaceVariant)
                        negocio.calificacion?.let { cal ->
                            Text("  ·  ", style = BodySm, color = OnSurfaceVariant)
                            Icon(Icons.Filled.Star, null, tint = Color(0xFFF9A825), modifier = Modifier.size(14.dp))
                            Text("%.1f".format(cal), style = BodySm, color = OnSurfaceVariant)
                        }
                    }
                }
                IconButton(onClick = onCerrar) {
                    Icon(Icons.Filled.Close, "Cerrar", tint = OnSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(12.dp))

            //----Dirección + distancia + horario----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Place, null, tint = OnSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("${negocio.direccion} · $textoDistancia", style = BodySm, color = OnSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, null, tint = OnSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(negocio.horario.texto(), style = BodySm, color = OnSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))

            //----Promo----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PrimaryContainer.copy(alpha = 0.14f))
                    .padding(12.dp)
            ) {
                Text(negocio.promoDetalle.texto(), style = BodySm, color = OnSurface)
            }

            //----Cupón----
            if (cupon != null) {
                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(12.dp))

                Text(cupon.detalle.texto(), style = BodyMdMedium, color = OnSurface)
                Spacer(Modifier.height(4.dp))
                Text(cupon.condiciones.texto(), style = BodySm, color = OnSurfaceVariant)
                if (!cupon.vence.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Vence: " + formatoVenceCupon(cupon.vence),
                        style = BodySm,
                        color = OnSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))

                // Código + copiar (deshabilitado si venció)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerHigh)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            cupon.codigo,
                            style = BodyMdMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            color = if (vigente) OnSurface else OnSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(cupon.codigo))
                            copiado = true
                        },
                        enabled = vigente
                    ) {
                        Icon(
                            if (copiado) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            null,
                            tint = if (copiado) Tertiary else AppPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (copiado) "Copiado" else "Copiar", style = BodySm, color = if (copiado) Tertiary else AppPrimary)
                    }
                }

                //----Guardar cupón (persistente)----
                if (vigente) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { scope.launch { CuponesStore.alternarCupon(negocio) } },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = if (guardado)
                            ButtonDefaults.buttonColors(containerColor = SecondaryContainer)
                        else
                            ButtonDefaults.buttonColors(containerColor = AppPrimary)
                    ) {
                        Icon(
                            if (guardado) Icons.Filled.CheckCircle else Icons.Filled.ConfirmationNumber,
                            null,
                            tint = if (guardado) OnSecondaryContainer else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (guardado) "Guardado" else "Guardar cupón",
                            style = BodyMdMedium,
                            color = if (guardado) OnSecondaryContainer else Color.White
                        )
                    }
                }
            }
        }
    }
}

// ── Top Bar ───────────────────────────────────────────────────────────────────
@Composable
private fun TopBar(rutaNombre: String, onFinish: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppPrimary)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "NAVEGANDO",
                style = LabelCapsSm,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                rutaNombre,
                style = HeadlineSm,
                color = Color.White
            )
        }
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
                TextButton(onClick = onFinish, contentPadding = PaddingValues(0.dp)) {
                    Text("Finalizar", style = LabelCapsMd, color = Color.White)
                }
            }
        }
    }
}

// ── Bottom Panel ──────────────────────────────────────────────────────────────
@Composable
private fun BottomPanel(
    instruccion: NavInstruccion,
    progreso: Float,
    tiempo: String,
    distancia: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1a1a1a))
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Instrucción actual
        AnimatedContent(
            targetState = instruccion,
            transitionSpec = {
                slideInVertically { it } + fadeIn() togetherWith
                        slideOutVertically { -it } + fadeOut()
            },
            label = "instruccion"
        ) { inst ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AppPrimary.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(inst.icono, null, tint = AppPrimary, modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(inst.texto, style = HeadlineSm, color = Color.White, maxLines = 2)
                    if (inst.distancia.isNotEmpty()) {
                        Text(inst.distancia, style = BodySm, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }

        // Progress bar
        LinearProgressIndicator(
            progress = { progreso },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = AppPrimary,
            trackColor = Color.White.copy(alpha = 0.15f)
        )

        // Tiempo y distancia
        Row(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("TIEMPO", style = LabelCapsSm, color = Color.White.copy(alpha = 0.5f))
                Text(tiempo, style = HeadlineSm, color = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("DISTANCIA", style = LabelCapsSm, color = Color.White.copy(alpha = 0.5f))
                Text(distancia, style = HeadlineSm, color = Color.White)
            }
        }
    }
}