# Building

[← README](../README.md) · [How it works](how-it-works.md) · [Usage](usage.md) · [Limitations](limitations.md)

## Requirements

- JDK 17 or newer (the JBR bundled with Android Studio works).
- Android SDK with platform **android-37** (`compileSdk`) and build-tools.
  `local.properties` with `sdk.dir=…` is created automatically by Android Studio. Otherwise set
  `ANDROID_HOME`.
- Internet access for the first build only, to download Gradle and the dependencies.

## Commands

```bash
# macOS / Linux / Git Bash
./gradlew assembleDebug

# Windows PowerShell (if java is not on PATH)
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
.\gradlew.bat assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/app-debug.apk`. Install it with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

You can also open the project in Android Studio and press **Run**.

## Stack

- Kotlin, Jetpack Compose (Material 3), AndroidX DataStore.
- AGP 9 with built-in Kotlin. Versions are listed in `gradle/libs.versions.toml`.
- `minSdk 29`, `targetSdk 35`.

## Source layout

```
app/src/main/java/com/oneping/
├── OnePingApp.kt                  Application: creates the engine, service status
├── data/
│   ├── Models.kt                  settings, rules, sources, log entries
│   ├── JsonCodec.kt               JSON (de)serialization for DataStore
│   └── Repository.kt              DataStore: settings, rules, timers, sources, apps, stats, log
├── engine/
│   ├── NotificationParser.kt      notification → source key + message state
│   └── CooldownEngine.kt          new-message detection, per-source cooldown, log
├── service/
│   ├── ListenerService.kt         judges notifications, cancels repeats, resets
│   ├── SilentCards.kt             one silent conversation card per source
│   ├── CardActionReceiver.kt      "Mute 1 h" / "Always ring" buttons
│   └── PauseTileService.kt        Quick Settings tile: pause for 1 hour
└── ui/                            Compose screens: Home, People
```
