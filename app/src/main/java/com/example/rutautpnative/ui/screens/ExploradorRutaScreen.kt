package com.example.rutautpnative.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.ui.theme.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import java.util.Locale

// Padding (px) al encuadrar el recorrido, para dejar margen en los bordes.
private const val PADDING_ENCUADRE = 96

// Colores semánticos de inicio/fin del recorrido.
private val ColorInicio = Color(0xFF2E7D32)
private val ColorFin = Color(0xFFC62828)

//----Explorador de ruta----
// Modal a pantalla completa (equivalente al fullScreenCover de iOS) que muestra
// el recorrido real de UNA ruta. Reutilizable: recibe una RutaGTFS y un onCerrar.
@Composable
fun ExploradorRutaScreen(ruta: RutaGTFS, onCerrar: () -> Unit) {
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(ruta.shape.firstOrNull() ?: LatLng(-8.1116, -79.0287), 13f)
    }
    val scope = rememberCoroutineScope()

    // Encuadra todo el recorrido; maneja rutas con 1 punto o sin puntos.
    fun encuadrar() {
        val shape = ruta.shape
        if (shape.isEmpty()) return
        scope.launch {
            if (shape.size == 1) {
                cameraState.animate(CameraUpdateFactory.newLatLngZoom(shape.first(), 15f))
            } else {
                val bounds = LatLngBounds.Builder()
                shape.forEach { bounds.include(it) }
                cameraState.animate(CameraUpdateFactory.newLatLngBounds(bounds.build(), PADDING_ENCUADRE))
            }
        }
    }

    LaunchedEffect(ruta.id) { encuadrar() }

    Dialog(
        onDismissRequest = onCerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
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
                if (ruta.shape.size >= 2) {
                    Polyline(points = ruta.shape, color = ruta.color, width = 10f)
                }
                ruta.paraderos.forEachIndexed { index, paradero ->
                    val esInicio = index == 0
                    val esFin = index == ruta.paraderos.lastIndex
                    MarkerComposable(
                        state = MarkerState(position = paradero.coordinate),
                        title = if (esInicio || esFin) paradero.nombre else null
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (esInicio || esFin) 14.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        esInicio -> ColorInicio
                                        esFin -> ColorFin
                                        else -> ruta.color
                                    }
                                )
                        )
                    }
                }
            }

            // Chrome superior
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BotonCircular(icono = Icons.Filled.Close, onClick = onCerrar)
                Spacer(Modifier.width(10.dp))
                // Píldora: línea + empresa
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AppSurface.copy(alpha = 0.92f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.width(4.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(ruta.color))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Línea ${ruta.linea}", style = HeadlineBody, color = OnSurface, maxLines = 1)
                        Text(ruta.empresa, style = BodySm, color = OnSurfaceVariant, maxLines = 1)
                    }
                }
                Spacer(Modifier.weight(1f))
                BotonCircular(icono = Icons.Filled.MyLocation, onClick = { encuadrar() })
            }

            // Leyenda inferior
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                LeyendaRuta(ruta)
            }
        }
    }
}

@Composable
private fun BotonCircular(icono: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(AppSurface.copy(alpha = 0.92f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icono, null, tint = OnSurface, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun LeyendaRuta(ruta: RutaGTFS) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(4.dp).height(38.dp).clip(RoundedCornerShape(2.dp)).background(ruta.color))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("${ruta.linea} · ${ruta.empresa}", style = HeadlineBody, color = OnSurface, maxLines = 1)
                    Text(ruta.recorrido, style = BodySm, color = OnSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(TertiaryContainer).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(ruta.frecuenciaTexto, style = LabelCapsMd, color = OnTertiaryContainer)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                DatoRuta(Icons.Filled.Schedule, if (ruta.duracionMin > 0) "${ruta.duracionMin} min" else "—", "Viaje", Modifier.weight(1f))
                DatoRuta(Icons.Filled.Payments, ruta.precioTexto, "Tarifa", Modifier.weight(1f))
                DatoRuta(Icons.Filled.LocationOn, "${ruta.paraderos.size}", "Paraderos", Modifier.weight(1f))
                DatoRuta(Icons.Filled.Straighten, String.format(Locale.US, "%.1f km", ruta.distanciaKm), "Longitud", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DatoRuta(icono: ImageVector, valor: String, etiqueta: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icono, null, tint = AppPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(valor, style = BodySmMedium, color = OnSurface)
        Text(etiqueta, style = LabelCapsSm, color = OnSurfaceVariant)
    }
}