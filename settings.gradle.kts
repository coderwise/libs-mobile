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

// The :map-view family lives in map/. Each module keeps its top-level project name — `:map-view-core`,
// not `:map:map-view-core` — which is what the release tags, the publish workflow and the
// dependencies between the modules all use; only where one sits on disk changes.
file("map").listFiles()?.filter { it.isDirectory && File(it, "build.gradle.kts").exists() }?.forEach {
    include(":${it.name}")
    project(":${it.name}").projectDir = it
}

// Sample app modules live one level down (sample/<platform>); the sample dir
// itself has no build file, so the top-level loop above skips it.
file("sample").listFiles()?.filter { it.isDirectory && File(it, "build.gradle.kts").exists() }?.forEach {
    include(":sample:${it.name}")
}
