package com.example.rutautpnative.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rutautpnative.data.monedero.MonederoStore
import com.example.rutautpnative.data.monedero.MovimientoMonedero
import com.example.rutautpnative.data.monedero.PagoQR
import com.example.rutautpnative.data.tarjetas.TarjetasStore
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

//----Hojas del monedero (puerto del MonederoView.swift + MetodosPagoSheet)----
// Todo es DEMO: no se procesa ningun pago real, no se abre ninguna app de
// pagos y el saldo vive solo en este dispositivo (textos honestos del iOS).

private fun descripcionMovimiento(m: MovimientoMonedero): String =
    if (m.esRecarga) L.t("Recarga de demostración", "Demo top-up")
    else L.t("Pasaje (simulado)", "Fare (simulated)")

private fun fechaMovimiento(m: MovimientoMonedero): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date((m.fecha * 1000).toLong()))

//----MonederoCard (dentro del hero de Perfil)----
@Composable
fun MonederoCard(onRecargar: () -> Unit, onPagarQR: () -> Unit) {
    val saldo by MonederoStore.saldoCentimos.collectAsState()
    val invalido by MonederoStore.datosLocalesInvalidos.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(16.dp)
    ) {
        Text(L.t("Saldo de demostración", "Demo balance"), style = LabelCapsSm, color = Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(4.dp))
        Text(
            if (invalido) "S/ —" else "S/ %.2f".format(saldo / 100.0),
            style = TextStyle(
                fontSize = 32.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            ),
            color = Color.White
        )
        Text(
            L.t("≈", "≈") + " ${(saldo / MonederoStore.TARIFA_CENTIMOS).toInt()} " +
                L.t("pasajes a", "rides at") + " " + MonederoStore.tarifaTexto,
            style = BodyXs, color = Color.White.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onRecargar,
                enabled = !invalido,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Icon(Icons.Filled.ArrowDownward, null, tint = AppPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(L.t("Recargar", "Top up"), style = BodyMdMedium, color = AppPrimary)
            }
            OutlinedButton(
                onClick = onPagarQR,
                enabled = !invalido,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(Icons.Filled.QrCodeScanner, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(L.t("Pagar con QR", "Pay with QR"), style = BodyMdMedium, color = Color.White)
            }
        }
    }
}

//----MetodosPagoSheet: referencias de tarjetas (solo ultimos 4)----
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetodosPagoSheet(onDismiss: () -> Unit) {
    val tarjetas by TarjetasStore.tarjetas.collectAsState()
    var mostrarForm by remember { mutableStateOf(false) }
    var detalle by remember { mutableStateOf<TarjetasStore.TarjetaGuardada?>(null) }
    var tarjetaAEliminar by remember { mutableStateOf<TarjetasStore.TarjetaGuardada?>(null) }
    var confirmarEliminar by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Text(L.t("Mis tarjetas", "My cards"), style = HeadlineMd, color = OnSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                L.t(
                    "Estas referencias se guardan cifradas en este dispositivo. No permiten pagar, recargar el monedero ni usar NFC.",
                    "These references are stored encrypted on this device. They can't be used to pay, top up the wallet or use NFC."
                ),
                style = BodySm, color = OnSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))

            if (tarjetas.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(L.t("Aún no hay tarjetas guardadas", "No saved cards yet"), style = BodySm, color = OnSurfaceVariant)
                }
            } else {
                tarjetas.forEach { tarjeta ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceContainerLow)
                            .clickable { detalle = tarjeta }
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Filled.CreditCard, null, tint = AppPrimary, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tarjeta.etiqueta, style = BodyMdMedium, color = OnSurface)
                            Text(tarjeta.nombre, style = BodyXs, color = OnSurfaceVariant, maxLines = 1)
                        }
                        if (tarjeta.id == tarjetas.first().id) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PrimaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(L.t("PRINCIPAL", "PRIMARY"), style = LabelCapsSm, color = OnPrimaryContainer)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { mostrarForm = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Add, null, tint = AppPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(L.t("Añadir referencia", "Add reference"), style = BodyMdMedium, color = AppPrimary)
            }
        }
    }

    // Detalle: elegir como principal / eliminar.
    detalle?.let { tarjeta ->
        AlertDialog(
            onDismissRequest = { detalle = null },
            title = { Text(tarjeta.etiqueta, style = HeadlineSm) },
            text = {
                Column {
                    Row {
                        Text(L.t("Nombre", "Name"), style = BodySm, color = OnSurfaceVariant, modifier = Modifier.width(110.dp))
                        Text(tarjeta.nombre, style = BodySm, color = OnSurface)
                    }
                    Row {
                        Text(L.t("Red", "Network"), style = BodySm, color = OnSurfaceVariant, modifier = Modifier.width(110.dp))
                        Text(tarjeta.red, style = BodySm, color = OnSurface)
                    }
                    Row {
                        Text(L.t("Últimos 4", "Last 4"), style = BodySm, color = OnSurfaceVariant, modifier = Modifier.width(110.dp))
                        Text(tarjeta.ultimos4, style = BodySm, color = OnSurface, fontFamily = FontFamily.Monospace)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { TarjetasStore.hacerPrincipal(tarjeta.id); detalle = null }) {
                    Text(L.t("Elegir como principal", "Set as primary"), color = AppPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { tarjetaAEliminar = tarjeta; confirmarEliminar = true; detalle = null }) {
                    Icon(Icons.Filled.Delete, null, tint = AppError, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(L.t("Eliminar", "Remove"), color = AppError)
                }
            }
        )
    }

    if (confirmarEliminar) {
        AlertDialog(
            onDismissRequest = { confirmarEliminar = false },
            title = { Text(L.t("¿Eliminar referencia?", "Remove reference?")) },
            text = {
                Text(
                    L.t(
                        "Solo se eliminará la referencia local; tu tarjeta bancaria no se modifica.",
                        "Only the local reference will be removed; your bank card isn't modified."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    tarjetaAEliminar?.let { TarjetasStore.eliminar(it.id) }
                    tarjetaAEliminar = null
                    confirmarEliminar = false
                }) {
                    Text(L.t("Eliminar", "Remove"), color = AppError)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmarEliminar = false }) { Text(L.t("Cancelar", "Cancel")) }
            }
        )
    }

    if (mostrarForm) {
        TarjetaFormSheet(
            onGuardar = { nombre, red, ultimos4 ->
                TarjetasStore.agregar(nombre, red, ultimos4)
                mostrarForm = false
            },
            onCancelar = { mostrarForm = false }
        )
    }
}
