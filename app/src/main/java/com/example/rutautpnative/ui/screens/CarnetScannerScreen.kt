package com.example.rutautpnative.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.rutautpnative.ui.theme.*
import com.google.accompanist.permissions.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CarnetScannerScreen(
    onCapture: () -> Unit,
    onDismiss: () -> Unit
) {
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
    var didCapture by remember { mutableStateOf(false) }
    var showCheckmark by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    // Auto-dismiss after checkmark
    LaunchedEffect(showCheckmark) {
        if (showCheckmark) {
            delay(1500)
            onCapture()
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            cameraPermission.status.isGranted -> {
                // Camera preview
                CameraPreview(modifier = Modifier.fillMaxSize())

                // Dark overlay with cutout
                ScannerOverlay()

                // Checkmark animation
                AnimatedVisibility(
                    visible = showCheckmark,
                    enter = scaleIn() + fadeIn(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Tertiary,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }

                // UI overlay
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text("Cancelar", style = BodyMdMedium, color = Color.White)
                            }
                        }
                    }

                    Spacer(Modifier.weight(0.3f))

                    // Instruction above cutout
                    Text(
                        "Encuadra tu carnet aquí",
                        style = BodyMdMedium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 12.dp)
                    )

                    // Space for cutout (proportional to CR80 card ratio)
                    Spacer(Modifier.fillMaxWidth().aspectRatio(1f / 0.54f).padding(horizontal = 24.dp))

                    // Instruction below cutout
                    Text(
                        "Asegúrate que el texto sea legible",
                        style = BodySm,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 12.dp)
                    )

                    Spacer(Modifier.weight(1f))

                    // Capture button
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 48.dp)
                    ) {
                        CaptureButton(
                            enabled = !didCapture,
                            onClick = {
                                didCapture = true
                                showCheckmark = true
                            }
                        )
                    }
                }
            }

            cameraPermission.status.shouldShowRationale ||
                    !cameraPermission.status.isGranted -> {
                PermissionDeniedView(onDismiss = onDismiss)
            }
        }
    }
}

//----Camera Preview----
@Composable
private fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier
    )
}

//----Scanner overlay with cutout----
@Composable
private fun ScannerOverlay() {
    val primaryColor = AppPrimary

    Canvas(modifier = Modifier.fillMaxSize()) {
        val scanW = size.width * 0.85f
        val scanH = scanW * 0.54f
        val scanLeft = (size.width - scanW) / 2f
        val scanTop = (size.height - scanH) / 2f - 40.dp.toPx()
        val scanRect = Rect(scanLeft, scanTop, scanLeft + scanW, scanTop + scanH)
        val cornerRadius = 12.dp.toPx()

        // Dark overlay with cutout
        val overlayPath = Path().apply {
            addRect(Rect(Offset.Zero, size))
            addRoundRect(RoundRect(scanRect, CornerRadius(cornerRadius)))
        }
        clipPath(overlayPath, clipOp = ClipOp.Difference) {
            drawRect(color = Color.Black.copy(alpha = 0.65f))
        }

        // White border around cutout
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(scanRect.left, scanRect.top),
            size = Size(scanW, scanH),
            cornerRadius = CornerRadius(cornerRadius),
            style = Stroke(width = 2.dp.toPx())
        )

        // Corner markers (red/primary)
        val cornerLen = 20.dp.toPx()
        val cornerThick = 3.dp.toPx()

        // Top-left
        drawLine(primaryColor, Offset(scanRect.left, scanRect.top + cornerLen), Offset(scanRect.left, scanRect.top), strokeWidth = cornerThick, cap = StrokeCap.Round)
        drawLine(primaryColor, Offset(scanRect.left, scanRect.top), Offset(scanRect.left + cornerLen, scanRect.top), strokeWidth = cornerThick, cap = StrokeCap.Round)
        // Top-right
        drawLine(primaryColor, Offset(scanRect.right - cornerLen, scanRect.top), Offset(scanRect.right, scanRect.top), strokeWidth = cornerThick, cap = StrokeCap.Round)
        drawLine(primaryColor, Offset(scanRect.right, scanRect.top), Offset(scanRect.right, scanRect.top + cornerLen), strokeWidth = cornerThick, cap = StrokeCap.Round)
        // Bottom-left
        drawLine(primaryColor, Offset(scanRect.left, scanRect.bottom - cornerLen), Offset(scanRect.left, scanRect.bottom), strokeWidth = cornerThick, cap = StrokeCap.Round)
        drawLine(primaryColor, Offset(scanRect.left, scanRect.bottom), Offset(scanRect.left + cornerLen, scanRect.bottom), strokeWidth = cornerThick, cap = StrokeCap.Round)
        // Bottom-right
        drawLine(primaryColor, Offset(scanRect.right - cornerLen, scanRect.bottom), Offset(scanRect.right, scanRect.bottom), strokeWidth = cornerThick, cap = StrokeCap.Round)
        drawLine(primaryColor, Offset(scanRect.right, scanRect.bottom), Offset(scanRect.right, scanRect.bottom - cornerLen), strokeWidth = cornerThick, cap = StrokeCap.Round)
    }
}

//----Capture button----
@Composable
private fun CaptureButton(enabled: Boolean, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(72.dp)) {
                drawCircle(
                    color = Color.White,
                    radius = size.minDimension / 2,
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(60.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            contentPadding = PaddingValues(0.dp)
        ) {}
    }
}

//----Permission denied view----
@Composable
private fun PermissionDeniedView(onDismiss: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.CameraAlt,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(60.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text("Acceso a la cámara denegado", style = HeadlineSm, color = Color.White)
        Spacer(Modifier.height(12.dp))
        Text(
            "Necesitamos acceso a la cámara para escanear tu carnet universitario.",
            style = BodySm,
            color = Color.White.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
        ) {
            Text("Abrir Ajustes", style = HeadlineSm, color = Color.White)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onDismiss) {
            Text("Cancelar", style = BodyMd, color = Color.White.copy(alpha = 0.7f))
        }
    }
}