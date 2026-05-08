# FakeSensor

Global Sensor Spoofing Xposed Module.

Hooks `SystemSensorManager.registerListenerImpl` and `unregisterListenerImpl` to inject a proxy `SensorEventListener` at the Zygote stage, intercepting and replacing sensor data for target apps.

## Prerequisites

- Device rooted with LSPosed framework installed
- This module enabled in LSPosed and your root manager
- Target apps checked in LSPosed scope
- Currently tested only on MIUI 13 (Android 12)

## Basic Setup

1. Install the module
2. Grant su permission for this module in Magisk or other root manager
3. Open LSPosed Manager, enable this module, check target apps (system framework required)
4. Reboot to activate the module
5. Open FakeSensor app
6. Check the sensors you want to spoof (at least one)
7. Tap **Static Sim** or **Dynamic Sim**
8. Restart the target app to apply spoofing

## Usage

### Static Sim

Adjust axis values in real-time via sliders — changes are saved immediately (`simulate=false`). Target apps will receive fixed values from the sliders. Ideal for precise sensor value control.

### Dynamic Sim

Generates real-time varying data based on selected scenario (`simulate=true`):

- **Stationary** — minimal noise, amplitude very small
- **Walking** — moderate periodic oscillation with random noise
- **Running** — large high-frequency oscillation
- **Cycling** — smooth low-frequency swing

Step sensors are pushed independently by a background timer, auto-incrementing steps based on the current scenario.

### Config File Paths

The module reads config from the following sources in priority order:

| Priority | Source | Path/URI |
|----------|--------|----------|
| 1 | ContentProvider | `content://com.app.fakesensor.config/all` |
| 2 | External storage | `/sdcard/Android/data/com.app.fakesensor/files/fake_sensor_config.txt` |
| 3 | Root directory | `/sdcard/fake_sensor_config.txt` |
| 4 | Temp directory | `/data/local/tmp/fake_sensor_config.txt` |
| 5 | SharedPreferences | XSharedPreferences (same package) |

Config format: `key=value` separated by `|`:

```
enabled=true|simulate=true|scenario=walking|types=1,4,9|accel_x=0.0|accel_y=0.0|accel_z=9.8
```

Key fields:

| Field | Type | Description |
|-------|------|-------------|
| `enabled` | boolean | Whether spoofing is enabled |
| `simulate` | boolean | true=dynamic random values, false=fixed values |
| `scenario` | String | `stationary` / `walking` / `running` / `cycling` |
| `types` | String | Comma-separated sensor type codes |
| `accel_x` / `accel_y` / `accel_z` | float | Fixed per-axis values (effective only in non-simulate mode) |

## ⚖️ Disclaimer & Known Limitations

- ⚠️ **This module is for learning and development testing only** — do not use for fraud or violating app Terms of Service
- ⚠️ **Tested on MIUI 13 (Android 12) only** — compatibility on other versions unknown, feedback welcome
- ⚠️ **Do not use to bypass anti-fraud in financial apps** — may violate local laws
- ⚠️ **Users bear all legal responsibility** — developer assumes no liability for any direct or indirect damages from using this project
- 📱 **Compatibility feedback for other Android versions and devices** — welcome in Issues

## How It Works

### Xposed Injection

The module implements both `IXposedHookZygoteInit` and `IXposedHookLoadPackage`:

```
initZygote()
  ├─ SensorHook.init()          → Initialize config
  └─ SensorHook.hookSensorManagerInClassLoader(null)
       └─ Locate registerListenerImpl / unregisterListenerImpl in SystemSensorManager
            └─ Inject proxy via XposedBridge.hookMethod()
```

Zygote-stage hooks are inherited by all app processes (since `SystemSensorManager` is loaded by the boot classloader), no per-app re-hook needed.

### Hook Point: registerListenerImpl

When a target app registers a sensor listener:

```
App: sensorManager.registerListener(listener, sensor, delay)
                     ↓
SystemSensorManager.registerListenerImpl(listener, sensor, ...)
                     ↓  (hook: beforeHookedMethod)
SensorListenerHook.beforeHookedMethod()
  ├─ reloadConfig()                        → Read latest config
  ├─ shouldFake(sensor.getType())          → Check if this sensor type is in spoof list
  └─ param.args[0] = new FakeSensorEventListener(listener, sensor)
       └─ Replace original listener with proxy
            └─ For step sensors: register to stepListeners, start global timer
```

In `FakeSensorEventListener.onSensorChanged()`, upon receiving a real sensor event:

1. Call `reloadConfig()` (throttled to 500ms)
2. If this sensor should be spoofed:
   - Step counter: record real step base (stepBase), don't forward (timer handles push)
   - Other sensors: replace `event.values` with return value from `getFakeValues()`
3. Forward modified event to original listener

### Hook Point: unregisterListenerImpl

When a target app unregisters a sensor listener:

```
SensorUnregisterHook.beforeHookedMethod()
  ├─ listenerMap.remove(original)          → Find corresponding FakeSensorEventListener
  ├─ stepListeners.remove(fake)             → Remove from step listener list
  └─ param.args[0] = fake                  → Replace with proxy so framework can find and cancel it correctly
```

This prevents memory leaks — the module won't hold references to destroyed Activity or Service listeners.

### Step Sensor Simulation

Step sensors (TYPE_STEP_COUNTER + TYPE_STEP_DETECTOR) have an independent push mechanism:

```
Global HandlerThread "FakeSensor-step" (runs every second)
        │
        ├─ Check enabled && simulate
        │
        ├─ Increment steps based on scenario:
        │   Stationary   → no increment
        │   Walking      → 1-2 steps
        │   Running      → 2-4 steps
        │   Cycling      → 30% chance of 1 step
        │
        └─ Iterate stepListeners, call pushStepValue() on each
             └─ Use latest SensorEvent object, set values[0] = stepBase + stepCount
             └─ Update timestamp = System.nanoTime()
             └─ Call original.onSensorChanged() to push
```

`stepBase` is recorded when the first real step event arrives, all subsequent increments are based on this base:

```
Value returned to App = stepBase (real base) + stepCount (module-incremented value)
```

### Dynamic Scenario Parameters

Each scenario defines different amplitude (amp) and frequency (freq) for generating sensor data waveforms:

| Scenario | Amplitude | Frequency | Accelerometer Characteristic |
|----------|-----------|-----------|------------------------------|
| Stationary | 0.05 | 0.5 | Minimal jitter, Z-axis near 9.8 |
| Walking | 1.0 | 2.0 | Moderate oscillation + sine wave, Z-axis fluctuates |
| Running | 4.0 | 5.0 | Large high-frequency oscillation |
| Cycling | 0.3 | 1.0 | Smooth low-amplitude swing |

Accelerometer data generation example (walking):

```
x = rand(-1.5, 1.5) + sin(t * 2) * 1.5
y = rand(-1, 1) + cos(t * 2) * 1
z = rand(9.5, 10.0) + |sin(t * 2)| * 2
```

Where `t = (time % period) / period * 2π`

## Project Structure

```
com.app.fakesensor/
├── XposedEntry.java           — Xposed entry, Zygote init + LoadPackage callbacks
├── SensorHook.java            — Core: hook logic, config loading, data spoofing, proxy Listener
├── MainActivity.java          — Main UI: sensor checkboxes, value inputs, mode selection
├── SimulationActivity.java    — Simulation UI: real-time value display, scenario switching
└── ConfigProvider.java        — ContentProvider: cross-process config reading
```

## Build

```
./gradlew assembleDebug
```

Depends on `libs/XposedBridgeAPI-82.jar`.

Min SDK: 28 (Android 9), Target SDK: 36 (Android 16).

## 📄 License

MIT License - see [LICENSE](./LICENSE) file