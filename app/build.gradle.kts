import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Signatur-Daten liegen außerhalb des Quellcodes in keystore.properties (siehe README).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "de.dachapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.dachapp"
        minSdk = 29
        targetSdk = 35
        versionCode = 35
        versionName = "1.14.0"
    }

    // Einzige Variante "schwimmen" (App-ID de.dachapp.schwimmen): Schwimmkurs, Schwimmgruppen, Schwimmleitung.
    // Unter-Apps: Abhängigkeiten unten + src/schwimmen/kotlin/.../ModuleRegistry.kt.
    flavorDimensions += "umfang"
    productFlavors {
        create("schwimmen") {
            dimension = "umfang"
            applicationIdSuffix = ".schwimmen"
        }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Native Bibliotheken (ML Kit) und Code komprimiert ablegen: APK „alle“ bleibt so unter ca. 25 MB,
        // sonst lässt es sich in der Claude-App nicht herunterladen (1.8.0 mit 35 MB ging nicht).
        jniLibs.useLegacyPackaging = true
        dex.useLegacyPackaging = true
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core:module-api"))
    // Unter-Apps. Eine neue Unter-App: Modul anlegen, hier eintragen (implementation),
    // in src/schwimmen/kotlin/.../ModuleRegistry.kt aufnehmen.
    implementation(project(":features:schwimmkurs:ui"))
    implementation(project(":features:schwimmgruppen:ui"))
    implementation(project(":features:schwimmleitung:ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
}
