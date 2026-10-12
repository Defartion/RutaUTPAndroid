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
fun PerfilScreen(router: AppRouter) {
    val contextoLocal = androidx.compose.ui.platform.LocalContext.current
    var nombre by remember { mutableStateOf("Joaquín Díaz") }
    // Notificaciones y compartir ubicación: persistidas (PreferenciasPerfilStore).
    val notifOn = PreferenciasPerfilStore.notificaciones
    val ubicacionOn = PreferenciasPerfilStore.compartirUbicacion
    var popupCompartirUbicacion by remember { mutableStateOf(false) }
    var ecoOff by remember { mutableStateOf(false) }
    // Modo Señas: ESTA preferencia SÍ persiste en disco (DataStore), a diferencia
    // de las demás de arriba, porque la leen todas las pantallas de la app.
    val modoSenias by SeniasPrefs.observarActivo().collectAsState(initial = false)
    // Idioma: la otra preferencia persistida de Perfil (ES por defecto, EN opcional).
    val esIngles = L.esIngles
    var showEditDialog by remember { mutableStateOf(false) }
    var newNameInput by remember { mutableStateOf("") }
    var showCarnetScanner by remember { mutableStateOf(false) }
    var showCarneDigital by remember { mutableStateOf(false) }
    // Hojas nuevas (Fase de perfil completo): datos personales, offline y
    // ayuda de accesibilidad.
    var showDatosPersonales by remember { mutableStateOf(false) }
    var showOffline by remember { mutableStateOf(false) }
    var showAccesibilidad by remember { mutableStateOf(false) }
    // Foto de perfil PERSISTIDA (ProfileImageStore): fuente única compartida
    // con el drawer, Datos Personales y el carné digital.
    val fotoPerfil by androidx.compose.runtime.produceState<Bitmap?>(initialValue = null, ProfileImageStore.revision) {
        value = ProfileImageStore.cargar(contextoLocal)
    }
    // Carné guardado: hay foto del carné para la EMPRESA activa (CarnetImageStore).
    val empresa = TematicaEmpresaStore.seleccion
    val carnetGuardado by androidx.compose.runtime.produceState(initialValue = false, CarnetImageStore.revision, empresa.id) {
        value = CarnetImageStore.existe(contextoLocal, empresa.id)
    }
    // Negocio cuyo detalle se ve desde un cupón ("Ver promoción").
    var negocioDetalle by remember { mutableStateOf<Negocio?>(null) }
    // Sheets del monedero (Fase 11) y de tarjetas.
    var showRecargar by remember { mutableStateOf(false) }
    var showQRPasaje by remember { mutableStateOf(false) }
    var showMetodosPago by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Cupones guardados: SÍ persisten (DataStore). Reactivo via Flow de ids,
    // resueltos contra el catálogo de NegociosService.
    val cuponesGuardados by produceState<List<Negocio>>(initialValue = emptyList()) {
        CuponesStore.observarIds().collect { ids ->
            value = NegociosService.todos()
                .filter { it.cupon != null && it.id in ids }
                .sortedBy { it.nombre }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Rol y degradado según la temática activa (PerfilView iOS):
            // "ESTUDIANTE UTP" o "COLABORADOR · [EMPRESA]".
            val rol = if (empresa == TematicaEmpresa.UTP)
                L.t("ESTUDIANTE UTP", "UTP STUDENT")
            else
                L.t("COLABORADOR · ", "EMPLOYEE · ") + empresa.nombre.uppercase()
            // Inkafarma: su gradiente termina en crema; se cierra en verde de
            // marca para que la tinta blanca siga siendo legible (adaptación:
            // el iOS resuelve la tinta por temática).
            val gradienteHero = when (empresa) {
                TematicaEmpresa.INKAFARMA -> Brush.linearGradient(listOf(AppPrimary, Color(0xFF0E5D2C)))
                else -> Brush.linearGradient(listOf(AppPrimary, PrimaryContainer, PrimaryGradientEnd))
            }
            // Hero gradient header: envuelve SOLO avatar + nombre + billetera
            // (wrapContentHeight, sin alto fijo). Como en iOS: termina ahí y todo
            // lo demás (Mis cupones, stats, preferencias) va afuera, sobre fondo normal.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(gradienteHero)
            ) {
                // Decorative circles
                Box(modifier = Modifier.size(220.dp).offset(x = 200.dp, y = (-70).dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)))
                Box(modifier = Modifier.size(150.dp).offset(x = (-50).dp, y = 50.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f)))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 56.dp, start = 20.dp, end = 20.dp, bottom = 28.dp)
                ) {
                    // Avatar + name
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(72.dp).clip(CircleShape).background(InversePrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            fotoPerfil?.let { foto ->
                                Image(
                                    bitmap = foto.asImageBitmap(),
                                    contentDescription = L.t("Foto de perfil", "Profile photo"),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } ?: run {
                                Text(iniciales(nombre), style = HeadlineMd, color = Color.White)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(nombre, style = HeadlineLg, color = Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.20f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text(rol, style = LabelCapsSm, color = Color.White.copy(alpha = 0.95f))
                            }
                            // "CARNÉ GUARDADO": hay foto del carné para la
                            // empresa activa y la tarjeta física es visible.
                            if (PreferenciasVisualesPerfilStore.visible(CarnetPerfil.FOTO, empresa) && carnetGuardado) {
                                Box(modifier = Modifier.clip(CircleShape).background(Tertiary).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Icon(Icons.Filled.Verified, null, tint = Color.White, modifier = Modifier.size(10.dp))
                                        Text(L.t("CARNÉ GUARDADO", "CARD SAVED"), style = LabelCapsSm, color = Color.White)
                                    }
                                }
                            }
                            }
                        }
                    }
                    Spacer(Modifier.height(22.dp))

                    // Seccion de billetera (puerto del PerfilView.swift):
                    // MonederoCard + carnets + acceso a "Mis tarjetas".
                    Text(L.t("MI BILLETERA", "MY WALLET"), style = LabelCapsSm, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(horizontal = 4.dp))
                    Spacer(Modifier.height(10.dp))
                    MonederoCard(
                        onRecargar = { showRecargar = true },
                        onPagarQR = { showQRPasaje = true }
                    )
                    Spacer(Modifier.height(12.dp))
                    // Tarjetas del carné segun las preferencias visuales de la
                    // empresa activa (PerfilPreferenciasVisuales iOS): ocultar
                    // una tarjeta NUNCA borra su foto.
                    val mostrarCarnetFoto = PreferenciasVisualesPerfilStore.visible(CarnetPerfil.FOTO, empresa)
                    val mostrarCarnetDigital = PreferenciasVisualesPerfilStore.visible(CarnetPerfil.DIGITAL, empresa)
                    if (mostrarCarnetFoto || mostrarCarnetDigital) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (mostrarCarnetFoto) {
                                WalletCard(
                                    icon = Icons.Filled.Badge,
                                    title = CarnetPerfil.FOTO.titulo(empresa),
                                    subtitle = if (carnetGuardado) L.t("Ver foto guardada", "View saved photo") else L.t("Añadir foto", "Add photo"),
                                    modifier = Modifier.weight(1f),
                                    // Con foto guardada abre el carné directo; si no,
                                    // primero el scanner.
                                    onClick = {
                                        if (carnetGuardado) showCarneDigital = true
                                        else showCarnetScanner = true
                                    }
                                )
                            }
                            if (mostrarCarnetDigital) {
                                WalletCard(
                                    icon = Icons.Filled.School,
                                    title = CarnetPerfil.DIGITAL.titulo(empresa),
                                    subtitle = L.t("Muestra · sin validez", "Display · not valid"),
                                    modifier = Modifier.weight(1f),
                                    onClick = { showCarneDigital = true }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // "Mis tarjetas": referencias locales (badge LOCAL, borde
                    // discontinuo, como el iOS).
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(
                                1.dp, Color.White.copy(alpha = 0.45f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { showMetodosPago = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Filled.CreditCard, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text(L.t("Mis tarjetas", "My cards"), style = BodyMdMedium, color = Color.White, modifier = Modifier.weight(1f))
                        Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.22f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("LOCAL", style = LabelCapsSm, color = Color.White)
                        }
                    }
                }
            }

            //----Mis cupones (orden iOS: justo después de la billetera)----
            // Hermana del hero, ya fuera del degradado y sin offsets negativos.
            Spacer(Modifier.height(16.dp))
            MisCuponesSection(
                cupones = cuponesGuardados,
                onExplorar = { router.navigate(AppScreen.MapaPrincipal) },
                onQuitar = { negocio -> scope.launch { CuponesStore.alternarCupon(negocio) } },
                onVerPromocion = { negocio -> negocioDetalle = negocio },
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(16.dp))

            // Stats card (sin offsets: flujo normal tras el hero ajustado)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
                elevation = CardDefaults.cardElevation(8.dp),
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    StatColumn("47", L.t("VIAJES", "TRIPS"), Modifier.weight(1f))
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(OutlineVariant.copy(alpha = 0.5f)).align(Alignment.CenterVertically))
                    StatColumn("12", L.t("RUTAS", "ROUTES"), Modifier.weight(1f))
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(OutlineVariant.copy(alpha = 0.5f)).align(Alignment.CenterVertically))
                    StatColumn("3", L.t("LOGROS", "TROPHIES"), Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(16.dp))

            //----Preferencias visuales (PerfilPreferenciasVisuales iOS)----
            // Qué tarjetas de carné aparecen en la billetera de la empresa
            // activa; se recuerda por empresa y ocultar no borra las fotos.
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(L.t("Preferencias visuales", "Visual preferences"), style = LabelCapsLg, color = OnSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest)
                ) {
                    Column {
                        ToggleRow(
                            Icons.Filled.Badge, AppPrimary,
                            L.t("Mostrar ", "Show ") + CarnetPerfil.FOTO.titulo(empresa),
                            PreferenciasVisualesPerfilStore.visible(CarnetPerfil.FOTO, empresa)
                        ) { activo ->
                            PreferenciasVisualesPerfilStore.establecer(activo, CarnetPerfil.FOTO, empresa)
                        }
                        Divider(modifier = Modifier.padding(start = 56.dp))
                        ToggleRow(
                            Icons.Filled.School, Tertiary,
                            L.t("Mostrar ", "Show ") + CarnetPerfil.DIGITAL.titulo(empresa),
                            PreferenciasVisualesPerfilStore.visible(CarnetPerfil.DIGITAL, empresa)
                        ) { activo ->
                            PreferenciasVisualesPerfilStore.establecer(activo, CarnetPerfil.DIGITAL, empresa)
                        }
                    }
                }
                Text(
                    L.t(
                        "Elige los carnets que aparecen en tu billetera de ${empresa.nombre}. Se recuerda para cada empresa; ocultarlos conserva tus fotos.",
                        "Choose the cards shown in your ${empresa.nombre} wallet. Choices are remembered for each company; hiding cards keeps your photos."
                    ),
                    style = BodyXs, color = OnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(16.dp))

            //----Modo Buses UTP (ModoBusesUTPSection del iOS)----
            // Solo si la temática y la referencia pertenecen a UTP.
            if (TransporteApp.busesUTPDisponibles) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(L.t("TRANSPORTE", "TRANSPORT"), style = LabelCapsLg, color = OnSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                    val busesUTPActivos = TransporteApp.busesUTPActivos
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Icon(Icons.Filled.DirectionsBus, null, tint = Color(0xFFCC0000), modifier = Modifier.size(23.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(L.t("Buses de la UTP", "UTP buses"), style = BodyMdMedium, color = OnSurface)
                                Text(
                                    if (busesUTPActivos) L.t("Modo UTP activado", "UTP mode on")
                                    else L.t("Usar transporte universitario", "Use university transport"),
                                    style = BodySm, color = OnSurfaceVariant
                                )
                            }
                            Switch(
                                checked = busesUTPActivos,
                                onCheckedChange = { activado ->
                                    // Los viajes en curso terminan al cambiar de modo.
                                    TransporteApp.establecerBusesUTP(activado)
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (TransporteApp.rutasUTPPendientes) TransporteApp.mensajePendiente
                        else L.t(
                            "Al activarlo, el mapa y las rutas usarán los buses de la UTP. Al desactivarlo, volverás al transporte urbano.",
                            "When enabled, the map and routes use UTP buses. When disabled, you return to city transport."
                        ),
                        style = BodySm, color = OnSurfaceVariant
                    )
                    Text(
                        L.t("Los viajes en curso terminan al cambiar de modo.", "Current trips end when you switch modes."),
                        style = BodySm, color = OnSurfaceVariant
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // Settings
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(L.t("Preferencias", "Preferences"), style = LabelCapsLg, color = OnSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest)
                ) {
                    Column {
                        // Persistidas (PreferenciasPerfilStore): sobreviven al
                        // reinicio, igual que notificaciones/compartir del iOS.
                        ToggleRow(Icons.Filled.Notifications, AppPrimary, L.t("Notificaciones", "Notifications"), notifOn) { activo ->
                            PreferenciasPerfilStore.establecerNotificaciones(activo)
                        }
                        Divider(modifier = Modifier.padding(start = 56.dp))
                        ToggleRow(Icons.Filled.LocationOn, Secondary, L.t("Compartir ubicación", "Share location"), ubicacionOn) { activo ->
                            PreferenciasPerfilStore.establecerCompartirUbicacion(activo)
                            // Popup informativo honesto al activar (iOS idem).
                            if (activo) popupCompartirUbicacion = true
                        }
                        Divider(modifier = Modifier.padding(start = 56.dp))
                        ToggleRow(Icons.Filled.CreditCard, Tertiary, L.t("Modo económico", "Saver mode"), ecoOff) { ecoOff = it }
                        Divider(modifier = Modifier.padding(start = 56.dp))
                        // Persistida: reactiva vía Flow; al tocar textos señables
                        // en cualquier pantalla se muestra el clip de señas.
                        // Fase 6: al ACTIVAR el modo se reproduce una vez el
                        // video de activación (media pantalla, cierra al
                        // terminar); reiniciar la app con el modo encendido NO
                        // lo reproduce (iOS idem).
                        ToggleRow(Icons.Filled.SignLanguage, AppPrimary, L.t("Modo Señas", "Sign language mode"), modoSenias) { activo ->
                            scope.launch {
                                SeniasPrefs.establecerActivo(activo)
                                if (activo) {
                                    com.example.rutautpnative.data.senias.SeniasOverlay.mostrarActivacionDelModo()
                                } else {
                                    // Apagar el modo detiene la presentacion en
                                    // curso (video de activacion o tarjeta).
                                    com.example.rutautpnative.data.senias.SeniasOverlay.cancelarPorNavegacion()
                                }
                            }
                        }
                        Divider(modifier = Modifier.padding(start = 56.dp))
                        // TODO(tema-en-perfil): aquí hubo un switch de tema oscuro
                        // (ícono DarkMode) — lo retiramos por fidelidad al original
                        // en iOS, que solo lo tiene en el Side Drawer (sección
                        // APARIENCIA). Sería una mejora de UX tenerlo también aquí;
                        // queda documentado como posible mejora futura, no olvidado.
                        // ES = switch apagado, EN = encendido. Persiste como las dos
                        // anteriores; toda la app reacciona al instante (L).
                        // Fase 6: botón señable (tarjeta 3 s -> cambiar idioma).
                        ToggleRow(
                            Icons.Filled.Language, AppPrimary,
                            if (esIngles) "Idioma: English" else "Idioma: Español",
                            esIngles
                        ) { activo ->
                            com.example.rutautpnative.data.senias.SeniasOverlay.ejecutarTrasVerSenia("perfil.idioma") {
                                L.establecer(if (activo) "en" else "es")
                            }
                        }
                        Divider(modifier = Modifier.padding(start = 56.dp))
                        ChevronRow(Icons.Filled.Person, AppPrimary, L.t("Nombre: $nombre", "Name: $nombre")) {
                            newNameInput = nombre
                            showEditDialog = true
                        }
                        Divider(modifier = Modifier.padding(start = 56.dp))
                        ChevronRow(Icons.Filled.Edit, OnSurfaceVariant, L.t("Editar perfil", "Edit profile")) {
                            showDatosPersonales = true
                        }
                        Divider(modifier = Modifier.padding(start = 56.dp))
                        // Modo offline: qué funciona sin conexión (OfflineMapSheet).
                        ChevronRow(Icons.Filled.CloudOff, Secondary, L.t("Modo offline", "Offline mode")) {
                            showOffline = true
                        }
                        Divider(modifier = Modifier.padding(start = 56.dp))
                        // Ayuda de TalkBack (VoiceOverHelpSheet iOS adaptado).
                        ChevronRow(Icons.Filled.Accessibility, Tertiary, L.t("Accesibilidad (TalkBack)", "Accessibility (TalkBack)")) {
                            showAccesibilidad = true
                        }
                    }
                }
            }

            // Holgura inferior: la BottomNavBar superpuesta mide ~64 dp MAS
            // los insets de navegacion del sistema (~24-48 dp); 90 dp dejaba
            // la ultima fila ("Accesibilidad") medio tapada.
            Spacer(Modifier.height(120.dp))
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            BottomNavBar(router)
        }
    }

    // Edit name dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(L.t("Editar nombre", "Edit name")) },
            text = {
                OutlinedTextField(
                    value = newNameInput,
                    onValueChange = { newNameInput = it },
                    label = { Text(L.t("Nombre completo", "Full name")) },
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newNameInput.isNotBlank()) nombre = newNameInput
                    showEditDialog = false
                }) { Text(L.t("Guardar", "Save")) }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text(L.t("Cancelar", "Cancel")) } }
        )
    }

    // Formulario de tarjeta (simulación: solo salen los últimos 4 dígitos).
    // Sheets del monedero (Fase 11) y de tarjetas.
    if (showRecargar) {
        RecargarSaldoSheet(onDismiss = { showRecargar = false })
    }
    if (showQRPasaje) {
        QRPasajeSheet(onDismiss = { showQRPasaje = false })
    }
    if (showMetodosPago) {
        MetodosPagoSheet(onDismiss = { showMetodosPago = false })
    }

    // Detalle de negocio desde un cupón ("Ver promoción").
    negocioDetalle?.let { negocio ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            NegocioDetailCard(
                negocio = negocio,
                desde = com.google.android.gms.maps.model.LatLng(negocio.latitud, negocio.longitud),
                onCerrar = { negocioDetalle = null }
            )
        }
    }

    // Carnet scanner: al "escanear", abre el carné digital para añadir la
    // foto del carné (la captura del scanner es simulada, sin OCR).
    if (showCarnetScanner) {
        CarnetScannerScreen(
            onCapture = {
                showCarneDigital = true
            },
            onDismiss = { showCarnetScanner = false }
        )
    }

    // Carné digital: foto persistida POR EMPRESA (CarnetImageStore).
    if (showCarneDigital) {
        CarneDigitalScreen(
            nombre = nombre,
            empresa = empresa,
            onCerrar = { showCarneDigital = false }
        )
    }

    // Datos personales (drawer iOS / Editar perfil): ficha + contacto +
    // emergencia + foto con editor de encuadre.
    if (showDatosPersonales) {
        DatosPersonalesSheet(onCerrar = { showDatosPersonales = false })
    }

    // Modo offline y ayuda de accesibilidad.
    if (showOffline) {
        OfflineMapSheet(onCerrar = { showOffline = false })
    }
    if (showAccesibilidad) {
        AyudaAccesibilidadSheet(onCerrar = { showAccesibilidad = false })
    }

    // Popup informativo honesto al activar "Compartir ubicación".
    if (popupCompartirUbicacion) {
        AlertDialog(
            onDismissRequest = { popupCompartirUbicacion = false },
            title = { Text(L.t("Compartir ubicación", "Share location")) },
            text = {
                Text(
                    L.t(
                        "Esta preferencia queda guardada solo en tu teléfono. La app no envía tu ubicación a ningún servidor por sí sola: el intercambio solo ocurre si activas «Ayudar con ubicaciones» en Ajustes y se detecta un viaje.",
                        "This preference is saved on your phone only. The app doesn't send your location to any server on its own: sharing only happens if you turn on \"Help with locations\" in Settings and a trip is detected."
                    ),
                    style = BodySm
                )
            },
            confirmButton = {
                TextButton(onClick = { popupCompartirUbicacion = false }) { Text(L.t("Entendido", "Got it")) }
            }
        )
    }
    }

@Composable
private fun WalletCard(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Column {
                Text(title, style = BodyXsMedium, color = Color.White)
                Text(subtitle, style = BodyXs, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
            }
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = DisplayNumberMd, color = OnSurface)
        Text(label, style = LabelCapsMd, color = OnSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, iconColor: Color, label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, style = BodyMdMedium, color = OnSurface, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppPrimary))
    }
}

@Composable
private fun ChevronRow(icon: ImageVector, iconColor: Color, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, style = BodyMdMedium, color = OnSurface, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

private fun iniciales(name: String): String =
    name.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("")

//----Tarjeta de cupón guardado (PerfilCuponCard)----
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
private fun MisCuponesSection(
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