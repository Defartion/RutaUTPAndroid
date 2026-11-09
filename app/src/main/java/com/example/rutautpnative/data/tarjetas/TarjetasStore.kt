package com.example.rutautpnative.data.tarjetas

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

//----Referencias de tarjetas (puerto del TarjetasStore.swift)----
// Reglas honestas del iOS, respetadas al byte:
//   - Solo se guardan los ULTIMOS 4 DIGITOS + red + nombre: nunca el numero
//     completo ni el CVV. No permite pagar, recargar el monedero ni usar NFC.
//   - El almacen es CIFRADO (EncryptedSharedPreferences, el equivalente
//     directo del Keychain con kSecAttrAccessibleWhenUnlockedThisDeviceOnly).
//   - `principal` es la primera de la lista; hacerPrincipal reordena.
object TarjetasStore {

    @Serializable
    data class TarjetaGuardada(
        val id: String = UUID.randomUUID().toString(),
        val nombre: String,     // <= 60 chars
        val red: String,        // Visa / Mastercard / ...
        val ultimos4: String    // exactamente 4 digitos ASCII
    ) {
        val etiqueta: String get() = "$red •••• $ultimos4"
    }

    private const val ARCHIVO = "rutautp_tarjetas"
    private const val LLAVE = "local"   // account "local" del iOS
    private const val SERVICIO = "RutaUTP.tarjetas.referencias.v1"

    private var prefs: SharedPreferences? = null
    private val json = Json { ignoreUnknownKeys = true }

    private val _tarjetas = MutableStateFlow<List<TarjetaGuardada>>(emptyList())
    val tarjetas: StateFlow<List<TarjetaGuardada>> = _tarjetas.asStateFlow()

    val principal: TarjetaGuardada? get() = _tarjetas.value.firstOrNull()

    fun init(context: Context) {
        val ctx = context.applicationContext
        // Keychain -> EncryptedSharedPreferences (dispositivo local, sin backup).
        prefs = try {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                ctx,
                ARCHIVO,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Si no está disponible el cifrado, no se escribe una copia en texto plano.
            null
        }
        cargar()
    }

    //----Operaciones (los errores nunca dejan el estado a medias)----

    fun agregar(nombre: String, red: String, ultimos4: String): Boolean {
        if (nombre.isBlank() || red.isBlank() || (ultimos4.length != 4 || ultimos4.any { it !in '0'..'9' })) return false
        val nueva = _tarjetas.value + TarjetaGuardada(
            nombre = nombre.trim().take(60),
            red = red.trim(),
            ultimos4 = ultimos4
        )
        return if (persistir(nueva)) {
            _tarjetas.value = nueva
            true
        } else false
    }

    fun eliminar(id: String): Boolean {
        val nueva = _tarjetas.value.filter { it.id != id }
        return if (persistir(nueva)) {
            _tarjetas.value = nueva
            true
        } else false
    }

    /// Reordena poniendo la elegida primero ("Elegir como principal" del iOS).
    fun hacerPrincipal(id: String) {
        val actual = _tarjetas.value
        val elegida = actual.firstOrNull { it.id == id } ?: return
        val nueva = listOf(elegida) + actual.filter { it.id != id }
        if (persistir(nueva)) _tarjetas.value = nueva
    }

    //----Interno----

    private fun cargar() {
        val raw = prefs?.getString(LLAVE, null) ?: return
        try {
            _tarjetas.value = json.decodeFromString<List<TarjetaGuardada>>(raw)
        } catch (e: Exception) {
            // JSON corrupto: se arranca vacio (los datos previos se conservan
            // en disco; no hay nada recuperable que hacer con 4 digitos).
            _tarjetas.value = emptyList()
        }
    }

    private fun persistir(lista: List<TarjetaGuardada>): Boolean = try {
        prefs?.edit()?.putString(LLAVE, json.encodeToString(lista))?.commit() == true
    } catch (e: Exception) {
        false
    }
}
