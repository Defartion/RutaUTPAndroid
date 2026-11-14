package com.example.rutautpnative.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.opengl.GLUtils
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Los seis modelos del repositorio iOS, convertidos sin sustituir su geometría. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Transportes3DScreen(ruta: RutaGTFS? = null, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    var tipo by remember(ruta?.id) { mutableStateOf("micro") }
    var detallado by recordarVariante3D()
    val referencia by produceState<Pair<Boolean, ReferenciaRuta?>>(false to null, ruta) {
        value = true to ruta?.let { withContext(Dispatchers.IO) { ReferenciasTransporte.referencia(ctx, it) } }
    }
    val identidad = remember(ruta, referencia) { identidadModelo(ruta, referencia.second) }
    val tipoVerificado = ruta?.let { tipoModeloRuta(it, referencia.second) } ?: tipo.takeIf { ruta == null }
    val nombre = (if (detallado) "detallado_" else "original_") + tipoVerificado
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = AppBackground, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(L.t("Transportes en 3D", "Transport in 3D"), style = HeadlineMd, modifier = Modifier.weight(1f))
                    TextButton(onClick = onCerrar) { Text(L.t("Cerrar", "Close")) }
                }
                ruta?.let {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        com.example.rutautpnative.ui.components.InsigniaLinea(it, grande = true)
                        Column(Modifier.weight(1f)) { Text(it.codigoTransporte, style = BodySm); Text(it.empresa, style = HeadlineSm) }
                    }
                    ReferenciaTransporte(it)
                }
                if (ruta == null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("micro" to L.t("Micro", "Bus"), "combi" to L.t("Combi", "Minibus"), "colectivo" to L.t("Colectivo", "Shared taxi")).forEach { (valor, texto) ->
                        FilterChip(tipo == valor, { tipo = valor }, label = { Text(texto) })
                    }
                }
                SelectorVarianteModelo(detallado) { detallado = it }
                if (referencia.first && tipoVerificado == null) Text(L.t("Modelo de esta categoría próximamente", "Model for this category coming soon"), style = BodySm, color = OnSurfaceVariant)
                else if (referencia.first) PanelModeloTransporte(nombre, identidad, ColorMarca.toArgb())
                else MarcoVisor3D(EstadoVisor3D.CARGANDO, {})
                Text(L.t("Modelos de referencia por categoría. No certifican la carrocería ni el aspecto actual de toda una línea.", "Reference models by category. They do not certify the bodywork or current appearance of an entire route."), style = BodyXs, color = OnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SelectorVarianteModelo(detallado: Boolean, onCambiar: (Boolean) -> Unit) {
    Text(L.t("Modelo del vehículo", "Vehicle model"), style = BodyMdMedium, color = OnSurface)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!detallado, { onCambiar(false) }, label = { Text(L.t("Original", "Original")) })
        FilterChip(detallado, { onCambiar(true) }, label = { Text(L.t("Detallado", "Detailed")) })
    }
}

