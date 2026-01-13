plugins {
    // Versions are defined centrally in settings.gradle.kts (pluginManagement) to avoid mismatch across modules.
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("maven-publish")
}

android {
    namespace = "io.securenode.branding"
    compileSdk = 34

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField(
            "String",
            "VERSION_NAME",
            "\"${(project.findProperty("VERSION_NAME") as String?) ?: "1.0.0"}\""
        )
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xjvm-default=all")
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.core:core-ktx:1.13.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.9.0")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = (project.findProperty("GROUP") as String?) ?: "io.securenode"
                artifactId = (project.findProperty("POM_ARTIFACT_ID") as String?) ?: "branding-sdk"
                version = (project.findProperty("VERSION_NAME") as String?) ?: "1.0.0"
                from(components["release"])
                pom {
                    name.set("SecureNode Branding SDK")
                    description.set("Android library for PSTN call branding with SecureNode-compatible backend.")
                }
            }
        }
    }
}
