plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.multiplatform.library)
    alias(libs.plugins.maven.publish.vanniktech)
}

group = "com.coderwise.libs"
version = providers.gradleProperty("libVersion").getOrElse("0.0.0-LOCAL")

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
    android {
        namespace = "com.coderwise.libs.mapview.core"
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
            // Only the annotations, which carry no Compose runtime and no Skiko: they tell a
            // module compiled with the Compose compiler that what is here is stable.
            api(libs.compose.runtime.annotation)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
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
        "map-view-core",
        providers.gradleProperty("libVersion").getOrElse("0.0.0-LOCAL"),
    )

    pom {
        name.set("Coderwise Map View Core")
        description.set("The types the com.coderwise.libs:map-view family speaks in that need no Compose: TileKey, the address of one tile. For modules that handle tiles without drawing them, such as a cache.")
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
