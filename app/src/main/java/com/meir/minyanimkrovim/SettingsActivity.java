package com.meir.minyanimkrovim;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TimePicker;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private CheckBox cbShacharit, cbMincha, cbArvit;
    private Button btnTimeShacharit, btnTimeMincha, btnTimeArvit, btnBattery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        cbShacharit = (CheckBox) findViewById(R.id.cbShacharit);
        cbMincha = (CheckBox) findViewById(R.id.cbMincha);
        cbArvit = (CheckBox) findViewById(R.id.cbArvit);
        btnTimeShacharit = (Button) findViewById(R.id.btnTimeShacharit);
        btnTimeMincha = (Button) findViewById(R.id.btnTimeMincha);
        btnTimeArvit = (Button) findViewById(R.id.btnTimeArvit);
        btnBattery = (Button) findViewById(R.id.btnBattery);

        setupPrayerRow(cbShacharit, btnTimeShacharit, AlarmScheduler.TYPE_SHACHARIT);
        setupPrayerRow(cbMincha, btnTimeMincha, AlarmScheduler.TYPE_MINCHA);
        setupPrayerRow(cbArvit, btnTimeArvit, AlarmScheduler.TYPE_ARVIT);

        btnBattery.setOnClickListener(v -> requestIgnoreBatteryOptimizations());
    }

    private void setupPrayerRow(final CheckBox checkBox, final Button timeButton, final String type) {
        boolean enabled = AlarmScheduler.isEnabled(this, type);
        int minutes = AlarmScheduler.getMinutesOfDay(this, type);
        checkBox.setChecked(enabled);
        timeButton.setText(formatMinutes(minutes) + " - " + getString(R.string.settings_set_time));

        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                int currentMinutes = AlarmScheduler.getMinutesOfDay(SettingsActivity.this, type);
                AlarmScheduler.setAlarm(SettingsActivity.this, type, isChecked, currentMinutes);
            }
        });

        timeButton.setOnClickListener(v -> {
            int currentMinutes = AlarmScheduler.getMinutesOfDay(SettingsActivity.this, type);
            int hour = currentMinutes / 60;
            int minute = currentMinutes % 60;
            new TimePickerDialog(SettingsActivity.this, new TimePickerDialog.OnTimeSetListener() {
                @Override
                public void onTimeSet(TimePicker view, int hourOfDay, int minuteOfHour) {
                    int newMinutes = hourOfDay * 60 + minuteOfHour;
                    timeButton.setText(formatMinutes(newMinutes) + " - " + getString(R.string.settings_set_time));
                    boolean isEnabled = checkBox.isChecked();
                    AlarmScheduler.setAlarm(SettingsActivity.this, type, isEnabled, newMinutes);
                }
            }, hour, minute, true).show();
        });
    }

    private String formatMinutes(int minutesOfDay) {
        return String.format(java.util.Locale.US, "%02d:%02d",
                minutesOfDay / 60, minutesOfDay % 60);
    }

    private void requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= 23 /* M - המנגנון קיים רק מכאן */) {
            String pkg = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(pkg)) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + pkg));
                startActivity(intent);
                return;
            }
        }
        // מכשיר ישן יותר - אין בכלל את מנגנון ניהול הסוללה הזה, נחשב כפטור ממילא
        Toast.makeText(this, R.string.settings_ignore_battery, Toast.LENGTH_SHORT).show();
    }
}
