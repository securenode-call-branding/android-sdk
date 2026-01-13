pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    // Defaults can be overridden via gradle.properties or env (e.g. -PAGP_VERSION=...).
    val agpVersion = providers.gradleProperty("AGP_VERSION").getOrElse("8.5.2")
    val kotlinVersion = providers.gradleProperty("KOTLIN_VERSION").getOrElse("1.9.24")
    val kspVersion = providers.gradleProperty("KSP_VERSION").getOrElse("1.9.24-1.0.20")

    plugins {
        id("com.android.library") version agpVersion
        id("com.android.application") version agpVersion
        id("org.jetbrains.kotlin.android") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.serialization") version kotlinVersion
        id("com.google.devtools.ksp") version kspVersion
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "securenode-android-sdk"
include(":sample-app")

