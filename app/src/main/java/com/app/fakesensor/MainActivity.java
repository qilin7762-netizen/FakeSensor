package com.app.fakesensor;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private int[] allCbIds = {
        R.id.cb_accel, R.id.cb_gyro, R.id.cb_mag, R.id.cb_gravity,
        R.id.cb_linear, R.id.cb_rotation, R.id.cb_light, R.id.cb_proximity,
        R.id.cb_pressure, R.id.cb_humidity, R.id.cb_temperature,
        R.id.cb_step_counter, R.id.cb_step_detector
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

        Button btnStart = findViewById(R.id.btn_start);
        btnStart.setOnClickListener(v -> {
            StringBuilder types = new StringBuilder();
            for (int id : allCbIds) {
                CheckBox cb = findViewById(id);
                if (cb.isChecked()) {
                    if (types.length() > 0) types.append(",");
                    types.append(cb.getTag());
                }
            }
            if (types.length() == 0) {
                android.widget.Toast.makeText(this,
                        getString(R.string.toast_select_sensor),
                        android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(this, SimulationActivity.class);
            intent.putExtra("types", types.toString());
            startActivity(intent);
        });
    }
}
