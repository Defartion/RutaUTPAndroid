package com.example.rutautpnative.data.tracking

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

object MapVehiclePreferences {
    val opciones = listOf(0, 5, 10, 15, 20)
    private var context: Context? = null
    var limite by mutableIntStateOf(0)
        private set
    fun init(value: Context) {
        context = value.applicationContext
        limite = value.getSharedPreferences("rutautp.persistencia", Context.MODE_PRIVATE).getInt("mapa.micros-visibles.v1", 0).takeIf { it in opciones } ?: 0
    }
    fun establecer(value: Int) {
        if (value !in opciones) return
        limite = value
        context?.getSharedPreferences("rutautp.persistencia", Context.MODE_PRIVATE)?.edit()?.putInt("mapa.micros-visibles.v1", value)?.apply()
    }
    fun <T> visibles(vehicles: List<T>, selectedID: String?, id: (T) -> String): List<T> = limitar(vehicles, limite, selectedID, id)
    fun <T> limitar(vehicles: List<T>, limit: Int, selectedID: String?, id: (T) -> String): List<T> {
        if (limit <= 0 || vehicles.size <= limit) return vehicles
        val visible = vehicles.take(limit).toMutableList()
        val selected = vehicles.firstOrNull { id(it) == selectedID }
        if (selected != null && visible.none { id(it) == selectedID }) visible[visible.lastIndex] = selected
        return visible
    }
}
