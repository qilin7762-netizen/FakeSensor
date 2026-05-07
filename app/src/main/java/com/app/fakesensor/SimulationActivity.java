package com.app.fakesensor;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class SimulationActivity extends AppCompatActivity {

    private String types;
    private String scenario = "walking";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private long stepCount;
    private boolean lastStepDetected;

    private boolean isStatic;
    private final Map<String, Float> staticValues = new LinkedHashMap<>();
    private final Map<String, SeekBar> seekBars = new LinkedHashMap<>();
    private final Map<String, float[]> sensorRanges = new LinkedHashMap<>();

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateDisplay();
            handler.postDelayed(this, 200);
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.attachBaseContext(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simulation);

        types = getIntent().getStringExtra("types");
        if (types == null || types.isEmpty()) { finish(); return; }

        isStatic = getIntent().getBooleanExtra("is_static", false);

        // 读取静态模式的初始值
        if (isStatic) {
            for (String t : types.split(",")) {
                int type = Integer.parseInt(t.trim());
                for (String key : getKeysForType(type)) {
                    if (getIntent().hasExtra(key)) {
                        staticValues.put(key, getIntent().getFloatExtra(key, 0f));
                    }
                }
            }
        }

        setTitle(getString(isStatic ? R.string.title_static_simulating : R.string.title_simulating));
        ((TextView) findViewById(R.id.tv_types)).setText(
                getString(R.string.label_selected_sensors, types));

        if (isStatic) {
            setupStaticUI();
        } else {
            setupDynamicUI();
        }

        resetStepCount();

        findViewById(R.id.btn_stop).setOnClickListener(v -> {
            handler.removeCallbacks(tick);
            deleteConfig();
            finish();
        });
    }

    private void setupDynamicUI() {
        findViewById(R.id.scenario_buttons).setVisibility(View.VISIBLE);
        findViewById(R.id.tv_scenario).setVisibility(View.VISIBLE);
        findViewById(R.id.separator_dynamic).setVisibility(View.VISIBLE);

        setupScenarios();
        setupDisplay();
    }

    private void setupStaticUI() {
        findViewById(R.id.scenario_buttons).setVisibility(View.GONE);
        findViewById(R.id.tv_scenario).setVisibility(View.GONE);
        findViewById(R.id.separator_dynamic).setVisibility(View.GONE);

        LinearLayout container = findViewById(R.id.values_container);

        for (String t : types.split(",")) {
            int type = Integer.parseInt(t.trim());
            String[] keys = getKeysForType(type);

            TextView title = new TextView(this);
            title.setText(sensorName(type));
            title.setTextSize(14);
            title.setPadding(0, 16, 0, 4);
            title.setTextColor(0xFF2196F3);
            container.addView(title);

            for (String key : keys) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, 4, 0, 4);

                TextView label = new TextView(this);
                label.setText(getAxisLabel(key));
                label.setTextSize(13);
                label.setMinWidth(60);

                SeekBar seekBar = new SeekBar(this);
                seekBar.setMax(1000);
                float initVal = staticValues.containsKey(key) ? staticValues.get(key) : getDefaultValue(key);
                float range = getRangeForKey(key);
                seekBar.setProgress((int) ((initVal + range) / (2 * range) * 1000));
                seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        float value = (progress / 1000f) * 2 * range - range;
                        staticValues.put(key, value);
                        updateSliderValueLabel(row, value);
                        saveStaticConfig();
                    }
                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
                });
                seekBars.put(key, seekBar);

                TextView valueLabel = new TextView(this);
                valueLabel.setText(String.format(Locale.US, "%.2f", initVal));
                valueLabel.setTextSize(13);
                valueLabel.setMinWidth(80);

                row.addView(label);
                row.addView(seekBar, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                row.addView(valueLabel);
                container.addView(row);
            }
        }
    }

    private void updateSliderValueLabel(LinearLayout row, float value) {
        TextView valueLabel = (TextView) row.getChildAt(2);
        if (valueLabel != null) {
            valueLabel.setText(String.format(Locale.US, "%.2f", value));
        }
    }

    private void saveStaticConfig() {
        StringBuilder sb = new StringBuilder();
        sb.append("enabled=true|simulate=false|scenario=walking|types=").append(types);
        for (Map.Entry<String, Float> e : staticValues.entrySet()) {
            sb.append("|").append(e.getKey()).append("=").append(e.getValue());
        }
        String content = sb.toString();
        try {
            SharedPreferences prefs = getSharedPreferences("fake_sensor_config", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit()
                .putBoolean("enabled", true)
                .putBoolean("simulate", false)
                .putString("fake_types", types);
            for (Map.Entry<String, Float> e : staticValues.entrySet()) {
                editor.putFloat(e.getKey(), e.getValue());
            }
            editor.apply();

            Process p = Runtime.getRuntime().exec(
                new String[]{"su", "-c", "cat > /data/local/tmp/fake_sensor_config.txt"});
            OutputStream os = p.getOutputStream();
            os.write(content.getBytes("UTF-8"));
            os.close();
            p.waitFor();
        } catch (Exception ignored) {}
    }

    private String[] getKeysForType(int type) {
        switch (type) {
            case 1: return new String[]{"accel_x", "accel_y", "accel_z"};
            case 4: return new String[]{"gyro_x", "gyro_y", "gyro_z"};
            case 2: return new String[]{"mag_x", "mag_y", "mag_z"};
            case 9: return new String[]{"gravity_x", "gravity_y", "gravity_z"};
            case 10: return new String[]{"linear_accel_x", "linear_accel_y", "linear_accel_z"};
            case 5: return new String[]{"light"};
            case 8: return new String[]{"proximity"};
            case 6: return new String[]{"pressure"};
            case 12: return new String[]{"humidity"};
            case 13: return new String[]{"temperature"};
            default: return new String[]{};
        }
    }

    private String getAxisLabel(String key) {
        if (key.endsWith("_x")) return "X";
        if (key.endsWith("_y")) return "Y";
        if (key.endsWith("_z")) return "Z";
        return "";
    }

    private float getRangeForKey(String key) {
        switch (key) {
            case "accel_x": case "accel_y": case "accel_z":
            case "gravity_x": case "gravity_y": case "gravity_z":
            case "linear_accel_x": case "linear_accel_y": case "linear_accel_z":
                return 20f;
            case "gyro_x": case "gyro_y": case "gyro_z":
                return 10f;
            case "mag_x": case "mag_y": case "mag_z":
                return 100f;
            case "light": return 10000f;
            case "proximity": return 10f;
            case "pressure": return 200f;
            case "humidity": return 100f;
            case "temperature": return 50f;
            default: return 100f;
        }
    }

    private float getDefaultValue(String key) {
        switch (key) {
            case "accel_z": return 9.8f;
            case "mag_x": return 25f;
            case "mag_y": return -25f;
            case "mag_z": return -45f;
            case "light": return 300f;
            case "proximity": return 0f;
            case "pressure": return 1013.25f;
            case "humidity": return 50f;
            case "temperature": return 25f;
            default: return 0f;
        }
    }

    @Override protected void onResume() { super.onResume(); handler.post(tick); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(tick); }

    private void setupScenarios() {
        int[] btnIds = {R.id.btn_stationary, R.id.btn_walking, R.id.btn_running, R.id.btn_cycling};
        String[] keys = {"stationary", "walking", "running", "cycling"};
        int[] nameIds = {R.string.scenario_stationary, R.string.scenario_walking,
                R.string.scenario_running, R.string.scenario_cycling};
        for (int i = 0; i < btnIds.length; i++) {
            final String s = keys[i];
            final int bid = btnIds[i];
            Button btn = findViewById(bid);
            btn.setOnClickListener(v -> {
                scenario = s;
                resetStepCount();
                saveConfig();
                ((TextView) findViewById(R.id.tv_scenario)).setText(
                        getString(R.string.current_scenario, getScenarioName(s)));
                highlightButton(bid);
            });
        }
        scenario = "walking";
        ((TextView) findViewById(R.id.tv_scenario)).setText(
                getString(R.string.current_scenario, getScenarioName(scenario)));
        saveConfig();
    }

    private void highlightButton(int activeId) {
        for (int id : new int[]{R.id.btn_stationary, R.id.btn_walking, R.id.btn_running, R.id.btn_cycling}) {
            Button b = findViewById(id);
            b.setSelected(id == activeId);
        }
    }

    private void resetStepCount() { stepCount = 0; lastStepDetected = false; }

    private String getScenarioName(String s) {
        switch (s) {
            case "stationary": return getString(R.string.scenario_stationary);
            case "walking": return getString(R.string.scenario_walking);
            case "running": return getString(R.string.scenario_running);
            case "cycling": return getString(R.string.scenario_cycling);
            default: return s;
        }
    }

    private void setupDisplay() {
        LinearLayout container = findViewById(R.id.values_container);
        for (String t : types.split(",")) {
            int type = Integer.parseInt(t.trim());
            TextView title = new TextView(this);
            title.setText(sensorName(type));
            title.setTextSize(14);
            title.setPadding(0, 12, 0, 4);

            TextView val = new TextView(this);
            val.setId(View.generateViewId());
            val.setTextSize(20);
            val.setTypeface(null, android.graphics.Typeface.BOLD);
            val.setPadding(16, 4, 16, 8);

            container.addView(title);
            container.addView(val);
        }
    }

    private void updateDisplay() {
        if (isStatic) {
            updateStaticDisplay();
        } else {
            updateDynamicDisplay();
        }
    }

    private void updateStaticDisplay() {
        LinearLayout container = findViewById(R.id.values_container);
        int idx = 0;
        for (String t : types.split(",")) {
            int type = Integer.parseInt(t.trim());
            float[] vals = getStaticValues(type);
            int valIdx = idx * 2 + 1;
            if (valIdx < container.getChildCount()) {
                TextView tv = (TextView) container.getChildAt(valIdx);
                if (tv != null) tv.setText(formatValues(vals));
            }
            idx++;
        }
    }

    private float[] getStaticValues(int type) {
        switch (type) {
            case 1: return v3(g("accel_x"), g("accel_y"), g("accel_z"));
            case 4: return v3(g("gyro_x"), g("gyro_y"), g("gyro_z"));
            case 2: return v3(g("mag_x"), g("mag_y"), g("mag_z"));
            case 9: return v3(g("gravity_x"), g("gravity_y"), g("gravity_z"));
            case 10: return v3(g("linear_accel_x"), g("linear_accel_y"), g("linear_accel_z"));
            case 5: return new float[]{g("light")};
            case 8: return new float[]{g("proximity")};
            case 6: return new float[]{g("pressure")};
            case 12: return new float[]{g("humidity")};
            case 13: return new float[]{g("temperature")};
            default: return new float[]{0};
        }
    }

    private float g(String key) {
        return staticValues.containsKey(key) ? staticValues.get(key) : getDefaultValue(key);
    }

    private void updateDynamicDisplay() {
        LinearLayout container = findViewById(R.id.values_container);
        int idx = 0;
        for (String t : types.split(",")) {
            int type = Integer.parseInt(t.trim());
            float[] vals = generateValues(type);
            int valIdx = idx * 2 + 1;
            if (valIdx < container.getChildCount()) {
                TextView tv = (TextView) container.getChildAt(valIdx);
                tv.setText(formatValues(vals));
            }
            idx++;
        }
    }

    private float[] generateValues(int type) {
        float r = random.nextFloat();
        switch (scenario) {
            case "stationary":
                switch (type) {
                    case 1: return v3(rand(-0.1f,0.1f), rand(-0.1f,0.1f), rand(9.7f,9.9f));
                    case 4: return v3(rand(-0.05f,0.05f), rand(-0.05f,0.05f), rand(-0.05f,0.05f));
                    case 2: return v3(rand(-3,3), rand(-3,3), rand(-5,5));
                    case 9: return v3(rand(-0.1f,0.1f), rand(-0.1f,0.1f), rand(9.7f,9.9f));
                    case 10: return v3(0,0,0);
                    case 18: return new float[] {0};
                    case 19: return new float[] {stepCount};
                    case 5: return new float[] {rand(200,400)};
                    default: return defaultValues(type);
                }
            case "walking":
                switch (type) {
                    case 1: {
                        float t = (float) (System.currentTimeMillis() % 1000 / 1000.0 * Math.PI * 2);
                        return v3(rand(-1.5f,1.5f)+(float)Math.sin(t*2)*1.5f,
                                   rand(-1f,1f)+(float)Math.cos(t*2)*1f,
                                   rand(9.5f,10f)+(float)Math.abs(Math.sin(t*2))*2f);
                    }
                    case 4: return v3(rand(-0.3f,0.3f), rand(-0.3f,0.3f), rand(-0.3f,0.3f));
                    case 9: return v3(rand(-0.5f,0.5f), rand(-0.5f,0.5f), rand(9.5f,10f));
                    case 10: return v3(rand(-1,1), rand(-1,1), rand(-2,2));
                    case 18:
                        lastStepDetected = r < 0.35f;
                        if (lastStepDetected) stepCount++;
                        return new float[] {lastStepDetected ? 1f : 0f};
                    case 19: return new float[] {stepCount};
                    case 5: return new float[] {rand(500,2000)};
                    default: return defaultValues(type);
                }
            case "running":
                switch (type) {
                    case 1: {
                        float t = (float) (System.currentTimeMillis() % 500 / 500.0 * Math.PI * 2);
                        return v3(rand(-3f,3f)+(float)Math.sin(t*3)*4f,
                                   rand(-2f,2f)+(float)Math.cos(t*3)*2f,
                                   rand(9f,11f)+(float)Math.abs(Math.sin(t*3))*4f);
                    }
                    case 4: return v3(rand(-1f,1f), rand(-1f,1f), rand(-0.5f,0.5f));
                    case 9: return v3(rand(-1,1), rand(-1,1), rand(9,11));
                    case 10: return v3(rand(-3,3), rand(-3,3), rand(-5,5));
                    case 18:
                        lastStepDetected = r < 0.6f;
                        if (lastStepDetected) stepCount++;
                        return new float[] {lastStepDetected ? 1f : 0f};
                    case 19: return new float[] {stepCount};
                    case 5: return new float[] {rand(1000,5000)};
                    default: return defaultValues(type);
                }
            case "cycling":
                switch (type) {
                    case 1: {
                        float t = (float) (System.currentTimeMillis() % 2000 / 2000.0 * Math.PI * 2);
                        return v3(rand(-0.5f,0.5f)+(float)Math.sin(t)*0.3f,
                                   rand(-0.5f,0.5f)+(float)Math.cos(t)*0.5f,
                                   rand(9.6f,10f));
                    }
                    case 4: {
                        float t = (float) (System.currentTimeMillis() % 3000 / 3000.0 * Math.PI * 2);
                        return v3(rand(-0.2f,0.2f), (float)Math.sin(t)*0.5f, rand(-0.2f,0.2f));
                    }
                    case 9: return v3(rand(-0.2f,0.2f), rand(-1,1), rand(9.6f,10f));
                    case 10: return v3(rand(-0.5f,0.5f), rand(-0.5f,0.5f), rand(-1,1));
                    case 18:
                        lastStepDetected = r < 0.05f;
                        if (lastStepDetected) stepCount++;
                        return new float[] {lastStepDetected ? 1f : 0f};
                    case 19: return new float[] {stepCount};
                    case 5: return new float[] {rand(3000,10000)};
                    default: return defaultValues(type);
                }
            default: return defaultValues(type);
        }
    }

    private float[] defaultValues(int type) {
        float r = random.nextFloat();
        switch (type) {
            case 2: return v3(rand(-50,50), rand(-50,50), rand(-60,60));
            case 6: return new float[] {rand(1000,1030)};
            case 8: return new float[] {r < 0.5f ? 0f : 5f};
            case 11: return new float[] {rand(-1,1), rand(-1,1), rand(-1,1), 1};
            case 12: return new float[] {rand(30,80)};
            case 13: return new float[] {rand(20,35)};
            default: return new float[] {0};
        }
    }

    private float[] v3(float x, float y, float z) { return new float[] {x, y, z}; }
    private float rand(float min, float max) { return min + random.nextFloat() * (max - min); }

    private String formatValues(float[] vals) {
        if (vals == null) return "N/A";
        StringBuilder sb = new StringBuilder();
        String[] axes = {"X", "Y", "Z", "W"};
        for (int i = 0; i < vals.length; i++) {
            if (i > 0) sb.append("  ");
            if (vals.length <= 3) sb.append(axes[i]).append("=");
            sb.append(String.format(Locale.US, "%.4f", vals[i]));
        }
        return sb.toString();
    }

    private String sensorName(int type) {
        switch (type) {
            case 1: return getString(R.string.sensor_short_accelerometer);
            case 2: return getString(R.string.sensor_short_magnetic);
            case 4: return getString(R.string.sensor_short_gyroscope);
            case 5: return getString(R.string.sensor_short_light);
            case 6: return getString(R.string.sensor_short_pressure);
            case 8: return getString(R.string.sensor_short_proximity);
            case 9: return getString(R.string.sensor_short_gravity);
            case 10: return getString(R.string.sensor_short_linear_acceleration);
            case 11: return getString(R.string.sensor_short_rotation);
            case 12: return getString(R.string.sensor_short_humidity);
            case 13: return getString(R.string.sensor_short_temperature);
            case 18: return getString(R.string.sensor_short_step_detector);
            case 19: return getString(R.string.sensor_short_step_counter);
            default: return getString(R.string.sensor_unknown, type);
        }
    }

    // ===== 配置保存 =====

    private void saveConfig() {
        SharedPreferences prefs = getSharedPreferences("fake_sensor_config", MODE_PRIVATE);
        prefs.edit()
            .putBoolean("enabled", true)
            .putBoolean("simulate", true)
            .putString("scenario", scenario)
            .putString("fake_types", types)
            .apply();

        String content = "enabled=true|simulate=true|scenario=" + scenario + "|types=" + types;
        writeConfigViaSu(content);
    }

    private void deleteConfig() {
        getSharedPreferences("fake_sensor_config", MODE_PRIVATE).edit()
            .putBoolean("enabled", false).putBoolean("simulate", false).apply();

        writeConfigViaSu("enabled=false|simulate=false|scenario=walking|types=");
    }

    private void writeConfigViaSu(String content) {
        try {
            Process p = Runtime.getRuntime().exec(
                new String[]{"su", "-c", "cat > /data/local/tmp/fake_sensor_config.txt"});
            OutputStream os = p.getOutputStream();
            os.write(content.getBytes("UTF-8"));
            os.close();
            p.waitFor();
        } catch (Exception ignored) {}
    }
}