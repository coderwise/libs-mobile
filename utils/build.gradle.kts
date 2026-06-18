plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish.vanniktech)
}

kotlin {
    android {
        namespace = "com.coderwise.libs.utils"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    iosArm64(); iosSimulatorArm64()
    js { browser() }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.compose.ui)
            implementation(libs.androidx.activity)
            implementation(libs.androidx.core.ktx)
            implementation(libs.koin.android)
        }
    }
}

// Publishing is handled by the vanniktech maven-publish plugin, which targets
// the Maven Central (Sonatype Central Portal) and creates the sources/javadoc
// jars and signs all publications. Credentials are supplied as Gradle
// properties / env vars (never committed):
//   mavenCentralUsername / mavenCentralPassword  -> Central Portal user token
//   signingInMemoryKey / signingInMemoryKeyPassword (+ optional ...KeyId) -> GPG
// `./gradlew :utils:publishToMavenLocal` still works without credentials.
mavenPublishing {
    publishToMavenCentral()

    // Sign only when explicitly asked (CI passes -Psign=true), so local
    // publishToMavenLocal works without a GPG key. Matches core.mobile.
    if (project.hasProperty("sign")) {
        signAllPublications()
    }

    // Version comes from the release tag (-PlibVersion=<version>, set by the
    // publish workflow from a `utils-v<version>` tag); local builds default to
    // 0.0.0-LOCAL. 0.2.0 was the last version published from maps-mobile.
    coordinates(
        "com.coderwise.libs",
        "utils",
        providers.gradleProperty("libVersion").getOrElse("0.0.0-LOCAL"),
    )

    pom {
        name.set("Coderwise Utils")
        description.set("Cross-platform utilities (file sharing, platform system-bar colors) for Compose Multiplatform.")
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
