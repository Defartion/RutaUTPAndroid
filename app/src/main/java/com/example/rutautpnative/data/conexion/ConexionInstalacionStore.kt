package com.example.rutautpnative.data.conexion

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.rutautpnative.BuildConfig
import java.io.File
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Archivo atómico sin backup. La clave AES no sale de Android Keystore. */
internal class ArchivoConexionCifrado(context: Context, espacio: String = "conexion-v1") : PersistenciaConexion {
    private val archivo = AtomicFile(File(context.noBackupFilesDir, "$espacio/credenciales.bin"))
    private val aad = "RutaUTP.Conexion.v1:${context.packageName}".toByteArray()
    private val alias = "rutautp.conexion.aes.v1.$espacio"
    init { require(espacio.matches(Regex("[a-z0-9-]{1,80}"))) }
    private fun clave(crear: Boolean): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        check(crear) { "Clave no disponible" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).setKeySize(256).build())
        }.generateKey()
    }
    fun reiniciar() {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(alias)) ks.deleteEntry(alias)
        archivo.delete()
    }
    override fun leer(): String? {
        if (!archivo.baseFile.exists() && !File(archivo.baseFile.path + ".bak").exists()) return null
        val bytes = archivo.openRead().use { input ->
            val buffer = ByteArray(131073)
            var total = 0
            while (total < buffer.size) {
                val n = input.read(buffer, total, buffer.size - total)
                if (n < 0) break
                total += n
            }
            buffer.copyOf(total)
        }
        require(bytes.size in 30..131072 && bytes[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, clave(false), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(aad)
        return cipher.doFinal(bytes.copyOfRange(13, bytes.size)).toString(Charsets.UTF_8)
    }
    override fun escribir(valor: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, clave(true)); cipher.updateAAD(aad)
        val bytes = byteArrayOf(1) + cipher.iv + cipher.doFinal(valor.toByteArray())
        require(cipher.iv.size == 12 && bytes.size <= 131072)
        check(archivo.baseFile.parentFile?.let { it.exists() || it.mkdirs() } == true)
        val stream = archivo.startWrite()
        try { stream.write(bytes); archivo.finishWrite(stream) }
        catch (e: Exception) { archivo.failWrite(stream); throw e }
    }
}

object ConexionInstalacionStore {
    private var repositorio: RepositorioConexion? = null
    private var archivo: ArchivoConexionCifrado? = null
    private var validador: (CanalConexion) -> Unit = {}
    var revision by mutableIntStateOf(0); private set
    var revisionWeb by mutableIntStateOf(0); private set
    var disponible by mutableStateOf(false); private set
    var autorizacionRechazada by mutableStateOf(false); private set
    fun init(context: Context) {
        if (repositorio != null) return
        val app = context.applicationContext
        val assets = app.assets
        validador = { canal ->
            if (canal.caPem.isNotBlank()) {
                require(CertificateFactory.getInstance("X.509").generateCertificates(canal.caPem.byteInputStream()).isNotEmpty())
            }
            if (canal.caAsset.isNotEmpty()) assets.open(canal.caAsset).use {
                require(CertificateFactory.getInstance("X.509").generateCertificates(it).isNotEmpty())
            }
        }
        try {
            archivo = ArchivoConexionCifrado(app)
            repositorio = RepositorioConexion(requireNotNull(archivo), BuildConfig.DEBUG, validador)
        } catch (_: Exception) { repositorio = null }
        disponible = repositorio?.disponible == true
    }
    fun reiniciar(): Boolean = try {
        requireNotNull(archivo).reiniciar()
        repositorio = RepositorioConexion(requireNotNull(archivo), BuildConfig.DEBUG, validador)
        disponible = repositorio?.disponible == true
        if (disponible) { revision++; revisionWeb++; autorizacionRechazada = false }
        disponible
    } catch (_: Exception) { disponible = false; false }
    fun receptor(): CuentaConexion? = repositorio?.receptor()
    fun contribucion(): CuentaConexion? = repositorio?.cuenta(RolConexion.CONTRIBUCION)
    fun cuenta(rol: RolConexion): CuentaConexion? = repositorio?.cuenta(rol)
    fun claveWeb(): String? = repositorio?.claveWeb()
    fun guardar(cuenta: CuentaConexion): Boolean {
        val repo = repositorio ?: return false
        val antes = repo.revision
        val ok = repo.guardar(cuenta)
        if (ok) { if (repo.revision != antes || autorizacionRechazada) revision++; autorizacionRechazada = false }
        return ok
    }
    fun borrar(rol: RolConexion): Boolean {
        val repo = repositorio ?: return false
        val antes = repo.revision
        val ok = repo.borrarCuenta(rol)
        if (ok) { if (repo.revision != antes) revision++; autorizacionRechazada = false }
        return ok
    }
    fun guardarClaveWeb(clave: String): Boolean {
        val repo = repositorio ?: return false
        val antes = repo.revisionWeb
        val ok = repo.guardarClaveWeb(clave)
        if (ok && repo.revisionWeb != antes) revisionWeb++
        return ok
    }
    fun rechazar(cuenta: CuentaConexion) {
        if (repositorio?.cuenta(cuenta.rol) == cuenta) autorizacionRechazada = true
    }
}
