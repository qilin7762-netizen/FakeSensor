package com.app.fakesensor;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.HandlerThread;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class SensorHook {

    private static final String MODULE_PACKAGE = "com.app.fakesensor";
    private static boolean enabled;
    private static boolean simulate;
    private static String scenario = "walking";
    private static String fakeTypes = "";
    private static final Map<String, Float> fakeValues = new HashMap<>();
    private static final java.util.Random random = new java.util.Random();

    // 步数模拟
    private static long stepCount;
    private static long stepBase = -1;
    private static HandlerThread stepTimer;
    private static volatile boolean stepTimerStarted;
    private static final java.util.List<FakeSensorEventListener> stepListeners
            = new java.util.ArrayList<>();

    private static synchronized void ensureStepTimer() {
        if (stepTimerStarted) return;
        stepTimerStarted = true;
        stepTimer = new HandlerThread("FakeSensor-step");
        stepTimer.start();
        new android.os.Handler(stepTimer.getLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                if (!enabled || !simulate) {
                    new android.os.Handler(stepTimer.getLooper()).postDelayed(this, 1000);
                    return;
                }
                switch (scenario != null ? scenario : "walking") {
                    case "stationary": break;
                    case "walking": stepCount += 1 + random.nextInt(2); break;
                    case "running": stepCount += 2 + random.nextInt(3); break;
                    case "cycling": if (random.nextFloat() < 0.3f) stepCount++; break;
                }
                // 推送给所有已注册的步数 listener
                synchronized (stepListeners) {
                    for (FakeSensorEventListener l : stepListeners) {
                        l.pushStepValue();
                    }
                }
                new android.os.Handler(stepTimer.getLooper()).postDelayed(this, 1000);
            }
        }, 1000);
    }

    // ===== 入口 =====

    public static void init() { reloadConfig(); }

    public static void hookSensorManagerInClassLoader(ClassLoader classLoader) {
        ClassLoader cl = classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
        try {
            Class<?> clz = XposedHelpers.findClass("android.hardware.SystemSensorManager", cl);
            int hooked = 0;
            for (java.lang.reflect.Method m : clz.getDeclaredMethods()) {
                if (!m.getName().equals("registerListenerImpl")) continue;
                Class<?>[] params = m.getParameterTypes();
                if (params.length < 3) continue;
                if (!SensorEventListener.class.isAssignableFrom(params[0])) continue;
                if (!Sensor.class.isAssignableFrom(params[1])) continue;
                XposedBridge.hookMethod(m, new SensorListenerHook());
                hooked++;
            }
            XposedBridge.log("[FakeSensor] Hooked " + hooked + " registerListenerImpl methods");
        } catch (Throwable t) {
            XposedBridge.log("[FakeSensor] Hook error: " + t.getMessage());
        }
    }

    // ===== Hook 回调 =====

    private static class SensorListenerHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            reloadConfig();
            if (!enabled) return;
            Sensor sensor = (Sensor) param.args[1];
            if (!shouldFake(sensor.getType())) return;
            XposedBridge.log("[FakeSensor] Wrapping listener type=" + sensor.getType()
                    + " name=" + sensor.getName());
            param.args[0] = new FakeSensorEventListener((SensorEventListener) param.args[0], sensor);
        }
    }

    // ===== 配置加载 =====

    private static long lastReload;

    private static void reloadConfig() {
        long now = System.currentTimeMillis();
        if (now - lastReload < 500) return;
        lastReload = now;

        if (tryContentProvider()) return;

        String[] configPaths = {
            "/storage/emulated/0/Android/data/" + MODULE_PACKAGE + "/files/fake_sensor_config.txt",
            "/sdcard/Android/data/" + MODULE_PACKAGE + "/files/fake_sensor_config.txt",
            "/sdcard/fake_sensor_config.txt",
            "/data/local/tmp/fake_sensor_config.txt",
        };
        for (String path : configPaths) {
            try {
                File f = new File(path);
                if (f.exists()) { parseKeyValueFile(readString(f)); return; }
            } catch (Exception ignored) {}
        }

        try {
            XSharedPreferences prefs = new XSharedPreferences(MODULE_PACKAGE, "fake_sensor_config");
            prefs.makeWorldReadable(); prefs.reload();
            if (!prefs.getAll().isEmpty()) {
                enabled = prefs.getBoolean("enabled", true);
                simulate = prefs.getBoolean("simulate", false);
                scenario = prefs.getString("scenario", scenario);
                fakeTypes = prefs.getString("fake_types", fakeTypes);
                loadFloatsFromX(prefs);
                writeToLocalTmp();
                return;
            }
        } catch (Exception ignored) {}

        if (enabled) XposedBridge.log("[FakeSensor] All sources failed, disabled");
        enabled = false; simulate = false; fakeTypes = ""; fakeValues.clear();
        stepBase = -1; stepCount = 0;
    }

    private static boolean tryContentProvider() {
        try {
            Class<?> atClass = XposedHelpers.findClass("android.app.ActivityThread", null);
            if (atClass == null) return false;
            Object at = XposedHelpers.callStaticMethod(atClass, "currentActivityThread");
            if (at == null) return false;
            Object app = XposedHelpers.callMethod(at, "getApplication");
            if (app == null) return false;
            android.content.ContentResolver cr = (android.content.ContentResolver) XposedHelpers.callMethod(app, "getContentResolver");
            if (cr == null) return false;
            Uri uri = Uri.parse("content://" + MODULE_PACKAGE + ".config/all");
            android.database.Cursor c = cr.query(uri, null, null, null, null);
            if (c == null) return false;
            boolean en = enabled;
            boolean sim = simulate;
            String sc = scenario;
            String types = fakeTypes;
            while (c.moveToNext()) {
                String k = c.getString(0), v = c.getString(1);
                if (v == null) continue;
                if ("enabled".equals(k)) en = "true".equals(v);
                else if ("simulate".equals(k)) sim = "true".equals(v);
                else if ("scenario".equals(k)) sc = v;
                else if ("fake_types".equals(k)) types = v;
                else { try { fakeValues.put(k, Float.parseFloat(v)); } catch (NumberFormatException ignored) {} }
            }
            c.close();
            enabled = en; simulate = sim; scenario = sc; fakeTypes = types;
            return true;
        } catch (Exception e) { return false; }
    }

    private static void writeToLocalTmp() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("enabled=").append(enabled).append("|simulate=").append(simulate)
              .append("|scenario=").append(scenario).append("|types=").append(fakeTypes);
            for (Map.Entry<String, Float> e : fakeValues.entrySet())
                sb.append("|").append(e.getKey()).append("=").append(e.getValue());
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat > /data/local/tmp/fake_sensor_config.txt"});
            p.getOutputStream().write(sb.toString().getBytes("UTF-8"));
            p.getOutputStream().close(); p.waitFor();
        } catch (Exception ignored) {}
    }

    private static void loadFloatsFromX(XSharedPreferences prefs) {
        String[] keys = {"accel_x","accel_y","accel_z","gyro_x","gyro_y","gyro_z",
            "mag_x","mag_y","mag_z","gravity_x","gravity_y","gravity_z",
            "linear_accel_x","linear_accel_y","linear_accel_z",
            "light","proximity","pressure","humidity","temperature"};
        for (String k : keys) { float v = prefs.getFloat(k, Float.NaN); if (!Float.isNaN(v)) fakeValues.put(k, v); }
    }

    private static void parseKeyValueFile(String content) {
        for (String part : content.split("\\|")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;
            String key = kv[0].trim(), val = kv[1].trim();
            if ("enabled".equals(key)) enabled = "true".equalsIgnoreCase(val);
            else if ("simulate".equals(key)) simulate = "true".equalsIgnoreCase(val);
            else if ("scenario".equals(key)) scenario = val;
            else if ("types".equals(key)) fakeTypes = val;
            else { try { fakeValues.put(key, Float.parseFloat(val)); } catch (NumberFormatException ignored) {} }
        }
    }

    // ===== 辅助 =====

    private static boolean shouldFake(int type) {
        if (fakeTypes == null || fakeTypes.isEmpty()) return false;
        for (String t : fakeTypes.split(","))
            try { if (Integer.parseInt(t.trim()) == type) return true; } catch (NumberFormatException ignored) {}
        return false;
    }

    public static float[] getFakeValues(int type) {
        if (simulate) return randomValues(type);
        switch (type) {
            case Sensor.TYPE_ACCELEROMETER:       return v3("accel_x",0,"accel_y",0,"accel_z",9.8f);
            case Sensor.TYPE_GYROSCOPE:           return v3("gyro_x",0,"gyro_y",0,"gyro_z",0);
            case Sensor.TYPE_MAGNETIC_FIELD:      return v3("mag_x",25,"mag_y",-25,"mag_z",-45);
            case Sensor.TYPE_GRAVITY:             return v3("gravity_x",0,"gravity_y",0,"gravity_z",9.8f);
            case Sensor.TYPE_LINEAR_ACCELERATION: return v3("linear_accel_x",0,"linear_accel_y",0,"linear_accel_z",0);
            case Sensor.TYPE_PROXIMITY:           return new float[] {g("proximity",0)};
            case Sensor.TYPE_LIGHT:               return new float[] {g("light",300)};
            case Sensor.TYPE_PRESSURE:            return new float[] {g("pressure",1013.25f)};
            case Sensor.TYPE_RELATIVE_HUMIDITY:    return new float[] {g("humidity",50)};
            case Sensor.TYPE_AMBIENT_TEMPERATURE:   return new float[] {g("temperature",25)};
            case Sensor.TYPE_ROTATION_VECTOR:     return new float[] {0,0,0,1};
            case Sensor.TYPE_STEP_COUNTER:
            case Sensor.TYPE_STEP_DETECTOR:       return new float[] {0};
            default: return null;
        }
    }

    private static float[] randomValues(int type) {
        float r = random.nextFloat();
        float amp, freq;
        switch (scenario != null ? scenario : "walking") {
            case "stationary": amp = 0.05f; freq = 0.5f; break;
            case "walking":    amp = 1f;    freq = 2f;   break;
            case "running":    amp = 4f;    freq = 5f;   break;
            case "cycling":    amp = 0.3f;  freq = 1f;   break;
            default:           amp = 1f;    freq = 2f;   break;
        }
        float t = (float) (System.currentTimeMillis() % (1000.0 / freq) / (1000.0 / freq) * Math.PI * 2);

        switch (type) {
            case Sensor.TYPE_ACCELEROMETER:
                return new float[] {rand(-amp,amp)+(float)Math.sin(t)*amp, rand(-amp,amp)+(float)Math.cos(t)*amp, rand(9.7f,9.9f)+(float)Math.abs(Math.sin(t))*amp*2};
            case Sensor.TYPE_GYROSCOPE:
                return new float[] {rand(-amp*0.3f,amp*0.3f), rand(-amp*0.3f,amp*0.3f), rand(-amp*0.2f,amp*0.2f)};
            case Sensor.TYPE_MAGNETIC_FIELD:
                return new float[] {rand(-50,50), rand(-50,50), rand(-60,60)};
            case Sensor.TYPE_PROXIMITY:
                return new float[] {"stationary".equals(scenario) ? 0f : (r < 0.5f ? 0f : 5f)};
            case Sensor.TYPE_LIGHT:
                switch (scenario) {
                    case "stationary": return new float[] {rand(200,400)};
                    case "walking":    return new float[] {rand(500,2000)};
                    case "running":    return new float[] {rand(1000,5000)};
                    case "cycling":    return new float[] {rand(3000,10000)};
                    default:           return new float[] {rand(0,1000)};
                }
            case Sensor.TYPE_PRESSURE: return new float[] {rand(1000,1030)};
            case Sensor.TYPE_RELATIVE_HUMIDITY: return new float[] {rand(30,80)};
            case Sensor.TYPE_AMBIENT_TEMPERATURE: return new float[] {rand(20,35)};
            case Sensor.TYPE_GRAVITY:
                return new float[] {rand(-amp*0.1f,amp*0.1f), rand(-amp*0.1f,amp*0.1f), rand(9.7f,9.9f)};
            case Sensor.TYPE_LINEAR_ACCELERATION:
                return new float[] {rand(-amp,amp), rand(-amp,amp), rand(-amp*2,amp*2)};
            case Sensor.TYPE_ROTATION_VECTOR:
                return new float[] {rand(-1,1), rand(-1,1), rand(-1,1), 1};
            case Sensor.TYPE_STEP_DETECTOR: {
                float prob = 0f;
                switch (scenario) {
                    case "stationary": prob = 0; break;
                    case "walking":    prob = 0.35f; break;
                    case "running":    prob = 0.6f; break;
                    case "cycling":    prob = 0.05f; break;
                }
                return new float[] {r < prob ? 1f : 0f};
            }
            case Sensor.TYPE_STEP_COUNTER:
                return new float[] {stepBase >= 0 ? stepBase + stepCount : stepCount};
            default: return null;
        }
    }

    private static float[] v3(String kx, float dx, String ky, float dy, String kz, float dz) {
        return new float[] {g(kx, dx), g(ky, dy), g(kz, dz)};
    }

    private static float g(String key, float def) { Float v = fakeValues.get(key); return v != null ? v : def; }
    private static float rand(float min, float max) { return min + random.nextFloat() * (max - min); }

    private static String readString(File f) throws Exception {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        String line; while ((line = br.readLine()) != null) sb.append(line);
        br.close(); return sb.toString();
    }

    // ===== 代理 Listener =====

    private static class FakeSensorEventListener implements SensorEventListener {
        private final SensorEventListener original;
        private final Sensor sensor;
        private SensorEvent lastEvent;

        FakeSensorEventListener(SensorEventListener original, Sensor sensor) {
            this.original = original;
            this.sensor = sensor;
            if (sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
                synchronized (stepListeners) { stepListeners.add(this); }
                ensureStepTimer();
            }
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            lastEvent = event;
            reloadConfig();
            if (enabled) {
                int type = sensor.getType();
                // 计步器由全局定时器推送，这里只记录真实事件作为参考，不转发
                if (type == Sensor.TYPE_STEP_COUNTER && simulate) {
                    if (stepBase < 0) stepBase = (long) event.values[0];
                    return;
                }
                float[] fakes = getFakeValues(type);
                if (fakes != null) {
                    System.arraycopy(fakes, 0, event.values, 0,
                            Math.min(event.values.length, fakes.length));
                }
            }
            original.onSensorChanged(event);
        }

        void pushStepValue() {
            if (lastEvent == null) return;
            if (stepBase < 0) stepBase = (long) lastEvent.values[0];
            lastEvent.values[0] = stepBase + stepCount;
            lastEvent.timestamp = System.nanoTime();
            try { original.onSensorChanged(lastEvent); } catch (Exception ignored) {}
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            original.onAccuracyChanged(sensor, accuracy);
        }
    }
}
