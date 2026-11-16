# Building Syzygy

Use JDK 21 and Android SDK Platform 37. Configure the SDK with `ANDROID_HOME` or set `sdk.dir` in
the project's ignored `local.properties`. The Gradle wrapper pins the Gradle version and checksum.

```sh
./gradlew :syzygy:jvmTest :sample:assembleDebug
```

The JVM suite runs the DSL and projection bridge in a headless Compose runtime. Build and run the
Android navigation and Activity recreation test on a connected device or emulator with API 26 or newer.

```sh
./gradlew :sample:connectedDebugAndroidTest
```

In the sample, go from Home to Profile to Settings, open Rename, and save a new name. Back returns
to Profile and Home, where the new name appears. A screen change crossfades between the two
presentations.
