// Die einzige gemeinsame Schnittstelle: Unter-Apps hängen nur hiervon ab, nie voneinander.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "de.dachapp.module"
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
    api(platform(libs.compose.bom))
    api(libs.compose.runtime)
    // Für die gemeinsame Pflichtfeld-Markierung (Pflichtfeld.kt).
    api(libs.compose.material3)
    // Benachrichtigungen (Erinnerungen.kt).
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
}