/** El detalle de ruta muestra el visor directamente, como IdentificacionRuta3DView. */
@Composable
fun ModeloRuta3DDiferido(ruta: RutaGTFS) {
    if (androidx.compose.ui.platform.LocalInspectionMode.current) {
        MarcoVisor3D(EstadoVisor3D.CARGANDO, {})
        return
    }
    val ventana = LocalView.current
    var visible by remember(ruta.id) { mutableStateOf(false) }
    var montado by remember(ruta.id) { mutableStateOf(false) }
    LaunchedEffect(visible) { if (visible && !montado) { delay(350); montado = true } }
    Column(Modifier.fillMaxWidth().onGloballyPositioned { coordenadas ->
        val bounds = coordenadas.boundsInWindow()
        val altoVisible = (minOf(bounds.bottom, ventana.height.toFloat()) - maxOf(bounds.top, 0f)).coerceAtLeast(0f)
        visible = bounds.height > 0f && altoVisible >= bounds.height * .25f
    }) {
        if (montado) ModeloRuta3D(ruta, visible)
        else Box(Modifier.fillMaxWidth().height(340.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { Icon(Icons.Filled.ViewInAr, L.t("Modelo 3D del transporte", "3D transport model"), tint = AcentoInterfaz, modifier = Modifier.size(40.dp)) }
    }
}

@Composable
fun ModeloRuta3D(ruta: RutaGTFS, activo: Boolean = true) {
    val ctx = LocalContext.current
    var detallado by recordarVariante3D()
    val referencia by produceState<Pair<Boolean, ReferenciaRuta?>>(false to null, ruta) {
        value = true to withContext(Dispatchers.IO) { ReferenciasTransporte.referencia(ctx, ruta) }
    }
    val identidad = remember(ruta, referencia) { identidadModelo(ruta, referencia.second) }
    val tipo = tipoModeloRuta(ruta, referencia.second)
    val nombre = (if (detallado) "detallado_" else "original_") + tipo
    SelectorVarianteModelo(detallado) { detallado = it }
    if (referencia.first && tipo == null) Text(L.t("Modelo de esta categoría próximamente", "Model for this category coming soon"), style = BodySm, color = OnSurfaceVariant)
    else if (referencia.first) PanelModeloTransporte(nombre, identidad, ColorMarca.toArgb(), activo)
    else MarcoVisor3D(EstadoVisor3D.CARGANDO, {})
}

@Composable
private fun recordarVariante3D(): MutableState<Boolean> {
    val ctx = LocalContext.current
    val prefs = remember(ctx) { ctx.getSharedPreferences("transportes3d", Context.MODE_PRIVATE) }
    val estado = remember(prefs) { mutableStateOf(prefs.getBoolean("detallado", false)) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "detallado") estado.value = prefs.getBoolean("detallado", false)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(estado.value) { prefs.edit().putBoolean("detallado", estado.value).apply() }
    return estado
}

internal data class MeshTransporte(val vertices: FloatBuffer, val cantidad: Int, val cartel: CartelModelo3D? = null) {
    val radio: Float by lazy { radioModelo3D(vertices, cantidad) }
    companion object {
        // AAPT descomprime los assets .gz y los empaqueta sin ese sufijo.
        fun cargar(ctx: Context, nombre: String, identidad: IdentidadModelo3D?, contorno: Int): MeshTransporte {
            val mesh = ctx.assets.open("transportes3d/$nombre.mesh").use { raw ->
            DataInputStream(raw).use { input ->
                require(input.readInt() == 0x52555433)
                val n = input.readInt()
                require(n in 3..300_000 && n % 3 == 0)
                val buffer = ByteBuffer.allocateDirect(n * 40).order(ByteOrder.nativeOrder()).asFloatBuffer()
                repeat(n * 10) { buffer.put(input.readFloat().also { require(it.isFinite()) }) }
                require(input.read() == -1)
                buffer.position(0)
                MeshTransporte(buffer, n)
            }
            }
            val partes = ctx.assets.open("transportes3d/$nombre.partes.json").bufferedReader().use { Json.decodeFromString<PartesModelo3D>(it.readText()) }
            return identificarModelo3D(mesh, partes, nombre, identidad, contorno).also { it.radio }
        }
    }
}

internal class TransporteSurface(ctx: Context, data: MeshTransporte, private val camara: CamaraTransporte3D,
    listo: () -> Unit, fallo: () -> Unit) : GLSurfaceView(ctx) {
    private val renderer = TransporteRenderer(data, camara, { post(listo) }, { post(fallo) })
    private var x = 0f; private var y = 0f
    private var dedos = 0
    private val scale = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean { zoom(1f / detector.scaleFactor); return true }
    })
    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }
    fun girar(radianes: Float) { camara.girar(radianes); requestRender() }
    fun mover(x: Float, y: Float) { camara.mover(x, y); requestRender() }
    fun zoom(factor: Float) { camara.ampliar(factor); requestRender() }
    fun restablecer() { camara.restablecer(); requestRender() }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scale.onTouchEvent(event)
        val cantidad = event.pointerCount.coerceAtLeast(1)
        val actualX = (0 until event.pointerCount).sumOf { event.getX(it).toDouble() }.toFloat() / cantidad
        val actualY = (0 until event.pointerCount).sumOf { event.getY(it).toDouble() }.toFloat() / cantidad
        if (dedos != event.pointerCount || event.actionMasked in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP)) {
            dedos = event.pointerCount; x = actualX; y = actualY
            parent?.requestDisallowInterceptTouchEvent(true)
        } else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            if (event.pointerCount == 1 && !scale.isInProgress) camara.arrastrarUnDedo(actualX - x, actualY - y)
            else if (event.pointerCount > 1) camara.arrastrarDosDedos(actualX - x, actualY - y, height)
            requestRender()
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            parent?.requestDisallowInterceptTouchEvent(false); dedos = 0
        }
        x = actualX; y = actualY
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}

