package com.example.rutautpnative

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.example.rutautpnative.data.gtfs.*
import com.example.rutautpnative.navigation.*
import com.example.rutautpnative.ui.components.BottomNavBar
import com.example.rutautpnative.ui.screens.*
import com.example.rutautpnative.ui.theme.*
import com.example.rutautpnative.data.monedero.PagoQR

class PantallasIosCapturas {
    @PreviewTest
    @Preview(name = "Rutas", widthDp = 390, heightDp = 844)
    @Composable
    fun rutas() { Catalogo() }

    @PreviewTest
    @Preview(name = "Rutas texto grande", widthDp = 360, heightDp = 800, fontScale = 1.5f)
    @Composable
    fun rutasTextoGrande() { Catalogo() }

    @PreviewTest
    @Preview(name = "Rutas oscuro", widthDp = 390, heightDp = 844)
    @Composable
    fun rutasOscuro() { Catalogo(oscuro = true) }

    @PreviewTest
    @Preview(name = "Perfil", widthDp = 390, heightDp = 844)
    @Composable
    fun perfil() { RutaUTPNativeTheme { PerfilScreen(remember { AppRouter().apply { navigate(AppScreen.Perfil) } }) } }

    @PreviewTest
    @Preview(name = "Perfil oscuro", widthDp = 390, heightDp = 844)
    @Composable
    fun perfilOscuro() { RutaUTPNativeTheme(modoOscuro = true) { PerfilScreen(remember { AppRouter().apply { navigate(AppScreen.Perfil) } }) } }

    @PreviewTest
    @Preview(name = "Recargar saldo", widthDp = 390, heightDp = 844)
    @Composable
    fun recarga() { RutaUTPNativeTheme { androidx.compose.material3.Surface(color = AppBackground) { FormularioRecarga(1000L, emptyList(), false, 1000L, {}, PagoQR.Billetera.YAPO, {}, false, false, {}, {}, {}) } } }

    @PreviewTest
    @Preview(name = "Recargar texto grande", widthDp = 360, heightDp = 800, fontScale = 1.5f)
    @Composable
    fun recargaTextoGrande() { RutaUTPNativeTheme { androidx.compose.material3.Surface(color = AppBackground) { FormularioRecarga(1000L, emptyList(), false, 1000L, {}, PagoQR.Billetera.YAPO, {}, false, false, {}, {}, {}) } } }

    @PreviewTest
    @Preview(name = "Pagar con QR", widthDp = 390, heightDp = 844)
    @Composable
    fun pagarQR() { RutaUTPNativeTheme { androidx.compose.material3.Surface(color = AppBackground) { FormularioPagoQR(PagoQR.Billetera.YAPO, {}, remember { PagoQR.imagen(PagoQR.cargaUtil(PagoQR.cuentaDemo)) }, false, {}, {}, {}) } } }

    @Composable
    private fun Catalogo(oscuro: Boolean = false) {
        val ruta = RutaGTFS("muestra-a", "C-01", "B", "Av. Miguel Grau → Av. Libertad", "El Cortijo", "00A850", Color(0xFF00A850), emptyList(), listOf(ParaderoGTFS("a", "Av. Miguel Grau", -8.1, -79.0), ParaderoGTFS("b", "Av. Libertad", -8.11, -79.02)), 48, 5, 2.5, 12.4, 150.0)
        val router = remember { AppRouter().apply { navigate(AppScreen.Rutas) } }
        RutaUTPNativeTheme(modoOscuro = oscuro) {
            Scaffold(containerColor = AppBackground, contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = { BottomNavBar(router) }) { padding ->
                Box(Modifier.padding(padding)) { RutasCatalogo(listOf(ruta, ruta.copy(id = "muestra-b", linea = "M-34", variante = "A", empresa = "Nuevo California", color = Color(0xFFEEB633))), emptyMap(), null, false, null, "", {}, {}, {}, {}, {}) }
            }
        }
    }
}
