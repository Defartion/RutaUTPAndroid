package com.example.rutautpnative

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.rutautpnative.navigation.*
import com.example.rutautpnative.ui.components.BottomNavBar
import com.example.rutautpnative.ui.idioma.L
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Solo monta la barra de navegación: no abre mapas, GPS, cámaras ni servicios. */
class NavegacionAccesibleInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private fun comprobar(escala: Float) {
        val router=AppRouter().apply { navigate(AppScreen.MapaPrincipal) }
        var densidad=1f
        compose.setContent {
            densidad=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(densidad,escala)) {
                MaterialTheme { Box(Modifier.width(320.dp)) { BottomNavBar(router) } }
            }
        }
        for (label in listOf(L.t("Mapa","Map"),L.t("Rutas","Routes"),L.t("Guardado","Saved"),L.t("Seguridad","Safety"),L.t("Perfil","Profile"))) {
            val node=compose.onNodeWithText(label).assertIsDisplayed().assertHasClickAction()
            assertTrue(node.fetchSemanticsNode().boundsInRoot.height >= 48f*densidad)
        }
        compose.onNodeWithText(L.t("Rutas","Routes")).performClick().assertIsSelected()
        compose.runOnIdle { assertSame(AppScreen.Rutas,router.currentScreen) }
    }
    @Test fun barraConTextoAlCientoCincuentaPorCiento() = comprobar(1.5f)
    @Test fun barraConTextoAlDoscientosPorCiento() = comprobar(2f)
}
