plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.elowen.niceTV"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.elowen.nicetv"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            keepDebugSymbols += "**/libbox.so"
        }
    }

    lint {
        disable += "AndroidGradlePluginVersion"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)

    // sing-box proxy library
    implementation(files("libs/libbox.aar"))

    // WebView proxy support
    implementation(libs.androidx.webkit)

    // 网络与解析
    implementation(libs.okhttp)
    implementation(libs.jsoup) // 解析 HTML
    implementation(libs.gson)

    // 图片加载
    implementation(libs.compose) // Compose 版 Glide
    implementation(libs.glide.okhttp)

    // 视频播放 (Media3)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.exoplayer.hls) // 支持 m3u8
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.datasource.okhttp)

    // 数据库 (Room)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // 图标 (可选)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material3.window.size.class1)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
