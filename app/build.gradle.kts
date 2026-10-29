import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.sonora.music"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sonora.music"
        minSdk = 26
        targetSdk = 36
        versionCode = 42
        versionName = "0.41.1-beta"
    }

    // Chiave di firma FISSA (nel repo): ogni build ha la stessa firma, quindi gli aggiornamenti
    // si installano sopra la versione precedente senza "firma non corrispondente".
    signingConfigs {
        create("sonora") {
            storeFile = file("sonora.jks")
            storePassword = "sonora-beta"
            keyAlias = "sonora"
            keyPassword = "sonora-beta"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("sonora")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sonora")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // Compose + Material 3 (Expressive)
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    // Material 3 stabile (versione decisa dal BOM). Le API Expressive ufficiali richiedono compileSdk 37 / AGP 9, quindi l'aspetto Expressive è realizzato con componenti propri.
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")

    // Riproduzione in background
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")
    implementation("androidx.media3:media3-database:1.8.0")
    implementation("androidx.media3:media3-datasource:1.8.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.8.0")

    // Rete, immagini, estrazione stream
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
}
