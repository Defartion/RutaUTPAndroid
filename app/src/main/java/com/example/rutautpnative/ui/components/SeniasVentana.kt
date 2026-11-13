package com.example.rutautpnative.ui.components

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.rutautpnative.data.senias.EstadoSenia
import com.example.rutautpnative.data.senias.SeniasOverlay
import com.example.rutautpnative.data.senias.SeniasService
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*

//----Ventana de señas (puerto del SeniasOverlayVentana / SeniasPresenter iOS)----
// El overlay vive en su PROPIA ventana creada al momento (equivalente de la
// UIWindow a nivel .alert del iOS): aparece por ENCIMA de Dialogs y
// ModalBottomSheets. Se configura SIN dim y NO touch-modal: los toques fuera
// de la tarjeta siguen llegando a la app (mapa, formularios, hojas).
//
// La ventana se compone solo mientras hay presentación vigente, así que al
// abrirse se apila sobre las ventanas ya existentes.

@Composable
fun SeniasVentana(presentacion: SeniasOverlay.Presentacion) {
    Dialog(
        onDismissRequest = { SeniasOverlay.cerrarPorUsuario() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        )
    ) {
        // Ventana transparente, sin oscurecer y sin robar los toques de fuera.
        val vista = LocalView.current
        LaunchedEffect(vista) {
            (vista.parent as? DialogWindowProvider)?.window?.apply {
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            }
        }

        val estado by produceState<EstadoSenia?>(initialValue = null, presentacion.clave, L.codigo) {
            value = SeniasService.estado(presentacion.clave)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (presentacion) {
                is SeniasOverlay.Presentacion.Mini ->
                    TarjetaSenia(
                        estado = estado,
                        centrada = false,
                        alCerrar = { SeniasOverlay.cerrarPorUsuario() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(16.dp)
                    )

                is SeniasOverlay.Presentacion.Accion ->
                    TarjetaSenia(
                        estado = estado,
                        centrada = true,
                        conCuentaRegresiva = true,
                        alCerrar = { SeniasOverlay.cerrarPorUsuario() },
                        modifier = Modifier.align(Alignment.Center)
                    )

                is SeniasOverlay.Presentacion.Activacion ->
                    PanelActivacion(
                        estado = estado,
                        alCerrar = { SeniasOverlay.cerrarPorUsuario() }
                    )
            }
        }
    }
}

//----Mini-tarjeta (bucle o cuenta atrás de 3 s), abajo a la derecha o centrada----
@Composable
private fun TarjetaSenia(
    estado: EstadoSenia?,
    centrada: Boolean,
    conCuentaRegresiva: Boolean = false,
    alCerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (estado) {
        is EstadoSenia.Clip -> Column(modifier = modifier) {
            ReproductorSenia(
                uri = estado.uri,
                enBucle = !conCuentaRegresiva,
                alTerminar = {
                    // Video de la tarjeta de accion termino antes de los 3 s:
                    // se queda en el ultimo fotograma hasta que venza la accion.
                }
            )
            if (conCuentaRegresiva) {
                Spacer(Modifier.height(6.dp))
                // Barra de la cuenta atras de 3 s: al llegar al final, la
                // accion se ejecuta (la logica la lleva el presentador).
                val progreso = progresoCuentaRegresiva()
                LinearProgressIndicator(
                    progress = { progreso },
                    modifier = Modifier
                        .width(160.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(4.dp)),
                    color = AppPrimary,
                    trackColor = Color.Black.copy(alpha = 0.35f)
                )
            }
        }

        is EstadoSenia.Pendiente -> AvisoSeniaPendiente(centrada, alCerrar, modifier)
        null -> {}
    }
}

/// Progreso 1->0 durante los 3 s de la tarjeta (solo visual; el temporizador
/// real vive en el presentador y no depende de esta animacion).
@Composable
private fun progresoCuentaRegresiva(): Float {
    var transcurrido by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (transcurrido < 3_000L) {
            kotlinx.coroutines.delay(50)
            transcurrido += 50
        }
    }
    return (1f - transcurrido / 3_000f).coerceIn(0f, 1f)
}

//----Panel de activacion del modo: media pantalla, encuadre completo----
// Reproduce UNA vez; al terminar cierra solo (el modo queda activo). Cerrar
// antes detiene la presentacion sin apagar el modo.
@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun PanelActivacion(estado: EstadoSenia?, alCerrar: () -> Unit) {
    val contexto = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        when (estado) {
        is EstadoSenia.Clip -> {
            val player = remember(estado.uri) {
                ExoPlayer.Builder(contexto).build().apply {
                    setMediaItem(MediaItem.fromUri(estado.uri))
                    repeatMode = Player.REPEAT_MODE_OFF   // una sola vez
                    playWhenReady = true
                    prepare()
                }
            }
            DisposableEffect(player) {
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(estado: Int) {
                        // Fin natural del video: cierra el panel (modo activo).
                        if (estado == Player.STATE_ENDED) SeniasOverlay.activacionTerminada()
                    }
                }
                player.addListener(listener)
                onDispose {
                    player.removeListener(listener)
                    player.release()
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                            // Encuadre completo dentro del panel (iOS idem).
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                BotonCerrarSenia(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp), alCerrar)
            }
        }

        is EstadoSenia.Pendiente -> AvisoSeniaPendiente(true, alCerrar, Modifier.align(Alignment.Center))
        null -> {}
        }
    }
}

//----Reproductor 160x220 con boton de cerrar----
@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun ReproductorSenia(uri: String, enBucle: Boolean, alTerminar: () -> Unit) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = if (enBucle) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    Box(
        modifier = Modifier
            .size(160.dp, 220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        BotonCerrarSenia(
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            alCerrar = { SeniasOverlay.cerrarPorUsuario() }
        )
    }
}

//----Degradado honesto: aviso breve cuando falta el clip del idioma activo----
@Composable
private fun AvisoSeniaPendiente(centrada: Boolean, alCerrar: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppSurface)
            .padding(12.dp)
    ) {
        Text(
            L.t("Seña no disponible todavía", "Sign not available yet"),
            style = BodySm, color = OnSurface,
            modifier = Modifier.padding(top = 48.dp, start = 8.dp, end = 8.dp, bottom = 8.dp)
        )
        BotonCerrarSenia(
            modifier = Modifier.align(Alignment.TopEnd),
            alCerrar = alCerrar
        )
    }
}

@Composable
private fun BotonCerrarSenia(modifier: Modifier = Modifier, alCerrar: () -> Unit) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable { alCerrar() },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Close, L.t("Cerrar seña", "Close sign"), tint = Color.White, modifier = Modifier.size(14.dp))
    }
}
