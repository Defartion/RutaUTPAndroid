package com.example.rutautpnative.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID

/** Un restablecimiento solo puede sustituir datos después de guardar una copia privada y exportable. */
object RecuperacionPreferencias {
    fun archivar(context: Context, grupo: String, originales: Map<String, Any?>): File {
        require(grupo.matches(Regex("[a-z_]+")))
        val carpeta = File(context.filesDir, "recuperacion").apply { check(mkdirs() || isDirectory) }
        val archivo = File(carpeta, "$grupo-${System.currentTimeMillis()}-${UUID.randomUUID()}.json")
        fun valor(item: Any?): JsonElement = when (item) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(item)
            is Number -> JsonPrimitive(item)
            is Set<*> -> JsonArray(item.map { JsonPrimitive(it.toString()) })
            else -> JsonPrimitive(item.toString())
        }
        val data = buildJsonObject {
            put("version", 1); put("grupo", grupo); put("fecha", System.currentTimeMillis())
            put("valores", JsonObject(originales.mapValues { valor(it.value) }))
        }.toString().toByteArray()
        val atomic = AtomicFile(archivo)
        val stream = atomic.startWrite()
        try { stream.write(data); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
        return archivo
    }
    fun listar(context: Context): List<File> {
        val carpeta = File(context.filesDir, "recuperacion").canonicalFile
        return carpeta.listFiles().orEmpty().filter { it.isFile && it.extension == "json" && it.canonicalFile.parentFile == carpeta }
            .sortedByDescending { it.lastModified() }
    }
}
