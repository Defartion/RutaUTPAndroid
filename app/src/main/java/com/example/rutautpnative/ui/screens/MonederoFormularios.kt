package com.example.rutautpnative.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.data.monedero.*
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private fun soles(centimos: Long) = "S/ %.2f".format(centimos / 100.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecargarSaldoSheet(onDismiss: () -> Unit) {
    val saldo by MonederoStore.saldoCentimos.collectAsState()
    val movimientos by MonederoStore.movimientos.collectAsState()
    val invalido by MonederoStore.datosLocalesInvalidos.collectAsState()
    var elegido by remember { mutableLongStateOf(1000L) }
    var billetera by remember { mutableStateOf(PagoQR.Billetera.YAPO) }
    var error by remember { mutableStateOf(false) }
    var procesando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { MonederoStore.reintentarLectura() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = AppBackground) {
        FormularioRecarga(saldo, movimientos, invalido, elegido, { elegido = it }, billetera, { billetera = it }, procesando, error,
            onRecargar = {
                if (!procesando) {
                    procesando = true
                    scope.launch { try { if (MonederoStore.recargar(elegido / 100.0)) onDismiss() else error = true } finally { procesando = false } }
                }
            }, onReintentar = MonederoStore::reintentarLectura, onCerrar = onDismiss)
    }
}

@Composable
internal fun FormularioRecarga(saldo: Long, movimientos: List<MovimientoMonedero>, invalido: Boolean, elegido: Long,
    onImporte: (Long) -> Unit, billetera: PagoQR.Billetera, onBilletera: (PagoQR.Billetera) -> Unit,
    procesando: Boolean, error: Boolean, onRecargar: () -> Unit, onReintentar: () -> Unit, onCerrar: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        CabeceraMonedero(L.t("Recargar saldo", "Top up balance"), L.t("Cancelar", "Cancel"), onCerrar)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(L.t("SALDO DE DEMOSTRACIÓN", "DEMO BALANCE"), style = BodyXsMedium, color = OnSurfaceVariant)
                Text(if (invalido) "S/ —" else soles(saldo), style = DisplayNumberLg, color = OnSurface, textAlign = TextAlign.Center)
                Text(L.f("Saldo tras la recarga: {0}", "Balance after top-up: {0}", soles(saldo + elegido)), style = BodySm, color = OnSurfaceVariant, textAlign = TextAlign.Center)
            }
            Text(L.t("Recarga de demostración: no se procesa ningún pago real ni se abre ninguna app de pagos. El saldo se guarda solo en este dispositivo.", "Demo top-up: no real payment is processed and no payment app is opened. The balance is stored only on this device."), style = BodyXs, color = OnSurfaceVariant)
            if (invalido) AvisoMonederoInvalido(onReintentar)
            GrupoFormularioMonedero(L.t("¿CUÁNTO QUIERES RECARGAR?", "HOW MUCH DO YOU WANT TO TOP UP?")) {
                val columnas = when { LocalDensity.current.fontScale >= 2f -> 1; LocalDensity.current.fontScale >= 1.3f -> 2; else -> 4 }
                FlowRow(Modifier.fillMaxWidth().selectableGroup().padding(10.dp), maxItemsInEachRow = columnas, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(500L, 1000L, 2000L, 5000L).forEach { importe ->
                        FilterChip(selected = elegido == importe, onClick = { onImporte(importe) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OnSurface, selectedLabelColor = AppBackground), label = { Text("S/ ${importe / 100}", style = BodyMdMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) })
                    }
                }
            }
            GrupoFormularioMonedero(L.t("PAGA CON", "PAY WITH")) {
                Column(Modifier.selectableGroup()) {
                    PagoQR.Billetera.entries.forEachIndexed { i, opcion ->
                        Row(Modifier.fillMaxWidth().selectable(billetera == opcion, role = Role.RadioButton, onClick = { onBilletera(opcion) }).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MarcaMonedero(opcion)
                            Column(Modifier.weight(1f)) { Text(opcion.nombreMarca, style = BodyMd, color = OnSurface); Text(L.t("Billetera digital", "Digital wallet"), style = BodySm, color = OnSurfaceVariant) }
                            RadioButton(billetera == opcion, onClick = null)
                        }
                        if (i < PagoQR.Billetera.entries.lastIndex) HorizontalDivider(Modifier.padding(start = 64.dp), color = OutlineVariant.copy(alpha = .3f))
                    }
                }
            }
            Button(onClick = onRecargar, enabled = !invalido && !procesando, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = OnSurface, contentColor = AppBackground)) {
                if (procesando) CircularProgressIndicator(Modifier.size(22.dp), color = AppBackground)
                else Text(L.f("Recargar {0} con {1}", "Top up {0} with {1}", soles(elegido), billetera.nombreMarca), style = BodyMdMedium, textAlign = TextAlign.Center)
            }
            if (error) Text(L.t("No se pudo recargar. Revisa los datos del monedero y el límite de S/ 1 000 000. No se hicieron cambios.", "Unable to top up. Check wallet data and the S/ 1,000,000 balance limit. No changes were made."), style = BodySm, color = AppError)
            if (movimientos.isNotEmpty()) GrupoFormularioMonedero(L.t("MOVIMIENTOS RECIENTES", "RECENT ACTIVITY")) {
                movimientos.take(5).forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(if (m.esRecarga) Icons.Filled.ArrowDownward else Icons.Filled.DirectionsBus, null, tint = if (m.esRecarga) Tertiary else AcentoInterfaz)
                        Column(Modifier.weight(1f)) { Text(if (m.esRecarga) L.t("Recarga de demostración", "Demo top-up") else L.t("Pasaje (simulado)", "Fare (simulated)"), style = BodySm, color = OnSurface); Text(SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date((m.fecha * 1000).toLong())), style = BodyXs, color = OnSurfaceVariant) }
                        Text((if (m.esRecarga) "+" else "") + soles(m.importeCentimos), style = BodySmMedium, color = if (m.esRecarga) Tertiary else AppError)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRPasajeSheet(onDismiss: () -> Unit) {
    var billetera by remember { mutableStateOf(PagoQR.Billetera.YAPO) }
    var escanear by remember { mutableStateOf(false) }
    val invalido by MonederoStore.datosLocalesInvalidos.collectAsState()
    val cuenta = PagoQR.cuentaDemo.copy(billetera = billetera)
    val qr by produceState<Bitmap?>(null, billetera) { value = withContext(Dispatchers.Default) { PagoQR.imagen(PagoQR.cargaUtil(cuenta)) } }
    LaunchedEffect(Unit) { MonederoStore.reintentarLectura() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = AppBackground) {
        FormularioPagoQR(billetera, { billetera = it }, qr, invalido, { escanear = true }, MonederoStore::reintentarLectura, onDismiss)
    }
    if (escanear) EscanerQRScreen { escanear = false }
}

