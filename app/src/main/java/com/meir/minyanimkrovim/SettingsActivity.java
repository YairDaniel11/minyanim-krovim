package com.meir.minyanimkrovim;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TimePicker;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private static final int REQUEST_CODE_OPEN_FILE = 1001;
    private static final int REQUEST_CODE_BROWSE_FILE = 1003;
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 1004;

    private CheckBox cbShacharit, cbMincha, cbArvit;
    private Button btnTimeShacharit, btnTimeMincha, btnTimeArvit, btnBattery, btnLoadFile;
    private RadioGroup rgTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        btnLoadFile = (Button) findViewById(R.id.btnLoadFile);
        cbShacharit = (CheckBox) findViewById(R.id.cbShacharit);
        cbMincha = (CheckBox) findViewById(R.id.cbMincha);
        cbArvit = (CheckBox) findViewById(R.id.cbArvit);
        btnTimeShacharit = (Button) findViewById(R.id.btnTimeShacharit);
        btnTimeMincha = (Button) findViewById(R.id.btnTimeMincha);
        btnTimeArvit = (Button) findViewById(R.id.btnTimeArvit);
        btnBattery = (Button) findViewById(R.id.btnBattery);
        rgTheme = (RadioGroup) findViewById(R.id.rgTheme);

        btnLoadFile.setOnClickListener(v -> openFilePicker());

        setupPrayerRow(cbShacharit, btnTimeShacharit, AlarmScheduler.TYPE_SHACHARIT);
        setupPrayerRow(cbMincha, btnTimeMincha, AlarmScheduler.TYPE_MINCHA);
        setupPrayerRow(cbArvit, btnTimeArvit, AlarmScheduler.TYPE_ARVIT);

        btnBattery.setOnClickListener(v -> requestIgnoreBatteryOptimizations());

        setupThemeSelector();
    }

    private void setupThemeSelector() {
        String mode = ThemeUtil.getMode(this);
        int checkedId = ThemeUtil.MODE_LIGHT.equals(mode) ? R.id.rbThemeLight
                : ThemeUtil.MODE_DARK.equals(mode) ? R.id.rbThemeDark
                : R.id.rbThemeSystem;
        ((RadioButton) findViewById(checkedId)).setChecked(true);

        rgTheme.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                String newMode = checkedId == R.id.rbThemeLight ? ThemeUtil.MODE_LIGHT
                        : checkedId == R.id.rbThemeDark ? ThemeUtil.MODE_DARK
                        : ThemeUtil.MODE_SYSTEM;
                ThemeUtil.setMode(SettingsActivity.this, newMode);
                recreate();
            }
        });
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

    private void openFilePicker() {
        if (Build.VERSION.SDK_INT < 29 /* לפני scoped storage */) {
            // במכשירים ישנים/מותאמים (כגון MIUI ישן) חלק ממנהלי הקבצים לא
            // מממשים Storage Access Framework בכלל, ואז ACTION_OPEN_DOCUMENT/
            // ACTION_GET_CONTENT מציגים רק אפליקציות ענן (Drive/Gmail) או
            // קטגוריות מובנות מוגבלות (הורדות/תמונות/קול/וידאו) בלי אפשרות
            // לעיין באחסון המקומי ולמצוא קובץ JSON שם. לכן, במכשירים כאלה
            // (שעדיין לא כפופים ל-scoped storage) פותחים דפדפן קבצים פנימי
            // שקורא ישירות מהאחסון החיצוני.
            openInternalFileBrowser();
        } else {
            openSystemFilePicker();
        }
    }

    private void openSystemFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*"); // מאפשרים גם text/plain - חלק מהמכשירים לא מזהים application/json
        try {
            startActivityForResult(
                    Intent.createChooser(intent, getString(R.string.btn_load_file)),
                    REQUEST_CODE_OPEN_FILE);
        } catch (Exception e) {
            Toast.makeText(this, R.string.label_no_data, Toast.LENGTH_LONG).show();
        }
    }

    private void openInternalFileBrowser() {
        if (Build.VERSION.SDK_INT >= 23 /* M - הרשאות בזמן ריצה */
                && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_CODE_STORAGE_PERMISSION);
            return;
        }
        startActivityForResult(new Intent(this, FileBrowserActivity.class), REQUEST_CODE_BROWSE_FILE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startActivityForResult(new Intent(this, FileBrowserActivity.class), REQUEST_CODE_BROWSE_FILE);
            } else {
                Toast.makeText(this, R.string.file_browser_permission_denied, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OPEN_FILE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            importFile(uri);
        } else if (requestCode == REQUEST_CODE_BROWSE_FILE && resultCode == Activity.RESULT_OK && data != null) {
            String path = data.getStringExtra(FileBrowserActivity.EXTRA_SELECTED_PATH);
            if (path == null) return;
            importFile(Uri.fromFile(new java.io.File(path)));
        }
    }

    private void importFile(Uri uri) {
        try {
            MinyanDataStore.importFromUri(this, uri);
            Toast.makeText(this, R.string.btn_load_file, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            String msg = getString(R.string.label_load_error, e.getMessage());
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        }
    }
}
