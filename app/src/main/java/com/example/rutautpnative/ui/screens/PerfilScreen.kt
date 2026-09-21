package com.example.rutautpnative.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.navigation.AppRouter
import com.example.rutautpnative.ui.components.BottomNavBar
import com.example.rutautpnative.ui.theme.*

@Composable
fun PerfilScreen(router: AppRouter) {
    var nombre by remember { mutableStateOf("Joaquín Díaz") }
    var notifOn by remember { mutableStateOf(true) }
    var ubicacionOn by remember { mutableStateOf(true) }
    var ecoOff by remember { mutableStateOf(false) }
    var carnetVerificado by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var newNameInput by remember { mutableStateOf("") }
    var showCarnetScanner by remember { mutableStateOf(false) }
    var showCarneDigital by remember { mutableStateOf(false) }
    // Foto del carné: estado de sesión (no persiste), compartido con el avatar.
    var fotoPerfil by remember { mutableStateOf<Bitmap?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Hero gradient header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .background(
                        Brush.linearGradient(listOf(AppPrimary, PrimaryContainer, Tertiary))
                    )
            ) {
                // Decorative circles
                Box(modifier = Modifier.size(220.dp).offset(x = 200.dp, y = (-70).dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)))
                Box(modifier = Modifier.size(150.dp).offset(x = (-50).dp, y = 50.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f)))

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 56.dp, start = 20.dp, end = 20.dp)
                ) {
                    // Avatar + name
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(72.dp).clip(CircleShape).background(InversePrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            if (fotoPerfil != null) {
                                Image(
                                    bitmap = fotoPerfil!!.asImageBitmap(),
                                    contentDescription = "Foto de perfil",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(iniciales(nombre), style = HeadlineMd, color = Color.White)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(nombre, style = HeadlineLg, color = Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.20f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                    Text("ESTUDIANTE UTP", style = LabelCapsSm, color = Color.White.copy(alpha = 0.95f))
                                }
                                if (carnetVerificado) {
                                    Box(modifier = Modifier.clip(CircleShape).background(Tertiary).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Icon(Icons.Filled.Verified, null, tint = Color.White, modifier = Modifier.size(10.dp))
                                            Text("VERIFICADO", style = LabelCapsSm, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(22.dp))

                    // Seccion de billetera
                    Text("MI BILLETERA", style = LabelCapsSm, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(horizontal = 4.dp))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WalletCard(
                            icon = Icons.Filled.CreditCard,
                            title = "Método Pago",
                            subtitle = "Agregar tarjeta",
                            modifier = Modifier.weight(1f),
                            onClick = {}
                        )
                        WalletCard(
                            icon = Icons.Filled.Badge,
                            title = "Carnet UTP",
                            subtitle = if (carnetVerificado) "Verificado" else "Escanear ahora",
                            modifier = Modifier.weight(1f),
                            // Ya verificado: abre el carné directo; si no, primero el scanner.
                            onClick = {
                                if (carnetVerificado) showCarneDigital = true
                                else showCarnetScanner = true
                            }
                        )
                    }
                }
            }

            // Stats card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
                elevation = CardDefaults.cardElevation(8.dp),
                modifier = Modifier.padding(horizontal = 20.dp).offset(y = (-40).dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    StatColumn("47", "VIAJES", Modifier.weight(1f))
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(OutlineVariant.copy(alpha = 0.5f)).align(Alignment.CenterVertically))
                    StatColumn("12", "RUTAS", Modifier.weight(1f))
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(OutlineVariant.copy(alpha = 0.5f)).align(Alignment.CenterVertically))
                    StatColumn("3", "LOGROS", Modifier.weight(1f))
                }
            }

            // Settings
            Column(modifier = Modifier.padding(horizontal = 20.dp).offset(y = (-28).dp)) {
                Text("Preferencias", style = LabelCapsLg, color = OnSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest)
                ) {
                    Column {
                        ToggleRow(Icons.Filled.Notifications, AppPrimary, "Notificaciones", notifOn) { notifOn = it }
                        Divider(modifier = Modifier.padding(start = 56.dp))
                        ToggleRow(Icons.Filled.LocationOn, Secondary, "Compartir ubicación", ubicacionOn) { ubicacionOn = it }
                        Divider(modifier = Modifier.padding(start = 56.dp))
                        ToggleRow(Icons.Filled.CreditCard, Tertiary, "Modo económico", ecoOff) { ecoOff = it }
                        Divider(modifier = Modifier.padding(start = 56.dp))
                        ChevronRow(Icons.Filled.Person, AppPrimary, "Nombre: $nombre") {
                            newNameInput = nombre
                            showEditDialog = true
                        }
                        Divider(modifier = Modifier.padding(start = 56.dp))
                        ChevronRow(Icons.Filled.Edit, OnSurfaceVariant, "Editar perfil") {
                            newNameInput = nombre
                            showEditDialog = true
                        }
                    }
                }
            }
            Spacer(Modifier.height(90.dp))
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            BottomNavBar(router)
        }
    }

    // Edit name dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Editar nombre") },
            text = {
                OutlinedTextField(
                    value = newNameInput,
                    onValueChange = { newNameInput = it },
                    label = { Text("Nombre completo") },
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newNameInput.isNotBlank()) nombre = newNameInput
                    showEditDialog = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Cancelar") } }
        )
    }

    // Carnet scanner: al "escanear", abre el carné digital.
    if (showCarnetScanner) {
        CarnetScannerScreen(
            onCapture = {
                carnetVerificado = true
                showCarneDigital = true
            },
            onDismiss = { showCarnetScanner = false }
        )
    }

    // Carné digital (tras escanear o al tocar la tarjeta ya verificada).
    if (showCarneDigital) {
        CarneDigitalScreen(
            nombre = nombre,
            foto = fotoPerfil,
            onFotoChange = { fotoPerfil = it },
            onCerrar = { showCarneDigital = false }
        )
    }
    }

@Composable
private fun WalletCard(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Column {
                Text(title, style = BodyXsMedium, color = Color.White)
                Text(subtitle, style = BodyXs, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
            }
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = DisplayNumberMd, color = OnSurface)
        Text(label, style = LabelCapsMd, color = OnSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, iconColor: Color, label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, style = BodyMdMedium, color = OnSurface, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppPrimary))
    }
}

@Composable
private fun ChevronRow(icon: ImageVector, iconColor: Color, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, style = BodyMdMedium, color = OnSurface, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

private fun iniciales(name: String): String =
    name.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("")