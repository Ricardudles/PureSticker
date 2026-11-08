plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.wppsticker"
    ndkVersion = "29.0.14206865"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ricardudles.puresticker"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        externalNativeBuild {
            cmake { arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON" }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-Xsuppress-version-warnings"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs { useLegacyPackaging = false }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

// Repositories are managed in settings.gradle.kts with FAIL_ON_PROJECT_REPOS

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)

    // Coil (Bundle: compose, video, gif)
    implementation(libs.bundles.coil)
    
    // Accompanist
    implementation(libs.accompanist.permissions)

    // Image Cropper
    implementation(libs.android.image.cropper)

    // Gson
    implementation(libs.gson)

    // Media3 / ExoPlayer (Bundle)
    implementation(libs.bundles.media3)

    // Sequential GIF frame decoding for animated WebP export (API 26+)
    implementation(libs.glide.gifdecoder)

    // ML Kit
    implementation(libs.mlkit.segmentation.selfie)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
