package com.example.rutautpnative.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

//----Esquema claro (marca igual en ambos modos; adaptativos versión clara)----
private val AppColorScheme = lightColorScheme(
    primary           = AppPrimary,
    onPrimary         = OnPrimary,
    primaryContainer  = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary         = Secondary,
    onSecondary       = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary          = Tertiary,
    onTertiary        = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background        = Color(0xFFf7f9fb),
    onBackground      = Color(0xFF191c1e),
    surface           = Color(0xFFf7f9fb),
    onSurface         = Color(0xFF191c1e),
    onSurfaceVariant  = Color(0xFF5c3f41),
    outline           = Color(0xFF906f70),
    outlineVariant    = Color(0xFFe4bdbf),
    error             = Color(0xFFba1a1a),
    errorContainer    = Color(0xFFffdad6),
    onErrorContainer  = Color(0xFF93000a),
)

//----Esquema oscuro (marca idéntica; adaptativos versión oscura)----
private val AppColorSchemeDark = darkColorScheme(
    primary           = AppPrimary,
    onPrimary         = OnPrimary,
    primaryContainer  = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary         = Secondary,
    onSecondary       = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary          = Tertiary,
    onTertiary        = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background        = Color(0xFF101314),
    onBackground      = Color(0xFFe4e8ea),
    surface           = Color(0xFF141719),
    onSurface         = Color(0xFFe4e8ea),
    onSurfaceVariant  = Color(0xFFc9adaf),
    outline           = Color(0xFFa98b8c),
    outlineVariant    = Color(0xFF4a3f40),
    error             = Color(0xFFff6b6b),
    errorContainer    = Color(0xFF5c2224),
    onErrorContainer  = Color(0xFFffd7d3),
)

private val AppTypography = Typography(
    bodyLarge  = BodyLg,
    bodyMedium = BodyMd,
    bodySmall  = BodySm,
    titleLarge  = HeadlineLg,
    titleMedium = HeadlineMd,
    titleSmall  = HeadlineSm,
    labelLarge  = LabelCapsLg,
    labelMedium = LabelCapsMd,
    labelSmall  = LabelCapsSm,
)

//----Tema raíz----
// modoOscuro viene de la preferencia persistida (interruptor manual en Perfil,
// no sigue el tema del sistema — igual que iOS).
// Al cambiarlo: MaterialTheme cambia su colorScheme Y las vars adaptativas de
// Color.kt se reasignan (Color.kt usa MutableState), así que TODA la UI
// recompone al instante — incluyendo Dialogs/ModalBottomSheets, que son
// ventanas aparte pero leen los mismos vals durante su composición.
@Composable
fun RutaUTPNativeTheme(
    modoOscuro: Boolean = false,
    content: @Composable () -> Unit
) {
    LaunchedEffect(modoOscuro) { aplicarTemaOscuro(modoOscuro) }

    // STATUS BAR + NAV BAR: iconos claros en oscuro, oscuros en claro.
    // Equivalente del overrideUserInterfaceStyle del iOS: con edge-to-edge,
    // el contenido se extiende detras de las barras, pero los ICONOS de la
    // status bar (hora, camara, senal) deben cambiar de color segun el tema
    // — sin esto quedan oscuros sobre fondo oscuro (o claros sobre claro).
    val view = LocalView.current
    LaunchedEffect(modoOscuro) {
        val ventana = (view.context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(ventana, view).apply {
            isAppearanceLightStatusBars = !modoOscuro
            isAppearanceLightNavigationBars = !modoOscuro
        }
    }

    MaterialTheme(
        colorScheme = if (modoOscuro) AppColorSchemeDark else AppColorScheme,
        typography  = AppTypography,
        content     = content
    )
}