private class TransporteRenderer(private val mesh: MeshTransporte, private val camara: CamaraTransporte3D, private val listo: () -> Unit, private val fallo: () -> Unit) : GLSurfaceView.Renderer {
    private var program = 0
    private var programaCartel = 0
    private var texturaCartel = 0
    private var aspecto = 1f
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val pv = FloatArray(16)
    private val mvp = FloatArray(16)
    private fun shader(type: Int, source: String): Int = GLES20.glCreateShader(type).also { id ->
        GLES20.glShaderSource(id, source); GLES20.glCompileShader(id)
        val status = IntArray(1); GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] != 0) { GLES20.glGetShaderInfoLog(id) }
    }
    private fun prepararCartel(cartel: CartelModelo3D) {
        val vertex = shader(GLES20.GL_VERTEX_SHADER, """
            uniform mat4 uMVP; attribute vec3 aPosition; attribute vec2 aUV; varying vec2 vUV;
            void main(){ vUV=aUV; gl_Position=uMVP*vec4(aPosition,1.0); }
        """.trimIndent())
        val fragment = shader(GLES20.GL_FRAGMENT_SHADER, """
            precision mediump float; uniform sampler2D uTexture; varying vec2 vUV;
            void main(){ gl_FragColor=texture2D(uTexture,vUV); }
        """.trimIndent())
        programaCartel = GLES20.glCreateProgram()
        GLES20.glAttachShader(programaCartel, vertex); GLES20.glAttachShader(programaCartel, fragment); GLES20.glLinkProgram(programaCartel)
        val estado = IntArray(1); GLES20.glGetProgramiv(programaCartel, GLES20.GL_LINK_STATUS, estado, 0); check(estado[0] != 0)
        GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment)
        val bitmap = Bitmap.createBitmap(1024, 640, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap); canvas.drawColor(android.graphics.Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER }
            fun texto(valor: String, tamano: Float, ancho: Float, centroY: Float, centroX: Float = 512f) {
                paint.textSize = tamano
                val anchoReal = paint.measureText(valor)
                if (anchoReal > ancho) paint.textSize *= ancho / anchoReal
                canvas.drawText(valor, centroX, centroY - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2, paint)
            }
            texto(cartel.codigo, 110f, 860f, 115f)
            if (cartel.distintivo == null) texto(cartel.principal, 390f, 880f, 400f)
            else { texto(cartel.principal, 390f, 573f, 400f, 358f); texto(cartel.distintivo, 144f, 245f, 510f, 839f) }
            val texturas = IntArray(1); GLES20.glGenTextures(1, texturas, 0); texturaCartel = texturas[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texturaCartel)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        } finally { bitmap.recycle() }
    }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            val vertex = shader(GLES20.GL_VERTEX_SHADER, """
                uniform mat4 uMVP; uniform mat4 uModel;
                attribute vec3 aPosition; attribute vec3 aNormal; attribute vec4 aColor;
                varying vec4 vColor;
                void main() {
                    vec3 n = normalize(mat3(uModel) * aNormal);
                    float light = 0.42 + 0.45 * abs(dot(n, normalize(vec3(0.4,0.9,0.7)))) + 0.13 * abs(dot(n, vec3(-1.0,0.0,0.0)));
                    vColor = vec4(aColor.rgb * light, 1.0);
                    gl_Position = uMVP * vec4(aPosition,1.0);
                }
            """.trimIndent())
            val fragment = shader(GLES20.GL_FRAGMENT_SHADER, "precision mediump float; varying vec4 vColor; void main(){ gl_FragColor=vColor; }")
            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertex); GLES20.glAttachShader(program, fragment); GLES20.glLinkProgram(program)
            val status = IntArray(1); GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0); check(status[0] != 0)
            GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glClearColor(1f, 1f, 1f, 1f)
            programaCartel = 0
            mesh.cartel?.let { prepararCartel(it) }
            listo()
        } catch (_: Exception) { program = 0; fallo() }
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspecto = width.coerceAtLeast(1).toFloat() / height.coerceAtLeast(1)
        camara.redimensionar(width, height)
    }
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (program == 0) return
        val toma = camara.vista()
        Matrix.perspectiveM(projection, 0, camara.campoVisual, aspecto, .01f, maxOf(100f, toma.distancia + mesh.radio * 4))
        Matrix.setLookAtM(view, 0, toma.ojo[0], toma.ojo[1], toma.ojo[2], toma.objetivo[0], toma.objetivo[1], toma.objetivo[2], 0f, 1f, 0f)
        Matrix.setIdentityM(model, 0)
        Matrix.multiplyMM(pv, 0, projection, 0, view, 0); Matrix.multiplyMM(mvp, 0, pv, 0, model, 0)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMVP"), 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uModel"), 1, false, model, 0)
        listOf(Triple("aPosition", 3, 0), Triple("aNormal", 3, 3), Triple("aColor", 4, 6)).forEach { (name, count, offset) ->
            val location = GLES20.glGetAttribLocation(program, name)
            mesh.vertices.position(offset)
            GLES20.glEnableVertexAttribArray(location)
            GLES20.glVertexAttribPointer(location, count, GLES20.GL_FLOAT, false, 40, mesh.vertices)
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.cantidad)
        listOf("aPosition", "aNormal", "aColor").forEach { GLES20.glDisableVertexAttribArray(GLES20.glGetAttribLocation(program, it)) }
        mesh.cartel?.takeIf { programaCartel != 0 }?.let { cartel ->
            GLES20.glUseProgram(programaCartel)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(programaCartel, "uMVP"), 1, false, mvp, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texturaCartel)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(programaCartel, "uTexture"), 0)
            val pos = GLES20.glGetAttribLocation(programaCartel, "aPosition"); val uv = GLES20.glGetAttribLocation(programaCartel, "aUV")
            cartel.vertices.position(0); GLES20.glEnableVertexAttribArray(pos); GLES20.glVertexAttribPointer(pos, 3, GLES20.GL_FLOAT, false, 20, cartel.vertices)
            cartel.vertices.position(3); GLES20.glEnableVertexAttribArray(uv); GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 20, cartel.vertices)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
            GLES20.glDisableVertexAttribArray(pos); GLES20.glDisableVertexAttribArray(uv)
        }
    }
}
