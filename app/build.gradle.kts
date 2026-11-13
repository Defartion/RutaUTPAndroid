import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Firebase es opcional: una instalación sin proyecto sigue compilando y usando avisos locales.
if (file("google-services.json").isFile) apply(plugin = "com.google.gms.google-services")
val generarCapturas = providers.gradleProperty("capturas").orNull == "true"
if (generarCapturas) apply(plugin = "com.android.compose.screenshot")

// Solo configuración pública en el APK. Las cuentas se aprovisionan por teléfono.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun mqttProp(nombre: String): String =
    (project.findProperty(nombre) as? String ?: System.getenv(nombre) ?: localProps.getProperty(nombre) ?: "").trim()

fun cadenaBuild(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""

android {
    if (generarCapturas) experimentalProperties["android.experimental.enableScreenshotTest"] = true
    namespace = "com.example.rutautpnative"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.rutautpnative"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"
        manifestPlaceholders["MAPS_API_KEY"] = mqttProp("MAPS_API_KEY")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "MQTT_HOST", cadenaBuild(mqttProp("MQTT_HOST")))
        buildConfigField("String", "MQTT_PORT", cadenaBuild(mqttProp("MQTT_PORT")))
        buildConfigField("String", "MQTT_TLS", cadenaBuild(mqttProp("MQTT_TLS")))
        buildConfigField("String", "MQTT_CA_ASSET", cadenaBuild(mqttProp("MQTT_CA_ASSET")))
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
    testOptions { unitTests.all { it.systemProperty("rutautp.assets", file("src/main/assets").absolutePath) } }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
dependencies {
    if (generarCapturas) {
        add("screenshotTestImplementation", platform(libs.androidx.compose.bom))
        add("screenshotTestImplementation", "com.android.tools.screenshot:screenshot-validation-api:0.0.1-alpha16")
        add("screenshotTestImplementation", "androidx.compose.ui:ui-tooling")
    }
    implementation("com.google.firebase:firebase-messaging:26.0.1")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // navigation-compose NO se usa directamente (la navegacion es manual via
    // AppRouter), PERO arrastra transitivamente lifecycle-viewmodel-compose,
    // que es de donde sale viewModel() usado en RootView y MainActivity.
    // TODO(l3-cleanup): sustituir por implementation("androidx.lifecycle:lifecycle-viewmodel-compose")
    // cuando se verifique que no hay mas dependencias transitivas criticas.
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
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
