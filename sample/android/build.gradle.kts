plugins {
    alias(libs.plugins.android.application)
    // No kotlin.android — AGP 9+ has built-in Kotlin support
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.coderwise.libs.sample.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.coderwise.libs.sample"
        minSdk = libs.versions.minSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        // Signed with the debug key so that `installRelease` just works. The point of it is not
        // shipping — nothing here ships — it is that a debuggable APK is pinned to ART's `verify`
        // filter and can never be AOT compiled, so a debug build measures the interpreter rather
        // than the app. Profile the map on this one.
        release {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":sample:common"))
    implementation(libs.androidx.activity.compose)
}
