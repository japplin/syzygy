# Building Syzygy

## Modules

| Module | What | Build target |
| --- | --- | --- |
| `syzygy-core` | The primitive: `Model`, `Projection`, `Syzygy`, `syzygy()`, `SyzygyContent`, `Lifetime`, the aligned-runtime engine + retention seam. Platform-free `kotlin("jvm")` — runs headless. | `./gradlew :syzygy-core:test` |
| `syzygy-projection` | Projection registry, `Project(output)`, `@OutputKey` multibinding glue. | `./gradlew :syzygy-projection:test` |
| `syzygy-nav3` | Nav3 integration: one aligned unit per back-stack entry, retention tied to entry lifecycle. | `./gradlew :syzygy-nav3:compileDebugKotlin` |
| `sample` | Home → Profile → Settings, each a Metro graph extension; model-nesting clock; effect survival. | `./gradlew :sample:assembleDebug` |

## Toolchain requirements (non-obvious — read this)

- **JDK 21 for the Gradle daemon.** Pinned in `gradle.properties` via `org.gradle.java.home`.
  - Metro 0.13.2's Gradle plugin requires a JVM **≥ 21**, so JDK 17 fails.
  - JDK **26**'s `jlink` rejects Android's prebuilt `core-for-system-modules.jar` (a new build-signature
    check), so the Android modules fail their `JdkImageTransform`. Use a **21** JVM.
  - If you relocate JDKs, update `org.gradle.java.home`.
- **Configuration cache is disabled** (`org.gradle.configuration-cache=false`): AGP 9's `JavaCompile` +
  `JdkImageTransform` isn't configuration-cache-serializable in this setup.
- **Android SDK** path is in `local.properties` (`sdk.dir`). compileSdk 36.

## What is verified here vs. on a device

- **Verified (JVM, `./gradlew test`):** the core engine (Molecule loop on an owned scope, effect
  scoping, retire cancels the scope), Retained-vs-Remembered lifetime, retain-registry keep/retire,
  Metro-through-`align`, and the projection registry lookup. 11 tests.
- **Verified (compiles + APK assembles):** `syzygy-nav3` and the full `sample` (Metro graph extensions,
  multibound projections, Nav3 hosting).
- **Not exercised here (needs an emulator/device):** actual rotation retention, push/pop teardown, and
  the on-screen clock/effect survival. The behavior is proven headless in core; the device run is the
  integration check.

## Version catalog

All versions live in `gradle/libs.versions.toml` (AGP 9.0.0, Kotlin 2.3.0, Metro 0.13.2, Molecule 2.1.0,
Compose BOM 2025.09.01, Nav3 1.0.0). The platform-free core uses the multiplatform Compose runtime
(`org.jetbrains.compose.runtime:runtime`, package `androidx.compose.runtime`), the same one Molecule pulls.
