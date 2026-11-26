plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish.vanniktech)
}

kotlin {
    jvm("desktop")
    macosArm64()

    sourceSets {
        applyDefaultHierarchyTemplate()
        commonMain.dependencies {
            api(libs.koin.core)
            // api: kotlinx.io.files.Path appears in the public API (DesktopAppConfig.koinModules).
            api(libs.kotlinx.io.core)
            api(libs.compose.runtime)
            api(libs.compose.ui)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        getByName("desktopMain").dependencies {
            api(compose.desktop.common)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// See :utils for the publishing setup notes. Version from the release tag
// (-PlibVersion, set by the publish workflow from a `desktop-v<version>` tag);
// local builds default to 0.0.0-LOCAL.
mavenPublishing {
    publishToMavenCentral()

    if (project.hasProperty("sign")) {
        signAllPublications()
    }

    coordinates(
        "com.coderwise.libs",
        "desktop",
        providers.gradleProperty("libVersion").getOrElse("0.0.0-LOCAL"),
    )

    pom {
        name.set("Coderwise Desktop")
        description.set("Desktop app bootstrap for Compose Multiplatform: Koin, data directory, dock icon, About dialog and remembered window state in one call.")
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
