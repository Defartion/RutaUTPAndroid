package com.example.rutautpnative.ui.screens

import java.nio.FloatBuffer
import kotlin.math.*

internal data class VistaCamara3D(val ojo: FloatArray, val objetivo: FloatArray, val distancia: Float)

/** Órbita de main iOS: 36°, encuadre por radio/aspecto y límites relativos al modelo. */
internal class CamaraTransporte3D {
    private var radio = 1.5f
    private var aspecto = 1f
    private var yaw = (-PI / 6).toFloat()
    private var pitch = (PI / 8).toFloat()
    private var zoom = 1f
    private val objetivo = FloatArray(3)
    val campoVisual = 36f
    private val distanciaInicial: Float get() {
        val vertical = (PI / 10).toFloat()
        val horizontal = atan(tan(vertical) * aspecto)
        return radio / sin(min(vertical, horizontal)) * 1.1f
    }
    @Synchronized fun configurarRadio(valor: Float) {
        require(valor.isFinite() && valor > 0)
        radio = valor
        limitar()
    }
    @Synchronized fun redimensionar(ancho: Int, alto: Int) {
        aspecto = (ancho.coerceAtLeast(1).toFloat() / alto.coerceAtLeast(1)).coerceIn(.01f, 100f)
        limitar()
    }
    @Synchronized fun girar(horizontal: Float, vertical: Float = 0f) {
        if (!horizontal.isFinite() || !vertical.isFinite()) return
        yaw = (yaw + horizontal) % (2 * PI).toFloat()
        pitch = (pitch + vertical).coerceIn((-PI / 12).toFloat(), (PI / 2.3).toFloat())
    }
    fun arrastrarUnDedo(dx: Float, dy: Float) = girar(-dx * .009f, dy * .007f)
    @Synchronized fun ampliar(factor: Float) {
        if (!factor.isFinite() || factor <= 0) return
        zoom = (zoom * factor).coerceIn(radio * 1.25f / distanciaInicial, 2.5f)
    }
    @Synchronized fun mover(x: Float, y: Float) {
        if (!x.isFinite() || !y.isFinite()) return
        val derecha = floatArrayOf(cos(yaw), 0f, -sin(yaw))
        val arriba = floatArrayOf(-sin(yaw) * sin(pitch), cos(pitch), -cos(yaw) * sin(pitch))
        repeat(3) { objetivo[it] += derecha[it] * x + arriba[it] * y }
        limitar()
    }
    @Synchronized fun arrastrarDosDedos(dx: Float, dy: Float, alto: Int) {
        val unidad = vista().distancia * 2 * tan((PI / 10).toFloat()) / alto.coerceAtLeast(1)
        mover(-dx * unidad, dy * unidad)
    }
    @Synchronized fun restablecer() {
        yaw = (-PI / 6).toFloat(); pitch = (PI / 8).toFloat(); zoom = 1f
        objetivo.fill(0f)
    }
    @Synchronized fun vista(): VistaCamara3D {
        val distancia = (distanciaInicial * zoom).coerceIn(radio * 1.25f, distanciaInicial * 2.5f)
        val desplazamiento = floatArrayOf(sin(yaw) * cos(pitch), sin(pitch), cos(yaw) * cos(pitch))
        return VistaCamara3D(FloatArray(3) { objetivo[it] + desplazamiento[it] * distancia }, objetivo.copyOf(), distancia)
    }
    @Synchronized fun guardar(): List<Float> = listOf(yaw, pitch, zoom) + objetivo.toList()
    @Synchronized fun restaurar(datos: List<Float>) {
        if (datos.size != 6 || datos.any { !it.isFinite() } || datos[2] <= 0) return
        yaw = datos[0]; pitch = datos[1]; zoom = datos[2]
        repeat(3) { objetivo[it] = datos[it + 3] }
        limitar(ajustarObjetivo = false)
    }
    private fun limitar(ajustarObjetivo: Boolean = true) {
        pitch = pitch.coerceIn((-PI / 12).toFloat(), (PI / 2.3).toFloat())
        zoom = zoom.coerceIn(.0001f, 2.5f)
        if (ajustarObjetivo) repeat(3) { objetivo[it] = objetivo[it].coerceIn(-radio * 2, radio * 2) }
    }
}

/** Usa lecturas absolutas: no cambia la posición del buffer que consume OpenGL. */
internal fun radioModelo3D(vertices: FloatBuffer, cantidad: Int): Float {
    require(cantidad >= 3 && cantidad.toLong() * 10 <= vertices.limit())
    val minimos = FloatArray(3) { Float.POSITIVE_INFINITY }
    val maximos = FloatArray(3) { Float.NEGATIVE_INFINITY }
    repeat(cantidad) { i -> repeat(3) { eje ->
        val valor = vertices.get(i * 10 + eje)
        require(valor.isFinite())
        minimos[eje] = min(minimos[eje], valor); maximos[eje] = max(maximos[eje], valor)
    } }
    // El recurso está centrado en origen; el radio abarca también adornos de la ruta.
    val radio = sqrt((0..2).sumOf { max(abs(minimos[it]), abs(maximos[it])).toDouble().pow(2) }).toFloat()
    require(radio.isFinite() && radio > 0)
    return radio
}
