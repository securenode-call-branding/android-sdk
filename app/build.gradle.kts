plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Avoid Windows file-lock issues under app/build/outputs by placing this module's build dir
// under the root build directory instead.
layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("app"))

android {
    namespace = "com.securenode.sdk.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.securenode.sdk.app"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        signingConfig = signingConfigs.getByName("debug")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
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
    }
}

dependencies {
    implementation(project(":"))

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")

    // Background sync
    implementation("androidx.work:work-runtime-ktx:2.11.0")

    // QR scan (CameraX + ML Kit)
    implementation("androidx.camera:camera-camera2:1.5.2")
    implementation("androidx.camera:camera-lifecycle:1.5.2")
    implementation("androidx.camera:camera-view:1.5.2")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
}


