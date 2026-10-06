package com.example.rutautpnative.ui.screens.mapa

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rutautpnative.R
import com.example.rutautpnative.ui.theme.*
import kotlin.math.roundToInt

// ---- Bus en 3D: un fotograma de la tira de giros ----
// Puerto de `BusEn3D.swift` (rama 3D-BUS del repo iOS).
//
// El asset `drawable-nodpi/bus_giros.png` es una tira de 36 fotogramas
// (6048×168 px → 168 px por fotograma) del modelo 3D de la combi trujillana,
// renderizada con Blender desde la vista de tres cuartos, uno por cada 10° de
// giro. Va en `drawable-nodpi` para que Android NO la escale por densidad: el
// recorte por fotograma se hace aquí a mano. Reglas heredadas de la tira:
//   1. El fotograma 0 apunta al NORTE y el orden avanza en sentido horario
//      (9 = este, 18 = sur, 27 = oeste).
//   2. Cada fotograma cubre 360/36 = 10°.
//
// NO se rota el bitmap en runtime: el giro ya viene "horneado" en los 36
// fotogramas (rotar encima sería doble rotación). Se dibuja la tira entera
// escalada y se desplaza el origen para que la ventana recortada muestre el
// fotograma del rumbo — mismo truco que iOS (Image + offset + clip), exacto y
// barato en GPU.
private const val BusFotogramas = 36
private const val GradosPorFotograma = 360.0 / BusFotogramas

/// Fotograma que corresponde a un rumbo de brujula: round(rumbo/10) % 36.
/// Rumbo desconocido (negativo o no finito) -> 0 (bus mirando al norte):
/// mejor un bus quieto bien dibujado que un hueco en el mapa.
/// Es PUBLICO porque MapaScreen lo pasa como `key` del MarkerComposable: el
/// bitmap del marcador en maps-compose SOLO se re-renderiza cuando cambia una
/// de sus claves, asi que el giro del bus viaja por aqui.
fun indiceFotogramaBus(rumbo: Double): Int =
    if (!rumbo.isFinite() || rumbo < 0) 0
    else ((rumbo % 360.0) / GradosPorFotograma).roundToInt() % BusFotogramas

