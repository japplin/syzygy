import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP 9 has built-in Kotlin support, so no kotlin-android plugin is applied here.
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    // Metro provides the multibinding glue for @OutputKey (the DI layer's choice, not the core's).
    alias(libs.plugins.metro)
}

android {
    namespace = "syzygy.projection"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}

dependencies {
    api(project(":syzygy-core"))

    api(platform(libs.compose.bom))
    api(libs.compose.runtime)
    implementation(libs.compose.ui)

    testImplementation(libs.junit)
}
