import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    // Metro is applied so the milestone-2 test can build a real @DependencyGraph on the owned scope.
    // It is a compiler plugin only: nothing in src/main references a Metro type — DI is threaded
    // through the `align` seam, never named by the core. (See IMPLEMENTATION_PLAN §2, §3.)
    alias(libs.plugins.metro)
}

kotlin {
    jvmToolchain(17)
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}

dependencies {
    // Discipline #1: syzygy-core depends ONLY on multiplatform libraries — the Compose runtime,
    // coroutines, and Molecule. No android.*/androidx.* platform types. This keeps the primitive
    // pure and lets a full tree run headless in a plain JVM `src/test` unit test.
    api(libs.compose.runtime.multiplatform)
    api(libs.compose.runtime.retain)
    api(libs.molecule.runtime)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Discipline #1 enforcement (IMPLEMENTATION_PLAN §6): fail the build if syzygy-core gains any
// Android/AndroidX non-Compose-runtime dependency. The Compose runtime itself resolves under the
// `androidx.compose.runtime` / `androidx.collection` / `androidx.annotation` coordinates but is
// multiplatform, so those are allowlisted; anything else android-flavored is a platform leak that
// breaks the headless-test story and must move to syzygy-projection / syzygy-nav3.
val multiplatformAndroidxAllowlist = listOf(
    "androidx.compose.runtime",
    "androidx.collection",
    "androidx.annotation",
)
val platformLeaks = configurations.named("runtimeClasspath").map { cfg ->
    cfg.incoming.resolutionResult.allComponents
        .mapNotNull { it.moduleVersion?.group }
        .filter { group ->
            (group.startsWith("androidx.") && multiplatformAndroidxAllowlist.none { group == it || group.startsWith("$it.") }) ||
                group.startsWith("com.android")
        }
        .distinct()
}
val checkNoPlatformDeps = tasks.register("checkNoPlatformDeps") {
    val leaks = platformLeaks
    doLast {
        val found = leaks.get()
        require(found.isEmpty()) {
            "syzygy-core must stay platform-free but resolved platform libraries: $found. " +
                "Push platform code into syzygy-projection / syzygy-nav3 (IMPLEMENTATION_PLAN §6, discipline #1)."
        }
    }
}
tasks.named("check") { dependsOn(checkNoPlatformDeps) }
