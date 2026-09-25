package com.example.rutautpnative.ui.theme

// TODO(tema-oscuro): el tema oscuro no está implementado; hoy la app es solo clara.

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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
    background        = AppBackground,
    onBackground      = OnSurface,
    surface           = AppSurface,
    onSurface         = OnSurface,
    onSurfaceVariant  = OnSurfaceVariant,
    outline           = Outline,
    outlineVariant    = OutlineVariant,
    error             = AppError,
    errorContainer    = ErrorContainer,
    onErrorContainer  = OnErrorContainer,
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

@Composable
fun RutaUTPNativeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography  = AppTypography,
        content     = content
    )
}