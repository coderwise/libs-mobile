plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish.vanniktech)
}

kotlin {
    android {
        namespace = "com.coderwise.libs.imagepicker"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    iosArm64(); iosSimulatorArm64()
    js { browser() }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
        }
        jsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

// See :utils for the publishing setup notes. Version comes from the release tag
// (-PlibVersion, set by the publish workflow from an `imagepicker-v<version>` tag);
// local builds default to 0.0.0-LOCAL.
mavenPublishing {
    publishToMavenCentral()

    if (project.hasProperty("sign")) {
        signAllPublications()
    }

    coordinates(
        "com.coderwise.libs",
        "imagepicker",
        providers.gradleProperty("libVersion").getOrElse("0.0.0-LOCAL"),
    )

    pom {
        name.set("Coderwise ImagePicker")
        description.set("Cross-platform system image picker with downscaling for Compose Multiplatform.")
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
