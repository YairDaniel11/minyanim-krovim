package com.meir.minyanimkrovim;

import android.content.Intent;
import android.content.pm.PackageManager;
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

public class MainActivity extends BaseActivity {

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

        selectedDayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        buildDayRow();
        requestNotificationPermissionIfNeeded();
    }

    // ---- תפריט (מקש התפריט / המקש הימני / שלוש נקודות) - במקום שורת כפתורים שתופסת מקום במסך ----

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.menu_zmanim) {
            startActivity(new Intent(this, ZmanimActivity.class));
            return true;
        } else if (id == R.id.menu_refresh) {
            refresh();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** במכשירי מקשים: המקש הימני (soft right) פותח את התפריט. */
    @Override
    public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_SOFT_RIGHT) {
            openOptionsMenu();
            return true;
        }
        return super.onKeyUp(keyCode, event);
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

    /**
     * בונה 7 כפתורי יום (א ב ג ד ה ו שבת) בשתי שורות קבועות ברוחב מלא
     * (4 בשורה העליונה, 3 בתחתונה) - כל הכפתורים ב-layout_weight=1, לא
     * ברוחב קבוע בתוך HorizontalScrollView. כך כל 7 הימים, כולל "שבת",
     * תמיד גלויים במלואם בכל רוחב מסך, בלי צורך בגלילה אופקית (שלא
     * עובדת בצורה אמינה בניווט מקשים פיזיים, ועלולה "להסתיר" את הכפתור
     * האחרון בשורה).
     */
    private void buildDayRow() {
        String[] letters = getResources().getStringArray(R.array.day_letters);
        dayRow.removeAllViews();
        dayButtons.clear();

        int todayDow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row2Lp.topMargin = 6;
        row2.setLayoutParams(row2Lp);

        for (int i = 0; i < 7; i++) {
            final int calendarDow = i + 1; // Calendar.SUNDAY == 1
            Button b = new Button(this);
            b.setId(View.generateViewId()); // נדרש כדי ש-setNextFocusRightId/LeftId יוכלו לאתר את הכפתור
            b.setText(letters[i]);
            b.setTextSize(13);
            b.setPadding(2, 8, 2, 8);
            b.setAllCaps(false);
            if (calendarDow == todayDow) {
                b.setTypeface(b.getTypeface(), Typeface.BOLD);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(3, 0, 3, 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedDayOfWeek = calendarDow;
                    refresh();
                }
            });
            // מציג חיווי ברור (אותו עיצוב שימוש בכפתורי הגדרות/זמנים/רענון)
            // כשהכפתור מקבל פוקוס ממקשי הניווט - ראו updateDaySelectionColors.
            b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    updateDaySelectionColors();
                }
            });
            (i < 4 ? row1 : row2).addView(b);
            dayButtons.add(b);
        }

        dayRow.addView(row1);
        dayRow.addView(row2);

        // שרשור ניווט ימין/שמאל בתוך כל שורה. הפריסה RTL הופכת אוטומטית את
        // הסדר החזותי של ילדי LinearLayout אופקי - כך שהכפתור הראשון שנוסף
        // (א) מוצג הכי מימין, והאחרון בשורה מוצג הכי משמאל. nextFocusRight/Left
        // הם לפי כיוון פיזי בפועל על המסך ולא הופכים אוטומטית עם RTL - לכן
        // משורשרים כאן בהתאם למיקום החזותי בפועל.
        chainRowFocus(row1);
        chainRowFocus(row2);
    }

    /** משרשר nextFocusRight/Left בין כפתורים עוקבים בשורה, לפי הסדר החזותי במסך RTL. */
    private void chainRowFocus(LinearLayout row) {
        int count = row.getChildCount();
        for (int i = 0; i < count; i++) {
            View current = row.getChildAt(i);
            if (i > 0) current.setNextFocusRightId(row.getChildAt(i - 1).getId());
            if (i < count - 1) current.setNextFocusLeftId(row.getChildAt(i + 1).getId());
        }
    }

    /** היום שנבחר = כפתור מלא (primary); שאר הימים = כפתור tonal. "היום" בפועל מודגש (bold) ובצבע primary. */
    private void updateDaySelectionColors() {
        int filled = resolveAttrDrawable(R.attr.bgFilled);
        int tonal = resolveAttrDrawable(R.attr.bgTonal);
        int onPrimary = resolveAttrColor(R.attr.m3OnPrimary);
        int onTonal = resolveAttrColor(R.attr.m3OnSecondaryContainer);
        int primary = resolveAttrColor(R.attr.m3Primary);
        int todayDow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        for (int i = 0; i < dayButtons.size(); i++) {
            boolean selected = (i + 1) == selectedDayOfWeek;
            boolean isToday = (i + 1) == todayDow;
            Button b = dayButtons.get(i);
            b.setBackgroundResource(selected ? filled : tonal);
            b.setTextColor(selected ? onPrimary : (isToday ? primary : onTonal));
            b.setTypeface(null, isToday ? Typeface.BOLD : Typeface.NORMAL);
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

        if (MinyanDataStore.needsAreaSelection(this)) {
            showEmpty(getString(R.string.label_choose_area));
            return;
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

        int cardDrawable = resolveAttrDrawable(R.attr.bgCard);
        int mainTextColor = resolveAttrColor(R.attr.colorTextMain);
        int secondaryTextColor = resolveAttrColor(R.attr.colorTextSecondary);
        int nextColor = resolveAttrColor(R.attr.colorNext); // אדום - הדגשת המניין הקרוב

        for (MinyanEntry entry : entries) {
            View row = inflater.inflate(R.layout.list_item_minyan, listMinyanim, false);
            TextView tvName = (TextView) row.findViewById(R.id.tvShulName);
            TextView tvPrayer = (TextView) row.findViewById(R.id.tvPrayerLine);
            TextView tvStatus = (TextView) row.findViewById(R.id.tvStatusLine);

            String nusachPart = entry.nusach.length() > 0 ? entry.nusach + " | " : "";
            String prefix = nusachPart + entry.prayerLabel(this) + " | ";
            String timeText = entry.timeLabel();
            boolean isHighlighted = highlighted != null && entry == highlighted;

            tvName.setText(entry.shulName);
            android.text.SpannableString line = new android.text.SpannableString(prefix + timeText);
            if (isHighlighted) {
                // הזמן של המניין הקרוב - באדום
                line.setSpan(new android.text.style.ForegroundColorSpan(nextColor),
                        prefix.length(), line.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                line.setSpan(new android.text.style.StyleSpan(Typeface.BOLD),
                        prefix.length(), line.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            tvPrayer.setText(line);
            tvStatus.setText(statusLine(entry, nowMinutes));

            row.setBackgroundResource(cardDrawable);
            tvName.setTextColor(mainTextColor);
            tvPrayer.setTextColor(secondaryTextColor);
            tvStatus.setTextColor(isHighlighted ? nextColor : secondaryTextColor);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 4);
            row.setLayoutParams(lp);

            listMinyanim.addView(row);
        }
    }
}
