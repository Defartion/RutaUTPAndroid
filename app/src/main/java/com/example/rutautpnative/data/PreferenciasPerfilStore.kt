package com.example.rutautpnative.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.TematicaEmpresa

//----Preferencias y datos del perfil (puerto de las @AppStorage del iOS)----
//Telefono/correo personales, contacto de emergencia, notificaciones y
//compartir ubicacion, todas persistentes con las claves del inventario de
//Persistencia. Espejos reactivos: la UI recomponen al instante.

/// Lista de parentesco (Parentesco del iOS). El rawValue en español es la
/// CLAVE estable guardada en `emergencia_parentesco`: traducirlo invalidaria
/// el dato ya guardado; `label` si traduce para mostrar.
enum class Parentesco(val clave: String) {
    PADRE_MADRE("Padre/Madre"),
    TUTOR_APODERADO("Tutor/Apoderado"),
    CONYUGE_PAREJA("Conyuge/Pareja de hecho"),
    HERMANO("Hermano(a)"),
    HIJO("Hijo(a)"),
    AMISTAD("Amigo(a)"), ABUELO("Abuelo(a)"), TIO("Tío(a)"), PRIMO("Primo(a)"),
    OTRO("Otro");

    val label: String
        get() = when (this) {
            PADRE_MADRE -> L.t("Padre/Madre", "Parent")
            TUTOR_APODERADO -> L.t("Tutor/Apoderado", "Guardian")
            CONYUGE_PAREJA -> L.t("Conyuge/Pareja de hecho", "Spouse/Partner")
            HERMANO -> L.t("Hermano(a)", "Sibling")
            HIJO -> L.t("Hijo(a)", "Child")
            AMISTAD -> L.t("Amigo(a)", "Friend")
            ABUELO -> L.t("Abuelo(a)", "Grandparent")
            TIO -> L.t("Tío(a)", "Aunt/Uncle")
            PRIMO -> L.t("Primo(a)", "Cousin")
            OTRO -> L.t("Otro", "Other")
        }

    companion object {
        /// Conserva valores antiguos desconocidos sin romperse.
        fun desdeClave(clave: String): String? =
            entries.firstOrNull { it.clave == clave }?.let { it.label } ?: clave.ifEmpty { null }
    }
}

object PreferenciasPerfilStore {
    private const val PREFS = "rutautp.persistencia"

    private var appContext: Context? = null

    //----Datos personales editables (persisten)----
    var telefono by mutableStateOf("999888777")
        private set
    var correoPersonal by mutableStateOf("joaquin.diaz@gmail.com")
        private set

    //----Contacto de emergencia (persisten)----
    var emergenciaNombre by mutableStateOf("")
        private set
    var emergenciaParentesco by mutableStateOf("")
        private set
    var emergenciaNumero by mutableStateOf("")
        private set
    var emergenciaAlternativo by mutableStateOf("")
        private set

    //----Preferencias del perfil (persisten; iOS: @AppStorage)----
    var notificaciones by mutableStateOf(false)
        private set
    var compartirUbicacion by mutableStateOf(false)
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        telefono = prefs.getString("perfil_telefono", telefono) ?: telefono
        correoPersonal = prefs.getString("perfil_correoPersonal", correoPersonal) ?: correoPersonal
        emergenciaNombre = prefs.getString("emergencia_nombre", "") ?: ""
        emergenciaParentesco = prefs.getString("emergencia_parentesco", "") ?: ""
        emergenciaNumero = prefs.getString("emergencia_numero", "") ?: ""
        emergenciaAlternativo = prefs.getString("emergencia_numeroAlternativo", "") ?: ""
        notificaciones = prefs.getBoolean("perfil_notificaciones", false)
        compartirUbicacion = prefs.getBoolean("perfil_compartirUbicacion", false)
    }

    fun establecerTelefono(valor: String) {
        telefono = valor
        escribir { it.putString("perfil_telefono", valor) }
    }

    fun establecerCorreoPersonal(valor: String) {
        correoPersonal = valor
        escribir { it.putString("perfil_correoPersonal", valor) }
    }

    fun establecerEmergencia(nombre: String, parentesco: String, numero: String, alternativo: String = emergenciaAlternativo) {
        emergenciaNombre = nombre
        emergenciaParentesco = parentesco
        emergenciaNumero = numero
        emergenciaAlternativo = alternativo
        escribir {
            it.putString("emergencia_nombre", nombre)
            it.putString("emergencia_parentesco", parentesco)
            it.putString("emergencia_numero", numero)
            it.putString("emergencia_numeroAlternativo", alternativo)
        }
    }

    fun establecerNotificaciones(activo: Boolean) {
        notificaciones = activo
        escribir { it.putBoolean("perfil_notificaciones", activo) }
        com.example.rutautpnative.data.notificaciones.NotificacionesService.refrescar()
        com.example.rutautpnative.data.notificaciones.PushRutasService.sincronizar()
    }

    fun establecerCompartirUbicacion(activo: Boolean) {
        compartirUbicacion = activo
        escribir { it.putBoolean("perfil_compartirUbicacion", activo) }
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun escribir(bloque: (android.content.SharedPreferences.Editor) -> Unit) {
        prefs()?.edit()?.apply {
            bloque(this)
            apply()
        }
    }
}

/// Tarjetas del carné en la billetera (CarnetPerfil del iOS).
enum class CarnetPerfil(val id: String) {
    FOTO("foto"),
    DIGITAL("digital");

    fun titulo(empresa: TematicaEmpresa): String = when {
        this == DIGITAL -> L.t("Carné Digital", "Digital ID")
        empresa == TematicaEmpresa.UTP -> L.t("Carnet Universitario", "University Card")
        else -> L.t("Carnet de empresa", "Company Card")
    }
}

/// Solo controla la VISIBILIDAD de las tarjetas del carné; ocultarlas nunca
/// borra sus fotos. Las elecciones se recuerdan POR EMPRESA (iOS idem).
object PreferenciasVisualesPerfilStore {
    private const val PREFS = "rutautp.persistencia"

    private var appContext: Context? = null
    private val valores = mutableStateMapOf<String, Boolean>()

    private fun clave(carnet: CarnetPerfil, empresa: TematicaEmpresa): String =
        "perfil.carnet.${carnet.id}.${empresa.id}.visible.v1"

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for (empresa in TematicaEmpresa.todas) {
            for (carnet in CarnetPerfil.entries) {
                val k = clave(carnet, empresa)
                if (prefs.contains(k)) valores[k] = prefs.getBoolean(k, true)
            }
        }
    }

    /// Por defecto, la empresa UTP muestra ambas tarjetas; el resto arranca
    /// sin el carné físico hasta que el usuario lo pida (iOS idem).
    fun visible(carnet: CarnetPerfil, empresa: TematicaEmpresa): Boolean =
        valores[clave(carnet, empresa)] ?: (empresa == TematicaEmpresa.UTP)

    fun establecer(visible: Boolean, carnet: CarnetPerfil, empresa: TematicaEmpresa) {
        val k = clave(carnet, empresa)
        valores[k] = visible
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(k, visible)?.apply()
    }
}
