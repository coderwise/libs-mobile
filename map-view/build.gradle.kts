plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish.vanniktech)
}


@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
    android {
        namespace = "com.coderwise.libs.mapview"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        withHostTest {}
    }
    iosArm64(); iosSimulatorArm64()
    js { browser() }
    wasmJs { browser() }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            // api, not implementation: Modifier, Composable and Offset are all over the public
            // surface, so consumers have to see them.
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        // The gesture detector can only be exercised by actually sending it pointers, and one
        // host is enough for that: the code under test is common.
        val desktopTest by getting {
            dependencies {
                implementation(libs.compose.ui.test)
                implementation(libs.compose.ui.test.junit4)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

// See :utils for publishing notes. Version from the release tag (-PlibVersion, set by the publish
// workflow); local builds default to 0.0.0-LOCAL.
mavenPublishing {
    publishToMavenCentral()

    if (project.hasProperty("sign")) {
        signAllPublications()
    }

    coordinates(
        "com.coderwise.libs",
        "map-view",
        providers.gradleProperty("libVersion").getOrElse("0.0.0-LOCAL"),
    )

    pom {
        name.set("Coderwise Map View")
        description.set("Compose Multiplatform map component: a MapView of layered tile slots, a fractional-zoom camera and its gestures, and overlays placed by coordinate. Draws whatever a MapState holds and fetches nothing itself.")
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
