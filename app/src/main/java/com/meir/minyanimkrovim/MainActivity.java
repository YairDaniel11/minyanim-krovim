package com.meir.minyanimkrovim;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQUEST_CODE_NOTIF_PERMISSION = 1002;
    private static final long REFRESH_INTERVAL_MS = 30_000;

    /** מניין שכבר התחיל עדיין "רלוונטי" עד כמה דקות לאחר תחילתו, לפי סוג התפילה. */
    private static final int VISIBLE_AFTER_START_SHACHARIT = 30;
    private static final int VISIBLE_AFTER_START_OTHER = 5;

    private TextView tvLocation, tvNextTitle, tvNext, tvLastTitle, tvLast, tvAllTitle, tvEmpty;
    private LinearLayout dayRow;
    private LinearLayout listMinyanim;
    private final List<Button> dayButtons = new ArrayList<>();
    private int selectedDayOfWeek;

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

        tvLocation = (TextView) findViewById(R.id.tvLocation);
        tvNextTitle = (TextView) findViewById(R.id.tvNextTitle);
        tvNext = (TextView) findViewById(R.id.tvNext);
        tvLastTitle = (TextView) findViewById(R.id.tvLastTitle);
        tvLast = (TextView) findViewById(R.id.tvLast);
        tvAllTitle = (TextView) findViewById(R.id.tvAllTitle);
        tvEmpty = (TextView) findViewById(R.id.tvEmpty);
        dayRow = (LinearLayout) findViewById(R.id.dayRow);
        listMinyanim = (LinearLayout) findViewById(R.id.listMinyanim);

        Button btnSettings = (Button) findViewById(R.id.btnSettings);
        Button btnZmanim = (Button) findViewById(R.id.btnZmanim);
        Button btnRefresh = (Button) findViewById(R.id.btnRefresh);

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        btnZmanim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ZmanimActivity.class));
            }
        });
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { refresh(); }
        });

        selectedDayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        buildDayRow();
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

    /** בונה שורת 7 כפתורי יום (א ב ג ד ה ו שבת) למעבר בין ימי השבוע. */
    private void buildDayRow() {
        String[] letters = getResources().getStringArray(R.array.day_letters);
        dayRow.removeAllViews();
        dayButtons.clear();
        int screenWidthDp = getResources().getConfiguration().screenWidthDp;
        // רוחב קבוע (לא weight=1 בתוך HorizontalScrollView, אחרת כל הכפתורים
        // מצטמצמים לרוחב אפס) - מספיק רחב לנוחות מקשים, אבל מצטמצם מעט
        // במסכים צרים כדי שיהיו נראים כמה שיותר כפתורים בבת אחת.
        int buttonWidthDp = screenWidthDp > 0 && screenWidthDp < 260 ? 40 : 48;
        int buttonWidthPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, buttonWidthDp, getResources().getDisplayMetrics());

        int todayDow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);

        for (int i = 0; i < 7; i++) {
            final int calendarDow = i + 1; // Calendar.SUNDAY == 1
            Button b = new Button(this);
            b.setText(letters[i]);
            b.setTextSize(13);
            b.setPadding(2, 8, 2, 8);
            b.setAllCaps(false);
            b.setBackgroundResource(R.drawable.bg_round_button);
            b.setTextColor(0xFFFFFFFF);
            if (calendarDow == todayDow) {
                b.setTypeface(b.getTypeface(), Typeface.BOLD);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    buttonWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(3, 0, 3, 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedDayOfWeek = calendarDow;
                    refresh();
                }
            });
            dayRow.addView(b);
            dayButtons.add(b);
        }
        updateDaySelectionColors();
    }

    private void updateDaySelectionColors() {
        int accent = resolveAttrColor(R.attr.colorAccent);
        int card = resolveAttrColor(R.attr.colorBgCard);
        int mainText = resolveAttrColor(R.attr.colorTextMain);
        for (int i = 0; i < dayButtons.size(); i++) {
            boolean selected = (i + 1) == selectedDayOfWeek;
            Button b = dayButtons.get(i);
            b.getBackground().mutate().setColorFilter(selected ? accent : card, PorterDuff.Mode.SRC_IN);
            b.setTextColor(selected ? 0xFFFFFFFF : mainText);
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
        updateDaySelectionColors();

        String location = MinyanDataStore.loadLocation(this);
        if (location.length() > 0) {
            tvLocation.setVisibility(View.VISIBLE);
            tvLocation.setText(location);
        } else {
            tvLocation.setVisibility(View.GONE);
        }

        if (!MinyanDataStore.hasData(this)) {
            showEmpty(getString(R.string.label_no_data));
            return;
        }

        List<MinyanEntry> all;
        try {
            all = MinyanDataStore.loadAll(this);
        } catch (Exception e) {
            showEmpty(getString(R.string.label_load_error, e.getMessage()));
            return;
        }

        Calendar now = Calendar.getInstance();
        boolean isToday = selectedDayOfWeek == now.get(Calendar.DAY_OF_WEEK);
        String dayType = MinyanDataStore.dayTypeForDayOfWeek(selectedDayOfWeek);
        int nowMinutes = MinyanDataStore.currentMinutesOfDay(now);

        List<MinyanEntry> dayEntries = MinyanDataStore.filterAndSortForToday(all, dayType);
        tvAllTitle.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        MinyanEntry highlighted = null;

        if (isToday) {
            tvNextTitle.setVisibility(View.VISIBLE);
            tvNext.setVisibility(View.VISIBLE);
            tvLastTitle.setVisibility(View.VISIBLE);
            tvLast.setVisibility(View.VISIBLE);
            tvAllTitle.setText(R.string.label_all_minyanim_title);

            MinyanEntry next = MinyanDataStore.findNext(dayEntries, nowMinutes);
            MinyanEntry last = MinyanDataStore.findLast(dayEntries, nowMinutes);
            highlighted = next;

            tvNext.setText(next != null
                    ? describeEntry(next) + " - " + statusLine(next, nowMinutes)
                    : getString(R.string.no_next_minyan));
            tvLast.setText(last != null
                    ? describeEntry(last) + " - " + statusLine(last, nowMinutes)
                    : getString(R.string.no_last_minyan));

            dayEntries = filterVisibleForNow(dayEntries, nowMinutes);
        } else {
            tvNextTitle.setVisibility(View.GONE);
            tvNext.setVisibility(View.GONE);
            tvLastTitle.setVisibility(View.GONE);
            tvLast.setVisibility(View.GONE);
            String[] dayNames = getResources().getStringArray(R.array.day_full_names);
            tvAllTitle.setText(getString(R.string.label_all_minyanim_title_for_day,
                    dayNames[selectedDayOfWeek - 1]));
        }

        if (dayEntries.isEmpty()) {
            listMinyanim.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            // אין מניינים ליום הזה - שונה במפורש מ"לא נטען קובץ בכלל"
            tvEmpty.setText(R.string.label_no_entries_for_day);
        } else {
            tvEmpty.setVisibility(View.GONE);
            listMinyanim.setVisibility(View.VISIBLE);
            populateList(dayEntries, nowMinutes, highlighted);
        }
    }

    /** מדלג על מניינים שהתחילו כבר מזמן (לא רלוונטיים יותר) - שחרית עד 30 דקות אחרי, מנחה/ערבית עד 5. */
    private List<MinyanEntry> filterVisibleForNow(List<MinyanEntry> sortedToday, int nowMinutes) {
        List<MinyanEntry> visible = new ArrayList<>();
        for (MinyanEntry e : sortedToday) {
            if (e.minutesOfDay >= nowMinutes) {
                visible.add(e);
                continue;
            }
            int agoMinutes = nowMinutes - e.minutesOfDay;
            int threshold = "shacharit".equals(e.prayerType)
                    ? VISIBLE_AFTER_START_SHACHARIT : VISIBLE_AFTER_START_OTHER;
            if (agoMinutes <= threshold) visible.add(e);
        }
        return visible;
    }

    private void showEmpty(String message) {
        tvNextTitle.setVisibility(View.GONE);
        tvNext.setVisibility(View.GONE);
        tvLastTitle.setVisibility(View.GONE);
        tvLast.setVisibility(View.GONE);
        tvAllTitle.setVisibility(View.GONE);
        tvEmpty.setText(message);
        listMinyanim.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.VISIBLE);
    }

    private String describeEntry(MinyanEntry entry) {
        String nusachPart = entry.nusach.length() > 0 ? " (" + entry.nusach + ")" : "";
        return entry.shulName + nusachPart + " - " + entry.prayerLabel(this) + " - " + entry.timeLabel();
    }

    private String statusLine(MinyanEntry entry, int nowMinutes) {
        int diff = entry.minutesOfDay - nowMinutes;
        if (diff == 0) return getString(R.string.starts_now);
        boolean future = diff > 0;
        int total = Math.abs(diff);
        int hours = total / 60;
        int minutes = total % 60;

        if (hours == 0) {
            if (minutes == 1) return getString(future ? R.string.starts_in_one_minute : R.string.started_one_minute_ago);
            return getString(future ? R.string.starts_in_minutes : R.string.started_minutes_ago, minutes);
        }
        if (hours == 1) {
            if (minutes == 0) return getString(future ? R.string.starts_in_one_hour : R.string.started_one_hour_ago);
            if (minutes == 1) return getString(future ? R.string.starts_in_one_hour_one_minute : R.string.started_one_hour_one_minute_ago);
            return getString(future ? R.string.starts_in_one_hour_minutes : R.string.started_one_hour_minutes_ago, minutes);
        }
        if (minutes == 0) return getString(future ? R.string.starts_in_hours : R.string.started_hours_ago, hours);
        if (minutes == 1) return getString(future ? R.string.starts_in_hours_one_minute : R.string.started_hours_one_minute_ago, hours);
        return getString(future ? R.string.starts_in_hours_minutes : R.string.started_hours_minutes_ago, hours, minutes);
    }

    /** ממלא ידנית את מיכל הרשימה (LinearLayout רגיל בתוך ה-ScrollView הראשי - לא ListView). */
    private void populateList(List<MinyanEntry> entries, int nowMinutes, MinyanEntry highlighted) {
        listMinyanim.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();

        int cardColor = resolveAttrColor(R.attr.colorBgCard);
        int mainTextColor = resolveAttrColor(R.attr.colorTextMain);
        int secondaryTextColor = resolveAttrColor(R.attr.colorTextSecondary);
        int nextColor = resolveAttrColor(R.attr.colorNext);

        for (MinyanEntry entry : entries) {
            View row = inflater.inflate(R.layout.list_item_minyan, listMinyanim, false);
            TextView tvName = (TextView) row.findViewById(R.id.tvShulName);
            TextView tvPrayer = (TextView) row.findViewById(R.id.tvPrayerLine);
            TextView tvStatus = (TextView) row.findViewById(R.id.tvStatusLine);

            String nusachPart = entry.nusach.length() > 0 ? entry.nusach + " | " : "";
            tvName.setText(entry.shulName);
            tvPrayer.setText(nusachPart + entry.prayerLabel(this) + " | " + entry.timeLabel());
            tvStatus.setText(statusLine(entry, nowMinutes));

            row.setBackgroundColor(cardColor);
            tvName.setTextColor(mainTextColor);
            tvPrayer.setTextColor(secondaryTextColor);

            boolean isHighlighted = highlighted != null && entry == highlighted;
            tvStatus.setTextColor(isHighlighted ? nextColor : secondaryTextColor);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 4);
            row.setLayoutParams(lp);

            listMinyanim.addView(row);
        }
    }

    private int resolveAttrColor(int attrResId) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attrResId, typedValue, true);
        return typedValue.data;
    }
}
