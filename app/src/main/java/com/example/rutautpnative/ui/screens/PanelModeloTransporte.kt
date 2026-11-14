package com.example.rutautpnative.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI

enum class EstadoVisor3D { CARGANDO, LISTO, ERROR }

/** Marco idéntico en galería y detalle; el contenido OpenGL se monta de forma independiente. */
@Composable
fun MarcoVisor3D(estado: EstadoVisor3D, onReintentar: () -> Unit, contenido: @Composable BoxScope.() -> Unit = {}) {
    Box(Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(24.dp)).background(Color.White),
        contentAlignment = Alignment.Center) {
        if (estado != EstadoVisor3D.ERROR) contenido()
        when (estado) {
            EstadoVisor3D.CARGANDO -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = Color.DarkGray)
                Text(L.t("Cargando modelo…", "Loading model…"), color = Color.DarkGray, style = BodySm)
            }
            EstadoVisor3D.ERROR -> Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.ViewInAr, null, tint = Color.DarkGray)
                Text(L.t("No se pudo cargar el modelo 3D.", "Could not load the 3D model."), color = Color.DarkGray, style = BodySm)
                OutlinedButton(onClick = onReintentar, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.DarkGray)) {
                    Text(L.t("Reintentar", "Try again"))
                }
            }
            EstadoVisor3D.LISTO -> Unit
        }
    }
}

private val GuardarCamara3D = listSaver<CamaraTransporte3D, Float>(
    save = { it.guardar() }, restore = { datos -> CamaraTransporte3D().apply { restaurar(datos) } })

@Composable
internal fun PanelModeloTransporte(nombre: String, identidad: IdentidadModelo3D?, contorno: Int, activo: Boolean = true) {
    var intento by rememberSaveable(nombre, identidad?.codigo, identidad?.letra) { mutableIntStateOf(0) }
    val camara = rememberSaveable(nombre, identidad, intento, saver = GuardarCamara3D) { CamaraTransporte3D() }
    // Un resultado o callback de la carga retirada solo modifica su propio estado.
    key(nombre, identidad, contorno, intento) {
        val context = LocalContext.current.applicationContext
        var fallo by remember { mutableStateOf(false) }
        var listo by remember { mutableStateOf(false) }
        var vista by remember { mutableStateOf<TransporteSurface?>(null) }
        val mesh by produceState<MeshTransporte?>(null) {
            try { value = withContext(Dispatchers.IO) { MeshTransporte.cargar(context, nombre, identidad, contorno) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { fallo = true }
        }
        val owner = LocalLifecycleOwner.current
        val visible = activo && LocalWindowInfo.current.isWindowFocused
        DisposableEffect(vista, owner, visible) {
            val actual = vista
            fun actualizar() {
                if (visible && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) actual?.onResume() else actual?.onPause()
            }
            val observador = LifecycleEventObserver { _, _ -> actualizar() }
            owner.lifecycle.addObserver(observador); actualizar()
            onDispose { owner.lifecycle.removeObserver(observador); actual?.onPause() }
        }
        val estado = if (fallo) EstadoVisor3D.ERROR else if (listo) EstadoVisor3D.LISTO else EstadoVisor3D.CARGANDO
        MarcoVisor3D(estado, { intento++ }) {
            mesh?.let { modelo ->
                AndroidView(factory = { ctx ->
                    camara.configurarRadio(modelo.radio)
                    TransporteSurface(ctx, modelo, camara, { listo = true }, { fallo = true }).also { vista = it }
                }, modifier = Modifier.fillMaxSize().semantics {
                    contentDescription = L.t("Modelo tridimensional del transporte", "Three dimensional transport model")
                    customActions = listOf(
                        CustomAccessibilityAction(L.t("Mover a la izquierda", "Move left")) { vista?.mover(-.15f, 0f); true },
                        CustomAccessibilityAction(L.t("Mover a la derecha", "Move right")) { vista?.mover(.15f, 0f); true },
                        CustomAccessibilityAction(L.t("Mover arriba", "Move up")) { vista?.mover(0f, .15f); true },
                        CustomAccessibilityAction(L.t("Mover abajo", "Move down")) { vista?.mover(0f, -.15f); true })
                })
            }
        }
        ControlesModelo3D(estado == EstadoVisor3D.LISTO && visible,
            { vista?.girar((-PI / 9).toFloat()) }, { vista?.girar((PI / 9).toFloat()) },
            { vista?.zoom(.82f) }, { vista?.zoom(1.22f) }, { vista?.restablecer() })
    }
    Text(L.t("Un dedo para girar; dos para ampliar o desplazar.", "One finger to rotate; two to zoom or pan."), style = BodySm, color = OnSurfaceVariant)
    Text(L.t("Modelos de referencia", "Reference models"), style = BodyXs, color = OnSurfaceVariant)
}

@Composable
fun ControlesModelo3D(habilitados: Boolean, izquierda: () -> Unit, derecha: () -> Unit,
    acercar: () -> Unit, alejar: () -> Unit, restablecer: () -> Unit) {
    val acciones = listOf(
        Triple(Icons.Filled.RotateLeft, L.t("Girar a la izquierda", "Rotate left"), izquierda),
        Triple(Icons.Filled.RotateRight, L.t("Girar a la derecha", "Rotate right"), derecha),
        Triple(Icons.Filled.ZoomIn, L.t("Acercar", "Zoom in"), acercar),
        Triple(Icons.Filled.ZoomOut, L.t("Alejar", "Zoom out"), alejar),
        Triple(Icons.Filled.RestartAlt, L.t("Restablecer vista", "Reset view"), restablecer))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        acciones.forEach { (icono, texto, accion) ->
            FilledTonalIconButton(onClick = accion, enabled = habilitados, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = SurfaceContainerLowest, contentColor = AcentoInterfaz)) {
                Icon(icono, texto, modifier = Modifier.size(20.dp))
            }
        }
    }
}
