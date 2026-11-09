package com.example.rutautpnative.ui.screens

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.rutautpnative.data.gtfs.RutaGTFS
import com.example.rutautpnative.ui.idioma.L
import com.example.rutautpnative.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    var tipo by remember { mutableStateOf(when { ruta?.linea?.startsWith("CT-") == true -> "colectivo"; ruta?.linea?.startsWith("C-") == true -> "combi"; else -> "micro" }) }
    var detallado by remember { mutableStateOf(false) }
    LaunchedEffect(ruta?.id) {
        ruta?.let { r ->
            val ref = withContext(Dispatchers.IO) { ReferenciasTransporte.referencia(ctx, r) }
            tipo = ref?.modelo ?: if (r.id == "17522705" && r.linea == "C-44" && r.variante == "T2" && r.empresa == "Miramar") "micro" else tipo
        }
    }
    val nombre = (if (detallado) "detallado_" else "original_") + tipo
    var error by remember(nombre) { mutableStateOf<String?>(null) }
    val mesh by produceState<MeshTransporte?>(null, nombre) {
        value = withContext(Dispatchers.IO) { runCatching { MeshTransporte.cargar(ctx, nombre) }.onFailure { error = it.message ?: "3D" }.getOrNull() }
    }
    var superficie by remember { mutableStateOf<TransporteSurface?>(null) }
    val vistaActual = superficie
    val cicloVida = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(vistaActual, cicloVida) {
        val observador = androidx.lifecycle.LifecycleEventObserver { _, evento ->
            when (evento) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> vistaActual?.onResume()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> vistaActual?.onPause()
                else -> Unit
            }
        }
        cicloVida.lifecycle.addObserver(observador)
        if (cicloVida.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) vistaActual?.onResume()
        onDispose { cicloVida.lifecycle.removeObserver(observador); vistaActual?.onPause() }
    }
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = AppBackground, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(L.t("Transportes en 3D", "Transport in 3D"), style = HeadlineMd, modifier = Modifier.weight(1f))
                    TextButton(onClick = onCerrar) { Text(L.t("Cerrar", "Close")) }
                }
                ruta?.let { Text(it.lineaConLetra + " · " + it.empresa, style = HeadlineSm); ReferenciaTransporte(it) }
                if (ruta == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("micro" to L.t("Micro", "Bus"), "combi" to L.t("Combi", "Minibus"), "colectivo" to L.t("Colectivo", "Shared taxi")).forEach { (id, label) ->
                        FilterChip(tipo == id, { tipo = id }, label = { Text(label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!detallado, { detallado = false }, label = { Text(L.t("Original", "Original")) })
                    FilterChip(detallado, { detallado = true }, label = { Text(L.t("Detallado", "Detailed")) })
                }
                val data = mesh
                when {
                    error != null -> {
                        Text(L.t("No se pudo cargar el modelo 3D.", "Could not load the 3D model."), color = AppError)
                    }
                    data == null -> Box(Modifier.fillMaxWidth().height(300.dp)) { CircularProgressIndicator() }
                    else -> key(nombre) {
                        AndroidView(factory = { TransporteSurface(it, data) { error = L.t("No se pudo mostrar el modelo 3D.", "Could not display the 3D model.") }.also { v -> superficie = v } },
                            modifier = Modifier.fillMaxWidth().height(340.dp))
                    }
                }
                Text(L.t("Arrastra para girar y usa dos dedos para acercar o alejar.", "Drag to rotate and use two fingers to zoom."), style = BodySm)
                // Los botones permiten operar el visor sin depender de gestos visuales.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { superficie?.mover(-15f, 0f) }) { Text(L.t("Izquierda", "Left")) }
                    OutlinedButton(onClick = { superficie?.mover(15f, 0f) }) { Text(L.t("Derecha", "Right")) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { superficie?.mover(0f, -10f) }) { Text(L.t("Arriba", "Up")) }
                    OutlinedButton(onClick = { superficie?.mover(0f, 10f) }) { Text(L.t("Abajo", "Down")) }
                    OutlinedButton(onClick = { superficie?.zoom(0.8f) }) { Text("+") }
                    OutlinedButton(onClick = { superficie?.zoom(1.25f) }) { Text("−") }
                }
                TextButton(onClick = { superficie?.restablecer() }) { Text(L.t("Restablecer vista", "Reset view")) }
                Text(L.t("Modelos de referencia por categoría. No certifican la carrocería ni el aspecto actual de toda una línea.", "Reference models by category. They do not certify the bodywork or current appearance of an entire route."), style = BodyXs, color = OnSurfaceVariant)
            }
        }
    }
}

internal data class MeshTransporte(val vertices: FloatBuffer, val cantidad: Int) {
    companion object {
        // AAPT descomprime los assets .gz y los empaqueta sin ese sufijo.
        fun cargar(ctx: Context, nombre: String): MeshTransporte = ctx.assets.open("transportes3d/$nombre.mesh").use { raw ->
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
    }
}

private class TransporteSurface(ctx: Context, data: MeshTransporte, fallo: () -> Unit) : GLSurfaceView(ctx) {
    private val renderer = TransporteRenderer(data) { post(fallo) }
    private var x = 0f; private var y = 0f
    private val scale = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean { zoom(1f / detector.scaleFactor); return true }
    })
    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        contentDescription = L.t("Modelo tridimensional del transporte", "Three dimensional transport model")
    }
    fun mover(dx: Float, dy: Float) { renderer.yaw += dx; renderer.pitch = (renderer.pitch + dy).coerceIn(-80f, 80f); requestRender() }
    fun zoom(factor: Float) { renderer.distance = (renderer.distance * factor).coerceIn(3f, 14f); requestRender() }
    fun restablecer() { renderer.yaw = 35f; renderer.pitch = 18f; renderer.distance = 7f; requestRender() }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scale.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) { x = event.x; y = event.y; parent?.requestDisallowInterceptTouchEvent(true) }
        if (event.actionMasked == MotionEvent.ACTION_MOVE && event.pointerCount == 1 && !scale.isInProgress) mover((event.x - x) * 0.5f, (event.y - y) * 0.35f)
        if (event.actionMasked == MotionEvent.ACTION_UP) { performClick(); parent?.requestDisallowInterceptTouchEvent(false) }
        x = event.x; y = event.y
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}

private class TransporteRenderer(private val mesh: MeshTransporte, private val fallo: () -> Unit) : GLSurfaceView.Renderer {
    @Volatile var yaw = 35f
    @Volatile var pitch = 18f
    @Volatile var distance = 7f
    private var program = 0
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
            GLES20.glClearColor(0.94f, 0.95f, 0.96f, 1f)
        } catch (_: Exception) { program = 0; fallo() }
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.perspectiveM(projection, 0, 42f, width.toFloat() / height.coerceAtLeast(1), 0.1f, 40f)
    }
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (program == 0) return
        Matrix.setLookAtM(view, 0, 0f, 0.3f, distance, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.setIdentityM(model, 0); Matrix.rotateM(model, 0, pitch, 1f, 0f, 0f); Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
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
    }
}
