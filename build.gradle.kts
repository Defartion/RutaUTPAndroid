// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.compose.screenshot") version "0.0.1-alpha16" apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
