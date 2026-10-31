// Reine Kotlin-Logik (Import, Kursplanung, Einladungen) ohne Android-Abhängigkeit, per Unit-Test prüfbar.
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
    implementation(libs.kotlinx.serialization.json)
    // QR-Code für die Überweisung (GiroCode), reine Java-Bibliothek ohne Android-Abhängigkeit.
    api(libs.zxing.core)
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
}
