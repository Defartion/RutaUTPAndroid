package com.example.rutautpnative.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.data.tracking.MQTTConfiguration
import com.example.rutautpnative.data.tracking.RouteChangesService
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

//----Sheet de cambios de ruta (puerto del RouteChangesSheet.swift)----
// Reportar o confirmar una incidencia (obras/cierre/desvio) sobre una linea,
// y ver las alertas confirmadas por la comunidad, vigentes 15 minutos.

private fun etiquetaMotivo(m: RouteChangesService.MotivoCambio): String = when (m) {
    RouteChangesService.MotivoCambio.OBRAS -> L.t("Obras en la vía", "Roadworks")
    RouteChangesService.MotivoCambio.CIERRE -> L.t("Vía cerrada", "Road closed")
    RouteChangesService.MotivoCambio.DESVIO -> L.t("El bus tomó un desvío", "The bus took a detour")
}

private fun iconoMotivo(m: RouteChangesService.MotivoCambio): ImageVector = when (m) {
    RouteChangesService.MotivoCambio.OBRAS -> Icons.Filled.Construction
    RouteChangesService.MotivoCambio.CIERRE -> Icons.Filled.Block
    RouteChangesService.MotivoCambio.DESVIO -> Icons.Filled.AltRoute
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteChangesSheet(
    onDismiss: () -> Unit,
    rutaInicial: String? = null
) {
    // El servicio vive y muere con el sheet (como el panel de ocupacion).
    val config = remember(com.example.rutautpnative.data.conexion.ConexionInstalacionStore.revision) { MQTTConfiguration.paraReportes() }
    val contexto = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val servicio = remember(config, contexto) { config?.let { RouteChangesService(it, contexto) } }

    DisposableEffect(servicio) {
        servicio?.start()
        onDispose { servicio?.stop() }
    }

    val listo by (servicio?.listo ?: remember { MutableStateFlow(false) }).collectAsState()
    val alertas by (servicio?.alertas ?: remember { MutableStateFlow(emptyList<RouteChangesService.AlertaCambio>()) }).collectAsState()
    val enviando by (servicio?.enviando ?: remember { MutableStateFlow(false) }).collectAsState()
    val resultado by (servicio?.resultado ?: remember { MutableStateFlow<String?>(null) }).collectAsState()

    // Catalogo GTFS para titulos de rutas (linea · variante · empresa).
    val rutas by produceState<List<RutaGTFS>>(emptyList()) {
        value = GTFSRepository.rutas()
    }
    fun rutaPorId(id: String): RutaGTFS? = rutas.firstOrNull { it.id == id }

    //----Estado del formulario----
    var rutaElegidaId by remember { mutableStateOf(rutaInicial) }
    var motivo by remember { mutableStateOf(RouteChangesService.MotivoCambio.OBRAS) }
    var punto by remember { mutableStateOf<LatLng?>(null) }
    var lugar by remember { mutableStateOf("") }
    var mostrarSelectorRuta by remember { mutableStateOf(false) }
    var mostrarPickerMapa by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    val rutaElegida = rutaElegidaId?.let { rutaPorId(it) }
    val puedeEnviar = config?.puedeContribuir == true && listo && !enviando && rutaElegidaId != null && punto != null && lugar.trim().length >= 3

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppSurface) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .verticalScroll(scroll)
                .padding(bottom = 24.dp)
        ) {
            Text(L.t("Cambios de ruta", "Route changes"), style = HeadlineMd, color = OnSurface)
            Spacer(Modifier.height(12.dp))

            //----Estado del servicio----
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (listo) Icons.Filled.Wifi else Icons.Filled.CloudOff,
                    null, tint = if (listo) Tertiary else OnSurfaceVariant, modifier = Modifier.size(16.dp)
                )
                Text(
                    when {
                        !RouteChangesService.configurado ->
                            L.t("Canal MQTT no configurado en esta compilación.", "MQTT channel not configured in this build.")
                        config?.puedeContribuir == false -> L.t("Cuenta de solo lectura. Puedes consultar los reportes.", "Read-only account. You can view reports.")
                        listo -> L.t("Reportes compartidos en línea.", "Reports shared online.")
                        else -> L.t("Conectando… Puedes preparar el reporte sin conexión.", "Connecting… You can prepare the report offline.")
                    },
                    style = BodySm, color = OnSurfaceVariant
                )
            }

            //----Cambios confirmados por la comunidad----
            if (alertas.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(L.t("CAMBIOS CONFIRMADOS", "CONFIRMED CHANGES"), style = LabelCapsMd, color = OnSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                alertas.forEach { alerta ->
                    val ruta = rutaPorId(alerta.routeId)
                    val motivoAlerta = alerta.motivo
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(32.dp).clip(CircleShape).background((ruta?.color ?: AppPrimary).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text((ruta?.linea ?: alerta.routeId), style = LabelCapsMd, color = ruta?.color ?: AppPrimary)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${ruta?.linea ?: alerta.routeId} · ${ruta?.variante ?: ""} · ${ruta?.empresa ?: ""}".trim('·'),
                                        style = BodySmMedium, color = OnSurface, maxLines = 1
                                    )
                                    if (motivoAlerta != null) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(iconoMotivo(motivoAlerta), null, tint = AppPrimary, modifier = Modifier.size(14.dp))
                                            Text(etiquetaMotivo(motivoAlerta), style = BodySm, color = AppPrimary)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.LocationOn, null, tint = OnSurfaceVariant, modifier = Modifier.size(14.dp))
                                Text(alerta.place, style = BodySm, color = OnSurfaceVariant, maxLines = 1)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                L.t("${alerta.confirmations} cuentas", "${alerta.confirmations} accounts") + " · " +
                                    L.t("vigente hasta", "valid until") + " ${alerta.vigenteHastaTexto}",
                                style = BodyXs, color = OnSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = {
                                // Precarga el formulario con esta alerta y salta a el.
                                rutaElegidaId = alerta.routeId
                                motivo = motivoAlerta ?: motivo
                                lugar = alerta.place
                                punto = LatLng(alerta.lat, alerta.lon)
                                scope.launch { scroll.animateScrollTo(scroll.maxValue) }
                            }) {
                                Text(L.t("Ver lugar y confirmar", "View place and confirm"), style = LabelCapsMd, color = AppPrimary)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            //----Formulario: reportar o confirmar un cambio----
            Spacer(Modifier.height(16.dp))
            Text(L.t("REPORTAR O CONFIRMAR UN CAMBIO", "REPORT OR CONFIRM A CHANGE"), style = LabelCapsMd, color = OnSurfaceVariant)
            Spacer(Modifier.height(10.dp))

            // Linea y ramal
            Text(L.t("Línea y ramal", "Line and branch"), style = LabelCapsSm, color = OnSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = rutaElegida?.let { "${it.linea}${if (it.variante.isNotEmpty()) " ${it.variante}" else ""}" }
                    ?: rutaElegidaId ?: "",
                onValueChange = {},
                readOnly = true,
                placeholder = { Text(L.t("Elige la línea afectada", "Pick the affected line"), style = BodySm) },
                trailingIcon = {
                    Icon(Icons.Filled.Search, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                },
                modifier = Modifier.fillMaxWidth().clickable { mostrarSelectorRuta = true },
                shape = RoundedCornerShape(12.dp),
                textStyle = BodySm.copy(color = OnSurface)
            )

            Spacer(Modifier.height(12.dp))

            // Motivo
            Text(L.t("¿Qué ocurre?", "What's going on?"), style = LabelCapsSm, color = OnSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RouteChangesService.MotivoCambio.entries.forEach { m ->
                    FilterChip(
                        selected = motivo == m,
                        onClick = { motivo = m },
                        label = { Text(etiquetaMotivo(m), style = BodyXs, maxLines = 1) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Punto afectado en el mapa
            Text(L.t("LUGAR AFECTADO", "AFFECTED SPOT"), style = LabelCapsSm, color = OnSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            if (punto != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = AppPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "%.4f, %.4f".format(punto?.latitude ?: 0.0, punto?.longitude ?: 0.0),
                        style = BodySm, color = OnSurface, modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { punto = null }) {
                        Text(L.t("Quitar", "Remove"), style = LabelCapsMd, color = AppError)
                    }
                }
            }
            OutlinedButton(
                onClick = { mostrarPickerMapa = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Edit, null, tint = AppPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (punto == null) L.t("Marcar el punto en el mapa", "Mark the point on the map")
                    else L.t("Ajustar el punto", "Adjust the point"),
                    style = BodyMdMedium, color = AppPrimary
                )
            }

            Spacer(Modifier.height(12.dp))

            // Calle o referencia
            OutlinedTextField(
                value = lugar,
                onValueChange = { if (it.length <= 120) lugar = it },
                placeholder = { Text(L.t("Calle o referencia (obligatorio)", "Street or landmark (required)"), style = BodySm) },
                supportingText = {
                    Text("${lugar.length}/120", style = BodyXs, color = if (lugar.length >= 118) AppError else OnSurfaceVariant)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(16.dp))

            // Resultado del ultimo envio (recibo del backend).
            resultado?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Tertiary, modifier = Modifier.size(16.dp))
                    Text(it, style = BodySm, color = OnSurface)
                }
                Spacer(Modifier.height(10.dp))
            }

            Button(
                onClick = {
                    val p = punto ?: return@Button
                    val idRuta = rutaElegidaId ?: return@Button
                    servicio?.enviar(idRuta, motivo, p.latitude, p.longitude, lugar.trim())
                },
                enabled = puedeEnviar,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary)
            ) {
                if (enviando) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text(L.t("Enviando…", "Sending…"), style = HeadlineSm, color = Color.White)
                } else {
                    Text(L.t("Reportar cambio", "Report change"), style = HeadlineSm, color = Color.White)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                L.t(
                    "Se necesitan dos cuentas distintas en la misma ruta, motivo y zona. Los reportes vencen a los 15 minutos. El aviso señala la incidencia; el recorrido alterno aún no está verificado.",
                    "Two different accounts are needed for the same route, reason and area. Reports expire after 15 minutes. The notice flags the incident; the alternate route isn't verified yet."
                ),
                style = BodyXs, color = OnSurfaceVariant
            )
        }
    }

    //----Selector de linea (buscador sobre el catalogo GTFS)----
    if (mostrarSelectorRuta) {
        SelectorRutaDialog(
            rutas = rutas,
            elegida = rutaElegidaId,
            onElegir = { rutaElegidaId = it; mostrarSelectorRuta = false },
            onCerrar = { mostrarSelectorRuta = false }
        )
    }

    //----Picker de punto en el mapa (pin central, arrastra el mapa)----
    if (mostrarPickerMapa) {
        MapaUbicacionPicker(
            inicial = punto,
            titulo = L.t("Arrastra el mapa hasta el lugar afectado", "Drag the map to the affected spot"),
            textoConfirmar = L.t("Usar este punto", "Use this point"),
            onConfirmar = { punto = it },
            onCerrar = { mostrarPickerMapa = false }
        )
    }
}

