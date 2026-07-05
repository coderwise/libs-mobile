plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":sample:common"))
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.coderwise.libs.sample.desktop.MainKt"

        nativeDistributions {
            packageName = "MapSample"
            packageVersion = "1.0.0"
        }
    }
}
