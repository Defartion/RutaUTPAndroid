package com.example.rutautpnative

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.example.rutautpnative.data.ComunidadDatos
import com.example.rutautpnative.data.gtfs.*
import com.example.rutautpnative.model.*
import com.example.rutautpnative.navigation.*
import com.example.rutautpnative.ui.components.*
import com.example.rutautpnative.ui.screens.*
import com.example.rutautpnative.ui.screens.mapa.*
import com.example.rutautpnative.ui.theme.*
import com.google.android.gms.maps.model.LatLng
import java.util.Date

/** Componentes de producción con entradas fijas. Sin red, GPS ni render OpenGL. */
class Fase6Capturas {
    private val ruta get() = RutaGTFS("muestra", "C-01", "B", "Av. Miguel Grau → Av. Libertad", "El Cortijo", "00A850",
        Color(0xFF00A850), listOf(LatLng(-8.1,-79.0),LatLng(-8.11,-79.015),LatLng(-8.115,-79.02)),
        listOf(ParaderoGTFS("a","Av. Miguel Grau",-8.1,-79.0),ParaderoGTFS("b","Av. Libertad",-8.115,-79.02)),48,5,2.5,12.4,150.0)
    private val bus get() = BusAnimado("muestra", "C-01","muestra","El Cortijo","Micro","B",5,Color(0xFF00A850),
        -8.1,-79.0,90.0,emptyList(),doubleArrayOf(0.0),doubleArrayOf(0.0),1000.0,0.0,5.0,true,0,fuente=FuenteFlota.REAL, dataOrigin=com.example.rutautpnative.data.tracking.VehicleDataOrigin.OBSERVED)

    @PreviewTest @Preview(name="Claro",widthDp=390,heightDp=844)
    @Preview(name="Oscuro",widthDp=390,heightDp=844,uiMode=Configuration.UI_MODE_NIGHT_YES)
    @Preview(name="Texto150",widthDp=360,heightDp=800,fontScale=1.5f)
    @Preview(name="Texto200",widthDp=320,heightDp=800,fontScale=2f)
    @Composable fun mapa() = Mapa()
    @PreviewTest @Preview(widthDp=390,heightDp=844)
    @Composable fun mapaCarga() = Mapa(cargando=true)
    @PreviewTest @Preview(widthDp=390,heightDp=844)
    @Composable fun mapaError() = Mapa(fallo=true)
    @PreviewTest @Preview(widthDp=390,heightDp=844)
    @Composable fun mapaSinBuses() = Mapa(vacio=true)
    @PreviewTest @Preview(widthDp=390,heightDp=844)
    @Composable fun mapaUtpPendiente() = Mapa(utp=true)

    @PreviewTest @Preview(name="Claro",widthDp=390,heightDp=844)
    @Preview(name="Oscuro",widthDp=390,heightDp=844,uiMode=Configuration.UI_MODE_NIGHT_YES)
    @Preview(name="Texto150",widthDp=360,heightDp=800,fontScale=1.5f)
    @Preview(name="Texto200",widthDp=320,heightDp=800,fontScale=2f)
    @Composable fun guardadoLugares() = Guardado()
    @PreviewTest @Preview(widthDp=390,heightDp=844)
    @Composable fun guardadoVacio() = Guardado(vacio=true)
    @PreviewTest @Preview(name="Claro",widthDp=390,heightDp=844)
    @Preview(name="Oscuro",widthDp=390,heightDp=844,uiMode=Configuration.UI_MODE_NIGHT_YES)
    @Composable fun guardadoLineas() = Guardado(lineas=true)
    @PreviewTest @Preview(widthDp=390,heightDp=844)
    @Composable fun guardadoError() = Guardado(lineas=true,fallo=true)

    @PreviewTest @Preview(name="Claro",widthDp=390,heightDp=844)
    @Preview(name="Oscuro",widthDp=390,heightDp=844,uiMode=Configuration.UI_MODE_NIGHT_YES)
    @Preview(name="Texto150",widthDp=360,heightDp=800,fontScale=1.5f)
    @Preview(name="Texto200",widthDp=320,heightDp=800,fontScale=2f)
    @Composable fun seguridadAyuda() = Seguridad()
    @PreviewTest @Preview(widthDp=390,heightDp=844)
    @Composable fun seguridadComunidad() = Seguridad(comunidad=true)

    @PreviewTest @Preview(name="Texto150",widthDp=360,heightDp=800,fontScale=1.5f)
    @Preview(name="Texto200",widthDp=320,heightDp=800,fontScale=2f)
    @Composable fun perfilGrande() { RutaUTPNativeTheme { PerfilScreen(remember { AppRouter().apply { navigate(AppScreen.Perfil) } }) } }

