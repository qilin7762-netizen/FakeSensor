package com.app.fakesensor;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    // checkboxId → {tag, keys[], editTextIds[]}
    private static class SensorEntry {
        final int cbId;
        final String tag;
        final String[] keys;
        final int[] etIds;

        SensorEntry(int cbId, String tag, String[] keys, int[] etIds) {
            this.cbId = cbId; this.tag = tag; this.keys = keys; this.etIds = etIds;
        }
    }

    private final SensorEntry[] sensorEntries = {
        new SensorEntry(R.id.cb_accel, "1",
            new String[]{"accel_x","accel_y","accel_z"},
            new int[]{R.id.et_accel_x, R.id.et_accel_y, R.id.et_accel_z}),
        new SensorEntry(R.id.cb_gyro, "4",
            new String[]{"gyro_x","gyro_y","gyro_z"},
            new int[]{R.id.et_gyro_x, R.id.et_gyro_y, R.id.et_gyro_z}),
        new SensorEntry(R.id.cb_mag, "2",
            new String[]{"mag_x","mag_y","mag_z"},
            new int[]{R.id.et_mag_x, R.id.et_mag_y, R.id.et_mag_z}),
        new SensorEntry(R.id.cb_gravity, "9",
            new String[]{"gravity_x","gravity_y","gravity_z"},
            new int[]{R.id.et_gravity_x, R.id.et_gravity_y, R.id.et_gravity_z}),
        new SensorEntry(R.id.cb_linear, "10",
            new String[]{"linear_accel_x","linear_accel_y","linear_accel_z"},
            new int[]{R.id.et_linear_x, R.id.et_linear_y, R.id.et_linear_z}),
        new SensorEntry(R.id.cb_rotation, "11",
            new String[]{},
            new int[]{}),
        new SensorEntry(R.id.cb_light, "5",
            new String[]{"light"},
            new int[]{R.id.et_light}),
        new SensorEntry(R.id.cb_proximity, "8",
            new String[]{"proximity"},
            new int[]{R.id.et_proximity}),
        new SensorEntry(R.id.cb_pressure, "6",
            new String[]{"pressure"},
            new int[]{R.id.et_pressure}),
        new SensorEntry(R.id.cb_humidity, "12",
            new String[]{"humidity"},
            new int[]{R.id.et_humidity}),
        new SensorEntry(R.id.cb_temperature, "13",
            new String[]{"temperature"},
            new int[]{R.id.et_temperature}),
        new SensorEntry(R.id.cb_step_counter, "19",
            new String[]{}, new int[]{}),
        new SensorEntry(R.id.cb_step_detector, "18",
            new String[]{}, new int[]{}),
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.attachBaseContext(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnLang = findViewById(R.id.btn_lang);
        btnLang.setText(LocaleHelper.getDisplayLabel(this));
        btnLang.setOnClickListener(v -> {
            LocaleHelper.toggleLanguage(this);
            recreate();
        });

        Button btnStatic = findViewById(R.id.btn_static);
        btnStatic.setOnClickListener(v -> doStaticMode());

        Button btnDynamic = findViewById(R.id.btn_dynamic);
        btnDynamic.setOnClickListener(v -> {
            String types = collectCheckedTypes();
            if (types == null) return;
            Intent intent = new Intent(this, SimulationActivity.class);
            intent.putExtra("types", types);
            startActivity(intent);
        });
    }

    private String collectCheckedTypes() {
        StringBuilder types = new StringBuilder();
        for (SensorEntry e : sensorEntries) {
            CheckBox cb = findViewById(e.cbId);
            if (cb.isChecked()) {
                if (types.length() > 0) types.append(",");
                types.append(e.tag);
            }
        }
        if (types.length() == 0) {
            android.widget.Toast.makeText(this,
                    getString(R.string.toast_select_sensor),
                    android.widget.Toast.LENGTH_SHORT).show();
            return null;
        }
        return types.toString();
    }

    private void doStaticMode() {
        String types = collectCheckedTypes();
        if (types == null) return;

        // 读取每个已勾选传感器的输入值
        Map<String, Float> values = new LinkedHashMap<>();
        for (SensorEntry e : sensorEntries) {
            CheckBox cb = findViewById(e.cbId);
            if (!cb.isChecked()) continue;
            for (int i = 0; i < e.keys.length; i++) {
                EditText et = findViewById(e.etIds[i]);
                try {
                    values.put(e.keys[i], Float.parseFloat(et.getText().toString().trim()));
                } catch (NumberFormatException ignored) {
                    values.put(e.keys[i], 0f);
                }
            }
        }

        // 保存到 SharedPreferences
        SharedPreferences prefs = getSharedPreferences("fake_sensor_config", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit()
            .putBoolean("enabled", true)
            .putBoolean("simulate", false)
            .putString("scenario", "walking")
            .putString("fake_types", types);
        for (Map.Entry<String, Float> e : values.entrySet()) {
            editor.putFloat(e.getKey(), e.getValue());
        }
        editor.apply();

        StringBuilder sb = new StringBuilder();
        sb.append("enabled=true|simulate=false|scenario=walking|types=").append(types);
        for (Map.Entry<String, Float> e : values.entrySet()) {
            sb.append("|").append(e.getKey()).append("=").append(e.getValue());
        }
        String content = sb.toString();
        try {
            Process p = Runtime.getRuntime().exec(
                new String[]{"su", "-c", "cat > /data/local/tmp/fake_sensor_config.txt"});
            OutputStream os = p.getOutputStream();
            os.write(content.getBytes("UTF-8"));
            os.close();
            p.waitFor();
        } catch (Exception ignored) {}

        android.widget.Toast.makeText(this,
                getString(R.string.toast_static_saved),
                android.widget.Toast.LENGTH_SHORT).show();
    }
}
