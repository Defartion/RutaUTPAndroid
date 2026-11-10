package com.example.rutautpnative.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rutautpnative.data.gtfs.TransporteApp
import com.example.rutautpnative.data.imagenes.CarnetImageStore
import com.example.rutautpnative.data.imagenes.ProfileImageStore
import com.example.rutautpnative.data.negocios.CuponesStore
import com.example.rutautpnative.data.negocios.NegociosService
import com.example.rutautpnative.data.PreferenciasPerfilStore
import com.example.rutautpnative.data.CarnetPerfil
import com.example.rutautpnative.data.PreferenciasVisualesPerfilStore
import com.example.rutautpnative.data.senias.SeniasPrefs
import com.example.rutautpnative.model.CategoriaNegocio
import com.example.rutautpnative.model.Negocio
import com.example.rutautpnative.navigation.AppRouter
import com.example.rutautpnative.navigation.AppScreen
import com.example.rutautpnative.ui.components.BottomNavBar
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.components.cuponVigente
import com.example.rutautpnative.ui.components.formatoVenceCupon
import com.example.rutautpnative.ui.components.iconoParaCategoria
import com.example.rutautpnative.ui.theme.*
import com.example.rutautpnative.ui.theme.TematicaEmpresa
import com.example.rutautpnative.ui.theme.TematicaEmpresaStore
import kotlinx.coroutines.launch

@Composable
private fun PerfilCuponCard(negocio: Negocio, onQuitar: () -> Unit, onVerPromocion: () -> Unit) {
    val cupon = negocio.cupon ?: return
    val vigente = cuponVigente(cupon.vence)
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copiado by remember { mutableStateOf(false) }

    // La confirmación "Código copiado" dura 2.5 s y luego se revierte sola.
    LaunchedEffect(copiado) {
        if (copiado) { kotlinx.coroutines.delay(2500); copiado = false }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        modifier = Modifier.width(285.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            //----Encabezado: ícono de categoría + nombre + categoría + chevron----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(negocio.categoria.color.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(iconoParaCategoria(negocio.categoria), null, tint = negocio.categoria.color, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(negocio.nombre, style = BodyMdMedium, color = OnSurface, maxLines = 1)
                    Text(negocio.categoria.label, style = BodySm, color = OnSurfaceVariant)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(10.dp))

            //----Detalle del cupón----
            Text(cupon.detalle.texto(), style = BodySm, color = OnSurface)
            Spacer(Modifier.height(8.dp))

            //----Estado + tipo demo----
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (vigente) SecondaryContainer else ErrorContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                        Text(
                            if (vigente) L.t("Guardado", "Saved") else L.t("Vencido", "Expired"),
                            style = LabelCapsSm,
                        color = if (vigente) OnSecondaryContainer else OnErrorContainer
                    )
                }
                Text(L.t("Cupón demo", "Demo coupon"), style = LabelCapsSm, color = OnSurfaceVariant)
            }
            if (!cupon.vence.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(L.t("Vence: ", "Expires: ") + formatoVenceCupon(cupon.vence), style = BodySm, color = OnSurfaceVariant)
            }

            Spacer(Modifier.height(10.dp))
            Divider()
            Spacer(Modifier.height(10.dp))

            //----Código + copiar----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    cupon.codigo,
                    style = BodyMdMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp),
                    color = if (vigente) OnSurface else OnSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(cupon.codigo))
                        copiado = true
                    },
                    enabled = vigente
                ) {
                    Icon(
                        if (copiado) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        "Copiar código",
                        tint = when {
                            copiado -> Tertiary
                            vigente -> AppPrimary
                            else -> OnSurfaceVariant.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (copiado) {
                Text(L.t("Código copiado", "Code copied"), style = BodyXs, color = Tertiary)
            }
            Spacer(Modifier.height(4.dp))

            //----Fila inferior: ver promoción + quitar----
            Row(verticalAlignment = Alignment.CenterVertically) {
                // "Ver promoción": abre el detalle del negocio (NegocioDetailCard
                // de NegociosDemoComponents) en un sheet.
                Text(
                    L.t("Ver promoción", "View promotion"),
                    style = BodySm, color = AppPrimary,
                    modifier = Modifier.clickable { onVerPromocion() }
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onQuitar) {
                    Icon(Icons.Filled.BookmarkRemove, "Quitar cupón", tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

//----Sección "Mis cupones"----
// Los cupones guardados sí persisten (DataStore); aquí solo se consumen.
@Composable
internal fun MisCuponesSection(
    cupones: List<Negocio>,
    onExplorar: () -> Unit,
    onQuitar: (Negocio) -> Unit,
    onVerPromocion: (Negocio) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ConfirmationNumber, null, tint = AppPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(L.t("Mis cupones", "My coupons"), style = HeadlineSm, color = OnSurface)
            Spacer(Modifier.width(8.dp))
            // Badge con el total.
            Box(
                modifier = Modifier.clip(CircleShape).background(PrimaryContainer.copy(alpha = 0.35f)).padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("${cupones.size}", style = LabelCapsSm, color = AppPrimary)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(L.t("Tus promociones guardadas en Tracking Demo.", "Your promotions saved in Tracking Demo."), style = BodySm, color = OnSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        if (cupones.isEmpty()) {
            //----Estado vacío----
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                ) {
                    Icon(Icons.Filled.ConfirmationNumber, null, tint = OnSurfaceVariant, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(L.t("Tu próxima promo te espera", "Your next promo awaits"), style = BodyMdMedium, color = OnSurface)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        L.t("Abre un negocio en el mapa y toca Guardar en su cupón. Aparecerá aquí.",
                            "Open a business on the map and tap Save on its coupon. It will show up here."),
                        style = BodySm,
                        color = OnSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onExplorar,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
                    ) {
                        Text(L.t("Explorar negocios", "Explore businesses"), style = BodyMdMedium, color = Color.White)
                    }
                }
            }
        } else {
            //----Scroll horizontal de tarjetas (Paso 2)----
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(cupones.size) { i ->
                    PerfilCuponCard(cupones[i], onQuitar = { onQuitar(cupones[i]) }, onVerPromocion = { onVerPromocion(cupones[i]) })
                }
            }
        }
    }
}