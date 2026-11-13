package com.example.rutautpnative

import com.example.rutautpnative.data.conexion.*
import com.example.rutautpnative.data.tracking.*
import com.example.rutautpnative.navigation.ModelosCatalogo
import androidx.lifecycle.*
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.junit.Assert.*
import org.junit.Test

class Phase4ConnectionTest {
    private class Memoria : PersistenciaConexion {
        var contenido: String? = null
        var fallaLectura = false; var fallaEscritura = false
        override fun leer(): String? { check(!fallaLectura); return contenido }
        override fun escribir(valor: String) { check(!fallaEscritura); contenido = valor }
    }
    private fun cuenta(user: String = "telefono-a", host: String = "broker.example", rol: RolConexion = RolConexion.CONTRIBUCION) =
        CuentaConexion(CanalConexion(host),rol,user,"CLAVE_SINTETICA_UNICA_123")
    @Test fun sinCuentaNoInventaUnaIdentidad() {
        val r = RepositorioConexion(Memoria()); assertTrue(r.disponible); assertNull(r.receptor()); assertNull(r.cuenta(RolConexion.CONTRIBUCION))
    }
    @Test fun dosInstalacionesDelMismoAPKConservanPrincipalesDistintos() {
        val a = RepositorioConexion(Memoria()); val b = RepositorioConexion(Memoria())
        assertTrue(a.guardar(cuenta())); assertTrue(b.guardar(cuenta("telefono-b")))
        assertNotEquals(a.receptor()!!.usuario,b.receptor()!!.usuario)
        assertEquals(a.receptor()!!.canal.identidad,b.receptor()!!.canal.identidad)
    }
    @Test fun seReabreLaCuentaAprovisionadaSinDatosDeCompilacion() {
        val m = Memoria(); RepositorioConexion(m).guardar(cuenta())
        assertEquals(cuenta(),RepositorioConexion(m).receptor())
    }
    @Test fun elLectorNoSustituyeLaCuentaDeContribucion() {
        val r = RepositorioConexion(Memoria()); r.guardar(cuenta())
        r.guardar(cuenta("presentation-reader",rol=RolConexion.LECTURA))
        assertEquals("presentation-reader",r.receptor()!!.usuario)
        assertEquals("telefono-a",r.cuenta(RolConexion.CONTRIBUCION)!!.usuario)
    }
    @Test fun unaCuentaNoSeGuardaConLosDosRoles() {
        val r = RepositorioConexion(Memoria()); assertTrue(r.guardar(cuenta()))
        assertFalse(r.guardar(cuenta(rol=RolConexion.LECTURA)))
        assertEquals(RolConexion.CONTRIBUCION,r.receptor()!!.rol)
    }
    @Test fun cambiarEndpointNuncaRecuperaCredencialesDelServidorAnterior() {
        val r = RepositorioConexion(Memoria()); r.guardar(cuenta())
        assertTrue(r.guardar(cuenta("lector-b","segundo.example",RolConexion.LECTURA)))
        assertEquals("segundo.example",r.receptor()!!.canal.host)
        assertNull(r.cuenta(RolConexion.CONTRIBUCION))
    }
    @Test fun reemplazarEsAtomicoSiFallaLaEscritura() {
        val m = Memoria(); val r = RepositorioConexion(m); r.guardar(cuenta()); val rev=r.revision
        m.fallaEscritura = true
        assertFalse(r.guardar(cuenta("telefono-b"))); assertEquals(cuenta(),r.receptor()); assertEquals(rev,r.revision)
    }
    @Test fun noMezclaUsuarioNuevoConUnaClaveVacia() {
        val r = RepositorioConexion(Memoria()); r.guardar(cuenta())
        assertFalse(r.guardar(cuenta("telefono-b").copy(clave="")))
        assertEquals(cuenta(),r.receptor())
    }
    @Test fun secretoIlegibleNoTieneFallbackEnTextoPlano() {
        val m = Memoria().apply { contenido="dato-corrupto" }
        val r = RepositorioConexion(m)
        assertFalse(r.disponible); assertNull(r.receptor()); assertFalse(r.guardar(cuenta())); assertEquals("dato-corrupto",m.contenido)
    }
    @Test fun falloDeLecturaNoReescribeLosDatosOriginales() {
        val m = Memoria(); RepositorioConexion(m).guardar(cuenta()); val original=m.contenido
        m.fallaLectura=true; val r=RepositorioConexion(m)
        assertFalse(r.disponible); assertNull(r.receptor()); assertEquals(original,m.contenido)
    }
    @Test fun quitarUnaCuentaNoAfectaLaOtraInstalacionNiSuClaveWeb() {
        val a=RepositorioConexion(Memoria()); val b=RepositorioConexion(Memoria())
        a.guardar(cuenta()); b.guardar(cuenta("telefono-b")); a.guardarClaveWeb("WEB_SINTETICA")
        assertTrue(a.borrarCuenta(RolConexion.CONTRIBUCION)); assertNull(a.receptor())
        assertEquals("WEB_SINTETICA",a.claveWeb()); assertEquals("telefono-b",b.receptor()!!.usuario)
    }
    @Test fun quitarLectorConservaLaCuentaPropiaDelMismoServidor() {
        val r=RepositorioConexion(Memoria()); r.guardar(cuenta()); r.guardar(cuenta("observer",rol=RolConexion.LECTURA))
        assertTrue(r.borrarCuenta(RolConexion.LECTURA)); assertEquals(cuenta(),r.receptor())
    }
    @Test fun guardarLoMismoNoReiniciaLosModelos() {
        val r=RepositorioConexion(Memoria()); r.guardar(cuenta()); val rev=r.revision
        assertTrue(r.guardar(cuenta())); assertEquals(rev,r.revision)
    }
    @Test fun laClaveWebNoReiniciaUnaSesionDeTransporte() {
        val r=RepositorioConexion(Memoria()); r.guardar(cuenta()); val rev=r.revision
        assertTrue(r.guardarClaveWeb("WEB_SINTETICA")); assertEquals(rev,r.revision); assertEquals(1,r.revisionWeb)
        assertTrue(r.guardarClaveWeb("")); assertNull(r.claveWeb()); assertEquals(2,r.revisionWeb)
    }
    @Test fun cuentasDeBackendSimuladorYLectorNoPuedenAportar() {
        for (name in listOf("backend","observer","presentation-reader","sim-uno")) {
            val r=RepositorioConexion(Memoria()); assertFalse(name,r.guardar(cuenta(name)))
        }
    }
    @Test fun produccionExigeTLSYDesarrolloSoloPermitePlanoEnRedLocal() {
        val local=cuenta(host="127.0.0.1").copy(canal=CanalConexion("127.0.0.1",1883,false))
        assertFalse(RepositorioConexion(Memoria()).guardar(local))
        assertTrue(RepositorioConexion(Memoria(),true).guardar(local))
        val publico=local.copy(canal=CanalConexion("broker.example",1883,false))
        assertFalse(RepositorioConexion(Memoria(),true).guardar(publico))
    }
    @Test fun endpointCanonicoSeparaPuertoYTransporte() {
        assertEquals(CanalConexion("BROKER.EXAMPLE.").identidad,CanalConexion("broker.example").identidad)
        assertNotEquals(CanalConexion("broker.example",8883,true).identidad,CanalConexion("broker.example",1883,false).identidad)
        val r=RepositorioConexion(Memoria()); assertFalse(r.guardar(cuenta().copy(canal=CanalConexion("user@broker.example"))))
        assertFalse(r.guardar(cuenta().copy(canal=CanalConexion("broker.example",0))))
    }
    @Test fun certificadoConfiguradoInvalidoNoDesviaHaciaOtraConfianza() {
        assertThrows(Exception::class.java) { MQTTTLS.socketFactory("",null,"no-es-un-certificado") }
        assertThrows(Exception::class.java) { MQTTTLS.socketFactory("ausente.pem",null) }
    }
    @Test fun TLSMantieneLaVerificacionDelNombreDelServidor() {
        val opts=MqttConnectOptions()
        MQTTTLS.aplicar(opts,MQTTConfiguration("broker.example",8883,"telefono-a","secreto-prueba",true,""),null)
        assertNotNull(opts.socketFactory); assertTrue(opts.isHttpsHostnameVerificationEnabled)
    }
    @Test fun modelosDelEndpointAnteriorSeRetiranAunqueElCatalogoNoCambie() {
        val modelos=ModelosCatalogo(); val padre=ViewModelStore().apply { put("m",modelos) }
        val factory=object:ViewModelProvider.Factory {}
        try {
            val a=modelos.obtener(false,factory,0)
            val b=modelos.obtener(false,factory,1)
            assertNotSame(a,b); assertTrue(modelos.consumirCambioCuenta()); assertEquals(0,modelos.consumirCambio())
            assertSame(b,modelos.obtener(false,factory,1)); assertFalse(modelos.consumirCambioCuenta())
        } finally { padre.clear() }
    }
    @Test fun ningunDiagnosticoDeConfiguracionReproduceLosSecretos() {
        val c=cuenta(); assertFalse(c.toString().contains(c.clave))
        assertFalse(MQTTConfiguration(c.canal.host,8883,c.usuario,c.clave,true,"").toString().contains(c.clave))
    }
}
