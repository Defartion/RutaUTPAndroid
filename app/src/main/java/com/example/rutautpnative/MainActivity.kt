package com.example.rutautpnative

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rutautpnative.data.LineasGuardadasStore
import com.example.rutautpnative.data.LugaresStore
import com.example.rutautpnative.data.SeguridadTilesStore
import com.example.rutautpnative.data.TemaStore
import com.example.rutautpnative.data.directions.DirectionsService
import com.example.rutautpnative.data.negocios.CuponesStore
import com.example.rutautpnative.data.negocios.NegociosService
import com.example.rutautpnative.data.places.PlacesService
import com.example.rutautpnative.data.senias.SeniasPrefs
import com.example.rutautpnative.data.senias.SeniasService
import com.example.rutautpnative.data.gtfs.GTFSRepository
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.navigation.AppRouter
import com.example.rutautpnative.ui.theme.RutaUTPNativeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        GTFSRepository.init(this)
        LugaresStore.init(this)
        SeguridadTilesStore.init(this)
        LineasGuardadasStore.init(this)
        DirectionsService.init(this)
        PlacesService.init(this)
        NegociosService.init(this)
        CuponesStore.init(this)
        SeniasService.init(this)
        SeniasPrefs.init(this)
        TemaStore.init(this)
        L.init(this)
        setContent {
            // Tema oscuro manual (persistido): la raíz de la app reacciona solo.
            val modoOscuro by TemaStore.observarOscuro().collectAsState(initial = false)
            RutaUTPNativeTheme(modoOscuro = modoOscuro) {
                val router: AppRouter = viewModel()
                RootView(router)
            }
        }
    }
}