package com.example.rutautpnative.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Smartphone
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

//----Bienvenida (puerto del BienvenidaView.swift)----
// Carrusel de 3 páginas: la clásica (LLEGANDO EN · hero · features) y las dos
// de novedades ("Tu micro, mejor identificado" y "Tus paraderos"). Puntos
// tocables ("Página N de 3"), CTA señable y pie legal que abre el LegalSheet
// (Términos/Privacidad, adaptado de Apple Maps a Google Maps).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BienvenidaScreen(router: AppRouter) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var mostrarLegal by remember { mutableStateOf(false) }

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
                TextButton(onClick = {
                    com.example.rutautpnative.data.senias.SeniasOverlay.ejecutarTrasVerSenia("bienvenida.saltar") {
                        router.navigate(AppScreen.MapaPrincipal)
                    }
                }) {
                    Text(L.t("Saltar", "Skip"), style = BodySm, color = OnSurfaceVariant)
                }
            }

            // Carrusel de páginas
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { pagina ->
                when (pagina) {
                    0 -> PaginaClasica()
                    1 -> PaginaNovedad(
                        icono = Icons.Filled.DirectionsBus,
                        badge = L.t("NUEVO · TU VIAJE", "NEW · YOUR TRIP"),
                        titulo = L.t("Tu micro, mejor identificado", "Help identify your bus"),
                        descripcion = L.t(
                            "Ahora puedes indicar qué línea tomaste y cómo va de llena, desde el mapa.",
                            "You can now choose your bus line and report how full it is, right from the map."
                        ),
                        highlights = listOf(
                            Icons.Filled.Place to L.t(
                                "Busca tu ruta y confirma «Sí, ya subí» cuando estés en el micro.",
                                "Find your route and confirm 'Yes, I'm on board' once you've boarded."
                            ),
                            Icons.Filled.Groups to L.t(
                                "Elige la línea y, si quieres, indica si va vacío, con espacio o lleno.",
                                "Choose your line and optionally report empty, room available or full."
                            ),
                            Icons.Filled.CheckCircle to L.t(
                                "La app comprueba el viaje; al bajar, toca «Ya bajé».",
                                "The app checks your trip; tap 'I've got off' when you leave."
                            )
                        )
                    )
                    else -> PaginaNovedad(
                        icono = Icons.Filled.Place,
                        badge = L.t("NUEVO · TUS PARADEROS", "NEW · YOUR STOPS"),
                        titulo = L.t("Guarda los puntos que te sirvan", "Keep the stops you need"),
                        descripcion = L.t(
                            "Encuentra tus paraderos guardados en Seguridad, antes de Comunidad.",
                            "Find your saved stops in Safety, before Community."
                        ),
                        highlights = listOf(
                            Icons.Filled.Bookmark to L.t(
                                "Guarda un paradero desde «Paraderos y referencias».",
                                "Save a stop from 'Stops and landmarks'."
                            ),
                            Icons.Filled.Favorite to L.t(
                                "En tu viaje puedes marcar dónde subiste o escribir una referencia. Es opcional.",
                                "During trip setup, optionally mark where you boarded or write a landmark."
                            ),
                            Icons.Filled.Smartphone to L.t(
                                "Las referencias de subida se guardan en tu teléfono para futuros paraderos de alumnos.",
                                "Boarding references stay on your phone for future student stops."
                            )
                        )
                    )
                }
            }

            // Puntos + CTA + pie legal (fijos bajo el carrusel).
            // navigationBarsPadding: con edge-to-edge, la barra de navegacion
            // del sistema (botones retroceder/inicio/recientes o el gestual)
            // se dibuja ENCIMA del fondo de la app; sin este inset, la segunda
            // linea del pie legal quedaba detras de los botones.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Puntos tocables: "Página N de 3".
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { indice ->
                        val activa = pagerState.currentPage == indice
                        val ancho by animateDpAsState(
                            if (activa) 36.dp else 8.dp, tween(250), label = "dot$indice"
                        )
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clickable {
                                    scope.launch { pagerState.animateScrollToPage(indice) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(ancho)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (activa) PrimaryFill else Color.Gray.copy(alpha = 0.3f))
                            )
                        }
                    }
                }
                Text(
                    L.t("Desliza para conocer las novedades", "Swipe to discover what's new"),
                    style = BodyXs, color = OnSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))

                // CTA señable (Fase 6): con el Modo Señas activo, muestra la
                // tarjeta 3 s y LUEGO navega; con el modo apagado, directo.
                Button(
                    onClick = {
                        com.example.rutautpnative.data.senias.SeniasOverlay.ejecutarTrasVerSenia("bienvenida.comenzar") {
                            router.navigate(AppScreen.MapaPrincipal)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(62.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
                ) {
                    Text(L.t("Comenzar", "Get started"), style = DisplayLgPhone, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.height(12.dp))

                // Pie legal: SOLO "Términos de Servicio" abre el LegalSheet.
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = OnSurfaceVariant)) {
                            append(L.t("Al continuar, aceptas nuestros ", "By continuing, you accept our "))
                        }
                        withStyle(SpanStyle(color = AppPrimary, textDecoration = TextDecoration.Underline)) {
                            append(L.t("Términos de Servicio", "Terms of Service"))
                        }
                    },
                    style = BodySm,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .clickable { mostrarLegal = true }
                )
            }
        }
    }

    //----LegalSheet (Términos + Privacidad; adaptado a Google Maps)----
    if (mostrarLegal) {
        ModalBottomSheet(onDismissRequest = { mostrarLegal = false }, containerColor = AppSurface) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(L.t("Términos de Servicio", "Terms of Service"), style = HeadlineMd, color = OnSurface, modifier = Modifier.weight(1f))
                    TextButton(onClick = { mostrarLegal = false }) { Text(L.t("Cerrar", "Close")) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    L.t(
                        "Ruta UTP Trujillo es una aplicación prototipo que facilita la orientación de transporte público hacia el campus de la Universidad Tecnológica del Perú (sede Trujillo). Al usar esta app aceptas las condiciones aquí descritas.",
                        "Ruta UTP Trujillo is a prototype app that helps you navigate public transport to the Universidad Tecnológica del Perú campus (Trujillo). By using this app you accept the conditions described here."
                    ),
                    style = BodyMd, color = OnSurface
                )
                Spacer(Modifier.height(14.dp))
                Text(L.t("Privacidad", "Privacy"), style = HeadlineSm, color = OnSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    L.t(
                        "Si autorizas el acceso, la app utiliza tu ubicación real para mostrar dónde estás, buscar paraderos cercanos y seguir tu avance durante un viaje. Las posiciones de los buses que aparecen en el mapa son simuladas.",
                        "If you grant permission, the app uses your real location to show where you are, find nearby stops and track your progress during a trip. Bus positions shown on the map are simulated."
                    ),
                    style = BodyMd, color = OnSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    L.t(
                        "La búsqueda de lugares y el cálculo de indicaciones utilizan servicios de Google Maps. Para resolver esas consultas se envían a Google los textos de búsqueda, direcciones o coordenadas necesarios, que pueden incluir tu ubicación como punto de partida.",
                        "Place searches and directions use Google Maps services. These requests send Google the necessary search text, addresses or coordinates, which may include your location as the starting point."
                    ),
                    style = BodyMd, color = OnSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    L.t(
                        "Los datos personales que introduces, tus lugares y líneas guardados, las fotos de perfil y carné y las referencias de tarjetas se guardan localmente. El monedero usa saldo de demostración y no procesa pagos reales. Los reportes y publicaciones comunitarias de esta versión no se envían a un servidor ni se comparten con otros usuarios.",
                        "The personal details you enter, saved places and routes, profile and student card photos, and card references are stored locally. The wallet uses a demo balance and does not process real payments. Reports and community posts in this version are not sent to a server or shared with other users."
                    ),
                    style = BodyMd, color = OnSurface
                )
            }
        }
    }
}

