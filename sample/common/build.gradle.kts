@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "com.coderwise.libs.sample.common"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "SampleCommon"
            isStatic = true
        }
    }
    js { browser() }
    wasmJs { browser() }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(project(":filepicker"))
            implementation(project(":imagepicker"))
            implementation(project(":logger"))
            implementation(project(":map-core"))
            implementation(project(":map-engine"))
            implementation(project(":map-view"))
            implementation(project(":map-view-tiles"))
            implementation(project(":map-view-tiles-raster"))
            implementation(project(":permissions"))
            implementation(project(":utils"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.preview)
            // Only for the image-picker example: decodeToImageBitmap() is what turns the
            // bytes :imagepicker hands back into something Image() can draw.
            implementation(libs.compose.components.resources)
            // Only for the map engine example: the engine fetches nothing itself, so pointing it
            // at a real tile server is the sample's job.
            implementation(libs.ktor.client.core)
        }
        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
        jsMain.dependencies { implementation(libs.ktor.client.js) }
        wasmJsMain.dependencies { implementation(libs.ktor.client.js) }
        getByName("desktopMain").dependencies { implementation(libs.ktor.client.okhttp) }
    }
}

dependencies {
    "androidRuntimeClasspath"(libs.compose.tooling)
}
