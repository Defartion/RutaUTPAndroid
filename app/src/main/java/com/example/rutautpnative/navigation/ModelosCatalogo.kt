package com.example.rutautpnative.navigation

import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Conservado por la actividad: rota sin perder modelos; cambiar catálogo los retira. */
class ModelosCatalogo : ViewModel() {
    class Contenedor(override val defaultViewModelProviderFactory: ViewModelProvider.Factory) :
        ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
        override val viewModelStore = ViewModelStore()
    }
    private var catalogo: Boolean? = null
    private var contenedor: Contenedor? = null
    private var cambioPendiente: Int? = null
    var revision = 0
        private set

    fun obtener(institucional: Boolean, factory: ViewModelProvider.Factory): Contenedor {
        if (catalogo != institucional || contenedor == null) {
            if (contenedor != null) {
                cambioPendiente = revision
                revision += 1
                contenedor!!.viewModelStore.clear()
            }
            catalogo = institucional
            contenedor = Contenedor(factory)
        }
        return contenedor!!
    }
    fun consumirCambio(): Int? = cambioPendiente.also { cambioPendiente = null }
    override fun onCleared() { contenedor?.viewModelStore?.clear() }
}
