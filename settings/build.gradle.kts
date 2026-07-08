plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish.vanniktech)
}

kotlin {
    android {
        namespace = "com.coderwise.libs.settings"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    iosArm64(); iosSimulatorArm64()
    js {
        browser()
        nodejs()
    }
    jvm("desktop")

    sourceSets {
        applyDefaultHierarchyTemplate()
        commonMain.dependencies {
            // api: these types appear in the public API (KSerializer in
            // SettingsDataStoreFactory.create, Flow in SettingsDataStore, and the
            // Koin Module exposed by settingsModule).
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
            api(libs.koin.core)
        }

        val nonJsMain = sourceSets.create("nonJsMain") {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.androidx.datastore.core)
                implementation(libs.androidx.datastore.core.okio)
                implementation(libs.okio)
            }
        }

        androidMain.get().dependsOn(nonJsMain)
        iosMain.get().dependsOn(nonJsMain)
        val desktopMain = sourceSets.getByName("desktopMain") {
            dependsOn(nonJsMain)
        }

        jsMain.dependencies {
        }
    }
}

// See :utils for the publishing setup notes. Version from the release tag
// (-PlibVersion, set by the publish workflow from a `settings-v<version>` tag);
// local builds default to 0.0.0-LOCAL.
mavenPublishing {
    publishToMavenCentral()

    if (project.hasProperty("sign")) {
        signAllPublications()
    }

    coordinates(
        "com.coderwise.libs",
        "settings",
        providers.gradleProperty("libVersion").getOrElse("0.0.0-LOCAL"),
    )

    pom {
        name.set("Coderwise Settings")
        description.set("Typed, serializable settings persistence (DataStore-backed) for Compose Multiplatform apps.")
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
