package com.example.rutautpnative.data.conexion

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.Locale

@Serializable
enum class RolConexion { LECTURA, CONTRIBUCION }

@Serializable
data class CanalConexion(val host: String, val puerto: Int = 8883, val tls: Boolean = true,
                         val caAsset: String = "", val caPem: String = "") {
    val identidad: String get() = URI(if (tls) "ssl" else "tcp", null, host.lowercase(Locale.ROOT).removeSuffix("."), puerto, null, null, null).toASCIIString()
    fun validar(planoLocal: Boolean) {
        require(host.isNotBlank() && host.length <= 253 && host.none { it.isWhitespace() || it in "/@?#\\" }) { "Servidor no válido" }
        require(puerto in 1..65535) { "Puerto no válido" }
        val uri = URI(identidad)
        require(uri.host != null && uri.userInfo == null && uri.rawPath.orEmpty().isEmpty()) { "Servidor no válido" }
        require(tls || (planoLocal && esLocal(host))) { "Usa una conexión protegida" }
        require(caPem.length <= 32768 && (caPem.isEmpty() || tls)) { "Certificado no válido" }
        require(caAsset.isEmpty() || (tls && !caAsset.startsWith("/") && caAsset.split('/').all { it.isNotEmpty() && it != ".." && it != "." } && caAsset.matches(Regex("[A-Za-z0-9_./-]+")))) { "Certificado no válido" }
    }
    companion object {
        private fun esLocal(host: String): Boolean {
            val h = host.lowercase(Locale.ROOT).removePrefix("[").removeSuffix("]")
            if (h == "localhost" || h == "::1" || h.endsWith(".local")) return true
            val v = h.split('.').map { it.toIntOrNull() ?: return false }
            if (v.size != 4 || v.any { it !in 0..255 }) return false
            return v[0] == 127 || v[0] == 10 || (v[0] == 192 && v[1] == 168) || (v[0] == 172 && v[1] in 16..31)
        }
    }
}

@Serializable
data class CuentaConexion(val canal: CanalConexion, val rol: RolConexion, val usuario: String, val clave: String) {
    fun validar(planoLocal: Boolean) {
        canal.validar(planoLocal)
        require(usuario.matches(Regex("[A-Za-z0-9._-]{1,128}")) && clave.isNotEmpty() && clave.length <= 1024 && '\u0000' !in clave) { "Cuenta no válida" }
        require(usuario != "backend" && !usuario.startsWith("sim-")) { "Esta cuenta no pertenece a un teléfono" }
        require(rol != RolConexion.CONTRIBUCION || usuario !in setOf("observer", "presentation-reader")) { "Esta cuenta es de solo lectura" }
    }
    override fun toString() = "CuentaConexion(canal=${canal.identidad}, rol=$rol, credenciales=ocultas)"
}

@Serializable
internal data class DatosConexion(val version: Int = 1, val activo: String? = null,
                                  val cuentas: List<CuentaConexion> = emptyList(), val claveWeb: String = "") {
    override fun toString() = "DatosConexion(version=$version, cuentas=${cuentas.size}, secretos=ocultos)"
}

interface PersistenciaConexion {
    fun leer(): String?
    fun escribir(valor: String)
}

/** No mezcla parejas ni endpoints; solo publica cambios después de una escritura segura. */
class RepositorioConexion(private val persistencia: PersistenciaConexion, private val planoLocal: Boolean = false,
                          private val validarCA: (CanalConexion) -> Unit = {}) {
    private var datos = DatosConexion()
    var disponible = false; private set
    var revision = 0; private set
    var revisionWeb = 0; private set
    init { recargar() }
    @Synchronized fun recargar(): Boolean = try {
        val raw = persistencia.leer()
        val next = if (raw == null) DatosConexion() else Json.decodeFromString<DatosConexion>(raw)
        require(next.version == 1 && next.cuentas.size <= 32 && next.claveWeb.length <= 4096)
        next.cuentas.forEach { it.validar(planoLocal) }
        require(next.cuentas.map { it.canal.identidad to it.rol }.distinct().size == next.cuentas.size)
        require(next.cuentas.groupBy { it.canal.identidad }.values.all { v -> v.map { it.usuario }.distinct().size == v.size })
        require(next.activo == null || next.cuentas.any { it.canal.identidad == next.activo })
        datos = next; disponible = true; true
    } catch (_: Exception) { datos = DatosConexion(); disponible = false; false }
    @Synchronized fun guardar(cuenta: CuentaConexion): Boolean {
        if (!disponible) return false
        return try {
            cuenta.validar(planoLocal); validarCA(cuenta.canal)
            val lista = datos.cuentas.filterNot { it.canal.identidad == cuenta.canal.identidad && it.rol == cuenta.rol } + cuenta
            require(lista.size <= 32)
            require(lista.groupBy { it.canal.identidad }.values.all { v -> v.map { it.usuario }.distinct().size == v.size })
            val next = datos.copy(activo = cuenta.canal.identidad, cuentas = lista)
            if (next != datos) { persistir(next); revision++ }
            true
        } catch (_: Exception) { false }
    }
    @Synchronized fun guardarClaveWeb(clave: String): Boolean {
        if (!disponible || clave.length > 4096 || clave.any { it.isWhitespace() || it.isISOControl() }) return false
        return try {
            val next = datos.copy(claveWeb = clave)
            if (next != datos) { persistir(next); revisionWeb++ }
            true
        } catch (_: Exception) { false }
    }
    @Synchronized fun borrarCuenta(rol: RolConexion): Boolean {
        if (!disponible) return false
        return try {
            val lista = datos.cuentas.filterNot { it.canal.identidad == datos.activo && it.rol == rol }
            val activo = datos.activo?.takeIf { a -> lista.any { it.canal.identidad == a } }
            val next = datos.copy(activo = activo, cuentas = lista)
            if (next != datos) { persistir(next); revision++ }
            true
        } catch (_: Exception) { false }
    }
    @Synchronized fun cuenta(rol: RolConexion): CuentaConexion? = if (!disponible) null else
        datos.cuentas.firstOrNull { it.canal.identidad == datos.activo && it.rol == rol }
    @Synchronized fun receptor(): CuentaConexion? = cuenta(RolConexion.LECTURA) ?: cuenta(RolConexion.CONTRIBUCION)
    @Synchronized fun claveWeb(): String? = datos.claveWeb.takeIf { disponible && it.isNotBlank() }
    private fun persistir(next: DatosConexion) {
        persistencia.escribir(Json.encodeToString(DatosConexion.serializer(), next))
        datos = next
    }
}
