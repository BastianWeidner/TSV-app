// Gemeinsamer Drive-Abgleich: mehrere Geräte teilen sich eine Datei (z. B. in Google Drive) und führen ihre Stände
// zusammen. Reine Kotlin-Logik ohne Android, per Unit-Test prüfbar; das Lesen und Schreiben der Datei steht in
// :core:module-api (GeteilteDatei).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
}
