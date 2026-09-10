package com.meir.minyanimkrovim;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.NumberPicker;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private static final int REQUEST_CODE_OPEN_FILE = 1001;

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

        btnLoadFile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openFilePicker(); }
        });

        setupPrayerRow(cbShacharit, btnTimeShacharit, AlarmScheduler.TYPE_SHACHARIT);
        setupPrayerRow(cbMincha, btnTimeMincha, AlarmScheduler.TYPE_MINCHA);
        setupPrayerRow(cbArvit, btnTimeArvit, AlarmScheduler.TYPE_ARVIT);

        btnBattery.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { requestIgnoreBatteryOptimizations(); }
        });

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

        timeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimePickerDialog(type, timeButton, checkBox);
            }
        });
    }

    /**
     * דיאלוג שעה שבנוי ידנית (NumberPicker) במקום TimePickerDialog של המערכת -
     * כך שהמראה זהה בכל גרסאות אנדרואיד (על מכשיר ישן במיוחד, TimePickerDialog
     * המובנה נראה שונה לגמרי בין גרסאות, וזה מבלבל משתמשים).
     */
    private void showTimePickerDialog(final String type, final Button timeButton, final CheckBox checkBox) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View content = inflater.inflate(R.layout.dialog_time_picker, null);

        final NumberPicker pickerHour = (NumberPicker) content.findViewById(R.id.pickerHour);
        final NumberPicker pickerMinute = (NumberPicker) content.findViewById(R.id.pickerMinute);
        Button btnOk = (Button) content.findViewById(R.id.btnTimePickerOk);
        Button btnCancel = (Button) content.findViewById(R.id.btnTimePickerCancel);

        pickerHour.setMinValue(0);
        pickerHour.setMaxValue(23);
        pickerHour.setFormatter(new NumberPicker.Formatter() {
            @Override public String format(int value) { return String.format(java.util.Locale.US, "%02d", value); }
        });

        pickerMinute.setMinValue(0);
        pickerMinute.setMaxValue(59);
        pickerMinute.setFormatter(new NumberPicker.Formatter() {
            @Override public String format(int value) { return String.format(java.util.Locale.US, "%02d", value); }
        });

        int currentMinutes = AlarmScheduler.getMinutesOfDay(this, type);
        pickerHour.setValue(currentMinutes / 60);
        pickerMinute.setValue(currentMinutes % 60);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .setCancelable(true)
                .create();

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });

        btnOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int newMinutes = pickerHour.getValue() * 60 + pickerMinute.getValue();
                timeButton.setText(formatMinutes(newMinutes) + " - " + getString(R.string.settings_set_time));
                boolean isEnabled = checkBox.isChecked();
                AlarmScheduler.setAlarm(SettingsActivity.this, type, isEnabled, newMinutes);
                dialog.dismiss();
            }
        });

        dialog.show();
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
        Toast.makeText(this, R.string.settings_ignore_battery, Toast.LENGTH_SHORT).show();
    }

    /**
     * הסייר המותאם אישית (FileBrowserActivity) הוסר לגמרי - הוא לא הגיב
     * בכלל במכשירים כשרים מסוימים (ה-ROM לא ניתב אליו לחיצת OK/מגע בשום
     * דרך שניתן היה לזהות מבחוץ). במקום זאת פשוט פותחים תמיד את בורר
     * הקבצים של המערכת (ACTION_GET_CONTENT) - זו האפליקציה שהספק של
     * הטלפון בנה בעצמו, כך שהיא זו שיודעת לטפל נכון בקלט של המכשיר שלו.
     * אם אין בכלל אפליקציית ניהול קבצים מותקנת במכשיר - startActivityForResult
     * יזרוק ActivityNotFoundException, ואז מוצגת הודעה למשתמש במקום קריסה.
     */
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(
                    Intent.createChooser(intent, getString(R.string.btn_load_file)),
                    REQUEST_CODE_OPEN_FILE);
        } catch (Exception e) {
            Toast.makeText(this, R.string.label_no_data, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OPEN_FILE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            importFile(uri);
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
