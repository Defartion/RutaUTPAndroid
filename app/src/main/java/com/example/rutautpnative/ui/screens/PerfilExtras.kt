package com.example.rutautpnative.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SettingsAccessibility
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.TransporteApp
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*

//----Modo offline y ayuda de accesibilidad (Fase de perfil completo)----

/// Qué funciona y qué no sin conexión (puerto del OfflineMapSheet.swift):
/// comprueba el feed REAL (cuenta las rutas) y explica qué se conserva y qué
/// necesita internet. No descarga mapas base ni simula conectividad.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapSheet(onCerrar: () -> Unit) {
    var comprobando by remember { mutableStateOf(true) }
    var numeroRutas by remember { mutableStateOf(0) }
    var revision by remember { mutableStateOf(0) }
    var errorCarga by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(revision) {
        comprobando = true
        errorCarga = null
        try {
            numeroRutas = GTFSRepository.rutas().size
        } catch (e: Exception) {
            errorCarga = L.t("No se pudieron cargar las rutas. Vuelve a intentarlo.", "Couldn't load routes. Try again.")
        }
        comprobando = false
    }

    ModalBottomSheet(onDismissRequest = onCerrar, containerColor = AppSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L.t("Modo offline", "Offline mode"), style = HeadlineSm, color = OnSurface, modifier = Modifier.weight(1f))
                TextButton(onClick = onCerrar) { Text(L.t("Listo", "Done")) }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .background(PrimaryFixed),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (comprobando) Icons.Filled.Storage
                    else if (numeroRutas > 0) Icons.Filled.SettingsAccessibility
                    else Icons.Filled.CloudOff,
                    null, tint = AppPrimary, modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    comprobando -> L.t("Comprobando datos locales…", "Checking local data…")
                    numeroRutas > 0 -> L.t("Rutas disponibles sin conexión", "Routes available offline")
                    errorCarga != null -> L.t("No se pudieron cargar las rutas", "Couldn't load routes")
                    TransporteApp.rutasUTPPendientes -> L.t("Rutas UTP próximamente", "UTP routes coming soon")
                    else -> L.t("El catálogo no contiene rutas", "The catalog contains no routes")
                },
                style = HeadlineMd, color = OnSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(12.dp))
            if (comprobando) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally).size(20.dp),
                    strokeWidth = 2.dp, color = AppPrimary
                )
            } else if (numeroRutas > 0) {
                Text(
                    L.t(
                        "$numeroRutas rutas cargadas desde la app",
                        "$numeroRutas routes loaded from the app"
                    ),
                    style = BodySmMedium, color = OnSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    L.t(
                        "Los recorridos y paraderos vienen incluidos en la app. No necesitas descargarlos ni activar un interruptor para consultarlos sin internet.",
                        "Routes and stops are included in the app. No download or switch is needed to view them offline."
                    ),
                    style = BodySm, color = OnSurfaceVariant
                )
            } else {
                Text(
                    errorCarga ?: if (TransporteApp.rutasUTPPendientes) TransporteApp.mensajePendiente
                    else L.t(
                        "El catálogo local se cargó correctamente, pero no incluye rutas.",
                        "The local catalog loaded successfully but contains no routes."
                    ),
                    style = BodySm, color = OnSurfaceVariant
                )
                if (errorCarga != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { revision++ }, shape = RoundedCornerShape(10.dp)) {
                        Text(L.t("Reintentar", "Retry"), color = AppPrimary)
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Storage, null, tint = AppPrimary, modifier = Modifier.size(18.dp))
                Text(L.t("También se conserva", "Also kept on this device"), style = BodyMdMedium, color = OnSurface)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                L.t(
                    "Tus lugares y líneas guardados, y las fotos del perfil y carné que hayas añadido.",
                    "Your saved places and lines, plus any profile and card photos you have added."
                ),
                style = BodySm, color = OnSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.WifiOff, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(L.t("Necesita conexión", "Requires a connection"), style = BodyMdMedium, color = OnSurface)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                L.t(
                    "La búsqueda de direcciones y el cálculo de indicaciones de Google Maps requieren internet. El mapa base puede no mostrarse si no está en caché. Esta app no descarga mapas de Google para uso offline ni garantiza navegación completa sin conexión.",
                    "Address search and Google Maps directions require internet. The base map may be unavailable when it isn't cached. This app doesn't download Google maps for offline use or guarantee fully offline navigation."
                ),
                style = BodySm, color = OnSurfaceVariant
            )
        }
    }
}

