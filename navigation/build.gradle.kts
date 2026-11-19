@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish.vanniktech)
}

kotlin {
    android {
        namespace = "com.coderwise.libs.navigation"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        withHostTest {}
    }
    iosArm64(); iosSimulatorArm64()
    js {
        browser()
        nodejs()
    }
    wasmJs { browser() }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            // api: NavKey, NavBackStack, SavedStateConfiguration and the Koin Module are
            // all in the public signatures.
            api(libs.navigation3.runtime)
            api(libs.savedstate)
            api(libs.koin.core)
            implementation(libs.compose.runtime)
            implementation(libs.compose.runtime.saveable)
            implementation(libs.koin.compose)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// See :utils for the publishing setup notes. Version from the release tag
// (-PlibVersion, set by the publish workflow from a `navigation-v<version>` tag);
// local builds default to 0.0.0-LOCAL.
mavenPublishing {
    publishToMavenCentral()

    if (project.hasProperty("sign")) {
        signAllPublications()
    }

    coordinates(
        "com.coderwise.libs",
        "navigation",
        providers.gradleProperty("libVersion").getOrElse("0.0.0-LOCAL"),
    )

    pom {
        name.set("Coderwise Navigation")
        description.set("Root and per-root child back stacks on Navigation 3, with navigation events and state that survives process death.")
        inceptionYear.set("2026")
        url.set("https://github.com/coderwise/libs-mobile")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("coderwise")
                name.set("Coderwise")
                url.set("https://github.com/coderwise")
            }
        }
        scm {
            url.set("https://github.com/coderwise/libs-mobile")
            connection.set("scm:git:git://github.com/coderwise/libs-mobile.git")
            developerConnection.set("scm:git:ssh://git@github.com/coderwise/libs-mobile.git")
        }
    }
}
