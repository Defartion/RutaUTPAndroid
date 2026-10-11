package com.example.rutautpnative.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

//----Colores de marca (RESUELTOS desde la temática activa)----
// Desde la Fase de temáticas son vars respaldados por MutableState, como los
// adaptativos: `aplicarTemaOscuro` los reasigna con la paleta de la empresa
// seleccionada (PaletasEmpresa.actual) y Compose recompone todo lo que los
// leyó — incluidos Dialogs/ModalBottomSheets. Con la temática UTP reproducen
// literalmente los valores originales.
var AppPrimary            by mutableStateOf(Color(0xFFa80033)); private set
var PrimaryContainer      by mutableStateOf(Color(0xFFd31245)); private set
var OnPrimary             = Color.White
var OnPrimaryContainer    by mutableStateOf(Color(0xFFffe8e8)); private set
var PrimaryFixed          by mutableStateOf(Color(0xFFffdadb)); private set
var PrimaryFixedDim       = Color(0xFFffb2b7)
var InversePrimary        by mutableStateOf(Color(0xFFffb2b7)); private set

// Tokens de la paleta con rol propio (primaryFill / onPrimaryFill / gradiente
// del hero / color de marca para discos y acentos).
var ColorMarca            by mutableStateOf(Color(0xFFa80033)); private set
var PrimaryFill           by mutableStateOf(Color(0xFFa80033)); private set
var OnPrimaryFill         by mutableStateOf(Color.White); private set
var PrimaryGradientEnd    by mutableStateOf(Color(0xFF005b6e)); private set

//----Colores Secundarios----
var Secondary             by mutableStateOf(Color(0xFF3c5d9c)); private set
var SecondaryContainer    by mutableStateOf(Color(0xFF99b8fe)); private set
var OnSecondary           = Color.White
var OnSecondaryContainer  by mutableStateOf(Color(0xFF244885)); private set

//----Colores Terciarios----
var Tertiary              by mutableStateOf(Color(0xFF005b6e)); private set
var TertiaryContainer     by mutableStateOf(Color(0xFF00758d)); private set
var OnTertiary            = Color.White
var OnTertiaryContainer   by mutableStateOf(Color(0xFFd1f2ff)); private set
var TertiaryFixed         = Color(0xFFb3ebff)
var TertiaryFixedDim      = Color(0xFF4cd6fb)

//----Colores adaptativos (claro/oscuro)----
// Superficies neutras, error e inversos: SIEMPRE los pares claro/oscuro del
// diseño original (las paletas de empresa solo toman marca y roles de acción).
// Son vars respaldados por MutableState: Theme.kt los reasigna al cambiar
// isDarkMode y Compose recompone todo lo que los leyó.
private val AppBackground_L = Color(0xFFf7f9fb);  private val AppBackground_D = Color(0xFF101314)
private val AppSurface_L    = Color(0xFFf7f9fb);  private val AppSurface_D    = Color(0xFF141719)
private val SurfaceContainer_L = Color(0xFFeceef0); private val SurfaceContainer_D = Color(0xFF1e2225)
private val SurfaceContainerLow_L = Color(0xFFf2f4f6); private val SurfaceContainerLow_D = Color(0xFF1a1d20)
private val SurfaceContainerHigh_L = Color(0xFFe6e8ea); private val SurfaceContainerHigh_D = Color(0xFF26292c)
private val SurfaceContainerHighest_L = Color(0xFFe0e3e5); private val SurfaceContainerHighest_D = Color(0xFF313537)
private val SurfaceContainerLowest_L = Color(0xFFffffff); private val SurfaceContainerLowest_D = Color(0xFF0b0d0e)
private val SurfaceDim_L = Color(0xFFd8dadc);    private val SurfaceDim_D = Color(0xFF3c4143)
private val SurfaceBright_L = Color(0xFFf7f9fb); private val SurfaceBright_D = Color(0xFF2a2e30)
private val SurfaceVariant_L = Color(0xFFe4bdbf); private val SurfaceVariant_D = Color(0xFF4a3537)
private val OnSurface_L = Color(0xFF191c1e);     private val OnSurface_D = Color(0xFFE4e8ea)
private val InverseSurface_L = Color(0xFF2d3133); private val InverseSurface_D = Color(0xFFe3e6e8)
private val InverseOnSurface_L = Color(0xFFeff1f3); private val InverseOnSurface_D = Color(0xFF1c2022)
private val AppError_L = Color(0xFFba1a1a);       private val AppError_D = Color(0xFFff6b6b)
private val ErrorContainer_L = Color(0xFFffdad6); private val ErrorContainer_D = Color(0xFF5c2224)
private val OnErrorContainer_L = Color(0xFF93000a); private val OnErrorContainer_D = Color(0xFFffd7d3)

// onSurfaceVariant/outline/outlineVariant pasan a depender de la PALETA
// (cada temática define los suyos); con UTP son exactamente los originales.

