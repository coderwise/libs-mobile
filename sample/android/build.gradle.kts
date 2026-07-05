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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":sample:common"))
    implementation(libs.androidx.activity.compose)
}
