package com.example.rutautpnative.data.tracking

import android.content.Context
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

/** La CA ausente usa el almacén del sistema; una CA configurada ilegible produce un fallo. */
object MQTTTLS {
    fun socketFactory(caAsset: String, context: Context?): SSLSocketFactory {
        if (caAsset.isBlank()) return SSLSocketFactory.getDefault() as SSLSocketFactory
        val ctx = requireNotNull(context) { "No se puede abrir la CA configurada" }
        val certificado = ctx.assets.open(caAsset).use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null); setCertificateEntry("rutautp", certificado) }
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
        return SSLContext.getInstance("TLS").apply { init(null, trust.trustManagers, null) }.socketFactory
    }
}
