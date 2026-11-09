package com.example.rutautpnative.data

object ContactoEmergencia {
    fun telefonoValido(valor: String): String? {
        val texto = valor.trim()
        if (texto.any { it !in "0123456789+()- .\u00a0" } || texto.count { it == '+' } > 1 || ('+' in texto && !texto.startsWith('+'))) return null
        val digitos = texto.filter { it in '0'..'9' }
        if (digitos.length !in 7..15) return null
        return (if (texto.startsWith('+')) "+" else "") + digitos
    }
}
