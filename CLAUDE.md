# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build

```bash
export ANDROID_HOME=/mnt/disk1/android-sdk
./gradlew assembleDebug
# If wrapper download fails, use system Gradle:
# /mnt/disk1/java/gradle-9.3.1/bin/gradle assembleDebug
```

APK outputs to `app/build/outputs/apk/debug/app-debug.apk` by default.

SDK minimum is 28 (Android 9), target 36. Xposed API jar is at `app/libs/XposedBridgeAPI-82.jar` (compileOnly, not bundled).

## Architecture

LSPosed Xposed module for global sensor faking. Entry point declared in `app/src/main/assets/xposed_init`.

**Core hook strategy:** `XposedEntry` implements `IXposedHookZygoteInit` — hooks `SystemSensorManager.registerListenerImpl` and `unregisterListenerImpl` at the Zygote level via boot classloader. All app processes inherit the hooks without per-app injection. `handleLoadPackage` is a fallback only.

**Sensor data injection:** `SensorHook.SensorListenerHook` wraps the original `SensorEventListener` with `FakeSensorEventListener`. The proxy's `onSensorChanged` replaces `event.values` with fake data before forwarding to the original listener.

**Config hot-reload chain** (tried in order, 500ms throttle in `reloadConfig()`):
1. ContentProvider query → `content://com.app.fakesensor.config/all`
2. File: `/sdcard/Android/data/com.app.fakesensor/files/fake_sensor_config.txt`
3. File: `/sdcard/fake_sensor_config.txt`
4. File: `/data/local/tmp/fake_sensor_config.txt` (written via `su`)
5. XSharedPreferences (same package name, MODE_WORLD_READABLE)

Config format: pipe-separated key=value. Key fields: `enabled`, `simulate`, `scenario`, `types` (comma-separated sensor type codes).

**Step counter:** `ensureStepTimer()` is a static global `HandlerThread` that increments `stepCount` per scenario every 1s and pushes to all registered step listeners. `stepBase` is captured from the first real hardware event; displayed value = `stepBase + stepCount`.

## Key files

| File | Role |
|------|------|
| `SensorHook.java` | Hook logic, config loading, fake data generation, proxy listener |
| `MainActivity.java` | Sensor checkboxes + language toggle + static/dynamic mode buttons. Static writes fixed values directly; Dynamic opens SimulationActivity |
| `SimulationActivity.java` | Scenario buttons, real-time value display, config save/delete |
| `ConfigProvider.java` | ContentProvider exporting SharedPreferences for cross-process reads |
| `XposedEntry.java` | Zygote init + LoadPackage fallback |
| `LocaleHelper.java` | Language switching (zh/en), saved to SharedPreferences |

## ConfigProvider authority

`content://com.app.fakesensor.config/all` — must match the authority declared in `AndroidManifest.xml`. The `SensorHook.tryContentProvider()` parses this and the authority is hardcoded in both files.

## Localization

Language preference stored in a non-Xposed SharedPreferences (`app_prefs`) separate from the sensor config. `LocaleHelper` wraps `attachBaseContext` to apply locale before `Activity.onCreate`. Language toggle calls `Activity.recreate()` to restart with the new locale.

Default: Simplified Chinese (`zh`). Also supported: English (`en`). All user-facing strings are in `res/values/strings.xml` (zh) and `res/values-en/strings.xml` (en). Layouts reference `@string/` resources — no hardcoded user-facing text.

## Platform-specific notes

- Android 12+ disallows `MODE_WORLD_READABLE`, so SharedPreferences alone is insufficient for cross-process config. The module writes config to `/data/local/tmp/` via `su` as the primary IPC path.
- `compileSdk 34` is used because the local SDK lacks platform 36. Target SDK 36 is set in `defaultConfig` regardless.
- AGP version is pinned to 8.5.0 (`gradle/libs.versions.toml`) for SDK 34 compatibility.