    @PreviewTest @Preview(name="Claro",widthDp=390,heightDp=844)
    @Preview(name="Oscuro",widthDp=390,heightDp=844,uiMode=Configuration.UI_MODE_NIGHT_YES)
    @Composable fun detalleRuta() = Escena(AppScreen.Rutas) { DetalleRutaActual(ruta, {}, {}, {}) }
    @PreviewTest @Preview(widthDp=390,heightDp=844)
    @Composable fun rutasCarga() = Escena(AppScreen.Rutas) { RutasCatalogo(emptyList(),emptyMap(),null,true,null,"",{}, {}, {}, {}, {}) }
    @PreviewTest @Preview(widthDp=390,heightDp=844)
    @Composable fun rutasError() = Escena(AppScreen.Rutas) { RutasCatalogo(emptyList(),emptyMap(),null,false,"No se pudo cargar el catálogo de rutas.","",{}, {}, {}, {}, {}) }
    @PreviewTest @Preview(widthDp=390,heightDp=844)
    @Composable fun rutasSinResultados() = Escena(AppScreen.Rutas) { RutasCatalogo(emptyList(),emptyMap(),null,false,null,"XYZ",{}, {}, {}, {}, {}) }
    @PreviewTest @Preview(name="Claro",widthDp=390,heightDp=650)
    @Preview(name="Oscuro",widthDp=390,heightDp=650,uiMode=Configuration.UI_MODE_NIGHT_YES)
    @Composable fun visorError() = Visor(EstadoVisor3D.ERROR)
    @PreviewTest @Preview(widthDp=390,heightDp=650)
    @Composable fun visorCarga() = Visor(EstadoVisor3D.CARGANDO)

    @Composable private fun Escena(pantalla: AppScreen, contenido: @Composable BoxScope.() -> Unit) {
        val router=remember { AppRouter().apply { navigate(pantalla) } }
        RutaUTPNativeTheme(modoOscuro=isSystemInDarkTheme()) {
            Scaffold(containerColor=AppBackground,contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={BottomNavBar(router)}) { padding ->
                Box(Modifier.fillMaxSize().padding(padding),content=contenido)
            }
        }
    }
    @Composable private fun Mapa(cargando:Boolean=false,fallo:Boolean=false,vacio:Boolean=false,utp:Boolean=false) = Escena(AppScreen.MapaPrincipal) {
        Box(Modifier.fillMaxSize().background(SurfaceContainerLow),contentAlignment=Alignment.Center) {
            Text("Fondo de mapa de muestra",style=BodySm,color=OnSurfaceVariant)
        }
        MapaHeader({},fuenteReal=true,datosRecientes=!cargando&&!vacio&&!fallo&&!utp)
        Box(Modifier.align(Alignment.BottomCenter)) {
            BottomPanel(if(vacio||utp||cargando||fallo) emptyList() else listOf(bus),true,utp,false,{}, {}, {},
                cargando=cargando,fallo=fallo)
        }
    }
    @Composable private fun Guardado(lineas:Boolean=false,vacio:Boolean=false,fallo:Boolean=false) = Escena(AppScreen.Guardado) {
        Column(Modifier.fillMaxSize()) {
            CabeceraSeccion("Guardado",Icons.Filled.Bookmark)
            SelectorSeccion(listOf(OpcionSeccion("Lugares",Icons.Filled.Place),OpcionSeccion("Líneas",Icons.Filled.DirectionsBus)),if(lineas)1 else 0,{})
            FlowRow(Modifier.fillMaxWidth().padding(20.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(if(lineas)"1 línea" else if(vacio)"0 guardados" else "2 guardados",style=BodySm,color=OnSurfaceVariant)
                Button(onClick={}) { Text(if(lineas)"Añadir línea" else "Añadir lugar") }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(fallo) Column { Text("No se pudo cargar el catálogo de rutas.", color=AppError); OutlinedButton(onClick={}) { Text("Reintentar") } }
                else if(lineas) { TituloGuardado("Líneas guardadas",1);RutaOpcionCard(ruta,accion=AccionTarjetaRuta.GUARDADA) {} }
                else {
                    TituloGuardado("Lugares guardados",if(vacio)0 else 1)
                    if(vacio) VacioGuardado("Aún no tienes lugares guardados","Toca Añadir lugar para guardar tu primer lugar.")
                    else LugarRow(LugarGuardado("casa","Casa","Av. España 123",CategoriaLugar.HOGAR,true)) {}
                    TituloGuardado("Paraderos guardados",if(vacio)0 else 1)
                    if(!vacio) LugarRow(LugarGuardado("paradero","Av. Miguel Grau","Trujillo",CategoriaLugar.PARADERO)) {}
                    OutlinedButton(onClick={},modifier=Modifier.fillMaxWidth()) { Text("Explorar paraderos") }
                }
            }
        }
    }
    @Composable private fun Seguridad(comunidad:Boolean=false) = Escena(AppScreen.Seguridad) {
        Column(Modifier.fillMaxSize()) {
            CabeceraSeccion("Seguridad",Icons.Filled.Shield)
            SaludoSeguridad(Date(1794585600000L), java.util.TimeZone.getTimeZone("America/Lima"))
            SelectorSeccion(listOf(OpcionSeccion("Ayuda",Icons.Filled.HealthAndSafety),OpcionSeccion("Comunidad",Icons.Filled.People)),if(comunidad)1 else 0,{})
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
                if(comunidad) ComunidadDatos.publicaciones.take(2).forEach { ReporteCard(it,null,it.utiles,it.dislikes,{}, {}) }
                else { AyudaRapidaSeguridad({}, {}, {});RutasSegurasSection(2,listOf(LatLng(-8.1,-79.0)),{}) }
            }
        }
    }
    @Composable private fun Visor(estado:EstadoVisor3D) {
        RutaUTPNativeTheme(modoOscuro=isSystemInDarkTheme()) {
            Surface(color=AppBackground) {
                Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text("Reconoce tu transporte",style=HeadlineSm)
                    MarcoVisor3D(estado,{})
                    ControlesModelo3D(false,{}, {}, {}, {}, {})
                }
            }
        }
    }
}
