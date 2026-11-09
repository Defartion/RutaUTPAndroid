package com.example.rutautpnative.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.rutautpnative.data.TemaStore

//----Temáticas de empresa (puerto del TematicaEmpresa.swift)----
// IDs de apariencia, independientes de empresa del perfil y modo oscuro.
enum class TematicaEmpresa(val id: String, val nombre: String) {
    UTP("utp", "UTP"),
    INTERBANK("interbank", "Interbank"),
    POPEYES("popeyes", "Popeyes"),
    PLAZA_VEA("plaza_vea", "Plaza Vea"),
    CINEPLANET("cineplanet", "Cineplanet"),
    INNOVA_SCHOOLS("innova_schools", "Innova Schools"),
    INKAFARMA("inkafarma", "Inkafarma"),
    MIFARMA("mifarma", "Mifarma"),
    BEMBOS("bembos", "Bembos"),
    DON_BELISARIO("don_belisario", "Don Belisario");

    companion object {
        val empresas: List<TematicaEmpresa>
            get() = listOf(
                INTERBANK, POPEYES, PLAZA_VEA, CINEPLANET, INNOVA_SCHOOLS,
                INKAFARMA, MIFARMA, BEMBOS, DON_BELISARIO
            )
        val todas: List<TematicaEmpresa> get() = listOf(UTP) + empresas

        fun desdeId(id: String?): TematicaEmpresa? =
            entries.firstOrNull { it.id == id }
    }
}

/// Seleccion persistida de tematica. Cambiarla reasigna TODOS los tokens de
/// Color.kt con la paleta de la empresa (aplicarTemaOscuro resuelve tambien
/// la variante claro/oscuro vigente), asi que la UI recompone al instante —
/// igual que el TematicaEmpresaStore observable del iOS, sin .id().
object TematicaEmpresaStore {
    private const val PREFS = "rutautp.persistencia"
    const val LLAVE = "apariencia.tematica.empresa.v1"

    private var appContext: Context? = null
    private var _valor by mutableStateOf(TematicaEmpresa.UTP)

    val seleccion: TematicaEmpresa get() = _valor

    fun init(context: Context) {
        appContext = context.applicationContext
        val guardado = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(LLAVE, null)
        _valor = TematicaEmpresa.desdeId(guardado) ?: TematicaEmpresa.UTP
    }

    fun seleccionar(nueva: TematicaEmpresa) {
        if (nueva == _valor) return
        _valor = nueva
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putString(LLAVE, nueva.id)?.apply()
        // Reasigna los tokens de color con la paleta nueva y el tema vigente.
        aplicarTemaOscuro(TemaStore.oscuroActual)
    }
}
