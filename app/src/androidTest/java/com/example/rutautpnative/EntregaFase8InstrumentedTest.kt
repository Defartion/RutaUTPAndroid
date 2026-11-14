package com.example.rutautpnative

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.example.rutautpnative.data.Persistencia
import com.example.rutautpnative.data.geo.PolylineMatching
import com.example.rutautpnative.data.gtfs.*
import com.example.rutautpnative.data.imagenes.*
import com.example.rutautpnative.data.tracking.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlin.system.measureNanoTime

/** Sin Activity, broker, GPS ni Firebase. Solo datos sintéticos y archivos aislados. */
class EntregaFase8InstrumentedTest {
    private inline fun aislado(test: (Context) -> Unit) {
        val base=InstrumentationRegistry.getInstrumentation().context
        val token="fase8-"+UUID.randomUUID()
        val folder=File(base.cacheDir,token).apply { mkdirs() }
        val prefs=mutableSetOf<String>()
        val context=object:ContextWrapper(base) {
            override fun getFilesDir()=folder
            override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences {
                val key=token+"-"+name;prefs+=key
                return base.getSharedPreferences(key,mode)
            }
        }
        try { test(context) } finally {
            prefs.forEach { base.getSharedPreferences(it,0).edit().clear().commit() }
            folder.listFiles()?.forEach { it.delete() };folder.delete()
        }
    }
    @Test fun migrarUnaInstalacionVersionUnoConservaPreferencias() = aislado { context ->
        val prefs=context.getSharedPreferences("rutautp.persistencia",0)
        prefs.edit().putInt("persistencia.esquema.version",1).putString("perfil_telefono","dato-sintetico")
            .putString("apariencia.tematica.empresa.v1","utp").putBoolean("perfil_notificaciones",false).commit()
        val original=prefs.all.toMap()
        assertEquals(1,Persistencia.migrarSiHaceFalta(context))
        assertEquals(original,prefs.all)
        assertEquals(1,Persistencia.migrarSiHaceFalta(context));assertEquals(original,prefs.all)
    }
    @Test fun lasFotosExistentesConservanRutaContenidoYLectura() = aislado { context ->
        val bitmap=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        try {
            assertTrue(ProfileImageStore.guardar(context,bitmap))
            assertTrue(CarnetImageStore.guardar(context,"utp",bitmap))
            val foto=ProfileImageStore.archivo(context);val carnet=CarnetImageStore.archivo(context,"utp")
            assertEquals("perfil_foto.jpg",foto.name);assertEquals("carnet-utp.jpg",carnet.name)
            val originalFoto=foto.readBytes();val originalCarnet=carnet.readBytes()
            Persistencia.migrarSiHaceFalta(context)
            val leida=ProfileImageStore.cargar(context);val leido=CarnetImageStore.cargar(context,"utp")
            assertNotNull(leida);assertNotNull(leido)
            leida?.recycle();leido?.recycle()
            assertArrayEquals(originalFoto,foto.readBytes());assertArrayEquals(originalCarnet,carnet.readBytes())
        } finally { bitmap.recycle() }
    }
    @Test fun medirEtaEnDispositivoSinServiciosExternos() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val rutas=GTFSRepository.parsearFuente { nombre ->
            instrumentation.targetContext.assets.open("gtfs/"+nombre+".txt").bufferedReader().use { it.readText() }
        }.filter { it.shape.size>=2 }
        val catalogo=rutas.associateBy { it.id };val ancla=GTFSRepository.coordenadaUTP
        val posiciones=List(1000) { i ->
            val r=rutas[i%rutas.size];val n=(i*17)%r.shape.lastIndex;val p=r.shape[n]
            VehiclePosition("sintetico-"+i,r.linea,r.id,p.latitude,p.longitude,
                PolylineMatching.headingDegrees(p,r.shape[n+1]),7.0,1_800_000_000.0,VehicleDataOrigin.OBSERVED)
        }
        val cache=VehicleETACache().apply { instalar(catalogo) }
        val expected=posiciones.map { VehicleETAEstimator.minutos(it,catalogo.getValue(it.routeId),ancla) }
        assertEquals(expected,posiciones.map { cache.minutos(it,ancla) })
        val tiempos=List(5) { measureNanoTime { assertEquals(expected,posiciones.map { cache.minutos(it,ancla) }) } }.sorted()
        val report=buildJsonObject {
            put("environment","android-instrumentation");put("vehicles",posiciones.size);put("routes",rutas.size)
            put("medianMs",tiempos[2]/1e6);put("p95Ms",tiempos.last()/1e6)
            put("geometryPreparations",cache.preparacionesRuta);put("targetProjections",cache.proyeccionesObjetivo)
            put("equivalentResults",true);put("networkUsed",false)
        }.toString()
        instrumentation.sendStatus(0,Bundle().apply { putString("phase8Performance",report) })
    }
}
