package com.example.rutautpnative.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import kotlin.math.max
import kotlin.math.roundToInt

//----Editor de encuadre (puerto del editor del iOS)----
// Mover y hacer zoom DENTRO de un marco; "Guardar encuadre" recorta lo
// visible. El marco es circular (foto de perfil) o rectangular ratio carné
// (CR80). Cancelar conserva la foto anterior; "Ajustar encuadre" vuelve a
// recortar la foto guardada.

@Composable
fun EditorEncuadre(
    bitmap: Bitmap,
    circular: Boolean,
    onGuardar: (Bitmap) -> Unit,
    onCancelar: () -> Unit
) {
    // Estado del encuadre: zoom sobre la escala base de "cover" y pan.
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    // Medidas del marco: cuadrado (perfil) o ratio carné ~1.586 (CR80).
    val dens = LocalDensity.current
    val marcoAltoDp = if (circular) 300.dp else 200.dp
    val marcoAnchoDp = if (circular) 300.dp else (200.dp * 1.586f)
    val marcoAlto = with(dens) { marcoAltoDp.toPx() }
    val marcoAncho = with(dens) { marcoAnchoDp.toPx() }

    // Escala base: la foto CUBRE el marco (zoom 1 = justo lo llena, sin huecos).
    val base = max(marcoAncho / bitmap.width, marcoAlto / bitmap.height)

    fun desplazamientoValido(nuevoPan: Offset, z: Float): Offset {
        val anchoMostrado = bitmap.width * base * z
        val altoMostrado = bitmap.height * base * z
        val maxX = max(0f, (anchoMostrado - marcoAncho) / 2f)
        val maxY = max(0f, (altoMostrado - marcoAlto) / 2f)
        return Offset(nuevoPan.x.coerceIn(-maxX, maxX), nuevoPan.y.coerceIn(-maxY, maxY))
    }

    val estadoTransformable = rememberTransformableState { zoomChange, panChange, _ ->
        val nuevoZoom = (zoom * zoomChange).coerceIn(1f, 6f)
        zoom = nuevoZoom
        pan = desplazamientoValido(pan + panChange, nuevoZoom)
    }

    fun recortarVisible(): Bitmap {
        val escala = base * zoom
        // Esquina superior izquierda de la imagen DENTRO del marco: el MISMO
        // calculo que el render (imagen centrada en el marco + pan). Sin el
        // centrado, el recorte queda desplazado media caja a la derecha/abajo.
        val imgIzq = marcoAncho / 2f - bitmap.width * escala / 2f + pan.x
        val imgArr = marcoAlto / 2f - bitmap.height * escala / 2f + pan.y
        // Region visible del marco, en coordenadas de bitmap.
        var izq = ((0f - imgIzq) / escala).roundToInt()
        var arr = ((0f - imgArr) / escala).roundToInt()
        var ancho = (marcoAncho / escala).roundToInt()
        var alto = (marcoAlto / escala).roundToInt()
        izq = izq.coerceIn(0, bitmap.width - 1)
        arr = arr.coerceIn(0, bitmap.height - 1)
        ancho = ancho.coerceIn(1, bitmap.width - izq)
        alto = alto.coerceIn(1, bitmap.height - arr)
        return Bitmap.createBitmap(bitmap, izq, arr, ancho, alto)
    }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = {
            Text(
                if (circular) L.t("Encuadre de tu foto", "Frame your photo")
                else L.t("Encuadre del carné", "Frame your ID card")
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(marcoAnchoDp, marcoAltoDp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .transformable(estadoTransformable)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val escala = base * zoom
                        val anchoMostrado = bitmap.width * escala
                        val altoMostrado = bitmap.height * escala
                        val izq = size.width / 2f - anchoMostrado / 2f + pan.x
                        val arr = size.height / 2f - altoMostrado / 2f + pan.y
                        drawImage(
                            image = bitmap.asImageBitmap(),
                            dstOffset = androidx.compose.ui.unit.IntOffset(izq.roundToInt(), arr.roundToInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(anchoMostrado.roundToInt(), altoMostrado.roundToInt())
                        )
                        // Velo fuera del marco + borde del encuadre.
                        val marco = Rect(0f, 0f, marcoAncho, marcoAlto)
                        val velo = Color.Black.copy(alpha = 0.55f)
                        // Cuatro bandas alrededor del marco.
                        drawRect(velo, size = Size(size.width, marco.top))
                        drawRect(velo, topLeft = Offset(0f, marco.bottom), size = Size(size.width, size.height - marco.bottom))
                        drawRect(velo, topLeft = Offset(0f, marco.top), size = Size(marco.left, marco.height))
                        drawRect(velo, topLeft = Offset(marco.right, marco.top), size = Size(size.width - marco.right, marco.height))
                        // Marco (circulo inscrito o rectangulo).
                        if (circular) {
                            val centro = Offset(marcoAncho / 2f, marcoAlto / 2f)
                            drawCircle(Color.White, radius = marcoAncho / 2f, center = centro, style = Stroke(2.dp.toPx()))
                        } else {
                            drawRect(Color.White, topLeft = marco.topLeft, size = marco.size, style = Stroke(2.dp.toPx()))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.CenterFocusStrong, null, tint = OnSurfaceVariant, modifier = Modifier.size(14.dp))
                    Text(
                        L.t("Arrastra para mover; pellizca para acercar. Guarda solo lo visible.",
                            "Drag to move; pinch to zoom in. Save keeps only what's visible."),
                        style = BodyXs, color = OnSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onGuardar(recortarVisible()) }) {
                Text(L.t("Guardar encuadre", "Save frame"))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) {
                Text(L.t("Cancelar", "Cancel"))
            }
        }
    )
}
