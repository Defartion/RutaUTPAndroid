package com.example.rutautpnative.data.tracking

import android.content.Context
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

/** La CA ausente usa el almacén del sistema; una CA configurada ilegible produce un fallo. */
object MQTTTLS {
    fun aplicar(opciones: org.eclipse.paho.client.mqttv3.MqttConnectOptions, config: MQTTConfiguration, context: Context?) {
        if (config.useTLS) {
            opciones.socketFactory = socketFactory(config.caAsset, context, config.caPem)
            opciones.setHttpsHostnameVerificationEnabled(true)
        }
    }
    fun socketFactory(caAsset: String, context: Context?, caPem: String = ""): SSLSocketFactory {
        if (caAsset.isBlank() && caPem.isBlank()) return SSLSocketFactory.getDefault() as SSLSocketFactory
        val certificados = if (caPem.isNotBlank()) CertificateFactory.getInstance("X.509").generateCertificates(caPem.byteInputStream())
            else requireNotNull(context).assets.open(caAsset).use { CertificateFactory.getInstance("X.509").generateCertificates(it) }
        require(certificados.isNotEmpty()) { "Certificado no válido" }
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            certificados.forEachIndexed { index, cert -> setCertificateEntry("rutautp-$index", cert) }
        }
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
        return SSLContext.getInstance("TLS").apply { init(null, trust.trustManagers, null) }.socketFactory
    }
}
