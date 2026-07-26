plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.colorsafe.trim"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.colorsafe.trim"
        // 要件: Android 12 (API 31) 以上対応
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.activity:activity-compose:1.10.0")

    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 動画プレビュー用 Media3 ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.10.0")
    implementation("androidx.media3:media3-ui:1.10.0")
    implementation("androidx.media3:media3-ui-compose:1.10.0")
    implementation("androidx.media3:media3-common:1.10.0")

    // FFmpegKit 後継フォーク(公式FFmpegKitは2025年4月に廃止済み)
    // API/パッケージ名は互換の com.arthenica.ffmpegkit.*
    implementation("com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
}