@Composable
private fun SelectorRutaDialog(
    rutas: List<RutaGTFS>,
    elegida: String?,
    onElegir: (String) -> Unit,
    onCerrar: () -> Unit
) {
    var texto by remember { mutableStateOf("") }
    val filtradas = remember(rutas, texto) {
        val q = texto.trim()
        if (q.isEmpty()) rutas
        else rutas.filter {
            it.linea.contains(q, true) || it.empresa.contains(q, true) ||
                it.recorrido.contains(q, true) || it.variante.contains(q, true)
        }
    }

    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 40.dp)
                .background(AppSurface, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Text(L.t("Elige la línea afectada", "Pick the affected line"), style = HeadlineSm, color = OnSurface)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                placeholder = { Text(L.t("Buscar línea, empresa o avenida", "Search line, company or avenue"), style = BodySm) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = OnSurfaceVariant) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                filtradas.forEach { ruta ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (ruta.id == elegida) PrimaryContainer else Color.Transparent)
                            .clickable { onElegir(ruta.id) }
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(34.dp).clip(CircleShape).background(ruta.color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(ruta.linea, style = LabelCapsMd, color = ruta.color)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${ruta.linea}${if (ruta.variante.isNotEmpty()) " ${ruta.variante}" else ""} · ${ruta.empresa}",
                                style = BodySmMedium, color = OnSurface, maxLines = 1
                            )
                            Text(ruta.recorrido, style = BodyXs, color = OnSurfaceVariant, maxLines = 1)
                        }
                        if (ruta.id == elegida) {
                            Icon(Icons.Filled.CheckCircle, null, tint = AppPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
