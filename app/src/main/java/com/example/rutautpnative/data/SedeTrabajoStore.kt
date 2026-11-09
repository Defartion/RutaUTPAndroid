package com.example.rutautpnative.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.rutautpnative.model.CatalogoSedesTrabajo
import com.example.rutautpnative.model.SedeTrabajo
import com.example.rutautpnative.ui.theme.TematicaEmpresa

//----Sede seleccionada (puerto del SedeTrabajoStore.swift)----
// UTP es el inicio sin una elección previa. Conserva solo el ID estable; no
// lee disco en cada render ni abre el GPS. El espejo reactivo (mutableStateOf)
// hace que mapa, chips y perfiles reaccionen al elegir una sede.
object SedeTrabajoStore {
    private const val PREFS = "rutautp.persistencia"
    const val LLAVE = "inicio.sede.trabajo.id.v1"
    const val LLAVE_REFERENCIA_UTP = "inicio.referencia.utp.v1"

    private var appContext: Context? = null

    var sede by mutableStateOf<SedeTrabajo?>(null)
        private set
    var utpComoReferencia by mutableStateOf(true)
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val idGuardado = prefs.getString(LLAVE, null)
        val sedeGuardada = idGuardado?.let { CatalogoSedesTrabajo.sede(it) }
        val hayBandera = prefs.contains(LLAVE_REFERENCIA_UTP)
        val referenciaGuardada = if (hayBandera) prefs.getBoolean(LLAVE_REFERENCIA_UTP, true) else null
        // Mantener sedes y elecciones explícitas anteriores. Un regreso a UTP
        // de la versión previa se refleja también en la sede mostrada.
        val usarUTP = (referenciaGuardada ?: (idGuardado == null)) || sedeGuardada?.tematica == TematicaEmpresa.UTP
        sede = if (usarUTP) CatalogoSedesTrabajo.campusUTP else sedeGuardada
        utpComoReferencia = usarUTP
    }

    fun seleccionar(id: String?) {
        val nueva = id?.let { CatalogoSedesTrabajo.sede(it) }
        // Una entrada desconocida no sustituye la elección existente.
        if (id != null && nueva == null) return
        configurarReferenciaUTP(nueva?.tematica == TematicaEmpresa.UTP)
        sede = nueva
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.let { prefs ->
            val editor = prefs.edit()
            if (nueva != null) editor.putString(LLAVE, nueva.id) else editor.remove(LLAVE)
            editor.apply()
        }
    }

    fun usarCampusUTP() = seleccionar(CatalogoSedesTrabajo.campusUTP.id)

    private fun configurarReferenciaUTP(activa: Boolean) {
        utpComoReferencia = activa
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(LLAVE_REFERENCIA_UTP, activa)?.apply()
    }
}
