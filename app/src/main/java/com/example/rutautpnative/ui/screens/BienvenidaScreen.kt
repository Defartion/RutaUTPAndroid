package com.example.rutautpnative.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.navigation.AppRouter
import com.example.rutautpnative.navigation.AppScreen
import com.example.rutautpnative.data.senias.SeniasOverlay
import com.example.rutautpnative.data.senias.SeniasPrefs
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun BienvenidaScreen(router: AppRouter) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(PrimaryFixed.copy(alpha = 0.6f), AppPrimary.copy(alpha = 0.6f))
                        )
                    )
            )
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(AppBackground)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ruta UTP Trujillo",
                    style = HeadlineLg,
                    color = AppPrimary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { router.navigate(AppScreen.MapaPrincipal) }) {
                    Text(L.t("Saltar", "Skip"), style = BodySm, color = OnSurfaceVariant)
                }
            }
            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Llegando card
                LlegandoCard()
                Spacer(modifier = Modifier.height(20.dp))

                // Bus image placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(290.dp)
                        .clip(RoundedCornerShape(34.dp))
                        .background(Color.Black)
                        .shadow(22.dp, RoundedCornerShape(34.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DirectionsBus,
                        contentDescription = "Bus",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(120.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))

                // Hero text
                Text(
                    text = L.t("Llega a la UTP sin perderte", "Get to UTP without getting lost"),
                    style = DisplayLg,
                    color = OnSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = L.t(
                        "Encuentra la ruta exacta desde tu ubicación hasta el campus sin complicaciones.",
                        "Find the exact route from your location to campus without complications."
                    ),
                    style = BodyLg,
                    color = OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))

                // Page dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.width(40.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(AppPrimary))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.3f)))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.3f)))
                }
                Spacer(modifier = Modifier.height(24.dp))

                // Feature grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FeatureCard(
                        icon = Icons.Filled.Favorite,
                        iconColor = AppPrimary,
                        label = L.t("SEGURIDAD", "SAFETY"),
                        title = L.t("Rutas nocturnas monitoreadas.", "Night routes monitored."),
                        modifier = Modifier.weight(1f)
                    )
                    FeatureCard(
                        icon = Icons.Filled.Payments,
                        iconColor = Tertiary,
                        label = L.t("AHORRO", "SAVINGS"),
                        title = L.t("Precios de micros y combis actualizados.", "Up-to-date micro and combi fares."),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))

                // CTA Button (señable: con el Modo Señas activo, muestra la seña
                // en vez de navegar — el chip/botón recupera su acción al apagarlo).
                val modoSenias by SeniasPrefs.observarActivo().collectAsState(initial = false)
                val scope = rememberCoroutineScope()
                Button(
                    onClick = {
                        if (modoSenias) {
                            scope.launch { SeniasOverlay.mostrar("bienvenida.comenzar") }
                        } else {
                            router.navigate(AppScreen.MapaPrincipal)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(62.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
                ) {
                    Text(L.t("Comenzar", "Get started"), style = DisplayLgPhone, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Color.White)
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Legal footer
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = OnSurfaceVariant)) { append("Al continuar, aceptas nuestros ") }
                        withStyle(SpanStyle(color = AppPrimary, textDecoration = TextDecoration.Underline)) {
                            append("Términos de Servicio")
                        }
                    },
                    style = BodySm,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun LlegandoCard() {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AppPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.DirectionsBus,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "LLEGANDO EN",
                    style = LabelCapsSm,
                    color = OnSurfaceVariant
                )
                Text(
                    text = "3 min",
                    style = DisplayNumberLg,
                    color = AppPrimary
                )
            }
        }
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    title: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        modifier = modifier.heightIn(min = 168.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.Top
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = label, style = LabelCapsMd, color = OnSurface)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, style = BodyLg, color = OnSurfaceVariant)
        }
    }
}