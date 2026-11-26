package com.meir.minyanimkrovim;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class SettingsActivity extends BaseActivity {

    private static final int REQUEST_CODE_OPEN_FILE = 1001;
    private static final int REQUEST_CODE_BROWSE_FILE = 1003;
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 1004;

    /** "כמה דקות לפני" - האפשרויות בהוספת התראה לזמן יום. */
    private static final int[] BEFORE_OPTIONS = {0, 5, 10, 15, 20, 30, 45, 60, 90};

    private CheckBox cbShacharit, cbMincha, cbArvit;
    private Button btnTimeShacharit, btnTimeMincha, btnTimeArvit, btnBattery, btnLoadFile;
    private RadioGroup rgTheme, rgSource;
    private TextView tvSourceNote, tvLocationStatus;
    private EditText etLat, etLon;
    private LinearLayout zmanAlarmList;
    private boolean updatingSourceUi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
        rgSource = (RadioGroup) findViewById(R.id.rgSource);
        tvSourceNote = (TextView) findViewById(R.id.tvSourceNote);
        tvLocationStatus = (TextView) findViewById(R.id.tvLocationStatus);
        etLat = (EditText) findViewById(R.id.etLat);
        etLon = (EditText) findViewById(R.id.etLon);
        zmanAlarmList = (LinearLayout) findViewById(R.id.zmanAlarmList);

        findViewById(R.id.btnArea).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, AreaActivity.class)); }
        });
        btnLoadFile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openFilePicker(); }
        });

        setupSourceSelector();
        setupLocationSection();
        setupZmanAlarmsSection();

        setupPrayerRow(cbShacharit, btnTimeShacharit, AlarmScheduler.TYPE_SHACHARIT);
        setupPrayerRow(cbMincha, btnTimeMincha, AlarmScheduler.TYPE_MINCHA);
        setupPrayerRow(cbArvit, btnTimeArvit, AlarmScheduler.TYPE_ARVIT);

        btnBattery.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { requestIgnoreBatteryOptimizations(); }
        });

        setupThemeSelector();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSourceUi();
        refreshLocationStatus();
    }

    // ------------------------------------------------------------------ מקור הנתונים

    private void setupSourceSelector() {
        rgSource.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (updatingSourceUi) return;
                String mode = checkedId == R.id.rbSrcFile ? AreaPrefs.MODE_FILE
                        : checkedId == R.id.rbSrcBoth ? AreaPrefs.MODE_BOTH
                        : AreaPrefs.MODE_NATIONAL;
                AreaPrefs.setMode(SettingsActivity.this, mode);
                refreshSourceUi();
            }
        });
    }

    private void refreshSourceUi() {
        updatingSourceUi = true;
        String mode = AreaPrefs.getMode(this);
        int id = AreaPrefs.MODE_FILE.equals(mode) ? R.id.rbSrcFile
                : AreaPrefs.MODE_BOTH.equals(mode) ? R.id.rbSrcBoth : R.id.rbSrcNational;
        ((RadioButton) findViewById(id)).setChecked(true);
        updatingSourceUi = false;

        boolean needsFile = AreaPrefs.usesFile(this) && !MinyanDataStore.hasPersonalFile(this);
        tvSourceNote.setVisibility(needsFile ? View.VISIBLE : View.GONE);
        tvSourceNote.setText(R.string.source_file_missing);
    }

    // ------------------------------------------------------------------ מיקום לזמני היום

    private void setupLocationSection() {
        findViewById(R.id.btnPickCity).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showCityDialog(); }
        });
        findViewById(R.id.btnSaveLocation).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveManualLocation(); }
        });
        findViewById(R.id.btnClearLocation).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                LocationPrefs.clearManualCoords(SettingsActivity.this);
                etLat.setText("");
                etLon.setText("");
                onLocationChanged();
            }
        });
        LocationPrefs.Coords manual = LocationPrefs.getManualCoords(this);
        if (manual != null) {
            etLat.setText(formatCoord(manual.lat));
            etLon.setText(formatCoord(manual.lon));
        }
    }

    private void showCityDialog() {
        final DefaultCities.City[] cities = DefaultCities.ALL;
        String[] labels = new String[cities.length];
        for (int i = 0; i < cities.length; i++) labels[i] = cities[i].name;
        new AlertDialog.Builder(this)
                .setTitle(R.string.location_pick_city)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        DefaultCities.City city = cities[which];
                        LocationPrefs.setManualCoords(SettingsActivity.this, city.lat, city.lon);
                        etLat.setText(formatCoord(city.lat));
                        etLon.setText(formatCoord(city.lon));
                        onLocationChanged();
                    }
                })
                .show();
    }

    private void saveManualLocation() {
        try {
            double lat = Double.parseDouble(etLat.getText().toString().trim());
            double lon = Double.parseDouble(etLon.getText().toString().trim());
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) throw new NumberFormatException();
            LocationPrefs.setManualCoords(this, lat, lon);
            onLocationChanged();
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.zmanim_invalid_location, Toast.LENGTH_SHORT).show();
        }
    }

    private void onLocationChanged() {
        Toast.makeText(this, R.string.zmanim_location_saved, Toast.LENGTH_SHORT).show();
        refreshLocationStatus();
        ZmanAlarmStore.rescheduleAll(this); // זמני ההתראות תלויים במיקום
    }

    private void refreshLocationStatus() {
        LocationPrefs.Coords manual = LocationPrefs.getManualCoords(this);
        if (manual != null) {
            tvLocationStatus.setText(getString(R.string.location_status_manual,
                    formatCoord(manual.lat), formatCoord(manual.lon)));
        } else if (LocationPrefs.getEffectiveCoords(this) != null) {
            tvLocationStatus.setText(R.string.location_status_area);
        } else {
            tvLocationStatus.setText(R.string.location_status_none);
        }
    }

    private static String formatCoord(double value) {
        return String.format(Locale.US, "%.4f", value);
    }

    // ------------------------------------------------------------------ התראות לזמני היום

    private void setupZmanAlarmsSection() {
        findViewById(R.id.btnAddZmanAlarm).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startAddZmanAlarm(); }
        });
        refreshZmanAlarmList();
    }

    private void refreshZmanAlarmList() {
        zmanAlarmList.removeAllViews();
        List<ZmanAlarmStore.Alarm> alarms = ZmanAlarmStore.list(this);
        if (alarms.isEmpty()) {
            TextView none = new TextView(this);
            none.setText(R.string.zman_alarm_none);
            none.setTextColor(resolveAttrColor(R.attr.colorTextSecondary));
            none.setTextSize(13);
            zmanAlarmList.addView(none);
            return;
        }
        for (final ZmanAlarmStore.Alarm a : alarms) {
            TextView row = new TextView(this);
            String before = a.before == 0 ? getString(R.string.zman_alarm_before_0)
                    : getString(R.string.zman_alarm_before_n, a.before);
            String type = getString(a.repeat ? R.string.zman_alarm_type_repeat : R.string.zman_alarm_type_once);
            row.setText(getString(R.string.zman_alarm_row, getString(ZmanimList.labelRes(a.key)), before, type));
            row.setTextColor(resolveAttrColor(R.attr.m3OnSecondaryContainer));
            row.setTextSize(14);
            row.setBackgroundResource(resolveAttrDrawable(R.attr.bgTonal));
            row.setFocusable(true);
            row.setClickable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 6;
            row.setLayoutParams(lp);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { confirmDeleteZmanAlarm(a.id); }
            });
            zmanAlarmList.addView(row);
        }
    }

    private void confirmDeleteZmanAlarm(final int id) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.zman_alarm_delete_title)
                .setPositiveButton(R.string.zman_alarm_delete, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        ZmanAlarmStore.remove(SettingsActivity.this, id);
                        refreshZmanAlarmList();
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    /** שלושה שלבים: איזה זמן -> כמה דקות לפני -> קבועה/חד-פעמית. */
    private void startAddZmanAlarm() {
        if (LocationPrefs.getEffectiveCoords(this) == null) {
            Toast.makeText(this, R.string.zman_alarm_need_location, Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[ZmanimList.ALL.length];
        for (int i = 0; i < labels.length; i++) labels[i] = getString(ZmanimList.ALL[i].labelRes);
        new AlertDialog.Builder(this)
                .setTitle(R.string.zman_alarm_pick_zman)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        pickBefore(ZmanimList.ALL[which].key);
                    }
                })
                .show();
    }

    private void pickBefore(final String key) {
        String[] labels = new String[BEFORE_OPTIONS.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = BEFORE_OPTIONS[i] == 0 ? getString(R.string.zman_alarm_before_0)
                    : getString(R.string.zman_alarm_before_n, BEFORE_OPTIONS[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.zman_alarm_pick_before)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        pickType(key, BEFORE_OPTIONS[which]);
                    }
                })
                .show();
    }

    private void pickType(final String key, final int before) {
        String[] labels = {getString(R.string.zman_alarm_type_repeat), getString(R.string.zman_alarm_type_once)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.zman_alarm_pick_type)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        boolean ok = ZmanAlarmStore.add(SettingsActivity.this, key, before, which == 0);
                        Toast.makeText(SettingsActivity.this,
                                ok ? R.string.zman_alarm_added : R.string.zman_alarm_need_location,
                                Toast.LENGTH_SHORT).show();
                        refreshZmanAlarmList();
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------ ערכת נושא

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

    // ------------------------------------------------------------------ התראות מניינים

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
     * כך שהמראה זהה בכל גרסאות אנדרואיד.
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
            @Override public String format(int value) { return String.format(Locale.US, "%02d", value); }
        });

        pickerMinute.setMinValue(0);
        pickerMinute.setMaxValue(59);
        pickerMinute.setFormatter(new NumberPicker.Formatter() {
            @Override public String format(int value) { return String.format(Locale.US, "%02d", value); }
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
        return String.format(Locale.US, "%02d:%02d", minutesOfDay / 60, minutesOfDay % 60);
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

    // ------------------------------------------------------------------ טעינת קובץ אישי

    /**
     * במכשירים ישנים (SDK < 29) בורר המערכת לרוב לא חושף עיון באחסון מקומי, ועל ROMs כשרים
     * מצומצמים ייתכן שאין בכלל אפליקציית ניהול קבצים - לכן שם פותחים את הדפדפן הפנימי.
     */
    private void openFilePicker() {
        if (Build.VERSION.SDK_INT < 29) {
            openInternalFileBrowser();
        } else {
            openSystemFilePicker();
        }
    }

    private void openSystemFilePicker() {
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

    private void openInternalFileBrowser() {
        if (Build.VERSION.SDK_INT >= 23
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
            refreshSourceUi();
        } catch (Exception e) {
            String msg = getString(R.string.label_load_error, e.getMessage());
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        }
    }
}
