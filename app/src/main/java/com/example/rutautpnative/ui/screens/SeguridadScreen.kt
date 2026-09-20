package com.example.rutautpnative.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rutautpnative.data.gtfs.ParaderosIluminados
import com.example.rutautpnative.model.CategoriaLugar
import com.example.rutautpnative.model.LugarGuardado
import com.example.rutautpnative.model.ReporteComunidad
import com.example.rutautpnative.model.TipoReporte
import com.example.rutautpnative.navigation.AppRouter
import com.example.rutautpnative.navigation.AppScreen
import com.example.rutautpnative.navigation.DestinoPendiente
import com.example.rutautpnative.ui.components.BottomNavBar
import com.example.rutautpnative.ui.theme.*
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.Calendar

@Composable
fun SeguridadScreen(router: AppRouter, viewModel: SeguridadViewModel = viewModel()) {
    val rutas by viewModel.rutas.collectAsState()
    val numParaderos = remember(rutas) { ParaderosIluminados.seleccionar(rutas).size }
    val context = LocalContext.current
    val borderColor = OutlineVariant.copy(alpha = 0.25f)
    var showReportarSheet by remember { mutableStateOf(false) }
    var showLlamarDialog by remember { mutableStateOf(false) }
    var mostrarParaderos by remember { mutableStateOf(false) }
    var showPublicarComunidad by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Eventos de búsqueda de zonas de referencia (una sola vez cada uno).
    LaunchedEffect(Unit) {
        viewModel.zonaEventos.collect { evento ->
            when (evento) {
                is ZonaEvento.Exito -> {
                    router.destinoPendiente = DestinoPendiente(evento.titulo, evento.lat, evento.lon)
                    router.navigate(AppScreen.MapaPrincipal)
                }
                ZonaEvento.SinResultados ->
                    snackbarHostState.showSnackbar("No encontramos esta ubicación.")
                ZonaEvento.Fallo ->
                    snackbarHostState.showSnackbar("No pudimos buscar el lugar. Revisa tu conexión.")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(AppSurface)
                    .drawBehind { drawLine(borderColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(PrimaryFixed), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Lock, null, tint = AppPrimary, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("Seguridad", style = HeadlineLg, color = AppPrimary, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier.clip(CircleShape).background(AppPrimary).clickable { showReportarSheet = true }.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        Text("Reportar", style = LabelCapsMd, color = Color.White)
                    }
                }
            }

            // Summary bar
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Alertas hoy: 2", style = BodySmMedium, color = OnSurface)
                        Text("Paraderos iluminados: $numParaderos", style = BodySmMedium, color = OnSurface)
                    }
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(SurfaceContainerHigh).clickable { showLlamarDialog = true }.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("Llamar 105", style = BodyXsMedium, color = OnSurface)
                    }
                }
            }

            // Scrollable content
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(4.dp))
                GreetingCard()
                Spacer(Modifier.height(28.dp))
                LugaresSection(router, viewModel)
                Spacer(Modifier.height(28.dp))
                RutasSegurasSection(numParaderos = numParaderos, onOpenParaderos = { mostrarParaderos = true })
                Spacer(Modifier.height(28.dp))
                ZonasReferenciaSection(onZona = { zona -> viewModel.buscarZona(zona.nombre) })
                Spacer(Modifier.height(28.dp))
                ComunidadSection(
                    viewModel = viewModel,
                    onAnadir = { showPublicarComunidad = true }
                )
                Spacer(Modifier.height(90.dp))
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            BottomNavBar(router)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
        )
    }

    // Dialogs & sheets
    if (showLlamarDialog) {
        AlertDialog(
            onDismissRequest = { showLlamarDialog = false },
            title = { Text("Llamar al 105") },
            text = { Text("Se abrirá la aplicación de teléfono para llamar a la central de emergencias.") },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:105"))
                    context.startActivity(intent)
                    showLlamarDialog = false
                }) { Text("Llamar") }
            },
            dismissButton = { TextButton(onClick = { showLlamarDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showReportarSheet) {
        ReportarSheet(onDismiss = { showReportarSheet = false })
    }

    if (showPublicarComunidad) {
        PublicarComunidadSheet(onDismiss = { showPublicarComunidad = false })
    }

    if (mostrarParaderos) {
        ParaderosIluminadosScreen(rutas = rutas, onCerrar = { mostrarParaderos = false })
    }
}

@Composable
private fun GreetingCard() {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val saludo = when (hour) {
        in 5..11  -> "Buenos días"
        in 12..18 -> "Buenas tardes"
        else      -> "Buenas noches"
    }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Tertiary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CalendarToday, null, tint = Tertiary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(saludo, style = HeadlineBody, color = OnSurface)
                Text("Trujillo, La Libertad", style = BodySm, color = OnSurfaceVariant)
            }
        }
    }
}

