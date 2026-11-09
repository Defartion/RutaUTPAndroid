package com.example.rutautpnative.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.example.rutautpnative.data.senias.SeniasOverlay
import com.example.rutautpnative.data.senias.SeniasPrefs
import com.example.rutautpnative.ui.idioma.L

//----Helper "señable" (puerto de L.signable / .seniable del iOS)----
// Dos comportamientos, como en iOS:
//   - TEXTOS (Signable/Modifier.signable): tocar muestra el mini-reproductor
//     de esa clave EN BUCLE, sin acción (las señas consultadas desde textos
//     conservan su bucle).
//   - BOTONES (Modifier.senableAccion): tocar muestra la tarjeta 3 s y luego
//     ejecuta la acción UNA sola vez; cerrar la tarjeta la adelanta; con el
//     Modo Señas apagado la acción va directa.
//
// Con el modo apagado, todo se comporta exactamente igual que sin señas.

/**
 * Texto señable: traducible (L.t) y, con el modo activo, al tocarlo muestra
 * el mini-reproductor en bucle.
 */
@Composable
fun Signable(
    clave: String,
    es: String,
    en: String,
    modifier: Modifier = Modifier,
    style: TextStyle = androidx.compose.ui.text.TextStyle.Default,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE
) {
    val activo by SeniasPrefs.observarActivo().collectAsState(initial = false)
    Box(
        modifier = if (activo) {
            modifier.clickable { SeniasOverlay.mostrarTexto(clave) }
        } else {
            modifier
        }
    ) {
        Text(L.t(es, en), style = style, color = color, maxLines = maxLines)
    }
}

/**
 * Variante Modifier para TEXTOS señables (bucle): agrega a elementos con
 * layout propio. Uso: Text("Rutas", modifier = Modifier.signable("rutas.titulo"))
 */
fun Modifier.signable(clave: String): Modifier = this.composed {
    val activo by SeniasPrefs.observarActivo().collectAsState(initial = false)
    if (activo) {
        clickable { SeniasOverlay.mostrarTexto(clave) }
    } else {
        this
    }
}

/**
 * Variante Modifier para BOTONES señables (puerto de ejecutarTrasVerSenia):
 * con el modo activo, muestra la tarjeta 3 s y LUEGO ejecuta la acción una
 * sola vez (cerrar la tarjeta la adelanta); con el modo apagado, directo.
 * Uso: Modifier.senableAccion("nav.iniciar") { navegar() }
 */
fun Modifier.senableAccion(clave: String, accion: () -> Unit): Modifier = this.composed {
    val activo by SeniasPrefs.observarActivo().collectAsState(initial = false)
    if (activo) {
        clickable { SeniasOverlay.ejecutarTrasVerSenia(clave, accion) }
    } else {
        clickable { accion() }
    }
}