/// Ayuda de accesibilidad (puerto del VoiceOverHelpSheet, adaptado a TalkBack):
/// estado real del lector de pantalla, pasos de activación, gestos y acceso
/// directo a los Ajustes de accesibilidad de Android.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AyudaAccesibilidadSheet(onCerrar: () -> Unit) {
    val contexto = LocalContext.current
    val talkbackActivo = remember { talkbackHabilitado(contexto) }

    ModalBottomSheet(onDismissRequest = onCerrar, containerColor = AppSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L.t("Ayuda de TalkBack", "TalkBack help"), style = HeadlineSm, color = OnSurface, modifier = Modifier.weight(1f))
                TextButton(onClick = onCerrar) { Text(L.t("Cerrar", "Close")) }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Accessibility, null, tint = AppPrimary, modifier = Modifier.size(22.dp))
                Text(
                    if (talkbackActivo) L.t("TalkBack está activado", "TalkBack is on")
                    else L.t("TalkBack está desactivado", "TalkBack is off"),
                    style = BodyMdMedium, color = AppPrimary
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                L.t(
                    "TalkBack describe los controles y lee la pantalla. La activación se realiza en Android; esta app mantiene sus controles accesibles aunque TalkBack esté apagado.",
                    "TalkBack describes controls and reads the screen. You turn it on in Android; this app keeps its controls accessible even when TalkBack is off."
                ),
                style = BodySm, color = OnSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))

            Text(L.t("Activación en tu teléfono", "Turn it on on your phone"), style = BodyMdMedium, color = OnSurface)
            Spacer(Modifier.height(8.dp))
            val pasos = listOf(
                L.t("Abre Ajustes en tu teléfono.", "Open Settings on your phone."),
                L.t("Entra en Accesibilidad y luego en TalkBack.", "Go to Accessibility, then TalkBack."),
                L.t("Activa o desactiva TalkBack con su interruptor.", "Use the switch to turn TalkBack on or off.")
            )
            pasos.forEachIndexed { i, paso ->
                Text("${i + 1}. $paso", style = BodySm, color = OnSurface, modifier = Modifier.padding(vertical = 2.dp))
            }
            Text(
                L.t(
                    "También puedes mantener pulsados los botones de volumen para activarlo o desactivarlo.",
                    "You can also press and hold both volume buttons to turn it on or off."
                ),
                style = BodyXs, color = OnSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(18.dp))

            Text(L.t("Cómo navegar", "How to navigate"), style = BodyMdMedium, color = OnSurface)
            Spacer(Modifier.height(8.dp))
            listOf(
                L.t("Explorar", "Explore") to
                    L.t("Desliza un dedo a derecha o izquierda para recorrer los controles.", "Swipe one finger right or left to move between controls."),
                L.t("Activar un control", "Activate a control") to
                    L.t("Después de escucharlo, toca dos veces en cualquier parte de la pantalla.", "After hearing it, double-tap anywhere on the screen."),
                L.t("Desplazar una lista", "Scroll a list") to
                    L.t("Desliza con dos dedos para ver más contenido.", "Swipe with two fingers to scroll."),
                L.t("Acciones adicionales", "Additional actions") to
                    L.t("En controles con acciones, el menú de TalkBack permite elegir opciones como abrir un reporte o mover un punto del mapa.", "On controls with actions, the TalkBack menu lets you choose options such as opening a report or moving a map point.")
            ).forEach { (titulo, texto) ->
                Text(titulo, style = BodySmMedium, color = OnSurface)
                Text(texto, style = BodyXs, color = OnSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }

            OutlinedButton(
                onClick = {
                    try {
                        contexto.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } catch (e: Exception) {
                        // Sin actividad capaz de abrirlo: no rompe la hoja.
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.SettingsAccessibility, null, tint = AppPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(L.t("Abrir Ajustes de accesibilidad", "Open accessibility settings"), color = AppPrimary)
            }
        }
    }
}

/// Estado real del lector de pantalla: hay algun servicio de accesibilidad
/// con feedback hablado habilitado (TalkBack y equivalentes).
private fun talkbackHabilitado(contexto: Context): Boolean = try {
    val am = contexto.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_SPOKEN).isNotEmpty()
} catch (e: Exception) {
    false
}