// Ícono por categoría, mismo mapeo que en Guardado.
private fun iconoParaCategoria(cat: CategoriaLugar): ImageVector = when (cat) {
    CategoriaLugar.UNIVERSIDAD -> Icons.Filled.School
    CategoriaLugar.HOGAR       -> Icons.Filled.Home
    CategoriaLugar.TIENDA      -> Icons.Filled.Storefront
    CategoriaLugar.RESTAURANTE -> Icons.Filled.Restaurant
    CategoriaLugar.PLAZA       -> Icons.Filled.AccountBalance
    CategoriaLugar.PLAYA       -> Icons.Filled.Water
    CategoriaLugar.OTRO        -> Icons.Filled.LocationOn
}

@Composable
private fun LugaresSection(router: AppRouter, viewModel: SeguridadViewModel) {
    val tiles by viewModel.tiles.collectAsState()
    val lugares by viewModel.lugares.collectAsState()
    var showElegir by remember { mutableStateOf(false) }
    var modoEdicion by remember { mutableStateOf(false) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lugares Guardados", style = HeadlineSm, color = OnSurface, modifier = Modifier.weight(1f))
            TextButton(onClick = { modoEdicion = !modoEdicion }) {
                Icon(if (modoEdicion) Icons.Filled.Check else Icons.Filled.Edit, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (modoEdicion) "LISTO" else "EDITAR", style = LabelCapsSm, color = OnSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        // UTP siempre primero y fuera del área arrastrable/editable.
        val utp = tiles.firstOrNull { it.esFijo }
        val otros = tiles.filter { !it.esFijo }

        // Lista local mientras se arrastra; se re-sincroniza cuando cambian los tiles.
        var listaArrastrable by remember { mutableStateOf(otros) }
        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            listaArrastrable = listaArrastrable.toMutableList().apply { add(to.index, removeAt(from.index)) }
        }
        LaunchedEffect(otros) {
            if (!reorderableState.isAnyItemDragging) listaArrastrable = otros
        }
        // Al soltar un tile se persiste el nuevo orden de inmediato.
        LaunchedEffect(reorderableState.isAnyItemDragging) {
            if (!reorderableState.isAnyItemDragging) {
                val ids = listaArrastrable.map { it.id }
                if (ids != otros.map { it.id }) viewModel.reordenarTilesMostrados(ids)
            }
        }

        Row(verticalAlignment = Alignment.Top) {
            if (utp != null) {
                LugarTile(lugar = utp, onClick = { router.navigate(AppScreen.MapaPrincipal) })
                Spacer(Modifier.width(14.dp))
            }
            LazyRow(
                state = lazyListState,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(listaArrastrable, key = { it.id }) { lugar ->
                    ReorderableItem(reorderableState, key = lugar.id) { isDragging ->
                        val escala by animateFloatAsState(if (isDragging) 1.08f else if (modoEdicion) 0.97f else 1f, label = "escalaTile")
                        val alfa by animateFloatAsState(if (isDragging) 0.75f else 1f, label = "alfaTile")
                        Box(
                            modifier = Modifier
                                .graphicsLayer { scaleX = escala; scaleY = escala; alpha = alfa }
                                .draggableHandle(enabled = modoEdicion)
                        ) {
                            LugarTile(
                                lugar = lugar,
                                indice = listaArrastrable.indexOf(lugar),
                                modoEdicion = modoEdicion,
                                onQuitar = { viewModel.quitarTileElegido(lugar.id) },
                                onClick = { router.navigate(AppScreen.Guardado) }
                            )
                        }
                    }
                }
                // El tile "Añadir" queda fijo al final, fuera de lo reordenable.
                item(key = "anadir") {
                    AnadirTile(onClick = { showElegir = true })
                }
            }
        }
    }

    if (showElegir) {
        // Marcados al abrir: los que ya se muestran (si nunca personalizó, los 2 por defecto).
        val marcados = tiles.filter { !it.esFijo }.map { it.id }
        ElegirLugaresSheet(
            lugares = lugares,
            seleccionInicial = marcados,
            onConfirm = { ids ->
                viewModel.guardarSeleccionElegidos(ids)
                showElegir = false
            },
            onIrAGuardado = {
                showElegir = false
                router.navigate(AppScreen.Guardado)
            },
            onDismiss = { showElegir = false }
        )
    }
}

//----Animación de jiggle (rotación oscilante estilo iOS)----
// Estado compartido: un solo InfiniteTransition para todos los tiles,
// con desfase por índice para que no se muevan en bloque.
@Composable
private fun jiggleRotation(activo: Boolean, indice: Int): Float {
    val transition = rememberInfiniteTransition(label = "jiggle")
    val rot by transition.animateFloat(
        initialValue = -1.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(120, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset((indice * 37) % 240)
        ),
        label = "jiggleRot"
    )
    return if (activo) rot else 0f
}

//----Tile circular de lugar (ícono + nombre, puntito si es frecuente)----
@Composable
private fun LugarTile(
    lugar: LugarGuardado,
    indice: Int = 0,
    modoEdicion: Boolean = false,
    onQuitar: () -> Unit = {},
    onClick: () -> Unit
) {
    val editable = modoEdicion && !lugar.esFijo
    val rot = jiggleRotation(activo = editable, indice = indice)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer { rotationZ = rot }
            .clickable(enabled = !modoEdicion, onClick = onClick)
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (lugar.esFijo) AppPrimary else PrimaryContainer.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    iconoParaCategoria(lugar.categoria), null,
                    tint = if (lugar.esFijo) Color.White else AppPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            // Puntito indicador de lugar frecuente.
            if (lugar.esFrecuente && !lugar.esFijo) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(AppSurface)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(AppPrimary)
                        .align(Alignment.TopEnd)
                )
            }
            // Botón de eliminar (solo en modo edición; UTP nunca lo tiene).
            if (editable) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(AppError)
                        .clickable(onClick = onQuitar),
                    contentAlignment = Alignment.Center
                ) {
                    Text("−", color = Color.White, style = BodyMdMedium)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            lugar.nombre,
            style = LabelCapsMd,
            color = OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 72.dp)
        )
    }
}

