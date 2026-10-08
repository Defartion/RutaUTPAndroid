import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// MQTT: las credenciales viven en local.properties (gitignored) o -P por
// línea de comandos, NUNCA en el repo. Sin MQTT_HOST el canal queda
// "no configurado" y la baliza del pasajero se desactiva sola (igual que iOS,
// que las lee del Scheme de Xcode).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun mqttProp(nombre: String): String =
    (localProps.getProperty(nombre) ?: project.findProperty(nombre) as? String ?: "").trim()

android {
    namespace = "com.example.rutautpnative"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.rutautpnative"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["MAPS_API_KEY"] = project.findProperty("MAPS_API_KEY") ?: ""
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "MQTT_HOST", "\"${mqttProp("MQTT_HOST")}\"")
        buildConfigField("String", "MQTT_PORT", "\"${mqttProp("MQTT_PORT")}\"")
        buildConfigField("String", "MQTT_USERNAME", "\"${mqttProp("MQTT_USERNAME")}\"")
        buildConfigField("String", "MQTT_PASSWORD", "\"${mqttProp("MQTT_PASSWORD")}\"")
        buildConfigField("String", "MQTT_TLS", "\"${mqttProp("MQTT_TLS")}\"")
        buildConfigField("String", "MQTT_CA_ASSET", "\"${mqttProp("MQTT_CA_ASSET")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.maps.compose)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.accompanist.permissions)

    implementation(libs.androidx.compose.material.icons)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sh.reorderable)
    implementation(libs.zxing.core)
    implementation(libs.paho.mqtt)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}