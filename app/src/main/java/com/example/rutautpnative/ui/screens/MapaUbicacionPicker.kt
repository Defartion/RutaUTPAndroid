package com.example.rutautpnative.ui.screens

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.ubicacion.UbicacionUnaVez
import com.example.rutautpnative.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

//----Selector de ubicación a pantalla completa (pin fijo al centro)----
// El pin NO se mueve: el usuario arrastra el mapa por debajo y la coordenada
// elegida es la que queda bajo el pin (patrón tipo Uber). La coordenada solo
// es un dato del formulario de Publicar; no se sube ni persiste en ningún lado.
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapaUbicacionPicker(
    inicial: LatLng?,          // si el usuario ya había marcado antes, arranca ahí
    onConfirmar: (LatLng) -> Unit,
    onCerrar: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(inicial ?: GTFSRepository.coordenadaUTP, 16f)
    }

    // Coordenada seleccionada = centro actual del mapa.
    var centro by remember { mutableStateOf(inicial ?: GTFSRepository.coordenadaUTP) }

    // Se actualiza SOLO cuando el usuario suelta el mapa (termina el movimiento),
    // no en cada frame de arrastre: `isMoving` pasa a false.
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            centro = cameraPositionState.position.target
        }
    }

    //----GPS de una sola lectura (mismo patrón que ParaderosIluminadosScreen)----
    val permisoUbicacion = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    var pendienteMiUbicacion by remember { mutableStateOf(false) }

    fun centrarEn(punto: LatLng) {
        scope.launch {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(punto, 17f))
        }
    }

    fun miUbicacion() {
        UbicacionUnaVez.solicitar(context, scope) { punto ->
            punto?.let { centrarEn(it) }
        }
    }

    // Tras conceder permiso desde el botón, se ejecuta la búsqueda pendiente.
    LaunchedEffect(permisoUbicacion.status.isGranted) {
        if (permisoUbicacion.status.isGranted && pendienteMiUbicacion) {
            pendienteMiUbicacion = false
            miUbicacion()
        }
    }

    // Auto-centrar en el GPS al abrir SOLO si no había ubicación previa
    // (si ya marcó una antes, no le quitamos la vista de su lugar elegido).
    LaunchedEffect(Unit) {
        if (inicial == null && permisoUbicacion.status.isGranted) {
            miUbicacion()
        }
    }

    Dialog(
        onDismissRequest = onCerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            )

            // Pin fijo visual en el centro de la pantalla (no es un Marker del mapa).
            Icon(
                Icons.Filled.Place,
                contentDescription = "Pin de ubicación",
                tint = AppPrimary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-19).dp) // la punta del pin queda en el centro exacto
                    .size(38.dp)
            )

            //----Barra superior: cerrar + instrucción----
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
                    Icon(Icons.Filled.Close, "Cerrar", tint = OnSurface, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    "Arrastra el mapa para marcar el lugar",
                    style = BodyMdMedium,
                    color = OnSurface,
                    modifier = Modifier
                        .shadow(2.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(AppSurface)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

            //----Botón "Mi ubicación" (círculo flotante, sobre el panel)----
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(AppSurface)
                        .clickable {
                            if (permisoUbicacion.status.isGranted) {
                                miUbicacion()
                            } else {
                                pendienteMiUbicacion = true
                                permisoUbicacion.launchPermissionRequest()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MyLocation, "Mi ubicación", tint = AppPrimary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(12.dp))

                //----Panel de confirmación----
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "%.4f, %.4f".format(centro.latitude, centro.longitude),
                            style = BodySm,
                            color = OnSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { onConfirmar(centro); onCerrar() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
                        ) {
                            Text("Confirmar ubicación", style = HeadlineSm, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
