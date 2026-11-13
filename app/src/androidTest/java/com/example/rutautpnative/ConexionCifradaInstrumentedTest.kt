package com.example.rutautpnative

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.rutautpnative.data.conexion.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID

/** Solo usa archivos y claves de prueba propios; no abre conexiones ni actividades. */
@RunWith(AndroidJUnit4::class)
class ConexionCifradaInstrumentedTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private fun espacio() = "conexion-prueba-" + UUID.randomUUID()
    private fun cuenta() = CuentaConexion(CanalConexion("broker.example"),RolConexion.CONTRIBUCION,"telefono-prueba","SECRETO_SINTETICO_ANDROID_123")
    @Test fun cifraSeReabreYLaClaveNoEsExportable() {
        val ns=espacio(); val archivo=ArchivoConexionCifrado(ctx,ns)
        try {
            val r=RepositorioConexion(archivo); assertTrue(r.guardar(cuenta()))
            val f=File(ctx.noBackupFilesDir,"$ns/credenciales.bin")
            val primero=f.readBytes()
            assertFalse(primero.toString(Charsets.UTF_8).contains(cuenta().clave))
            assertEquals(cuenta(),RepositorioConexion(ArchivoConexionCifrado(ctx,ns)).receptor())
            assertTrue(r.guardar(cuenta().copy(clave="SEGUNDO_SECRETO_SINTETICO_123")))
            assertFalse(primero.contentEquals(f.readBytes()))
            val ks=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            assertNull(ks.getKey("rutautp.conexion.aes.v1.$ns",null).encoded)
        } finally { archivo.reiniciar() }
    }
    @Test fun datosAlteradosFallaCerradoSinSobrescribirlos() {
        val ns=espacio();val archivo=ArchivoConexionCifrado(ctx,ns)
        try {
            assertTrue(RepositorioConexion(archivo).guardar(cuenta()))
            val f=File(ctx.noBackupFilesDir,"$ns/credenciales.bin");val bytes=f.readBytes()
            bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();f.writeBytes(bytes)
            val r=RepositorioConexion(ArchivoConexionCifrado(ctx,ns))
            assertFalse(r.disponible);assertNull(r.receptor());assertFalse(r.guardar(cuenta()))
            assertArrayEquals(bytes,f.readBytes())
        } finally { archivo.reiniciar() }
    }
    @Test fun copiarUnArchivoAOtraInstalacionSinSuClaveNoRecuperaLasCredenciales() {
        val a=espacio();val b=espacio();val archivoA=ArchivoConexionCifrado(ctx,a);val archivoB=ArchivoConexionCifrado(ctx,b)
        try {
            assertTrue(RepositorioConexion(archivoA).guardar(cuenta()))
            val copia=File(ctx.noBackupFilesDir,"$b/credenciales.bin");check(copia.parentFile!!.mkdirs())
            copia.writeBytes(File(ctx.noBackupFilesDir,"$a/credenciales.bin").readBytes())
            val r=RepositorioConexion(archivoB);assertFalse(r.disponible);assertNull(r.receptor())
        } finally { archivoA.reiniciar();archivoB.reiniciar() }
    }
}