//----Tile "Añadir" con borde punteado----
@Composable
private fun AnadirTile(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .drawBehind {
                    drawCircle(
                        color = Outline,
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, null, tint = Outline, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text("Añadir", style = LabelCapsMd, color = OnSurfaceVariant, maxLines = 1)
    }
}

//----Sheet para elegir qué lugares se muestran en la fila----
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ElegirLugaresSheet(
    lugares: List<LugarGuardado>,
    seleccionInicial: List<String>,
    onConfirm: (List<String>) -> Unit,
    onIrAGuardado: () -> Unit,
    onDismiss: () -> Unit
) {
    val noFijos = lugares.filter { !it.esFijo }
    var seleccion by remember { mutableStateOf(seleccionInicial.toSet()) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Elige los lugares que verás aquí", style = HeadlineMd, color = OnSurface)
            Spacer(Modifier.height(4.dp))
            Text("Tus lugares guardados de la pestaña Guardado. UTP siempre aparece.", style = BodySm, color = OnSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            if (noFijos.isEmpty()) {
                // Estado vacío: sin lugares guardados todavía.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                ) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(SurfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.BookmarkBorder, null, tint = OnSurfaceVariant, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Aún no tienes lugares guardados", style = BodyMdMedium, color = OnSurface)
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onIrAGuardado,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
                    ) {
                        Text("Ir a Guardado", style = BodyMdMedium, color = Color.White)
                    }
                }
            } else {
                noFijos.forEach { lugar ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                seleccion = if (lugar.id in seleccion) seleccion - lugar.id else seleccion + lugar.id
                            }
                            .padding(vertical = 6.dp)
                    ) {
                        Checkbox(
                            checked = lugar.id in seleccion,
                            onCheckedChange = { checked ->
                                seleccion = if (checked) seleccion + lugar.id else seleccion - lugar.id
                            }
                        )
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(PrimaryContainer.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(iconoParaCategoria(lugar.categoria), null, tint = AppPrimary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(lugar.nombre, style = BodyMd, color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        // Se confirma en el orden natural de la lista guardada.
                        onConfirm(noFijos.filter { it.id in seleccion }.map { it.id })
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
                ) {
                    Text("Listo", style = BodyMdMedium, color = Color.White)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RutasSegurasSection(numParaderos: Int, onOpenParaderos: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Shield, null, tint = Tertiary, modifier = Modifier.size(20.dp))
            Text("Rutas Seguras Hoy", style = HeadlineSm, color = OnSurface)
        }
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth().height(192.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Tertiary.copy(alpha = 0.65f), Secondary.copy(alpha = 0.45f))))
                .clickable { onOpenParaderos() },
            contentAlignment = Alignment.BottomStart
        ) {
            Box(
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 12.dp, vertical = 8.dp).padding(start = 12.dp, bottom = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Lightbulb, null, tint = TertiaryFixedDim, modifier = Modifier.size(16.dp))
                    Text("Paraderos iluminados activos: $numParaderos", style = BodySm, color = Color.White)
                }
            }
        }
    }
}

