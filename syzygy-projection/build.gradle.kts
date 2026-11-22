import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp)
    alias(libs.plugins.metro)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }

    android {
        namespace = "syzygy.projection"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmAndAndroidMain = create("jvmAndAndroidMain") { dependsOn(commonMain.get()) }
        jvmMain { dependsOn(jvmAndAndroidMain) }
        androidMain { dependsOn(jvmAndAndroidMain) }

        commonMain.dependencies {
            api(project(":syzygy"))
            api(libs.compose.animation)
            implementation(libs.compose.runtime.retain)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(libs.appstate.transform.testing)
        }
    }
}
