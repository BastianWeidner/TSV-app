// Vereinsmeisterschaft: Oberfläche, Urkunden/Listen als PDF, Zeitzettel-Scan. Ohne Internet, ohne Kamera-Berechtigung
// (Fotos über die Kamera-App bzw. Galerie). Kennt nur :core:module-api, :core:scanner (Texterkennung) und die eigene Logik.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "de.dachapp.vereinsmeisterschaft.ui"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core:module-api"))
    implementation(project(":core:scanner"))
    implementation(project(":features:vereinsmeisterschaft:logic"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
}
