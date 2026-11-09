package com.example.rutautpnative.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.data.LugaresStore
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.navigation.AppRouter
import com.example.rutautpnative.navigation.AppScreen
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ParaderosGuardadosSeguridad(router: AppRouter, rutas: List<RutaGTFS>) {
    val lugares by LugaresStore.observar().collectAsState(initial = emptyList())
    val catalogo = remember(rutas) { rutas.flatMap { it.paraderos }.distinctBy { it.id } }
    val guardados = lugares.filter { it.esParaderoGuardadoEn(catalogo) }
    val scope = rememberCoroutineScope()
    Text(L.t("Paraderos guardados", "Saved stops"), style = HeadlineSm, color = OnSurface)
    if (guardados.isEmpty()) Text(L.t("Guarda paraderos para verlos aquí.", "Save stops to see them here."), style = BodySm, color = OnSurfaceVariant)
    guardados.forEach { lugar ->
        OutlinedCard(onClick = { router.lugarGuardadoPendiente = lugar.id; router.navigate(AppScreen.Guardado) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(lugar.nombre, style = BodyMdMedium)
                Row {
                    TextButton(onClick = {
                        if (lugar.lat != null && lugar.lon != null) {
                            router.destinoPendiente = com.example.rutautpnative.navigation.DestinoPendiente(lugar.nombre, lugar.lat, lugar.lon)
                            router.navigate(AppScreen.MapaPrincipal)
                        }
                    }) { Text(L.t("Ver ubicación", "View location")) }
                    TextButton(onClick = { scope.launch { LugaresStore.modificar { lista -> lista.filter { it.id != lugar.id || it.esFijo } } } }) { Text(L.t("Quitar", "Remove")) }
                }
            }
        }
    }
}