@Composable
internal fun FormularioPagoQR(billetera: PagoQR.Billetera, onBilletera: (PagoQR.Billetera) -> Unit, qr: Bitmap?, invalido: Boolean,
    onEscanear: () -> Unit, onReintentar: () -> Unit, onCerrar: () -> Unit) {
    val cuenta = PagoQR.cuentaDemo.copy(billetera = billetera)
    Column(Modifier.fillMaxWidth()) {
        CabeceraMonedero(L.t("Pagar con QR", "Pay with QR"), L.t("Listo", "Done"), onCerrar)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            if (invalido) AvisoMonederoInvalido(onReintentar)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(L.t("PAGAR", "PAY"), style = BodyXsMedium, color = OnSurfaceVariant)
                Button(onClick = onEscanear, enabled = !invalido, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = OnSurface, contentColor = AppBackground)) { Icon(Icons.Filled.QrCodeScanner, null); Spacer(Modifier.width(10.dp)); Text(L.t("Escanear un QR", "Scan a QR"), style = BodyMdMedium) }
                Text(L.f("Escanea el QR del cobrador y se descuenta el pasaje ({0}) del saldo de demostración.", "Scan the collector's QR and the fare ({0}) is deducted from the demo balance.", MonederoStore.tarifaTexto), style = BodyXs, color = OnSurfaceVariant)
            }
            GrupoFormularioMonedero(L.t("Mi QR para recibir", "My QR to receive")) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    MarcaMonedero(billetera)
                    Box(Modifier.widthIn(max = 244.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(12.dp), contentAlignment = Alignment.Center) {
                        if (qr != null) Image(qr.asImageBitmap(), L.t("QR de cobro de demostración", "Demo payment QR"), Modifier.fillMaxSize()) else CircularProgressIndicator()
                    }
                    Text(L.t("Muestra este código para recibir una transferencia.", "Show this code to receive a transfer."), style = BodyXs, color = OnSurfaceVariant, textAlign = TextAlign.Center)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        PagoQR.Billetera.entries.forEachIndexed { i, opcion -> SegmentedButton(billetera == opcion, { onBilletera(opcion) }, shape = SegmentedButtonDefaults.itemShape(i, PagoQR.Billetera.entries.size)) { Text(opcion.nombreMarca, style = BodySm) } }
                    }
                    DatoCuentaMonedero(L.t("Titular", "Holder"), cuenta.titular)
                    DatoCuentaMonedero(L.t("Celular", "Phone"), cuenta.celularLegible)
                }
            }
            Text(L.t("Es una demostración: el código lleva los datos de una cuenta inventada y ninguna app de pagos lo leerá como un cobro. No se mueve dinero real.", "This is a demo: the code carries the details of a made-up account and no payment app will read it as a charge. No real money moves."), style = BodyXs, color = OnSurfaceVariant)
        }
    }
}

@Composable
private fun CabeceraMonedero(titulo: String, accion: String, onCerrar: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(titulo, style = HeadlineSm, color = OnSurface, modifier = Modifier.weight(1f).semantics { heading() }); TextButton(onClick = onCerrar) { Text(accion, style = BodyMdMedium) } }
}

@Composable
private fun GrupoFormularioMonedero(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titulo, style = BodyXsMedium, color = OnSurfaceVariant, modifier = Modifier.semantics { heading() })
        Surface(shape = RoundedCornerShape(18.dp), color = SuperficieAgrupada) { Column(Modifier.fillMaxWidth(), content = contenido) }
    }
}

@Composable
private fun MarcaMonedero(billetera: PagoQR.Billetera) {
    Surface(shape = RoundedCornerShape(10.dp), color = billetera.colorMarca) { Text(billetera.nombreMarca, style = HeadlineSm, color = billetera.colorTextoMarca, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
}

@Composable
private fun DatoCuentaMonedero(titulo: String, valor: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text(titulo, style = BodySm, color = OnSurfaceVariant); Text(valor, style = BodySm, color = OnSurface, modifier = Modifier.weight(1f), textAlign = TextAlign.End) }
}

@Composable
private fun AvisoMonederoInvalido(onReintentar: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(L.t("No se pudieron recuperar los datos del monedero. Las operaciones están bloqueadas y los datos originales se conservan.", "Wallet data could not be recovered. Operations are blocked and the original data has been preserved."), style = BodySm, color = AppError); OutlinedButton(onClick = onReintentar) { Text(L.t("Reintentar lectura", "Retry reading")) } }
}