//----Zonas de referencia----
// Las 10 zonas fijas de Trujillo, igual que en iOS. El texto de descripción
// es una redacción breve razonable (el texto original no era crítico).
private data class ZonaReferencia(
    val nombre: String,
    val descripcion: String,
    val icono: ImageVector
)

private val zonasReferencia = listOf(
    ZonaReferencia("Óvalo Papal", "Nodo vial principal hacia el norte de la ciudad.", Icons.Filled.AltRoute),
    ZonaReferencia("Avenida España 1450", "Tramo comercial con alto flujo de micros.", Icons.Filled.Signpost),
    ZonaReferencia("Comisaría Víctor Larco", "Comisaría de referencia del distrito.", Icons.Filled.LocalPolice),
    ZonaReferencia("Real Plaza", "Centro comercial con gran afluencia diaria.", Icons.Filled.ShoppingBag),
    ZonaReferencia("Plaza de Armas", "Centro histórico y principal punto de encuentro.", Icons.Filled.AccountBalance),
    ZonaReferencia("Mall Aventura", "Centro comercial frente al Óvalo Papal.", Icons.Filled.Storefront),
    ZonaReferencia("Paseo de los Héroes", "Avenida arbolada en zona residencial.", Icons.Filled.Park),
    ZonaReferencia("Hospital Belén", "Hospital de referencia para emergencias.", Icons.Filled.LocalHospital),
    ZonaReferencia("Estadio Mansiche", "Estadio regional; eventos masivos.", Icons.Filled.Stadium),
    ZonaReferencia("Cineplanet", "Cines dentro del Mall Aventura Plaza.", Icons.Filled.Movie)
)

@Composable
private fun ZonasReferenciaSection(onZona: (ZonaReferencia) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Explore, null, tint = AppPrimary, modifier = Modifier.size(20.dp))
            Text("Zonas de referencia", style = HeadlineSm, color = OnSurface)
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(zonasReferencia.size) { i ->
                ZonaCard(
                    numero = i + 1,
                    zona = zonasReferencia[i],
                    onClick = { onZona(zonasReferencia[i]) }
                )
            }
        }
    }
}

