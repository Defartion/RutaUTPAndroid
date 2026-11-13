package com.example.rutautpnative.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*

/** Mismo contenido y política del AvisoDiarioDetalleView de iOS, en una hoja Android. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvisoDiarioSheet(tipo: Int, puedeNavegar: Boolean, onAbrir: () -> Unit, onCerrar: () -> Unit) {
    val titulo = when (tipo) {
        1 -> L.t("Consulta los cambios de ruta", "Check route changes")
        2 -> L.t("Revisa las condiciones de tus cupones", "Check your coupon terms")
        3 -> L.t("Prepara tu próximo viaje", "Prepare your next trip")
        else -> L.t("Antes de salir", "Before heading out")
    }
    val mensaje = when (tipo) {
        1 -> L.t("Abre la consulta de cambios para revisar los reportes disponibles de tu línea. Sin información reciente no se puede confirmar el estado de la ruta.", "Open route changes to check available reports for your line. Without recent information, the route status cannot be confirmed.")
        2 -> L.t("Antes de canjear un cupón, comprueba sus condiciones, su fecha de vencimiento y la confirmación del negocio.", "Before redeeming a coupon, check its terms, expiry date and confirmation from the business.")
        3 -> L.t("Guarda las líneas que usas y revisa tu destino con tiempo para preparar tu salida.", "Save the lines you use and check your destination ahead of time to prepare your departure.")
        else -> L.t("Revisa tu línea y sus paraderos antes de salir. Los horarios del catálogo son una referencia para planear tu viaje.", "Check your line and stops before heading out. Catalog timetables are a reference for planning your trip.")
    }
    ModalBottomSheet(onDismissRequest = onCerrar, containerColor = AppSurface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(L.t("Avisos del día", "Daily notifications"), style = HeadlineMd, color = OnSurface)
            Text(titulo, style = HeadlineSm, color = OnSurface)
            Text(mensaje, style = BodySm, color = OnSurfaceVariant)
            if (tipo == 2) Text(L.t("El catálogo actual de negocios es de demostración. Todavía no hay descuentos reales verificados para enviar avisos.", "The current business catalog is for demonstration. There are no verified real discounts for notifications yet."), style = BodySm, color = OnSurfaceVariant)
            if (puedeNavegar) Button(onClick = onAbrir, modifier = Modifier.fillMaxWidth()) {
                Text(when (tipo) {
                    1 -> L.t("Consultar cambios de ruta", "Check route changes")
                    2 -> L.t("Mis cupones", "My coupons")
                    3 -> L.t("Guardado", "Saved")
                    else -> L.t("Ver rutas", "View routes")
                })
            } else Text(L.t("Puedes leer este consejo y continuar tu viaje. Revisa las otras pantallas cuando termines.", "You can read this tip and continue your trip. Check the other screens when you finish."), style = BodySm, color = OnSurfaceVariant)
            TextButton(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) { Text(L.t("Cerrar", "Close")) }
        }
    }
}
