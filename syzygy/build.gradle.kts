import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }

    android {
        namespace = "syzygy"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(libs.appstate.transform.testing)
        }
    }
}
