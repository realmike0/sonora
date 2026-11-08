import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Sonora Framework: app invisibile (senza icona) che gestisce gli account Sonora ID per Sonora.
// Stessa firma di Sonora (serve per il permesso "signature" tra le due app). Tieni la versione allineata a quella di :app.
android {
    namespace = "com.sonora.framework"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sonora.framework"
        minSdk = 26
        targetSdk = 36
        versionCode = 90
        versionName = "0.90-beta"
    }

    signingConfigs {
        create("sonora") {
            storeFile = file("../app/sonora.jks")
            storePassword = "sonora-beta"
            keyAlias = "sonora"
            keyPassword = "sonora-beta"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("sonora") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sonora")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
