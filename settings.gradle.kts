@file:Suppress("UnstableApiUsage")

rootProject.name = "libs-mobile"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// Auto-include every top-level module (a directory holding a build.gradle.kts),
// so adding a new shared library module needs no settings change.
rootDir.listFiles()?.filter { it.isDirectory && File(it, "build.gradle.kts").exists() }?.forEach {
    include(":${it.name}")
}
