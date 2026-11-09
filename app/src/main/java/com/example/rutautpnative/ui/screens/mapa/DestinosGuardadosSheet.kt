package com.example.rutautpnative.ui.screens.mapa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.data.LugaresStore
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.ParaderoGTFS
import com.example.rutautpnative.model.LugarGuardado
import com.example.rutautpnative.ui.components.iconoParaCategoria
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*

//----Destinos guardados del drawer (puerto del DestinosGuardadosSheet.swift)----
// Dos secciones: PARADEROS guardados (reconocidos contra el catálogo GTFS
// por nombre + coordenadas <5 m) y LUGARES guardados. Comparte la fuente de
// datos con Guardado y Seguridad; elegir uno busca cómo llegar desde la
// ubicación actual. "Guardar nuevos paraderos" lleva a Seguridad.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinosGuardadosSheet(
    onSeleccionar: (LugarGuardado) -> Unit,
    onAdministrarParaderos: () -> Unit,
    onCerrar: () -> Unit
) {
    val context = LocalContext.current

    // Guardados (fuente compartida con Guardado/Seguridad).
    val guardados by LugaresStore.observar().collectAsState(initial = emptyList())

    // Catálogo de paraderos: cargado UNA vez por revision (con reintento).
    var catalogoParaderos by remember { mutableStateOf<List<ParaderoGTFS>>(emptyList()) }
    var cargandoCatalogo by remember { mutableStateOf(true) }
    var errorCatalogo by remember { mutableStateOf<String?>(null) }
    var revisionCatalogo by remember { mutableStateOf(0) }

    LaunchedEffect(revisionCatalogo) {
        cargandoCatalogo = true
        errorCatalogo = null
        try {
            val rutas = GTFSRepository.rutas()
            val vistos = HashSet<String>()
            catalogoParaderos = rutas.flatMap { it.paraderos }.filter { vistos.add(it.id) }
        } catch (e: Exception) {
            errorCatalogo = L.t("No pudimos cargar el catálogo de paraderos.", "Couldn't load the stop catalog.")
        }
        cargandoCatalogo = false
    }

    // Resolucion una sola vez por carga (no recorrer el feed en cada render).
    val (paraderos, lugares) = remember(guardados, catalogoParaderos) {
        val nuevosParaderos = mutableListOf<LugarGuardado>()
        val nuevosLugares = mutableListOf<LugarGuardado>()
        for (lugar in guardados) {
            if (lugar.esParaderoGuardadoEn(catalogoParaderos)) nuevosParaderos.add(lugar)
            else nuevosLugares.add(lugar)
        }
        nuevosParaderos to nuevosLugares
    }

    ModalBottomSheet(onDismissRequest = onCerrar, containerColor = AppSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L.t("Destinos guardados", "Saved destinations"), style = HeadlineSm, color = OnSurface, modifier = Modifier.weight(1f))
                TextButton(onClick = onCerrar) { Text(L.t("Cerrar", "Close")) }
            }
            Spacer(Modifier.height(12.dp))

            //----Paraderos guardados----
            Text(L.t("PARADEROS GUARDADOS", "SAVED STOPS"), style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (paraderos.isEmpty()) {
                Text(L.t("Aún no tienes paraderos guardados.", "You haven't saved any stops yet."), style = BodySm, color = OnSurfaceVariant)
            } else {
                paraderos.forEach { lugar -> FilaDestino(lugar, esParadero = true, onSeleccionar = onSeleccionar) }
            }
            TextButton(onClick = onAdministrarParaderos) {
                Icon(Icons.Filled.DirectionsBus, null, tint = AppPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(L.t("Guardar nuevos paraderos", "Save new stops"), style = BodySmMedium, color = AppPrimary)
            }
            Text(
                L.t(
                    "Son los mismos que guardas en Seguridad. Elige uno para buscar cómo llegar desde tu ubicación.",
                    "These are the stops you save in Safety. Choose one to find directions from your location."
                ),
                style = BodyXs, color = OnSurfaceVariant
            )

            //----Lugares guardados----
            if (lugares.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(L.t("LUGARES GUARDADOS", "SAVED PLACES"), style = LabelCapsMd, color = OnSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                lugares.forEach { lugar -> FilaDestino(lugar, esParadero = false, onSeleccionar = onSeleccionar) }
            }

            //----Estado del catálogo (carga / error con reintento)----
            if (cargandoCatalogo) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AppPrimary)
                    Text(L.t("Cargando paraderos…", "Loading stops…"), style = BodySm, color = OnSurfaceVariant)
                }
            } else if (errorCatalogo != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Warning, null, tint = AppError, modifier = Modifier.size(16.dp))
                    Text(errorCatalogo ?: "", style = BodyXs, color = OnSurfaceVariant, modifier = Modifier.weight(1f))
                }
                TextButton(onClick = { revisionCatalogo++ }) {
                    Text(L.t("Reintentar carga de paraderos", "Retry loading stops"), style = BodySmMedium, color = AppPrimary)
                }
            }
        }
    }
}

@Composable
private fun FilaDestino(lugar: LugarGuardado, esParadero: Boolean, onSeleccionar: (LugarGuardado) -> Unit) {
    val ubicacionValida = lugar.lat != null && lugar.lon != null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerLow)
            .clickable(enabled = ubicacionValida) { onSeleccionar(lugar) }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        val icono: ImageVector = if (esParadero) Icons.Filled.DirectionsBus else com.example.rutautpnative.ui.components.iconoParaLugar(lugar)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppPrimary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icono, null, tint = AppPrimary, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(lugar.nombre, style = BodyMdMedium, color = if (ubicacionValida) OnSurface else OnSurfaceVariant)
            if (lugar.direccion.isNotBlank()) {
                Text(lugar.direccion, style = BodyXs, color = OnSurfaceVariant)
            }
            if (!ubicacionValida) {
                Text(L.t("Añade su ubicación en Guardado", "Add its location in Saved"), style = BodyXs, color = OnSurfaceVariant)
            }
        }
        if (ubicacionValida) {
            Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
        } else {
            Icon(Icons.Filled.Place, null, tint = OnSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
        }
    }
    Spacer(Modifier.height(6.dp))
}
