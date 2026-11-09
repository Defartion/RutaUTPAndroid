package com.example.rutautpnative.ui.screens.mapa

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.data.gtfs.IndiceBusquedaRuta
import com.example.rutautpnative.data.routing.TransitPlanner
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import kotlin.math.ceil
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlternativasRutaSheet(vm: MapaViewModel, onCerrar: () -> Unit) {
    var consulta by remember { mutableStateOf("") }
    val original = remember { vm.itinerario?.optionID }
    LaunchedEffect(vm.itinerario?.optionID) { if (vm.itinerario?.optionID != original) onCerrar() }
    ModalBottomSheet(onDismissRequest = onCerrar, containerColor = AppSurface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 20.dp)) {
            Text(L.t("Ver otras rutas", "See other routes"), style = HeadlineMd, color = OnSurface)
            Text(L.t("Compara el tiempo estimado y cuánto necesitas caminar. Las caminatas se comprueban al elegir.", "Compare estimated travel time and walking distance. Walking legs are checked when selected."), style = BodySm, color = OnSurfaceVariant)
            OutlinedTextField(consulta, { consulta = it }, label = { Text(L.t("Letra, línea o empresa", "Letter, line or company")) }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
            vm.mensajeAlternativa?.let { Text(it, color = AppError) }
            if (vm.seleccionandoAlternativa) LinearProgressIndicator(Modifier.fillMaxWidth())
            val palabras = IndiceBusquedaRuta.normalizar(consulta).split(" ").filter { it.isNotBlank() }
            val opciones = vm.alternativas.filter { plan ->
                val texto = IndiceBusquedaRuta.normalizar(plan.ruta.lineaConLetra + " " + plan.ruta.empresa + " " + plan.transbordo?.ruta?.lineaConLetra.orEmpty() + " " + plan.transbordo?.ruta?.empresa.orEmpty())
                palabras.all { it in texto }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                if (opciones.isEmpty()) item { Text(L.t("No hay rutas que coincidan con tu búsqueda.", "No routes match your search.")) }
                items(opciones, key = { it.optionID }) { plan ->
                    OutlinedCard(onClick = { if (!vm.seleccionandoAlternativa) vm.seleccionarAlternativa(plan) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val categorias = vm.destacadas.firstOrNull { it.plan.optionID == plan.optionID }?.categorias.orEmpty()
                            if (categorias.isNotEmpty()) Text(categorias.joinToString(" · ") { when (it) {
                                TransitPlanner.Categoria.RECOMENDADA -> L.t("Recomendada", "Recommended")
                                TransitPlanner.Categoria.MAS_RAPIDA -> L.t("Más rápida", "Fastest")
                                TransitPlanner.Categoria.SUBIDA_CERCANA -> L.t("Subida más cercana", "Closest boarding")
                            } }, style = BodyXs, color = AppPrimary)
                            Text(plan.ruta.lineaConLetra + (plan.transbordo?.let { " → " + it.ruta.lineaConLetra } ?: ""), style = HeadlineSm)
                            Text(plan.ruta.empresa, style = BodySm)
                            Text("~${ceil(plan.viajeEstimadoSeg / 60).toInt()} min · " + L.f("Camina {0} m", "Walk {0} m", "${plan.caminataTotalMetros.roundToInt()}"), style = BodySm)
                            Text(L.f("Sube en {0} · a {1} m", "Board at {0} · {1} m away", plan.board.nombre, "${plan.caminataBoardMetros.roundToInt()}"), style = BodyXs)
                            Text(if (plan.transbordo == null) L.t("Sin transbordos", "No transfers") else L.t("1 transbordo", "1 transfer"), style = BodyXs)
                            if (plan.caminataAproximada) Text(L.t("Distancias aproximadas", "Approximate distances"), style = BodyXs, color = OnSurfaceVariant)
                            if (plan.optionID == vm.itinerario?.optionID) Text(L.t("Seleccionada", "Selected"), color = AppPrimary)
                        }
                    }
                }
            }
        }
    }
}
