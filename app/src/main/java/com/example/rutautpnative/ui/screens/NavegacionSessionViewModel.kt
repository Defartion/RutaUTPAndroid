package com.example.rutautpnative.ui.screens

import androidx.lifecycle.ViewModel
import com.example.rutautpnative.data.gtfs.RutaGTFS

internal class NavegacionSessionViewModel : ViewModel() {
    private var modelo: NavegacionVM? = null
    fun obtener(ruta: RutaGTFS): NavegacionVM {
        if (modelo?.ruta?.id != ruta.id) { finalizar(); modelo = NavegacionVM(ruta) }
        return checkNotNull(modelo)
    }
    fun finalizar() { modelo?.detener(); modelo = null }
    override fun onCleared() { finalizar() }
}
