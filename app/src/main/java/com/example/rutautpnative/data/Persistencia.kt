package com.example.rutautpnative.data

import android.content.Context

//----Persistencia versionada (puerto del Persistencia.swift)----
// Inventario central de claves + versionado del esquema + migraciones
// idempotentes al arranque (igual que el iOS: version 1, nunca borra datos).
//
// NOTA: AppHaptics y DatosPersonalesStore fueron eliminados en la limpieza
// (L3) porque no tenian consumidores. Si se necesitan, el inventario de
// claves los documenta para su reintroduccion.
object Persistencia {
    private const val PREFS = "rutautp.persistencia"
    const val VERSION_ESQUEMA = 1
    const val LLAVE_VERSION = "persistencia.esquema.version"

    // Inventario de claves conocidas (documentacion; nadie las borra).
    val clavesConocidas = listOf(
        "persistencia.esquema.version",
        "lugares.guardados.v2",
        "lineas.guardadas.v1",
        "negocios.cupones.guardados",
        "seguridad.tiles.v1",
        "seguridad.tiles.orden.v1",
        "idioma.app",
        "isDarkMode",
        "ciudadSeleccionada",
        "senias.modo.activado",
        "perfil_notificaciones",
        "perfil_compartirUbicacion",
        "perfil_telefono",
        "perfil_correoPersonal",
        "emergencia_nombre",
        "emergencia_parentesco",
        "emergencia_numero",
        "monedero.saldo.v1",
        "monedero.movimientos.v1",
        "tracking.radioParadero",
        "tracking.velocidadDemo",
        "rutautp.passive-tracking-consent",
        "rutautp.boarding-place-notes.v1",
        "apariencia.tematica.empresa.v1",
        "inicio.sede.trabajo.id.v1",
        "inicio.referencia.utp.v1",
        "transporte.buses.utp.v1",
        // Visibilidad de los carnets por empresa: patron
        // "perfil.carnet.<foto|digital>.<empresa>.visible.v1".
        "perfil.carnet.foto.utp.visible.v1",
        "perfil.carnet.digital.utp.visible.v1"
    )

    /// Migraciones idempotentes al arranque. Nunca borra datos; solo aplica
    /// transformaciones ordenadas con version > instalada y sella la version.
    fun migrarSiHaceFalta(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val instalada = prefs.getInt(LLAVE_VERSION, 0)

        if (instalada >= VERSION_ESQUEMA) return instalada

        // v1: semilla. No hay migraciones de v0 (instalacion nueva).
        // Cuando exista v2+, se anadiran aqui en orden.

        prefs.edit().putInt(LLAVE_VERSION, VERSION_ESQUEMA).apply()
        return VERSION_ESQUEMA
    }
}
