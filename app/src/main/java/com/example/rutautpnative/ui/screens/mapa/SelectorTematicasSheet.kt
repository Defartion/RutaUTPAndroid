package com.example.rutautpnative.ui.screens.mapa

import androidx.compose.ui.graphics.asImageBitmap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.data.SedeTrabajoStore
import com.example.rutautpnative.data.gtfs.TransporteApp
import com.example.rutautpnative.model.CatalogoSedesTrabajo
import com.example.rutautpnative.model.SedeTrabajo
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import com.example.rutautpnative.ui.theme.TematicaEmpresa

//----Temáticas y sede de trabajo (puerto de SelectorTematicasSheet.swift----
//----+ SelectorSedeTrabajo.swift)                                   ----
// Adaptación documentada: el iOS renderiza LOGOS monocromáticos por empresa
// (assets del bundle); Android no los tiene, así que cada empresa se muestra
// como un disco con sus iniciales sobre su color de marca — mismo dato, mismo
// comportamiento, distinto recurso.

/// Disco con las iniciales de la empresa sobre su color de marca.
@Composable
private fun DiscoEmpresa(empresa: TematicaEmpresa, tamano: Int = 40) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val logo by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, empresa.id) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { ctx.assets.open("marcas/${empresa.id}.png").use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull()
        }
    }
    if (logo != null) {
        androidx.compose.foundation.Image(logo!!.asImageBitmap(), empresa.nombre, modifier = Modifier.size(tamano.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
        return
    }

    val iniciales = empresa.nombre.split(" ")
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
    Box(
        modifier = Modifier
            .size(tamano.dp)
            .clip(CircleShape)
            .background(PaletasEmpresa.paleta(empresa).colorMarca),
        contentAlignment = Alignment.Center
    ) {
        Text(iniciales, style = LabelCapsMd, color = Color.White)
    }
}

/// Fila compacta "MI SEDE DE TRABAJO" para Ajustes (SelectorSedeTrabajo iOS):
/// muestra la sede vigente y abre la hoja de empresas y sedes.
@Composable
fun SelectorSedeTrabajo(onAbrir: () -> Unit, modifier: Modifier = Modifier) {
    val sede = TransporteApp.sedeSeleccionada
    Column(modifier = modifier) {
        Text(L.t("MI SEDE DE TRABAJO", "MY WORKPLACE"), style = LabelCapsMd, color = OnSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            L.t(
                "Elige la sede que aparecerá como referencia al abrir el mapa.",
                "Choose the workplace shown as a reference when you open the map."
            ),
            style = BodyXs, color = OnSurfaceVariant
        )
        if (sede?.tematica == TematicaEmpresa.UTP) {
            Spacer(Modifier.height(4.dp))
            Text(
                L.t(
                    "UTP es la opción predeterminada. Puedes elegir otra empresa y sede cuando quieras.",
                    "UTP is the default option. You can choose another company and workplace at any time."
                ),
                style = BodyXs, color = OnSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceContainerLow)
                .clickable { onAbrir() }
                .padding(16.dp)
        ) {
            if (sede != null) DiscoEmpresa(sede.tematica, 36) else Icon(Icons.Filled.Business, null, tint = AppPrimary, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    sede?.nombre ?: L.t("Elegir empresa y sede", "Choose company and workplace"),
                    style = BodyMdMedium, color = OnSurface
                )
                sede?.let {
                    Text(it.direccion, style = BodyXs, color = OnSurfaceVariant)
                }
            }
            Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/// Una sola hoja: elegir una marca abre sus sedes sin cambiar aún el contexto
/// (SelectorTematicasSheet + SedeTrabajoSheet del iOS). UTP aplica directo.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorTematicasSheet(onCerrar: () -> Unit) {
    var busqueda by remember { mutableStateOf("") }
    var empresaParaSede by remember { mutableStateOf<TematicaEmpresa?>(null) }

    ModalBottomSheet(onDismissRequest = onCerrar, containerColor = AppSurface) {
        if (empresaParaSede != null) {
            ContenidoSedeTrabajo(
                empresaInicial = empresaParaSede!!,
                onVolverATematicas = { empresaParaSede = null },
                onCerrar = onCerrar
            )
        } else {
            ContenidoTematicas(
                busqueda = busqueda,
                onBusqueda = { busqueda = it },
                onElegir = { opcion ->
                    if (opcion == TematicaEmpresa.UTP) {
                        TransporteApp.usarSedeTrabajo(CatalogoSedesTrabajo.campusUTP)
                        onCerrar()
                    } else {
                        empresaParaSede = opcion
                    }
                },
                onCerrar = onCerrar
            )
        }
    }
}

@Composable
private fun ContenidoTematicas(
    busqueda: String,
    onBusqueda: (String) -> Unit,
    onElegir: (TematicaEmpresa) -> Unit,
    onCerrar: () -> Unit
) {
    val elegida = TematicaEmpresaStore.seleccion
    val consulta = busqueda.trim()
    val opciones = TematicaEmpresa.todas.filter {
        consulta.isEmpty() || it.nombre.contains(consulta, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                L.t("Temáticas de colores", "Color themes"),
                style = HeadlineSm, color = OnSurface, modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCerrar) { Text(L.t("Cerrar", "Close")) }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = busqueda,
            onValueChange = onBusqueda,
            placeholder = { Text(L.t("Buscar empresa", "Search company"), style = BodyMd) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp)) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SurfaceContainerLow,
                unfocusedContainerColor = SurfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(12.dp))
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                L.t(
                    "Elige la empresa a la que perteneces. Después podrás confirmar tu sede.",
                    "Choose the company you belong to. Then you can confirm your workplace."
                ),
                style = BodySm, color = OnSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            if (TransporteApp.busesUTPActivos) {
                Spacer(Modifier.height(6.dp))
                Text(
                    L.t(
                        "Confirmar otra empresa vuelve al transporte urbano y termina el viaje en curso.",
                        "Confirming another company returns to city transport and ends the current trip."
                    ),
                    style = BodyXs, color = OnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            if (opciones.isEmpty()) {
                Text(
                    L.t("No encontramos esa empresa.", "No matching company."),
                    style = BodySm, color = OnSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
            } else {
                // Grid de 2 columnas (filas de tarjetas, como el LazyVGrid iOS).
                opciones.chunked(2).forEach { fila ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                    ) {
                        fila.forEach { opcion ->
                            TarjetaTematica(
                                empresa = opcion,
                                elegida = elegida == opcion,
                                onClick = { onElegir(opcion) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (fila.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun TarjetaTematica(
    empresa: TematicaEmpresa,
    elegida: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceContainerLowest)
            .border(
                if (elegida) 2.dp else 1.dp,
                if (elegida) OnSurface else OutlineVariant,
                RoundedCornerShape(14.dp)
            )
            .clickable {
                com.example.rutautpnative.ui.components.AppHaptics.seleccion()
                onClick()
            }
            .padding(16.dp)
    ) {
        DiscoEmpresa(empresa, 44)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(PaletasEmpresa.paleta(empresa).colorMarca)
            )
            Text(empresa.nombre, style = BodySmMedium, color = OnSurface)
            Icon(
                if (elegida) Icons.Filled.CheckCircle else Icons.Filled.Business,
                null,
                tint = if (elegida) AppPrimary else Outline,
                modifier = Modifier.size(16.dp)
            )
        }
        if (empresa == TematicaEmpresa.UTP) {
            Spacer(Modifier.height(4.dp))
            Text(L.t("Predeterminada", "Default"), style = BodyXs, color = OnSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/// Contenido de "Mi sede de trabajo" (SedeTrabajoSheet iOS): cambiar empresa,
/// sedes verificadas de Trujillo, colores sin sede (Popeyes) y volver a UTP.
@Composable
fun ContenidoSedeTrabajo(
    empresaInicial: TematicaEmpresa?,
    onVolverATematicas: (() -> Unit)?,
    onCerrar: () -> Unit
) {
    var empresa by remember { mutableStateOf(empresaInicial ?: TransporteApp.sedeSeleccionada?.tematica ?: TematicaEmpresa.UTP) }
    var mostrarEmpresas by remember { mutableStateOf(false) }
    var busquedaSede by remember { mutableStateOf("") }

    val sedes = CatalogoSedesTrabajo.sedes(empresa)
    val consulta = busquedaSede.trim()
    val sedesFiltradas = sedes.filter {
        consulta.isEmpty() || it.nombre.contains(consulta, ignoreCase = true) ||
            it.direccion.contains(consulta, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onVolverATematicas != null) {
                TextButton(onClick = onVolverATematicas) { Text(L.t("Temáticas", "Themes")) }
            }
            Text(
                L.t("Mi sede de trabajo", "My workplace"),
                style = HeadlineSm, color = OnSurface, modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCerrar) { Text(L.t("Cerrar", "Close")) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            L.t(
                "Elige la sede a la que perteneces. El mapa y los colores usarán esa empresa al confirmar.",
                "Choose the workplace you belong to. The map and colors will use that company when you confirm."
            ),
            style = BodySm, color = OnSurfaceVariant
        )
        if (TransporteApp.busesUTPActivos && empresa != TematicaEmpresa.UTP) {
            Spacer(Modifier.height(6.dp))
            Text(
                L.t(
                    "Al elegir una sede volverás al transporte urbano y terminará el viaje en curso.",
                    "Choosing a workplace returns to city transport and ends the current trip."
                ),
                style = BodyXs, color = OnSurfaceVariant
            )
        }
        Spacer(Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceContainerLow)
                .clickable { mostrarEmpresas = !mostrarEmpresas }
                .padding(14.dp)
        ) {
            DiscoEmpresa(empresa, 32)
            Text(L.t("Cambiar empresa", "Change company"), style = BodyMdMedium, color = OnSurface, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
        }

        if (mostrarEmpresas) {
            Spacer(Modifier.height(8.dp))
            TematicaEmpresa.todas.forEach { marca ->
                val nota = when {
                    marca == TematicaEmpresa.UTP -> L.t("Predeterminado", "Default")
                    marca == TematicaEmpresa.POPEYES -> L.t("Próximamente", "Coming soon")
                    CatalogoSedesTrabajo.sedes(marca).isEmpty() -> L.t("Sin sedes verificadas", "No verified workplaces")
                    else -> null
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (empresa == marca) PrimaryFixed else SurfaceContainerLow)
                        .clickable {
                            empresa = marca
                            busquedaSede = ""
                            mostrarEmpresas = false
                        }
                        .padding(14.dp)
                ) {
                    DiscoEmpresa(marca, 28)
                    Text(marca.nombre, style = BodyMd, color = OnSurface, modifier = Modifier.weight(1f))
                    nota?.let { Text(it, style = BodyXs, color = OnSurfaceVariant) }
                    Icon(
                        if (empresa == marca) Icons.Filled.CheckCircle else Icons.Filled.Business,
                        null,
                        tint = if (empresa == marca) AppPrimary else Outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(L.t("SEDES EN TRUJILLO", "WORKPLACES IN TRUJILLO"), style = LabelCapsMd, color = OnSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (sedes.isEmpty()) {
            Text(
                if (empresa == TematicaEmpresa.POPEYES)
                    L.t(
                        "Próximamente. Aún no hay sedes de Popeyes disponibles para elegir en Trujillo.",
                        "Coming soon. There are no Popeyes workplaces available to select in Trujillo yet."
                    )
                else
                    L.t(
                        "No hay sedes verificadas de ${empresa.nombre} disponibles para elegir en Trujillo por ahora.",
                        "There are no verified ${empresa.nombre} workplaces available to select in Trujillo at present."
                    ),
                style = BodySm, color = OnSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                L.t(
                    "Tu referencia actual del mapa se conserva hasta que elijas una sede disponible.",
                    "Your current map reference is kept until you choose an available workplace."
                ),
                style = BodyXs, color = OnSurfaceVariant
            )
            if (TematicaEmpresaStore.seleccion != empresa) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        TransporteApp.seleccionarEmpresa(empresa)
                        onCerrar()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        L.t("Aplicar colores de ${empresa.nombre}", "Apply ${empresa.nombre} colors"),
                        style = BodyMdMedium, color = AppPrimary
                    )
                }
            }
        } else if (sedesFiltradas.isEmpty()) {
            Text(L.t("No encontramos esa sede.", "No matching workplace."), style = BodySm, color = OnSurfaceVariant)
        } else {
            sedesFiltradas.forEach { sede ->
                val seleccionada = TransporteApp.sedeSeleccionada?.id == sede.id
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppSurface)
                        .clickable {
                            com.example.rutautpnative.ui.components.AppHaptics.seleccion()
                            TransporteApp.usarSedeTrabajo(sede)
                            onCerrar()
                        }
                        .padding(14.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(sede.nombre, style = BodyMdMedium, color = OnSurface)
                        Text(sede.direccion, style = BodyXs, color = OnSurfaceVariant)
                    }
                    Icon(
                        if (seleccionada) Icons.Filled.CheckCircle else Icons.Filled.Place,
                        null,
                        tint = AppPrimary, modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        if (TransporteApp.sedeSeleccionada?.tematica != TematicaEmpresa.UTP) {
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = {
                    TransporteApp.usarSedeTrabajo(CatalogoSedesTrabajo.campusUTP)
                    onCerrar()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(L.t("Usar UTP como sede predeterminada", "Use UTP as the default workplace"), style = BodyMdMedium, color = AppPrimary)
            }
        }
    }
}
