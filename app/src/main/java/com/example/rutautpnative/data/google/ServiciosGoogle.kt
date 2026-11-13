package com.example.rutautpnative.data.google

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.rutautpnative.data.conexion.ConexionInstalacionStore
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** La clave del SDK se resuelve del manifiesto; las llamadas web usan otra credencial local. */
object ServiciosGoogle {
    private var application: Application? = null
    fun init(value: Context) { application = value.applicationContext as Application }
    val mapaConfigurado: Boolean get() = runCatching {
        val ctx = application ?: return@runCatching false
        val key = ctx.packageManager.getApplicationInfo(ctx.packageName, PackageManager.GET_META_DATA)
            .metaData?.getString("com.google.android.geo.API_KEY").orEmpty()
        key.isNotBlank() && !key.contains("${'$'}{")
    }.getOrDefault(false)
    @Suppress("DEPRECATION")
    fun identidad(): IdentidadAndroid? = runCatching {
        val ctx = application ?: return@runCatching null
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, flags)
        val firma = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners?.firstOrNull() else info.signatures?.firstOrNull()
        firma?.let { IdentidadAndroid(ctx.packageName, huellaSha1(it.toByteArray())) }
    }.getOrNull()
    fun claveWeb(): String? = ConexionInstalacionStore.claveWeb()
    fun huellaSha1(certificado: ByteArray): String = MessageDigest.getInstance("SHA-1").digest(certificado).joinToString("") { "%02X".format(it) }
}

data class IdentidadAndroid(val paquete: String, val certificado: String) {
    init { require(paquete.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) && certificado.matches(Regex("[A-Fa-f0-9]{40}"))) }
    fun cabeceras(): Map<String, String> = mapOf("X-Android-Package" to paquete, "X-Android-Cert" to certificado.uppercase())
}

internal data class RespuestaGoogle(val codigo: Int, val cuerpo: String)

/** Conexiones cancelables y sin redirecciones de la credencial a otro servidor. */
internal object GoogleHTTP {
    private val workers = Executors.newFixedThreadPool(4) { r -> Thread(r, "rutautp-google").apply { isDaemon = true } }
    suspend fun ejecutar(url: String, identidad: IdentidadAndroid, metodo: String = "GET", cuerpo: String? = null,
                         cabeceras: Map<String, String> = emptyMap()): RespuestaGoogle = suspendCancellableCoroutine { cont ->
        val con = URL(url).openConnection() as HttpURLConnection
        con.requestMethod = metodo; con.connectTimeout = 4000; con.readTimeout = 4000; con.instanceFollowRedirects = false
        (identidad.cabeceras() + cabeceras).forEach { (k,v) -> con.setRequestProperty(k,v) }
        if (cuerpo != null) { con.doOutput = true; con.setRequestProperty("Content-Type", "application/json") }
        val task = workers.submit {
            if (!cont.isActive) { con.disconnect(); return@submit }
            try {
                if (cuerpo != null) con.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(cuerpo) }
                val codigo = con.responseCode
                val stream = if (codigo in 200..299) con.inputStream else con.errorStream
                val texto = stream?.bufferedReader(Charsets.UTF_8)?.use {
                    val buffer = CharArray(524289); var count = 0
                    while (count < buffer.size) { val n = it.read(buffer,count,buffer.size-count); if (n<0) break; count+=n }
                    require(count <= 524288); String(buffer,0,count)
                }.orEmpty()
                if (cont.isActive) cont.resume(RespuestaGoogle(codigo, texto))
            } catch (e: Exception) { if (cont.isActive) cont.resumeWith(Result.failure(e)) }
            finally { con.disconnect() }
        }
        cont.invokeOnCancellation { con.disconnect(); task.cancel(true) }
    }
}
