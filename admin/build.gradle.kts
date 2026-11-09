import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Sonora Admin: piccola app per gestire utenti, commenti e dediche (serve la chiave ADMIN_KEY).
android {
    namespace = "com.sonora.admin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sonora.admin"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
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
