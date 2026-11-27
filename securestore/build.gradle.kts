@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.multiplatform.library)
    alias(libs.plugins.maven.publish.vanniktech)
}

kotlin {
    android {
        namespace = "com.coderwise.libs.securestore"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdkCompat.get().toInt()
    }
    iosArm64(); iosSimulatorArm64()
    js {
        browser()
        nodejs()
    }
    wasmJs { browser() }
    jvm("desktop")

    sourceSets {
        applyDefaultHierarchyTemplate()
        commonMain.dependencies {
            // api: Koin's Module is the type of secureStoreModule.
            api(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
    }
}

// See :utils for the publishing setup notes. Version from the release tag
// (-PlibVersion, set by the publish workflow from a `securestore-v<version>` tag);
// local builds default to 0.0.0-LOCAL.
mavenPublishing {
    publishToMavenCentral()

    if (project.hasProperty("sign")) {
        signAllPublications()
    }

    coordinates(
        "com.coderwise.libs",
        "securestore",
        providers.gradleProperty("libVersion").getOrElse("0.0.0-LOCAL"),
    )

    pom {
        name.set("Coderwise SecureStore")
        description.set("A tiny key-value store for secrets: Keychain on iOS, Keystore-encrypted prefs on Android, memory elsewhere.")
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
