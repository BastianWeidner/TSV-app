// Gemeinsamer Scanner: Barcodes und Texterkennung mit ML Kit, beide Modelle in der App, ohne Internet.
// Nur Unter-Apps der Variante „alle“ hängen hiervon ab (Einkauf, Tracker, Preise), damit die Schwimm-App klein bleibt.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "de.dachapp.scanner"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    // ImageProxy und ImageAnalysis.Analyzer stehen in der Schnittstelle.
    api(libs.androidx.camera.camera2)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.text.recognition)
}
