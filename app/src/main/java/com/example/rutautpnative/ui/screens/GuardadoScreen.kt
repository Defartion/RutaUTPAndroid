package com.example.rutautpnative.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rutautpnative.data.geo.Geocodificacion
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.model.CategoriaLugar
import com.example.rutautpnative.model.LugarGuardado
import com.example.rutautpnative.navigation.AppRouter
import com.example.rutautpnative.navigation.AppScreen
import com.example.rutautpnative.navigation.DestinoPendiente
import com.example.rutautpnative.ui.components.BottomNavBar
import com.example.rutautpnative.ui.components.Signable
import com.example.rutautpnative.ui.components.iconoParaCategoria
import com.example.rutautpnative.ui.components.senableAccion
import com.example.rutautpnative.ui.components.signable
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Pantalla
@Composable
internal fun LugarRow(lugar: LugarGuardado, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val isUTP = lugar.esFijo
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(if (isUTP) AppPrimary else PrimaryContainer.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(com.example.rutautpnative.ui.components.iconoParaLugar(lugar), null, tint = if (isUTP) Color.White else AppPrimary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(lugar.nombre, style = BodyMdMedium, color = OnSurface)
                    if (lugar.esFrecuente) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Tertiary).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(L.t("FRECUENTE", "FREQUENT"), style = LabelCapsMd, color = OnTertiary)
                        }
                    }
                }
                Text(lugar.direccion, style = BodySm, color = OnSurfaceVariant, maxLines = 1)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun LineaRow(ruta: RutaGTFS, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(ruta.color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Text(ruta.linea, style = HeadlineSm, color = ruta.color, maxLines = 1)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(ruta.empresa, style = BodyMdMedium, color = OnSurface)
                Text(ruta.recorrido, style = BodySm, color = OnSurfaceVariant, maxLines = 1)
            }
            Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Tertiary).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(ruta.frecuenciaTexto, style = LabelCapsMd, color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LugarDetailSheet(lugar: LugarGuardado, router: AppRouter, onEditar: () -> Unit, onEliminar: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Resuelve la coordenada del lugar: la guardada, o la geocodificacion de
    // su direccion ("direccion, Trujillo, Peru") si aun no tiene — mismo
    // comportamiento del LugarDetailSheet de iOS.
    fun resolverCoordenada(alResolver: (Double, Double) -> Unit) {
        val lat = lugar.lat
        val lon = lugar.lon
        if (lat != null && lon != null) {
            alResolver(lat, lon)
        } else {
            scope.launch {
                val punto = Geocodificacion.coordenadaDe("${lugar.direccion}, Trujillo, Perú", context)
                if (punto != null) {
                    alResolver(punto.latitude, punto.longitude)
                } else {
                    Toast.makeText(
                        context,
                        L.t("No se pudo ubicar la dirección de este lugar.", "Couldn't locate this place's address."),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(modifier = Modifier.padding(20.dp)) {
            val isUTP = lugar.nombre == "UTP"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(if (isUTP) AppPrimary else PrimaryContainer.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(com.example.rutautpnative.ui.components.iconoParaLugar(lugar), null, tint = if (isUTP) Color.White else AppPrimary, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(lugar.nombre, style = HeadlineMd, color = OnSurface)
                    Text(lugar.direccion, style = BodySm, color = OnSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))
            // "Como llegar": publica destinoPendiente y el Mapa calcula la
            // ruta desde la posicion real del usuario (consumido en MapaScreen).
            Button(onClick = {
                resolverCoordenada { lat, lon ->
                    router.destinoPendiente = DestinoPendiente(lugar.nombre, lat, lon)
                    router.navigate(AppScreen.MapaPrincipal)
                    onDismiss()
                }
            }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)) {
                Icon(Icons.Filled.Map, null); Spacer(Modifier.width(8.dp)); Text(L.t("Ver ruta desde mi posición", "See route from my location"), style = BodyMdMedium, color = Color.White)
            }
            Spacer(Modifier.height(10.dp))
            // "Transporte cerca de este lugar": filtro de Rutas por paraderos a
            // <300 m del punto (lugarCercanoPendiente, consumido en RutasScreen).
            Button(onClick = {
                resolverCoordenada { lat, lon ->
                    router.lugarCercanoPendiente = DestinoPendiente(lugar.nombre, lat, lon)
                    router.navigate(AppScreen.Rutas)
                    onDismiss()
                }
            }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer)) {
                Icon(Icons.Filled.DirectionsBus, null, tint = OnPrimaryContainer); Spacer(Modifier.width(8.dp)); Text(L.t("Buscar transporte cercano", "Find nearby transport"), style = BodyMdMedium, color = OnPrimaryContainer)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onEditar, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(L.t("Editar lugar", "Edit place")) }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onEliminar, enabled = !lugar.esFijo, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = ErrorContainer)) {
                Icon(Icons.Filled.Delete, null, tint = OnErrorContainer); Spacer(Modifier.width(8.dp)); Text(L.t("Eliminar de guardados", "Remove from saved"), style = BodyMdMedium, color = OnErrorContainer)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LineaDetailSheet(ruta: RutaGTFS, onQuitar: () -> Unit, onVerRutaCompleta: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(ruta.color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Text(ruta.linea, style = DisplayNumberMd, color = ruta.color, maxLines = 1)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(ruta.empresa, style = HeadlineSm, color = OnSurface)
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(TertiaryContainer).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(ruta.frecuenciaTexto, style = LabelCapsMd, color = Tertiary)
                    }
                }
            }
            Spacer(Modifier.height(16.dp)); Divider(); Spacer(Modifier.height(16.dp))
            Text(L.t("RECORRIDO", "ROUTE"), style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(ruta.recorrido, style = BodyMd, color = OnSurface)
            Spacer(Modifier.height(16.dp))
            Text(L.t("PARADAS PRINCIPALES", "MAIN STOPS"), style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            ruta.paraderos.take(5).forEach { paradero ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AppPrimary))
                    Spacer(Modifier.width(10.dp))
                    Text(paradero.nombre, style = BodyMd, color = OnSurface)
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onVerRutaCompleta, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)) {
                Icon(Icons.Filled.LocationOn, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text(L.t("Ver ruta completa", "See full route"), style = HeadlineSm, color = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onQuitar, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = ErrorContainer)) {
                Icon(Icons.Filled.Delete, null, tint = OnErrorContainer); Spacer(Modifier.width(8.dp)); Text(L.t("Quitar de guardados", "Remove from saved"), style = BodyMdMedium, color = OnErrorContainer)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(L.t("Cerrar", "Close"), style = BodyMdMedium, color = OnSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddLineaSheet(
    catalogo: List<RutaGTFS>,
    idsGuardados: Set<String>,
    onAgregar: (RutaGTFS) -> Unit,
    onDismiss: () -> Unit,
    cargando: Boolean = false,
    errorCatalogo: String? = null,
    onReintentar: () -> Unit = {}
) {
    var textoBusqueda by remember { mutableStateOf("") }

    val indices = remember(catalogo) { catalogo.associate { it.id to com.example.rutautpnative.data.gtfs.IndiceBusquedaRuta(it) } }
    val disponibles = remember(catalogo, idsGuardados, textoBusqueda) {
        val consulta = com.example.rutautpnative.data.gtfs.ConsultaRuta(textoBusqueda, indices.values.map { it.letra }.toSet())
        catalogo.filter { it.id !in idsGuardados && indices[it.id]?.coincide(consulta) == true }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(L.t("Añadir línea", "Add route"), style = HeadlineMd, color = OnSurface)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = textoBusqueda,
                onValueChange = { textoBusqueda = it },
                placeholder = { Text(L.t("Buscar línea, empresa o avenida", "Search route, company or avenue"), style = BodySm, color = OnSurfaceVariant) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = OnSurfaceVariant) },
                trailingIcon = {
                    if (textoBusqueda.isNotEmpty()) {
                        IconButton(onClick = { textoBusqueda = "" }) {
                            Icon(Icons.Filled.Close, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            if (cargando) {
                CircularProgressIndicator(Modifier.padding(20.dp))
                Text(L.t("Cargando líneas…", "Loading routes…"), style = BodySm)
            } else if (errorCatalogo != null) {
                Text(L.t("No se pudo cargar el catálogo.", "Could not load the catalog."), color = AppError)
                TextButton(onClick = onReintentar) { Text(L.t("Reintentar", "Try again")) }
            } else if (disponibles.isEmpty()) {
                Text(
                    if (textoBusqueda.isNotBlank()) L.t("No hay líneas que coincidan", "No matching routes") else L.t("No hay más líneas para agregar", "No more routes to add"),
                    style = BodySm,
                    color = OnSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    disponibles.forEach { ruta ->
                        com.example.rutautpnative.ui.components.RutaOpcionCard(ruta, accion = com.example.rutautpnative.ui.components.AccionTarjetaRuta.ANADIR) { onAgregar(ruta) }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// Estado de la geocodificación de la dirección (AddLugarSheet).
private enum class EstadoDireccion {
    SIN_BUSCAR,      // campo vacío o muy corto
    BUSCANDO,        // debounce/geocoder en vuelo
    ENCONTRADA,      // coordenada resuelta ✓
    NO_ENCONTRADA,   // el geocoder no la reconoció → ajustar en el mapa
    AJUSTADA         // el usuario la fijó manualmente en el mapa
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddLugarSheet(onSave: (LugarGuardado) -> Unit, onDismiss: () -> Unit, inicial: LugarGuardado? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var nombre by remember { mutableStateOf(inicial?.nombre.orEmpty()) }
    var direccion by remember { mutableStateOf(inicial?.direccion.orEmpty()) }
    var categoria by remember { mutableStateOf(inicial?.categoria ?: CategoriaLugar.OTRO) }
    var icono by remember { mutableStateOf(inicial?.iconoPersonalizado) }
    var frecuente by remember { mutableStateOf(inicial?.esFrecuente ?: false) }
    var coordenada by remember { mutableStateOf(inicial?.let { lugar -> lugar.lat?.let { lat -> lugar.lon?.let { lon -> LatLng(lat, lon) } } }) }
    var estadoDireccion by remember { mutableStateOf(EstadoDireccion.SIN_BUSCAR) }
    var geocodeJob by remember { mutableStateOf<Job?>(null) }
    var mostrarPicker by remember { mutableStateOf(false) }

    // Geocodificación con debounce (puerto del AddLugarSheet de iOS: misma
    // consulta "direccion, Trujillo, Peru" y 0.7 s de espera). Una edición
    // invalida la coordenada anterior hasta resolver la nueva.
    fun programarBusqueda(texto: String) {
        geocodeJob?.cancel()
        val t = texto.trim()
        if (t.length < 5) {
            estadoDireccion = EstadoDireccion.SIN_BUSCAR
            if (coordenada != null) coordenada = null
            return
        }
        estadoDireccion = EstadoDireccion.BUSCANDO
        coordenada = null
        geocodeJob = scope.launch {
            delay(700)
            val punto = Geocodificacion.coordenadaDe("$t, Trujillo, Perú", context)
            coordenada = punto
            estadoDireccion = if (punto != null) EstadoDireccion.ENCONTRADA else EstadoDireccion.NO_ENCONTRADA
        }
    }

    // Solo se guarda con nombre + dirección + COORDENADA resuelta (o ajustada
    // en el mapa): sin coordenada no puede ser chip ni destino.
    val puedeGuardar = nombre.isNotBlank() && direccion.isNotBlank() && coordenada != null

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(modifier = Modifier.fillMaxHeight(0.9f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(L.t("Guardar lugar", "Save place"), style = HeadlineMd, color = OnSurface)
            Spacer(Modifier.height(16.dp))
            Text(L.t("NOMBRE", "NAME"), style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                placeholder = { Text(L.t("Ej. Mi trabajo", "E.g. My job")) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(L.t("DIRECCIÓN", "ADDRESS"), style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = direccion,
                onValueChange = {
                    direccion = it
                    programarBusqueda(it)
                },
                placeholder = { Text(L.t("Ej. Av. España 123", "E.g. España Ave 123")) },
                trailingIcon = {
                    Icon(Icons.Filled.LocationOn, null, tint = OnSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Text(L.t("Categoría", "Category"), style = BodyMdMedium, modifier = Modifier.padding(top = 12.dp))
            CategoriaLugar.entries.chunked(3).forEach { fila ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    fila.forEach { valor -> FilterChip(categoria == valor, { categoria = valor }, label = { Text(L.t(valor.label, valor.name.lowercase().replace('_', ' ')), maxLines = 1) }) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(frecuente, { frecuente = it }); Text(L.t("Lugar frecuente", "Frequent place"))
            }
            Text(L.t("Icono personalizado", "Custom icon"), style = BodyMdMedium)
            val iconos = listOf("favorite", "flag", "people", "pets", "local_pharmacy", "sports_soccer", "movie", "music_note", "star")
            iconos.chunked(5).forEach { fila -> Row {
                fila.forEach { valor ->
                    IconButton(onClick = { icono = if (icono == valor) null else valor }) {
                        Icon(com.example.rutautpnative.ui.components.iconoParaLugar(LugarGuardado(nombre = "", direccion = "", categoria = categoria, iconoPersonalizado = valor)), valor,
                            tint = if (icono == valor) AppPrimary else OnSurfaceVariant)
                    }
                }
            } }
            // Estado de la geocodificación (con coordenada visible).
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (estadoDireccion) {
                    EstadoDireccion.SIN_BUSCAR -> {}
                    EstadoDireccion.BUSCANDO -> {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AppPrimary)
                        Text(L.t("Buscando dirección…", "Looking up address…"), style = BodyXs, color = OnSurfaceVariant)
                    }
                    EstadoDireccion.ENCONTRADA -> {
                        Icon(Icons.Filled.CheckCircle, null, tint = AppPrimary, modifier = Modifier.size(14.dp))
                        Text(L.t("Dirección encontrada", "Address found"), style = BodyXs, color = OnSurfaceVariant)
                    }
                    EstadoDireccion.NO_ENCONTRADA -> {
                        Icon(Icons.Filled.Info, null, tint = OnSurfaceVariant, modifier = Modifier.size(14.dp))
                        Text(L.t("No la encontramos; ubícala en el mapa", "Couldn't find it; set it on the map"), style = BodyXs, color = OnSurfaceVariant)
                    }
                    EstadoDireccion.AJUSTADA -> {
                        Icon(Icons.Filled.CheckCircle, null, tint = AppPrimary, modifier = Modifier.size(14.dp))
                        Text(L.t("Ubicación ajustada en el mapa", "Location set on the map"), style = BodyXs, color = OnSurfaceVariant)
                    }
                }
            }
            coordenada?.let {
                Text("%.4f, %.4f".format(it.latitude, it.longitude), style = BodyXs, color = OnSurfaceVariant)
            }

            Spacer(Modifier.height(10.dp))
            // Ajuste manual en el mapa (equivale al mini-mapa tocable del iOS).
            OutlinedButton(
                onClick = { mostrarPicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Map, null, tint = AppPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(L.t("Ubicar en el mapa", "Set on map"), style = BodyMdMedium, color = AppPrimary)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    // Botón señable (Fase 6): tarjeta 3 s y luego guarda.
                    com.example.rutautpnative.data.senias.SeniasOverlay.ejecutarTrasVerSenia("guardado.guardar_lugar") {
                        val p = coordenada ?: return@ejecutarTrasVerSenia
                        onSave(LugarGuardado(
                            id = inicial?.id ?: java.util.UUID.randomUUID().toString(),
                            esFijo = inicial?.esFijo ?: false,
                            paraderoID = inicial?.paraderoID,
                            iconoPersonalizado = icono,
                            esFrecuente = frecuente,
                            nombre = nombre.ifBlank { "Nuevo lugar" },
                            direccion = direccion.ifBlank { "Sin dirección" },
                            categoria = categoria,
                            lat = p.latitude,
                            lon = p.longitude
                        ))
                    }
                },
                enabled = puedeGuardar,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
            ) {
                Text(L.t("Guardar", "Save"), style = HeadlineSm, color = Color.White)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (mostrarPicker) {
        MapaUbicacionPicker(
            inicial = coordenada,
            titulo = L.t("Arrastra el mapa hasta el lugar", "Drag the map to the place"),
            textoConfirmar = L.t("Usar esta ubicación", "Use this location"),
            onConfirmar = { punto ->
                coordenada = punto
                estadoDireccion = EstadoDireccion.AJUSTADA
            },
            onCerrar = { mostrarPicker = false }
        )
    }
}
