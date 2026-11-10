package com.example.rutautpnative.ui.screens

import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Partes y transformación extraídas del USDZ; las ventanas y ruedas conservan su material. */
@Serializable
internal data class PartesModelo3D(val carrocerias: List<List<Int>>, val contornos: List<List<Int>>,
    val origen: List<Float>, val ejes: List<List<Float>>) {
    fun punto(p: FloatArray): FloatArray = FloatArray(3) { j -> origen[j] + (0..2).sumOf { i -> (ejes[i][j] * p[i]).toDouble() }.toFloat() }
    fun normal(n: FloatArray): FloatArray {
        val r = FloatArray(3) { j -> (0..2).sumOf { i -> (ejes[i][j] * n[i]).toDouble() }.toFloat() }
        val largo = sqrt(r.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(.00001f)
        return FloatArray(3) { r[it] / largo }
    }
}

internal data class IdentidadModelo3D(val codigo: String, val letra: String, val colores: List<Int>)
internal data class CartelModelo3D(val vertices: FloatBuffer, val codigo: String, val letra: String)

internal fun bufferModelo3D(datos: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(datos.size * 4)
    .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(datos); position(0) }

/** Mismas bandas y coordenadas del cristal que VisorTransporte3D.swift en main. */
internal fun identificarModelo3D(mesh: MeshTransporte, partes: PartesModelo3D, nombre: String,
    identidad: IdentidadModelo3D?, contorno: Int): MeshTransporte {
    val datos = FloatArray(mesh.cantidad * 10)
    mesh.vertices.duplicate().apply { position(0); get(datos) }
    fun canales(c: Int) = floatArrayOf(((c ushr 16) and 255) / 255f, ((c ushr 8) and 255) / 255f, (c and 255) / 255f)
    fun pintar(rangos: List<List<Int>>, color: Int) {
        val rgb = canales(color)
        rangos.forEach { rango ->
            require(rango.size == 2 && rango[0] >= 0 && rango[1] >= 0 && rango[0].toLong() + rango[1] <= mesh.cantidad)
            repeat(rango[1]) { i -> repeat(3) { c -> datos[(rango[0] + i) * 10 + 6 + c] = rgb[c] } }
        }
    }
    pintar(partes.contornos, contorno)
    identidad?.colores?.firstOrNull()?.let { pintar(partes.carrocerias, it) }
    val extra = ArrayList<Float>()
    val detallado = nombre.startsWith("detallado_")
    val tipo = nombre.substringAfter('_')
    val banda = when (tipo) {
        "micro" -> floatArrayOf(1.17f, if (detallado) 6.4f else 6.5f, if (detallado) -.5f else -.45f, 1.20f, 1.48f)
        "combi" -> if (detallado) floatArrayOf(.97f, 3.3f, -.45f, .90f, 1.06f) else floatArrayOf(.98f, 3.9f, -.45f, .93f, 1.19f)
        else -> if (detallado) floatArrayOf(.90f, 2.15f, -.425f, .79f, .88f) else floatArrayOf(.91f, 4f, 0f, .79f, .95f)
    }
    val colores = identidad?.colores.orEmpty().drop(1)
    val paso = (banda[4] - banda[3]) / colores.size.coerceAtLeast(1)
    // Cajas delgadas sobre el costado recto, sin teñir cristales ni neumáticos.
    colores.forEachIndexed { i, color ->
        for (lado in listOf(-1f, 1f)) {
            val centro = floatArrayOf(lado * banda[0], banda[3] + (i + .5f) * paso, banda[2])
            val mitad = floatArrayOf(.006f, paso * .4f, banda[1] / 2)
            for (eje in 0..2) for (signo in listOf(-1f, 1f)) {
                val a = (eje + 1) % 3; val b = (eje + 2) % 3
                val normal = partes.normal(FloatArray(3) { if (it == eje) signo else 0f })
                for ((u, v) in listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to -1f, 1f to 1f, -1f to 1f)) {
                    val punto = centro.copyOf().apply { this[eje] += signo * mitad[eje]; this[a] += u * mitad[a]; this[b] += v * mitad[b] }
                    extra.addAll(partes.punto(punto).toList()); extra.addAll(normal.toList()); extra.addAll(canales(color).toList()); extra.add(1f)
                }
            }
        }
    }
    val cartel = identidad?.takeIf { it.letra.isNotBlank() }?.let {
        // x,y,z, ancho,alto,inclinación: espacio local de Carroceria en el recurso iOS.
        val p = when (tipo) {
            "micro" -> if (detallado) floatArrayOf(-.60f, 1.7983f, 4.2398f, .56f, .34f, -.152454f) else floatArrayOf(-.60f, 1.801f, 4.331f, .56f, .34f, -kotlin.math.atan(.14f / 1.5f))
            "combi" -> if (detallado) floatArrayOf(-.48f, 1.39379f, 1.94615f, .44f, .30f, -.631219f) else floatArrayOf(-.48f, 1.612f, 2.583f, .44f, .30f, -kotlin.math.atan(.97f / .91f))
            else -> if (detallado) floatArrayOf(-.42f, 1.09999f, .77982f, .34f, .24f, -1.057528f) else floatArrayOf(-.42f, 1.200f, .891f, .34f, .24f, -kotlin.math.atan(.673f / .481f))
        }
        val vertices = ArrayList<Float>()
        for ((u, v) in listOf(0f to 0f, 0f to 1f, 1f to 1f, 0f to 0f, 1f to 1f, 1f to 0f)) {
            val x = (u - .5f) * p[3]; val y = (.5f - v) * p[4]; val z = .006f
            vertices.addAll(partes.punto(floatArrayOf(p[0] + x, p[1] + y * cos(p[5]) - z * sin(p[5]), p[2] + y * sin(p[5]) + z * cos(p[5]))).toList())
            vertices.add(u); vertices.add(v)
        }
        CartelModelo3D(bufferModelo3D(vertices.toFloatArray()), it.codigo, it.letra)
    }
    return MeshTransporte(bufferModelo3D(datos + extra.toFloatArray()), mesh.cantidad + extra.size / 10, cartel)
}