/// El bus visto desde arriba y atrás, girado según su rumbo.
@Composable
fun BusEn3D(
    rumbo: Double,
    lado: Dp = 56.dp,
    seleccionado: Boolean = false
) {
    // Fotograma correspondiente al rumbo (misma formula que usa MapaScreen
    // para las keys del MarkerComposable).
    val indice = indiceFotogramaBus(rumbo)

    // Un bus seleccionado se dibuja un 18% más grande, con transición suave.
    val escala by animateFloatAsState(
        targetValue = if (seleccionado) 1.18f else 1f,
        animationSpec = tween(durationMillis = 180, easing = EaseInOut),
        label = "bus_seleccion_escala"
    )

    // La tira completa, decodificada una sola vez (6048×168 px, nodpi).
    val tira: ImageBitmap = ImageBitmap.imageResource(R.drawable.bus_giros)

    // Canvas + drawImage con región fuente EXPLÍCITA en píxeles: recorta el
    // fotograma `indice` de la tira y lo pinta escalado al cuadro de `lado`.
    // Nada de offsets de layout ni clip: la ventana de recorte es exacta.
    Canvas(
        modifier = Modifier
            .size(lado)
            // El scale va sobre la capa ya dibujada: crece el fotograma
            // elegido, no cambia qué fotograma se ve (igual que iOS).
            .graphicsLayer { scaleX = escala; scaleY = escala }
    ) {
        val anchoFrame = tira.width / BusFotogramas   // 168 px exactos
        drawImage(
            image = tira,
            srcOffset = IntOffset(anchoFrame * indice, 0),
            srcSize = IntSize(anchoFrame, tira.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            // Suavizado al reducir: la tira viene a resolución 3x
            // (equivalente al .interpolation(.high) de iOS).
            filterQuality = FilterQuality.High
        )
    }
}

// ---- La píldora que identifica el bus: color de línea, icono y rumbo ----
// Puerto de `EtiquetaBus` (MapMarkers.swift, rama 3D-BUS). El color de la
// cápsula lateral identifica la línea sin teñir todo el vehículo; encima del
// modelo 3D sigue siendo lo que dice de un vistazo qué línea es.
@Composable
fun EtiquetaBus(
    linea: String,
    color: Color,
    heading: Double,
    seleccionado: Boolean = false
) {
    val forma = RoundedCornerShape(11.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .shadow(
                elevation = if (seleccionado) 3.dp else 2.dp,
                shape = forma,
                ambientColor = Color.Black.copy(alpha = if (seleccionado) 0.32f else 0.20f),
                spotColor = Color.Black.copy(alpha = if (seleccionado) 0.32f else 0.20f)
            )
            .widthIn(min = 78.dp, max = 120.dp)
            .height(AltoEtiquetaBus)
            .background(SurfaceContainerLowest, forma)
            // Tinte de la línea solo cuando está seleccionada (iOS: 0.08).
            .background(color.copy(alpha = if (seleccionado) 0.08f else 0f), forma)
            .border(
                width = if (seleccionado) 1.5.dp else 0.75.dp,
                color = OnSurface.copy(alpha = if (seleccionado) 0.75f else 0.16f),
                shape = forma
            )
            .padding(horizontal = 9.dp)
    ) {
        // El color identifica la línea sin teñir todo el vehículo.
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(19.dp)
                .clip(CircleShape)
                .background(color)
        )
        Box(modifier = Modifier.size(23.dp), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.DirectionsBus, null,
                tint = OnSurface, modifier = Modifier.size(18.dp)
            )
        }
        Text(
            linea,
            style = BodyXs.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
            color = OnSurface,
            maxLines = 1
        )
        // Flechita de rumbo (solo si el rumbo es conocido).
        if (heading.isFinite() && heading >= 0) {
            Icon(
                Icons.Filled.Navigation, null,
                tint = OnSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier
                    .size(10.dp)
                    .graphicsLayer { rotationZ = heading.toFloat() }
            )
        }
    }
}

// ---- Bus en 3D con su etiqueta encima (marcador del mapa) ----
// Puerto de `BusMarker3D` (MapMarkers.swift, rama 3D-BUS).

/// Alto de la etiqueta sobre el bus.
val AltoEtiquetaBus = 36.dp
/// Alto del modelo 3D: 56 dp (= 168 px a 3x, el lado del fotograma de la
/// tira). Por debajo se convierte en una mancha y no compensa el coste.
val AltoModeloBus = 56.dp
/// Altura total del marcador: etiqueta + modelo.
val AltoMarcadorBus = AltoEtiquetaBus + AltoModeloBus

/// Punto (fracción 0..1) del marcador que se clava en la coordenada.
/// El marcador completo es más alto que el bus solo, así que no se centra
/// todo el bloque: el punto que coincide con la posición real es el CENTRO
/// VERTICAL DEL MODELO. iOS: UnitPoint(x: 0.5, y: (36 + 56/2) / 92).
val BusMarkerAncla = Offset(
    x = 0.5f,
    y = (36f + 56f / 2f) / (36f + 56f) // = 64/92 ≈ 0.6957
)

@Composable
fun BusMarker3D(
    linea: String,
    color: Color,
    heading: Double,
    seleccionado: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        EtiquetaBus(linea = linea, color = color, heading = heading, seleccionado = seleccionado)
        BusEn3D(rumbo = heading, lado = AltoModeloBus, seleccionado = seleccionado)
    }
}

// Marcador del usuario
@Composable
fun PulsingUserMarker() {
    val infiniteTransition = rememberInfiniteTransition(label = "user_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(SecondaryContainer.copy(alpha = 0.35f))
        )
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Secondary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.DirectionsWalk,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(10.dp)
            )
        }
    }
}

// Marcador de utp
@Composable
fun MarcadorUTP() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(AppPrimary)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text("UTP Trujillo", style = LabelCapsSm, color = Color.White)
        }
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(AppPrimary),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.School, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}