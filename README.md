# MedTracker

Minimal Android application using Kotlin, Jetpack Compose and Gradle Kotlin DSL.

- Application ID: `dev.dadyarri.medtracker`
- Minimum / target / compile Android SDK: 36 (Android 16)
- JDK 21; JVM bytecode target 17
- Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.2.21

Set `JAVA_HOME` to a full JDK 21 and `ANDROID_HOME` to the Android SDK.
Install `platforms;android-36`, `build-tools;35.0.0` and `platform-tools`.
Alternatively set the SDK path in an untracked `local.properties`.

```sh
./gradlew assembleDebug lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Open the project in Android Studio or use `./gradlew installDebug` with an
Android 16+ device/emulator connected. No backend or credentials are required.

In the prepared cloud environment:

```sh
source /workspace/toolchains/env.sh
cd /workspace/med-tracker
./gradlew assembleDebug lintDebug
```

The cloud environment keeps SDK/JDK and dependency caches outside the checkout.
