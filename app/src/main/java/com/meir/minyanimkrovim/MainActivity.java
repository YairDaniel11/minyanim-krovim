package com.meir.minyanimkrovim;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQUEST_CODE_OPEN_FILE = 1001;
    private static final int REQUEST_CODE_NOTIF_PERMISSION = 1002;
    private static final long REFRESH_INTERVAL_MS = 30_000;

    private TextView tvDayType, tvNext, tvLast, tvEmpty;
    private ListView listMinyanim;
    private final Handler handler = new Handler();
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvDayType = (TextView) findViewById(R.id.tvDayType);
        tvNext = (TextView) findViewById(R.id.tvNext);
        tvLast = (TextView) findViewById(R.id.tvLast);
        tvEmpty = (TextView) findViewById(R.id.tvEmpty);
        listMinyanim = (ListView) findViewById(R.id.listMinyanim);

        Button btnLoadFile = (Button) findViewById(R.id.btnLoadFile);
        Button btnSettings = (Button) findViewById(R.id.btnSettings);
        Button btnRefresh = (Button) findViewById(R.id.btnRefresh);

        btnLoadFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { openFilePicker(); }
        });
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { refresh(); }
        });

        requestNotificationPermissionIfNeeded();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 /* TIRAMISU */) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_CODE_NOTIF_PERMISSION);
            }
        }
    }

    private void openFilePicker() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= 19 /* KITKAT - ACTION_OPEN_DOCUMENT */) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
        } else {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
        }
        intent.setType("*/*"); // מאפשרים גם text/plain - חלק מהמכשירים לא מזהים application/json
        try {
            startActivityForResult(intent, REQUEST_CODE_OPEN_FILE);
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
            try {
                MinyanDataStore.importFromUri(this, uri);
                Toast.makeText(this, R.string.btn_load_file, Toast.LENGTH_SHORT).show();
                refresh();
            } catch (Exception e) {
                String msg = getString(R.string.label_load_error, e.getMessage());
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshRunnable);
    }

    private void refresh() {
        if (!MinyanDataStore.hasData(this)) {
            tvEmpty.setVisibility(View.VISIBLE);
            listMinyanim.setVisibility(View.GONE);
            tvNext.setText("");
            tvLast.setText("");
            tvDayType.setText("");
            return;
        }

        List<MinyanEntry> all;
        try {
            all = MinyanDataStore.loadAll(this);
        } catch (Exception e) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(getString(R.string.label_load_error, e.getMessage()));
            listMinyanim.setVisibility(View.GONE);
            return;
        }

        tvEmpty.setVisibility(View.GONE);
        listMinyanim.setVisibility(View.VISIBLE);

        Calendar now = Calendar.getInstance();
        String dayType = MinyanDataStore.currentDayType(now);
        int nowMinutes = MinyanDataStore.currentMinutesOfDay(now);

        tvDayType.setText(dayType.equals("shabbat")
                ? getString(R.string.label_day_type_shabbat)
                : getString(R.string.label_day_type_weekday));

        List<MinyanEntry> today = MinyanDataStore.filterAndSortForToday(all, dayType);
        MinyanEntry next = MinyanDataStore.findNext(today, nowMinutes);
        MinyanEntry last = MinyanDataStore.findLast(today, nowMinutes);

        if (next != null) {
            tvNext.setText(getString(R.string.label_next_minyan_title) + ":\n"
                    + next.shulName + " (" + next.nusach + ") - " + next.prayerLabel(this)
                    + " - " + statusLine(next, nowMinutes));
        } else {
            tvNext.setText(getString(R.string.label_next_minyan_title) + ":\n"
                    + getString(R.string.no_next_minyan));
        }

        if (last != null) {
            tvLast.setText(getString(R.string.label_last_minyan_title) + ":\n"
                    + last.shulName + " (" + last.nusach + ") - " + last.prayerLabel(this)
                    + " - " + statusLine(last, nowMinutes));
        } else {
            tvLast.setText(getString(R.string.label_last_minyan_title) + ":\n"
                    + getString(R.string.no_last_minyan));
        }

        listMinyanim.setAdapter(new MinyanAdapter(this, today, nowMinutes, next));
    }

    private String statusLine(MinyanEntry entry, int nowMinutes) {
        int diff = entry.minutesOfDay - nowMinutes;
        if (diff == 0) return getString(R.string.starts_now);
        if (diff > 0) {
            return diff == 1 ? getString(R.string.starts_in_one_minute)
                    : getString(R.string.starts_in_minutes, diff);
        }
        int ago = -diff;
        return ago == 1 ? getString(R.string.started_one_minute_ago)
                : getString(R.string.started_minutes_ago, ago);
    }

    /** אדפטר פשוט לרשימת המניינים - מדגיש את המניין הקרוב ביותר. */
    private class MinyanAdapter extends ArrayAdapter<MinyanEntry> {
        private final int nowMinutes;
        private final MinyanEntry highlighted;
        private final LayoutInflater inflater;

        MinyanAdapter(Activity activity, List<MinyanEntry> items, int nowMinutes, MinyanEntry highlighted) {
            super(activity, 0, new ArrayList<>(items));
            this.nowMinutes = nowMinutes;
            this.highlighted = highlighted;
            this.inflater = activity.getLayoutInflater();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView != null ? convertView
                    : inflater.inflate(R.layout.list_item_minyan, parent, false);

            MinyanEntry entry = getItem(position);
            TextView tvName = (TextView) row.findViewById(R.id.tvShulName);
            TextView tvPrayer = (TextView) row.findViewById(R.id.tvPrayerLine);
            TextView tvStatus = (TextView) row.findViewById(R.id.tvStatusLine);

            tvName.setText(entry.shulName);
            tvPrayer.setText(entry.nusach + " | " + entry.prayerLabel(MainActivity.this)
                    + " | " + entry.timeLabel());
            tvStatus.setText(statusLine(entry, nowMinutes));

            int cardColor = resolveAttrColor(R.attr.colorBgCard);
            int mainTextColor = resolveAttrColor(R.attr.colorTextMain);
            int secondaryTextColor = resolveAttrColor(R.attr.colorTextSecondary);
            int nextColor = resolveAttrColor(R.attr.colorNext);

            row.setBackgroundColor(cardColor);
            tvName.setTextColor(mainTextColor);
            tvPrayer.setTextColor(secondaryTextColor);

            boolean isHighlighted = highlighted != null && entry == highlighted;
            tvStatus.setTextColor(isHighlighted ? nextColor : secondaryTextColor);

            return row;
        }
    }

    private int resolveAttrColor(int attrResId) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attrResId, typedValue, true);
        return typedValue.data;
    }
}
