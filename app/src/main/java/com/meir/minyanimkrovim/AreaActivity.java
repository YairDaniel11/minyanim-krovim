package com.meir.minyanimkrovim;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * הגדרת אזור המגורים (עיר/שכונה + רדיוס), ניהול רשימת בתי הכנסת להצגה/התעלמות,
 * והורדת הנתונים העדכניים מ-GitHub.
 */
public class AreaActivity extends Activity {

    private static final int REQUEST_PICK_CENTER = 2001;
    private static final int REQUEST_MANAGE = 2002;

    private TextView tvCurrent, tvCount, tvDataInfo;
    private Button btnRadius, btnDownload, btnSwitchSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_area);
        setTitle(R.string.area_title);

        tvCurrent = (TextView) findViewById(R.id.tvAreaCurrent);
        tvCount = (TextView) findViewById(R.id.tvAreaCount);
        tvDataInfo = (TextView) findViewById(R.id.tvDataInfo);
        btnRadius = (Button) findViewById(R.id.btnRadius);
        btnDownload = (Button) findViewById(R.id.btnDownload);
        btnSwitchSource = (Button) findViewById(R.id.btnSwitchSource);

        findViewById(R.id.btnPickCity).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showCityDialog(); }
        });
        findViewById(R.id.btnPickByShul).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openShulList(REQUEST_PICK_CENTER); }
        });
        btnRadius.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showRadiusDialog(); }
        });
        findViewById(R.id.btnManageShuls).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!AreaPrefs.hasArea(AreaActivity.this)) {
                    Toast.makeText(AreaActivity.this, R.string.area_choose_first, Toast.LENGTH_SHORT).show();
                    return;
                }
                openShulList(REQUEST_MANAGE);
            }
        });
        btnDownload.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startDownload(); }
        });
        btnSwitchSource.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                AreaPrefs.setMode(AreaActivity.this,
                        AreaPrefs.isNational(AreaActivity.this) ? AreaPrefs.MODE_FILE : AreaPrefs.MODE_NATIONAL);
                refresh();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /** פתיחת רשימת בתי כנסת - אפשרית רק במצב נתונים ארציים (אם היינו במצב קובץ - עוברים אליו). */
    private void openShulList(int request) {
        if (!AreaPrefs.isNational(this)) {
            AreaPrefs.setMode(this, AreaPrefs.MODE_NATIONAL);
        }
        Intent i = new Intent(this, ShulListActivity.class);
        i.putExtra(ShulListActivity.EXTRA_MODE,
                request == REQUEST_PICK_CENTER ? ShulListActivity.MODE_PICK_CENTER : ShulListActivity.MODE_MANAGE);
        startActivityForResult(i, request);
    }

    private void showCityDialog() {
        final DefaultCities.City[] cities = DefaultCities.ALL;
        String[] labels = new String[cities.length];
        for (int i = 0; i < cities.length; i++) labels[i] = cities[i].name;
        new AlertDialog.Builder(this)
                .setTitle(R.string.area_pick_city)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        setArea(cities[which].name, cities[which].lat, cities[which].lon);
                    }
                })
                .show();
    }

    private void showRadiusDialog() {
        final float[] opts = AreaPrefs.RADIUS_OPTIONS_KM;
        String[] labels = new String[opts.length];
        for (int i = 0; i < opts.length; i++) labels[i] = getString(R.string.area_radius_option, formatKm(opts[i]));
        new AlertDialog.Builder(this)
                .setTitle(R.string.area_radius_title)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        AreaPrefs.setRadiusKm(AreaActivity.this, opts[which]);
                        refresh();
                    }
                })
                .show();
    }

    private void setArea(String name, double lat, double lon) {
        AreaPrefs.setMode(this, AreaPrefs.MODE_NATIONAL);
        AreaPrefs.setArea(this, name, lat, lon);
        refresh();
    }

    private void startDownload() {
        btnDownload.setEnabled(false);
        btnDownload.setText(R.string.area_downloading);
        DataDownloader.download(this, new DataDownloader.Callback() {
            @Override public void onSuccess(boolean updated, String generated) {
                btnDownload.setEnabled(true);
                Toast.makeText(AreaActivity.this,
                        updated ? getString(R.string.area_download_updated, generated)
                                : getString(R.string.area_download_uptodate),
                        Toast.LENGTH_LONG).show();
                refresh();
            }
            @Override public void onError(String message) {
                btnDownload.setEnabled(true);
                Toast.makeText(AreaActivity.this, getString(R.string.area_download_failed, message), Toast.LENGTH_LONG).show();
                refresh();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_CENTER && resultCode == RESULT_OK && data != null) {
            setArea(data.getStringExtra(ShulListActivity.EXTRA_RESULT_NAME),
                    data.getDoubleExtra(ShulListActivity.EXTRA_RESULT_LAT, 0),
                    data.getDoubleExtra(ShulListActivity.EXTRA_RESULT_LON, 0));
        }
    }

    private static String formatKm(float km) {
        return (km == Math.floor(km)) ? String.valueOf((int) km) : String.format(Locale.US, "%.1f", km);
    }

    private void refresh() {
        boolean national = AreaPrefs.isNational(this);
        if (AreaPrefs.hasArea(this)) {
            tvCurrent.setText(getString(R.string.area_current, AreaPrefs.getAreaName(this)));
            if (national) {
                int count = 0;
                try { count = MinyanDataStore.listInArea(this).size(); } catch (Exception ignored) { }
                tvCount.setText(getString(R.string.area_count, count));
            } else {
                tvCount.setText(R.string.area_file_mode_note);
            }
        } else {
            tvCurrent.setText(R.string.area_none);
            tvCount.setText(national ? "" : getString(R.string.area_file_mode_note));
        }
        btnRadius.setText(getString(R.string.area_radius, formatKm(AreaPrefs.getRadiusKm(this))));

        String embedded = MinyanDataStore.embeddedGenerated(this);
        String used = MinyanDataStore.nationalGenerated(this);
        tvDataInfo.setText(getString(R.string.area_data_info, used.length() == 0 ? "?" : used,
                used.equals(embedded) ? getString(R.string.area_data_src_embedded) : getString(R.string.area_data_src_downloaded)));
        if (btnDownload.isEnabled()) btnDownload.setText(R.string.area_download);

        if (MinyanDataStore.hasPersonalFile(this)) {
            btnSwitchSource.setVisibility(View.VISIBLE);
            btnSwitchSource.setText(national ? R.string.area_use_personal_file : R.string.area_use_national);
        } else {
            btnSwitchSource.setVisibility(View.GONE);
        }
    }
}
