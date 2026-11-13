package com.example.rutautpnative.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.negocios.CuponesStore
import com.example.rutautpnative.model.Negocio
import com.example.rutautpnative.ui.components.cuponVigente
import com.example.rutautpnative.ui.components.formatoVenceCupon
import com.example.rutautpnative.ui.components.iconoParaCategoria
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

//----Componentes de negocios demo (burbuja + tarjeta de detalle)----
// Puerto de NegociosComponents.swift. Vivia dentro de NavegacionScreen; se
// extrajo a su propio archivo porque en iOS pertenecen a la pantalla
// TrackingDemo (Fase 10), donde se reutilizaran.

@Composable
fun NegocioBubbleMarker(negocio: Negocio, seleccionado: Boolean) {
    val color = negocio.categoria.color
    val escala by animateFloatAsState(
        targetValue = if (seleccionado) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "burbujaEscala"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer { scaleX = escala; scaleY = escala }
    ) {
        // Etiqueta con el nombre (solo si está seleccionado)
        if (seleccionado) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(negocio.nombre, style = LabelCapsSm, color = Color.Black, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
        }

        Box {
            // Cuadrado 44dp con gradiente del color de la categoría y borde blanco
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(listOf(color, color.copy(alpha = 0.75f)))
                    )
                    .border(3.dp, Color.White, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(iconoParaCategoria(negocio.categoria), null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            // Insignia de ticket si tiene cupón vigente
            val cupon = negocio.cupon
            if (cupon != null && cuponVigente(cupon.vence)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.ConfirmationNumber, null, tint = color, modifier = Modifier.size(11.dp))
                }
            }
        }

        // Cola (triángulo) del globo, del color de la categoría.
        Canvas(modifier = Modifier.size(width = 14.dp, height = 7.dp)) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(path, color)
        }
    }
}

//----Tarjeta de detalle de negocio (NegocioDetailCard)----
@Composable
fun NegocioDetailCard(
    negocio: Negocio,
    desde: LatLng,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    // Cupones guardados (reactivo: "Guardar cupón" → "Guardado" sin reiniciar).
    val idsGuardados by CuponesStore.observarIds().collectAsState(initial = emptySet())
    val guardado = negocio.id in idsGuardados

    val cupon = negocio.cupon
    val vigente = cuponVigente(cupon?.vence)

    val metros = GTFSRepository.distanciaMetros(desde, LatLng(negocio.latitud, negocio.longitud))
    val textoDistancia = com.example.rutautpnative.ui.components.formatoDistancia(metros)

    // Confirmación de copiado (~1.6 s, como la referencia).
    var copiado by remember { mutableStateOf(false) }
    LaunchedEffect(copiado) {
        if (copiado) { delay(1600); copiado = false }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            //----Encabezado: ícono, nombre, rating, cerrar----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(negocio.categoria.color),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(iconoParaCategoria(negocio.categoria), null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(negocio.nombre, style = HeadlineSm, color = OnSurface, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(negocio.categoria.etiqueta, style = BodySm, color = OnSurfaceVariant)
                        negocio.calificacion?.let { cal ->
                            Text("  ·  ", style = BodySm, color = OnSurfaceVariant)
                            Icon(Icons.Filled.Star, null, tint = Color(0xFFF9A825), modifier = Modifier.size(14.dp))
                            Text("%.1f".format(cal), style = BodySm, color = OnSurfaceVariant)
                        }
                    }
                }
                IconButton(onClick = onCerrar) {
                    Icon(Icons.Filled.Close, "Cerrar", tint = OnSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(12.dp))

            //----Dirección + distancia + horario----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Place, null, tint = OnSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("${negocio.direccion} · $textoDistancia", style = BodySm, color = OnSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, null, tint = OnSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(negocio.horario.texto(), style = BodySm, color = OnSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))

            //----Promo----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PrimaryContainer.copy(alpha = 0.14f))
                    .padding(12.dp)
            ) {
                Text(negocio.promoDetalle.texto(), style = BodySm, color = OnSurface)
            }

            //----Cupón----
            if (cupon != null) {
                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(12.dp))

                Text(cupon.detalle.texto(), style = BodyMdMedium, color = OnSurface)
                Spacer(Modifier.height(4.dp))
                Text(cupon.condiciones.texto(), style = BodySm, color = OnSurfaceVariant)
                if (!cupon.vence.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        L.t("Vence: ", "Expires: ") + formatoVenceCupon(cupon.vence),
                        style = BodySm,
                        color = OnSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))

                // Código + copiar (deshabilitado si venció)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerHigh)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            cupon.codigo,
                            style = BodyMdMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            color = if (vigente) OnSurface else OnSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(cupon.codigo))
                            copiado = true
                        },
                        enabled = vigente
                    ) {
                        Icon(
                            if (copiado) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            null,
                            tint = if (copiado) Tertiary else AppPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (copiado) L.t("Copiado", "Copied") else L.t("Copiar", "Copy"), style = BodySm, color = if (copiado) Tertiary else AppPrimary)
                    }
                }

                //----Guardar cupón (persistente)----
                if (vigente) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { scope.launch { CuponesStore.alternarCupon(negocio) } },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = if (guardado)
                            ButtonDefaults.buttonColors(containerColor = SecondaryContainer)
                        else
                            ButtonDefaults.buttonColors(containerColor = AppPrimary)
                    ) {
                        Icon(
                            if (guardado) Icons.Filled.CheckCircle else Icons.Filled.ConfirmationNumber,
                            null,
                            tint = if (guardado) OnSecondaryContainer else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (guardado) L.t("Guardado", "Saved") else L.t("Guardar cupón", "Save coupon"),
                            style = BodyMdMedium,
                            color = if (guardado) OnSecondaryContainer else Color.White
                        )
                    }
                }
            }
        }
    }
}
