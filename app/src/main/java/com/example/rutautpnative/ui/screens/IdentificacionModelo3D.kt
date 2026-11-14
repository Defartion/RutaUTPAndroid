package com.example.rutautpnative.ui.screens

import com.example.rutautpnative.data.gtfs.RutaGTFS

private fun ReferenciaRuta.corresponde(ruta: RutaGTFS) =
    routeId == ruta.id && linea == ruta.codigoTransporte && letra == ruta.letraTransporte && empresa == ruta.empresa

internal fun tipoModeloRuta(ruta: RutaGTFS, referencia: ReferenciaRuta? = null): String? {
    if (ruta.id.startsWith("utp:")) return null
    referencia?.takeIf { it.corresponde(ruta) }?.modelo?.takeIf { it in setOf("micro", "combi", "colectivo") }?.let { return it }
    if (ruta.id == "17522705" && ruta.codigoTransporte == "C-44" && ruta.letraTransporte == "T2" && ruta.empresa == "Miramar") return "micro"
    return when {
        ruta.codigoTransporte.startsWith("M-") -> "micro"
        ruta.codigoTransporte.startsWith("C-") -> "combi"
        else -> null
    }
}

internal fun identidadModelo(ruta: RutaGTFS?, referencia: ReferenciaRuta?): IdentidadModelo3D? = ruta?.let {
    val colores = referencia?.takeIf { ref -> ref.corresponde(it) }?.colores.orEmpty()
    IdentidadModelo3D(it.codigoTransporte, it.letraTransporte,
        colores.map { c -> (0xFF000000L or c.hex.removePrefix("#").toLong(16)).toInt() }, it.id, it.empresa)
}