//----Página 0: la clásica (LLEGANDO · bus · hero · features)----
@Composable
private fun PaginaClasica() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        LlegandoCard()
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
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
        Spacer(modifier = Modifier.height(20.dp))
    }
}

//----Páginas 1 y 2: novedades (newsPage del iOS)----
@Composable
private fun PaginaNovedad(
    icono: ImageVector,
    badge: String,
    titulo: String,
    descripcion: String,
    highlights: List<Pair<ImageVector, String>>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        // Hero iconico de la novedad (degradado + anillos, como el iOS).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.linearGradient(
                        listOf(AppPrimary.copy(alpha = 0.16f), SurfaceContainerLowest)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .border(1.dp, AppPrimary.copy(alpha = 0.15f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .clip(CircleShape)
                    .background(AppPrimary.copy(alpha = 0.1f))
            )
            Icon(icono, null, tint = AppPrimary, modifier = Modifier.size(58.dp))
        }
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(AppPrimary.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(badge, style = BodyXsMedium, color = AppPrimary)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            titulo,
            style = DisplayLg,
            color = OnSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            descripcion,
            style = BodyLg,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(20.dp))
        // Highlights.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(SurfaceContainerLowest)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            highlights.forEach { (iconoItem, texto) ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(iconoItem, null, tint = AppPrimary, modifier = Modifier.size(20.dp))
                    Text(texto, style = BodyMd, color = OnSurface)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
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
                    text = L.t("LLEGANDO EN", "ARRIVING IN"),
                    style = LabelCapsSm,
                    color = OnSurfaceVariant
                )
                Text(
                    text = L.t("3 min", "3 min"),
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
