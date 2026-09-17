package com.example.rutautpnative.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.example.rutautpnative.ui.theme.*
import kotlinx.coroutines.delay

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