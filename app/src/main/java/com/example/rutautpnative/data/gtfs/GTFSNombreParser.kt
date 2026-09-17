package com.example.rutautpnative.data.gtfs

//----Parser de nombres----
// Lógica pura para interpretar los nombres del feed (route_short_name y
// route_long_name). Portada de `GTFSNombreParser` (iOS).
object GTFSNombreParser {

    //----Resultado de separar línea/variante----
    data class LineaYVariante(
        val linea: String,
        val variante: String
    )

    /// `C-01 "B"` → línea "C-01", variante "B".
    fun lineaYVariante(shortName: String): LineaYVariante {
        val partes = shortName.split("\"")
        val linea = (partes.firstOrNull() ?: shortName).trim()
        val variante = if (partes.size > 1) partes[1].trim() else ""
        return LineaYVariante(
            linea = if (linea.isEmpty()) shortName else linea,
            variante = variante
        )
    }

    /// `C-01 "B" : Av. Grau → Av. Grau` → "Av. Grau → Av. Grau".
    /// Si origen == destino (ruta circular), devuelve "Origen (ramal circular)".
    fun recorrido(longName: String, shortName: String): String {
        var nombre = longName
        val prefijo = "$shortName : "
        if (nombre.startsWith(prefijo)) {
            nombre = nombre.removePrefix(prefijo)
        } else {
            val idx = nombre.indexOf(" : ")
            if (idx >= 0) {
                nombre = nombre.substring(idx + 3)
            }
        }
        nombre = nombre.trim()

        val partes = nombre.split("→").map { it.trim() }
        if (partes.size == 2 && partes[0] == partes[1] && partes[0].isNotEmpty()) {
            return "${partes[0]} (ramal circular)"
        }
        return nombre
    }
}