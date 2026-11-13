package com.example.rutautpnative.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rutautpnative.data.*
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.model.LugarGuardado
import com.example.rutautpnative.navigation.AppRouter
import com.example.rutautpnative.ui.components.*
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*

@Composable
fun GuardadoScreen(router: AppRouter, viewModel: GuardadoViewModel = viewModel(), estado: EstadoGuardadoViewModel = viewModel()) {
    val lugares by viewModel.lugares.collectAsState()
    val lineas by viewModel.lineas.collectAsState()
    val catalogo by viewModel.catalogo.collectAsState()
    val cargandoCatalogo by viewModel.cargandoCatalogo.collectAsState()
    val errorCatalogo by viewModel.errorCatalogo.collectAsState()
    val lugaresInvalidos by LugaresStore.datosInvalidos.collectAsState()
    val lineasInvalidas by LineasGuardadasStore.datosInvalidos.collectAsState()
    var tab by estado::pestana
    var anadirLugar by remember { mutableStateOf(false) }
    var anadirLinea by remember { mutableStateOf(false) }
    var seleccionado by remember { mutableStateOf<LugarGuardado?>(null) }
    var editar by remember { mutableStateOf<LugarGuardado?>(null) }
    var linea by remember { mutableStateOf<RutaGTFS?>(null) }
    var explorar by remember { mutableStateOf<RutaGTFS?>(null) }
    var paraderosMapa by remember { mutableStateOf(false) }
    val paraderos = remember(catalogo) { catalogo.flatMap { it.paraderos }.distinctBy { it.id } }
    val guardadosParadero = remember(lugares, paraderos) { lugares.filter { it.esParaderoGuardadoEn(paraderos) } }
    val guardadosLugar = remember(lugares, guardadosParadero) { lugares.filter { lugar -> guardadosParadero.none { it.id == lugar.id } } }
    LaunchedEffect(router.lugarGuardadoPendiente, lugares) {
        lugares.firstOrNull { it.id == router.lugarGuardadoPendiente }?.let { seleccionado = it; tab = 0; router.lugarGuardadoPendiente = null }
    }
    Scaffold(containerColor = AppBackground, contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = { BottomNavBar(router) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(L.t("Guardado", "Saved"), style = HeadlineLg, color = OnSurface, modifier = Modifier.weight(1f).signable("guardado.titulo"))
                Icon(Icons.Filled.Bookmark, null, tint = AcentoInterfaz)
            }
            TabRow(tab, containerColor = Color.Transparent, contentColor = AcentoInterfaz, modifier = Modifier.padding(horizontal = 20.dp)) {
                listOf(L.t("Lugares", "Places") to Icons.Filled.Place, L.t("Líneas", "Lines") to Icons.Filled.DirectionsBus).forEachIndexed { i, (titulo, icono) ->
                    Tab(tab == i, onClick = { SeniasPresenterAccion(if (i == 0) "guardado.lugares" else "guardado.lineas") { tab = i } }, text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(icono, null, modifier = Modifier.size(20.dp)); Text(titulo, style = BodyMdMedium) } })
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (tab == 0) L.f("{0} guardados", "{0} saved", lugares.size.toString()) else L.f("{0} líneas", "{0} lines", lineas.size.toString()), style = BodySm, color = OnSurfaceVariant, modifier = Modifier.weight(1f))
                Button(onClick = { SeniasPresenterAccion(if (tab == 0) "guardado.anadir_lugar" else "guardado.anadir_linea") { if (tab == 0) anadirLugar = true else anadirLinea = true } },
                    enabled = if (tab == 0) !lugaresInvalidos else !lineasInvalidas, colors = ButtonDefaults.buttonColors(containerColor = PrimaryFill, contentColor = OnPrimaryFill)) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (tab == 0) L.t("Añadir lugar", "Add place") else L.t("Añadir línea", "Add line"))
                }
            }
            LazyColumn(Modifier.weight(1f), state = estado.lista, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (lugaresInvalidos || lineasInvalidas) item {
                    Column {
                        Text(L.t("No se pueden leer algunos datos guardados. Los originales se conservan. Revisa Perfil → Respaldos guardados.", "Some saved data could not be read. Originals are preserved. Open Profile → Saved backups."), color = AppError, style = BodySm)
                        OutlinedButton(onClick = { if (lugaresInvalidos) viewModel.reintentarLecturaLugares(); if (lineasInvalidas) viewModel.reintentarLecturaLineas() }) { Text(L.t("Reintentar lectura", "Retry reading")) }
                    }
                }
                if (tab == 0) {
                    item { TituloGuardado(L.t("Lugares guardados", "Saved places"), guardadosLugar.size) }
                    if (guardadosLugar.isEmpty()) item { VacioGuardado(L.t("Aún no tienes lugares guardados", "No saved places yet"), L.t("Toca Añadir lugar para guardar tu primer lugar.", "Tap Add place to save your first place.")) }
                    items(guardadosLugar, key = { "lugar:${it.id}" }) { lugar -> LugarRow(lugar) { seleccionado = lugar } }
                    item { TituloGuardado(L.t("Paraderos guardados", "Saved stops"), guardadosParadero.size) }
                    if (guardadosParadero.isEmpty()) item { Text(L.t("Guarda un paradero en el mapa y lo encontrarás aquí.", "Save a stop on the map and find it here."), style = BodySm, color = OnSurfaceVariant) }
                    items(guardadosParadero, key = { "paradero:${it.id}" }) { lugar -> LugarRow(lugar) { seleccionado = lugar } }
                    item { OutlinedButton(onClick = { paraderosMapa = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Map, null); Spacer(Modifier.width(8.dp)); Text(L.t("Explorar paraderos", "Explore stops")) } }
                } else {
                    item { TituloGuardado(L.t("Líneas guardadas", "Saved lines"), lineas.size) }
                    if (cargandoCatalogo) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    else if (errorCatalogo != null) item { Column { Text(L.t("No se pudo cargar el catálogo de rutas.", "Could not load the route catalog."), color = AppError); OutlinedButton(onClick = { viewModel.cargarCatalogo(true) }) { Text(L.t("Reintentar", "Try again")) } } }
                    else if (lineas.isEmpty()) item { VacioGuardado(L.t("No tienes líneas guardadas", "No saved lines"), L.t("Toca Añadir línea para consultar el catálogo oficial.", "Tap Add line to browse the official catalog.")) }
                    items(lineas, key = { it.id }) { ruta -> RutaOpcionCard(ruta, accion = AccionTarjetaRuta.GUARDADA) { linea = ruta } }
                }
            }
        }
    }
    seleccionado?.let { lugar -> LugarDetailSheet(lugar, router, { editar = lugar; seleccionado = null }, { viewModel.eliminar(lugar); seleccionado = null }, { seleccionado = null }) }
    editar?.let { lugar -> AddLugarSheet({ viewModel.actualizar(it); editar = null }, { editar = null }, lugar) }
    if (anadirLugar) AddLugarSheet({ viewModel.agregar(it); anadirLugar = false }, { anadirLugar = false })
    if (anadirLinea) AddLineaSheet(catalogo, lineas.map { it.id }.toSet(), { viewModel.agregarLinea(it); anadirLinea = false }, { anadirLinea = false }, cargandoCatalogo, errorCatalogo, { viewModel.cargarCatalogo(true) })
    linea?.let { ruta -> LineaDetailSheet(ruta, { linea = null; explorar = ruta }, { viewModel.quitarLinea(ruta.id); linea = null }, { linea = null }) }
    explorar?.let { ruta -> ExploradorRutaScreen(ruta) { explorar = null } }
    if (paraderosMapa) ParaderosIluminadosScreen(catalogo, router) { paraderosMapa = false }
}

private fun SeniasPresenterAccion(clave: String, accion: () -> Unit) = com.example.rutautpnative.data.senias.SeniasOverlay.ejecutarTrasVerSenia(clave, accion)

@Composable
private fun TituloGuardado(texto: String, cantidad: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, style = HeadlineXs, color = OnSurface, modifier = Modifier.weight(1f))
        Text(cantidad.toString(), style = BodySm, color = OnSurfaceVariant)
    }
}

@Composable
private fun VacioGuardado(titulo: String, texto: String) {
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), color = SuperficieAgrupada) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.BookmarkBorder, null, tint = OnSurfaceVariant, modifier = Modifier.size(32.dp))
            Text(titulo, style = BodyMdMedium, color = OnSurface)
            Text(texto, style = BodySm, color = OnSurfaceVariant)
        }
    }
}