//----Vals que lee toda la app (se reasignan al cambiar el tema)----
var AppBackground            by mutableStateOf(AppBackground_L);    private set
var AppSurface               by mutableStateOf(AppSurface_L);       private set
var SurfaceContainer         by mutableStateOf(SurfaceContainer_L); private set
var SurfaceContainerLow      by mutableStateOf(SurfaceContainerLow_L); private set
var SurfaceContainerHigh     by mutableStateOf(SurfaceContainerHigh_L); private set
var SurfaceContainerHighest  by mutableStateOf(SurfaceContainerHighest_L); private set
var SurfaceContainerLowest   by mutableStateOf(SurfaceContainerLowest_L); private set
var SurfaceDim               by mutableStateOf(SurfaceDim_L);       private set
var SurfaceBright            by mutableStateOf(SurfaceBright_L);    private set
var SurfaceVariant           by mutableStateOf(SurfaceVariant_L);   private set
var OnSurface                by mutableStateOf(OnSurface_L);        private set
var OnSurfaceVariant         by mutableStateOf(Color(0xFF5c3f41));  private set
var InverseSurface           by mutableStateOf(InverseSurface_L);   private set
var InverseOnSurface         by mutableStateOf(InverseOnSurface_L); private set
var Outline                  by mutableStateOf(Color(0xFF906f70)); private set
var OutlineVariant           by mutableStateOf(Color(0xFFe4bdbf)); private set
var AppError                 by mutableStateOf(AppError_L);         private set
var ErrorContainer           by mutableStateOf(ErrorContainer_L);   private set
var OnErrorContainer         by mutableStateOf(OnErrorContainer_L); private set

//----Reasignación al cambiar de tema o de temática----
// Llamada desde Theme.kt (cambio claro/oscuro) y desde TematicaEmpresaStore
// (cambio de empresa). Resuelve la paleta activa y asigna marca + roles de
// acción; las superficies neutras conservan sus pares claro/oscuro.
fun aplicarTemaOscuro(oscuro: Boolean) {
    val paleta = PaletasEmpresa.actual

    // Marca y roles de acción de la temática.
    ColorMarca            = paleta.colorMarca
    AppPrimary            = paleta.appPrimary.en(oscuro)
    PrimaryFill           = paleta.primaryFill
    OnPrimaryFill         = paleta.onPrimaryFill
    PrimaryGradientEnd    = paleta.primaryGradientEnd
    PrimaryContainer      = paleta.primaryContainer
    OnPrimaryContainer    = paleta.onPrimaryContainer
    PrimaryFixed          = paleta.primaryFixed
    InversePrimary        = paleta.inversePrimary.en(oscuro)
    Secondary             = paleta.secondary
    SecondaryContainer    = paleta.secondaryContainer.en(oscuro)
    OnSecondaryContainer  = paleta.onSecondaryContainer.en(oscuro)
    Tertiary              = paleta.tertiary
    TertiaryContainer     = paleta.tertiaryContainer
    OnTertiaryContainer   = paleta.onTertiaryContainer
    OnSurfaceVariant      = paleta.onSurfaceVariant.en(oscuro)
    Outline               = paleta.outline.en(oscuro)
    OutlineVariant        = paleta.outlineVariant.en(oscuro)

    // Superficies neutras (pares fijos del diseño).
    AppBackground           = if (oscuro) AppBackground_D           else AppBackground_L
    AppSurface              = if (oscuro) AppSurface_D              else AppSurface_L
    SurfaceContainer        = if (oscuro) SurfaceContainer_D        else SurfaceContainer_L
    SurfaceContainerLow     = if (oscuro) SurfaceContainerLow_D     else SurfaceContainerLow_L
    SurfaceContainerHigh    = if (oscuro) SurfaceContainerHigh_D    else SurfaceContainerHigh_L
    SurfaceContainerHighest = if (oscuro) SurfaceContainerHighest_D else SurfaceContainerHighest_L
    SurfaceContainerLowest  = if (oscuro) SurfaceContainerLowest_D  else SurfaceContainerLowest_L
    SurfaceDim              = if (oscuro) SurfaceDim_D              else SurfaceDim_L
    SurfaceBright           = if (oscuro) SurfaceBright_D           else SurfaceBright_L
    SurfaceVariant          = if (oscuro) SurfaceVariant_D          else SurfaceVariant_L
    OnSurface               = if (oscuro) OnSurface_D               else OnSurface_L
    InverseSurface          = if (oscuro) InverseSurface_D          else InverseSurface_L
    InverseOnSurface        = if (oscuro) InverseOnSurface_D        else InverseOnSurface_L
    AppError                = if (oscuro) AppError_D                else AppError_L
    ErrorContainer          = if (oscuro) ErrorContainer_D          else ErrorContainer_L
    OnErrorContainer        = if (oscuro) OnErrorContainer_D        else OnErrorContainer_L
}
