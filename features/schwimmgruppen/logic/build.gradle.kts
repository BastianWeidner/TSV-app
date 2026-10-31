// Reine Kotlin-Logik (Import, Gruppen, Anwesenheit, Zeiten) ohne Android-Abhängigkeit, per Unit-Test prüfbar.
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
    // Abgleich mit anderen Trainern über eine geteilte Datei.
    api(project(":core:abgleich"))
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
}
