package com.example.rutautpnative.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/** Una posición independiente por subpestaña, retenida durante la sesión. */
class EstadoGuardadoViewModel : ViewModel() {
    var pestana by mutableIntStateOf(0)
    private val listas = List(2) { LazyListState() }
    val lista: LazyListState get() = listas[pestana]
}
