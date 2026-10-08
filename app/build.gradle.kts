plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aravind.spectra"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aravind.spectra"
        minSdk = 26
        targetSdk = 34
        // The release workflow passes these in; local builds use the fallbacks.
        versionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 3
        versionName = (project.findProperty("appVersionName") as String?) ?: "0.3.0"

        // FFmpeg ships arm64 + x86_64; keep only arm64 (real phones) to halve the APK size.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // One stable signing key (from GitHub Secrets) so every release can update the previous one.
    val keystorePath = System.getenv("KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null && File(keystorePath).exists()) {
            create("spectra") {
                storeFile = File(keystorePath)
                storeType = "pkcs12"
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("spectra")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("spectra")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Bit-exact decoding (community-maintained FFmpegKit fork, LGPL-3.0)
    implementation("dev.ffmpegkit-maintained:ffmpeg:8.1.9")
}