@Composable
private fun ZonaCard(numero: Int, zona: ZonaReferencia, onClick: () -> Unit) {
    // El círculo de ícono alterna los contenedores del tema para dar variedad.
    val (bg, fg) = when ((numero - 1) % 3) {
        0 -> PrimaryContainer.copy(alpha = 0.35f) to AppPrimary
        1 -> SecondaryContainer to OnSecondaryContainer
        else -> TertiaryContainer to OnTertiaryContainer
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        modifier = Modifier.width(150.dp).clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(bg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(zona.icono, null, tint = fg, modifier = Modifier.size(20.dp))
                }
                Text(
                    "%02d".format(numero),
                    style = LabelCapsSm,
                    color = OnSurfaceVariant,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(zona.nombre, style = BodyMdMedium, color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(zona.descripcion, style = BodySm, color = OnSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Explorar ubicación", style = LabelCapsSm, color = AppPrimary)
                Icon(Icons.Filled.ArrowForward, null, tint = AppPrimary, modifier = Modifier.size(12.dp))
            }
        }
    }
}

//----Sección Comunidad: publicaciones demo que rotan cada 4 minutos----
@Composable
private fun ComunidadSection(viewModel: SeguridadViewModel, onAnadir: () -> Unit) {
    val ventana by viewModel.ventanaComunidad.collectAsState()
    val votos by viewModel.votosComunidad.collectAsState()

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Group, null, tint = AppPrimary, modifier = Modifier.size(20.dp))
                Text("Comunidad", style = HeadlineSm, color = OnSurface)
            }
            TextButton(onClick = onAnadir) { Text("AÑADIR", style = LabelCapsSm, color = AppPrimary) }
        }
        Spacer(Modifier.height(12.dp))

        // Transición suave (fade + slide) al cambiar de ventana.
        AnimatedContent(
            targetState = ventana,
            transitionSpec = {
                (fadeIn() + slideInHorizontally { it / 3 }) togetherWith
                    (fadeOut() + slideOutHorizontally { -it / 3 })
            },
            label = "ventanaComunidad"
        ) { ventanaActual ->
            Column {
                viewModel.publicacionesVisibles().forEach { r ->
                    ReporteCard(
                        reporte = r,
                        votoActual = votos[r.id],
                        utiles = viewModel.utilesMostrados(r),
                        noUtiles = viewModel.noUtilesMostrados(r),
                        onVotar = { voto -> viewModel.votar(r.id, voto) }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

//----Tarjeta de una publicación de la comunidad----
@Composable
private fun ReporteCard(
    reporte: ReporteComunidad,
    votoActual: VotoComunidad?,
    utiles: Int,
    noUtiles: Int,
    onVotar: (VotoComunidad) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(reporte.avatarColor), contentAlignment = Alignment.Center) {
                    Text(reporte.iniciales, style = LabelCapsMd, color = reporte.avatarForeground)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(reporte.nombre, style = BodyMdMedium, color = OnSurface)
                    Text(reporte.hace, style = LabelCapsSm, color = OnSurfaceVariant)
                }
                Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(reporte.tipo.background).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(reporte.tipo.label, style = LabelCapsSm, color = reporte.tipo.foreground)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(reporte.cuerpo, style = BodyMd, color = OnSurface)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Voto "útil"
                VotoChip(
                    icono = if (votoActual == VotoComunidad.UTIL) Icons.Filled.ThumbUp else Icons.Filled.ThumbUpOffAlt,
                    texto = "Útil ($utiles)",
                    activo = votoActual == VotoComunidad.UTIL,
                    onClick = { onVotar(VotoComunidad.UTIL) }
                )
                Spacer(Modifier.width(16.dp))
                // Voto "no útil"
                VotoChip(
                    icono = if (votoActual == VotoComunidad.NO_UTIL) Icons.Filled.ThumbDown else Icons.Filled.ThumbDownOffAlt,
                    texto = "No útil ($noUtiles)",
                    activo = votoActual == VotoComunidad.NO_UTIL,
                    onClick = { onVotar(VotoComunidad.NO_UTIL) }
                )
                Spacer(Modifier.width(16.dp))
                // Solo el número de comentarios (no hay comentarios reales que abrir).
                Icon(Icons.Filled.ChatBubbleOutline, null, tint = OnSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("${reporte.comentarios}", style = BodySm, color = OnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun VotoChip(
    icono: ImageVector,
    texto: String,
    activo: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Icon(icono, null, tint = if (activo) AppPrimary else OnSurfaceVariant, modifier = Modifier.size(14.dp))
        Text(texto, style = BodySm, color = if (activo) AppPrimary else OnSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportarSheet(onDismiss: () -> Unit) {
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
            text = { Text("Gracias por colaborar con la comunidad.") },
            confirmButton = { TextButton(onClick = { onDismiss() }) { Text("OK") } }
        )
    }
}