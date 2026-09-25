package com.example.rutautpnative.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

//----Colores de marca (CONSTANTES: iguales en claro y oscuro)----
val AppPrimary            = Color(0xFFa80033)
val PrimaryContainer      = Color(0xFFd31245)
val OnPrimary             = Color.White
val OnPrimaryContainer    = Color(0xFFffe8e8)
val PrimaryFixed          = Color(0xFFffdadb)
val PrimaryFixedDim       = Color(0xFFffb2b7)
val InversePrimary        = Color(0xFFffb2b7)

//----Colores Secundarios----
val Secondary             = Color(0xFF3c5d9c)
val SecondaryContainer    = Color(0xFF99b8fe)
val OnSecondary           = Color.White
val OnSecondaryContainer  = Color(0xFF244885)

//----Colores Terciarios----
val Tertiary              = Color(0xFF005b6e)
val TertiaryContainer     = Color(0xFF00758d)
val OnTertiary            = Color.White
val OnTertiaryContainer   = Color(0xFFd1f2ff)
val TertiaryFixed         = Color(0xFFb3ebff)
val TertiaryFixedDim      = Color(0xFF4cd6fb)

//----Colores adaptativos (claro/oscuro)----
// Estos son los que leía la UI suelta por todo el proyecto con sus nombres
// "flat" (AppBackground, OnSurface...). Para que el cambio de tema reaccione
// al instante sin migrar cientos de call sites a MaterialTheme.colorScheme,
// son vars respaldados por MutableState: Theme.kt los reasigna al cambiar
// isDarkMode y Compose recompone todo lo que los leyó. Cada uno tiene su par
// claro (sufijo _L, los hex originales) y oscuro (sufijo _D, del diseño iOS).
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
private val OnSurfaceVariant_L = Color(0xFF5c3f41); private val OnSurfaceVariant_D = Color(0xFFc9adaf)
private val InverseSurface_L = Color(0xFF2d3133); private val InverseSurface_D = Color(0xFFe3e6e8)
private val InverseOnSurface_L = Color(0xFFeff1f3); private val InverseOnSurface_D = Color(0xFF1c2022)
private val Outline_L = Color(0xFF906f70);        private val Outline_D = Color(0xFFa98b8c)
private val OutlineVariant_L = Color(0xFFe4bdbf); private val OutlineVariant_D = Color(0xFF4a3f40)
private val AppError_L = Color(0xFFba1a1a);       private val AppError_D = Color(0xFFff6b6b)
private val ErrorContainer_L = Color(0xFFffdad6); private val ErrorContainer_D = Color(0xFF5c2224)
private val OnErrorContainer_L = Color(0xFF93000a); private val OnErrorContainer_D = Color(0xFFffd7d3)

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
var OnSurfaceVariant         by mutableStateOf(OnSurfaceVariant_L); private set
var InverseSurface           by mutableStateOf(InverseSurface_L);   private set
var InverseOnSurface         by mutableStateOf(InverseOnSurface_L); private set
var Outline                  by mutableStateOf(Outline_L);          private set
var OutlineVariant           by mutableStateOf(OutlineVariant_L);   private set
var AppError                 by mutableStateOf(AppError_L);         private set
var ErrorContainer           by mutableStateOf(ErrorContainer_L);   private set
var OnErrorContainer         by mutableStateOf(OnErrorContainer_L); private set

//----Reasignación al cambiar de tema (llamada desde Theme.kt)----
fun aplicarTemaOscuro(oscuro: Boolean) {
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
    OnSurfaceVariant        = if (oscuro) OnSurfaceVariant_D        else OnSurfaceVariant_L
    InverseSurface          = if (oscuro) InverseSurface_D          else InverseSurface_L
    InverseOnSurface        = if (oscuro) InverseOnSurface_D        else InverseOnSurface_L
    Outline                 = if (oscuro) Outline_D                 else Outline_L
    OutlineVariant          = if (oscuro) OutlineVariant_D          else OutlineVariant_L
    AppError                = if (oscuro) AppError_D                else AppError_L
    ErrorContainer          = if (oscuro) ErrorContainer_D          else ErrorContainer_L
    OnErrorContainer        = if (oscuro) OnErrorContainer_D        else OnErrorContainer_L
}
