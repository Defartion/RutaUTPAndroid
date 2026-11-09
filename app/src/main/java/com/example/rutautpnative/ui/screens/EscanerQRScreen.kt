package com.example.rutautpnative.ui.screens

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.rutautpnative.data.monedero.MonederoStore
import com.example.rutautpnative.data.monedero.PagoQR
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import java.util.concurrent.Executors
import kotlin.math.min

//----Escanner QR real (puerto del EscanerQRView.swift)----
// CameraX ImageAnalysis + ZXing MultiFormatReader: SIN dependencias nuevas.
// Detalles heredados del iOS:
//   - La captura se DETIENE al leer (ahorro de bateria).
//   - Ventana cuadrada con esquinas blancas y linea de barrido animada
//     (respetando "reducir movimiento": quieta si esta activo).
//   - Linterna si el dispositivo la trae.
//   - Tarjeta de resultado parseada con PagoQR.leer (demo + EMVCo) y cobro
//     SIMULADO contra el MonederoStore.
//   - "Probar con un codigo de ejemplo": permite probar sin camara (emulador).

// Paleta oscura: importada de PaletaOscura.kt (compartida, antes duplicada).
// El escaner tiene su fondo apenas mas oscuro que el resto.
private val FondoScanner = Color(0xFF101012)
private val TextoScanner = TextoOscuro
private val TextoScannerVar = TextoOscuroVariante
private val RojoError = RojoPeligro

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun EscanerQRScreen(onCerrar: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permisoCamara = rememberPermissionState(Manifest.permission.CAMERA)
    val scope = rememberCoroutineScope()
    val datosLocalesInvalidos by MonederoStore.datosLocalesInvalidos.collectAsState()

    var resultado by remember { mutableStateOf<PagoQR.CamposQR?>(null) }
    var textoLeido by remember { mutableStateOf<String?>(null) }
    var analisisActivo by remember { mutableStateOf(true) }
    var linternaDisponible by remember { mutableStateOf(false) }
    var linternaEncendida by remember { mutableStateOf(false) }
    var camaraDisponible by remember { mutableStateOf(true) }
    var lectura by remember { mutableStateOf<com.example.rutautpnative.data.monedero.ResultadoQR?>(null) }
    var pagoEnCurso by remember { mutableStateOf(false) }
    val capturaReservada = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var resultadoPago by remember { mutableStateOf<String?>(null) }
    var camaraRef by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var ejecutorRef by remember { mutableStateOf<java.util.concurrent.ExecutorService?>(null) }
    var proveedorRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // Linterna: se aplica a la camara referenciada cuando cambia el toggle.
    LaunchedEffect(linternaEncendida) {
        camaraRef?.cameraControl?.enableTorch(linternaEncendida)
    }

    // LIMPIEZA: apaga el executor y desvincula la cámara al cerrar el
    // escáner (antes: cada apertura dejaba un hilo zombie + la cámara
    // retenida por el lifecycle owner).
    DisposableEffect(Unit) {
        onDispose {
            // Silenciosos a proposito (limpieza de cierre, la pantalla ya se va),
            // pero se loguean para diagnostico de fugas si persisten.
            try {
                proveedorRef?.unbindAll()
            } catch (e: Exception) {
                android.util.Log.w("EscanerQR", "unbindAll en dispose fallo", e)
            }
            try {
                ejecutorRef?.shutdown()
            } catch (e: Exception) {
                android.util.Log.w("EscanerQR", "executor shutdown en dispose fallo", e)
            }
            camaraRef = null
            ejecutorRef = null
            proveedorRef = null
        }
    }

    fun procesarLectura(texto: String) {
        if (!capturaReservada.compareAndSet(false, true)) return
        // La captura se detiene al leer (iOS idem).
        analisisActivo = false
        textoLeido = texto
        resultado = PagoQR.leer(texto)
        lectura = com.example.rutautpnative.data.monedero.ResultadoQR(resultado!!)
        resultadoPago = null
    }

    // "Escanear otro": reactiva el analisis y limpia el resultado.
    fun reanudar() {
        if (pagoEnCurso) return
        lectura = null
        capturaReservada.set(false)
        resultado = null
        textoLeido = null
        resultadoPago = null
        analisisActivo = true
    }

    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(FondoScanner)) {

            //----Preview de camara + analisis----
            if (permisoCamara.status.isGranted && camaraDisponible) {
                AndroidView(
                    factory = { ctx ->
                        val vista = PreviewView(ctx)
                        val proveedorFuturo = ProcessCameraProvider.getInstance(ctx)
                        proveedorFuturo.addListener({
                            try {
                                val proveedor = proveedorFuturo.get()
                                proveedorRef = proveedor
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(vista.surfaceProvider)
                                }
                                val analisis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                val ejecutor = Executors.newSingleThreadExecutor()
                                ejecutorRef = ejecutor
                                analisis.setAnalyzer(ejecutor) { imagen ->
                                    if (analisisActivo) {
                                        decodificarQR(imagen)?.let { texto ->
                                            imagen.close()
                                            vista.post { procesarLectura(texto) }
                                        } ?: imagen.close()
                                    } else {
                                        imagen.close()
                                    }
                                }
                                val camara = proveedor.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analisis
                                )
                                camaraRef = camara
                                linternaDisponible = camara.cameraInfo.hasFlashUnit()
                            } catch (e: Exception) {
                                camaraDisponible = false
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        vista
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (!permisoCamara.status.isGranted)
                            L.t("Concede el permiso de cámara para escanear.", "Grant camera permission to scan.")
                        else L.t("En este dispositivo no hay cámara disponible.", "No camera available on this device."),
                        style = BodySm, color = TextoScannerVar
                    )
                }
            }

            //----Ventana de escaneo (esquinas + linea de barrido)----
            if (resultado == null && permisoCamara.status.isGranted && camaraDisponible) {
                val transicion = rememberInfiniteTransition(label = "barrido")
                val posicion by transicion.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1400, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "linea"
                )
                val lado = with(androidx.compose.ui.platform.LocalConfiguration.current) {
                    min(screenWidthDp, screenHeightDp).dp * 0.62f
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(lado)
                ) {
                    // Esquinas blancas de la ventana.
                    EsquinasVentana()
                    // Linea de barrido (el recorrido usa el tamano del canvas).
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val y = size.height * posicion
                        drawLine(
                            color = Color.White.copy(alpha = 0.9f),
                            start = Offset(8f, y),
                            end = Offset(size.width - 8f, y),
                            strokeWidth = 2.5f
                        )
                    }
                }
                // Texto guia bajo la ventana.
                Text(
                    L.t("Apunta al QR del cobrador", "Point at the collector's QR"),
                    style = BodySm, color = TextoScannerVar,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = lado / 2 + 40.dp)
                )
            }

            //----Barra superior: X + titulo + linterna----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FondoScanner)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                IconButton(onClick = onCerrar) {
                    Icon(Icons.Filled.Close, L.t("Cerrar", "Close"), tint = TextoScanner)
                }
                Text(
                    L.t("Escanear QR", "Scan QR"),
                    style = HeadlineSm, color = TextoScanner, modifier = Modifier.weight(1f)
                )
                // Hueco del mismo tamano cuando no hay linterna (centra el titulo).
                if (linternaDisponible) {
                    IconButton(onClick = { linternaEncendida = !linternaEncendida }) {
                        Icon(
                            if (linternaEncendida) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            L.t("Linterna", "Torch"),
                            tint = if (linternaEncendida) Color(0xFFFFC107) else TextoScannerVar
                        )
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }

            //----Panel inferior por estado----
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(FondoScanner)
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                when {
                    !permisoCamara.status.isGranted -> {
                        Text(
                            L.t("Se necesita permiso de cámara para escanear.", "Camera permission is needed to scan."),
                            style = BodySm, color = TextoScanner
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { permisoCamara.launchPermissionRequest() }, shape = RoundedCornerShape(12.dp)) {
                            Text(L.t("Dar permiso", "Grant permission"), color = Color.White)
                        }
                    }

                    !camaraDisponible -> {
                        Text(L.t("En el emulador no hay cámara.", "There's no camera in the emulator."), style = BodySm, color = TextoScannerVar)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { procesarLectura(PagoQR.cargaUtil(PagoQR.cuentaDemo, 2.50)) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(L.t("Probar con un código de ejemplo", "Try a sample code"), color = AppPrimary)
                        }
                    }

                    resultado != null -> {
                        val campos = resultado ?: return@Column   // smart-cast sin !!
                        Text(L.t("Código leído", "Code scanned"), style = HeadlineSm, color = TextoScanner)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (campos.esPago) L.t("QR de pago", "Payment QR")
                            else L.t("Código sin formato de pago", "Code without payment format"),
                            style = LabelCapsMd, color = if (campos.esPago) VerdeExito else TextoScannerVar
                        )
                        Spacer(Modifier.height(8.dp))
                        if (campos.esPago) {
                            FilaResultado(L.t("Billetera", "Wallet"), campos.billetera?.nombreMarca ?: L.t("Detectada", "Detected"))
                            campos.titular?.let { FilaResultado(L.t("Cobra", "Collects"), it) }
                            campos.celular?.let { FilaResultado(L.t("Celular", "Phone"), it) }
                            campos.importe?.let { FilaResultado(L.t("Importe", "Amount"), campos.importeTexto) }
                            campos.ciudad?.let { FilaResultado(L.t("Ciudad", "City"), it) }
                        } else {
                            Text(
                                (textoLeido ?: "").take(80),
                                style = BodySm, color = TextoScannerVar, maxLines = 2
                            )
                        }

                        resultadoPago?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(it, style = BodySm, color = if (it.contains("pagado", true)) VerdeExito else RojoError)
                        }
                        Spacer(Modifier.height(12.dp))

                        lectura?.errorParaPago?.let { Text(it, style = BodySm, color = RojoError) }
                        val autorizada = lectura
                        if (autorizada?.importeParaPago != null && resultadoPago == null) {
                            val importe = autorizada.importeParaPago!!
                            Button(
                                onClick = {
                                    if (!autorizada.reservarPago()) return@Button
                                    pagoEnCurso = true
                                    scope.launch {
                                        val ok = try { MonederoStore.cobrarPasaje(importe) } finally { pagoEnCurso = false }
                                        resultadoPago = if (ok) {
                                            L.t("Pasaje pagado (simulado)", "Fare paid (simulated)")
                                        } else {
                                            L.t("Saldo insuficiente. Recarga para continuar.", "Insufficient balance. Top up to continue.")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VerdeExito),
                                enabled = !pagoEnCurso && !datosLocalesInvalidos
                            ) {
                                Text(
                                    L.t("Pagar", "Pay") + " S/ %.2f".format(importe) + " " + L.t("(simulado)", "(simulated)"),
                                    color = Color.White, maxLines = 1
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        OutlinedButton(
                            onClick = { reanudar() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(L.t("Escanear otro", "Scan another"), color = AppPrimary)
                        }
                    }

                    else -> {
                        // Escaneando: hueco para el aviso de camara (silencio).
                    }
                }
            }
        }
    }
}

//----Piezas----

@Composable
private fun FilaResultado(clave: String, valor: String) {
    Row {
        Text(clave, style = BodySm, color = TextoScannerVar, modifier = Modifier.width(90.dp))
        Text(valor, style = BodySm, color = TextoScanner, modifier = Modifier.weight(1f), maxLines = 1)
    }
}

@Composable
private fun EsquinasVentana() {
    val largo = 22.dp
    val grueso = 3.dp
    val color = Color.White
    Box(modifier = Modifier.fillMaxSize()) {
        // Cuatro esquinas en L.
        Box(Modifier.align(Alignment.TopStart).size(largo, grueso).background(color))
        Box(Modifier.align(Alignment.TopStart).size(grueso, largo).background(color))
        Box(Modifier.align(Alignment.TopEnd).size(largo, grueso).background(color))
        Box(Modifier.align(Alignment.TopEnd).size(grueso, largo).background(color))
        Box(Modifier.align(Alignment.BottomStart).size(largo, grueso).background(color))
        Box(Modifier.align(Alignment.BottomStart).size(grueso, largo).background(color))
        Box(Modifier.align(Alignment.BottomEnd).size(largo, grueso).background(color))
        Box(Modifier.align(Alignment.BottomEnd).size(grueso, largo).background(color))
    }
}

//----Decodificacion ZXing del frame YUV----
// Plano Y con su rowStride (el dataWidth del LuminanceSource): soporta
// camaras con padding por fila.
private fun decodificarQR(imagen: ImageProxy): String? = try {
    val plano = imagen.planes[0]
    val buffer = plano.buffer
    val rowStride = plano.rowStride
    val ancho = imagen.width
    val alto = imagen.height
    val datos = ByteArray(rowStride * (alto - 1) + ancho)
    buffer.get(datos)
    val fuente = PlanarYUVLuminanceSource(datos, rowStride, alto, 0, 0, ancho, alto, false)
    val lector = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true
            )
        )
    }
    lector.decodeWithState(BinaryBitmap(com.google.zxing.common.HybridBinarizer(fuente)))?.text
} catch (e: Exception) {
    // Frame sin QR: normal, se intentara en el siguiente.
    null
}
