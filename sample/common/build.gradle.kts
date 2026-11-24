@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    // For the settings example's @Serializable state.
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.coderwise.libs.sample.common"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        withHostTest {}
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
            implementation(project(":billing"))
            implementation(project(":camera"))
            implementation(project(":database"))
            implementation(project(":filepicker"))
            implementation(project(":imagepicker"))
            implementation(project(":locale"))
            implementation(project(":location"))
            implementation(project(":logger"))
            implementation(project(":permissions"))
            implementation(project(":settings"))
            implementation(project(":utils"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.preview)
            // The gallery is a NavDisplay; :utils supplies its slide transition.
            implementation(libs.navigation3.ui)
            // Only for the image-picker example: decodeToImageBitmap() is what turns the
            // bytes :imagepicker hands back into something Image() can draw.
            implementation(libs.compose.components.resources)
        }
        androidMain.dependencies { implementation(libs.compose.tooling) }
    }
}
