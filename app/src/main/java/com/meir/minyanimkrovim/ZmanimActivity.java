package com.meir.minyanimkrovim;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

/**
 * מסך "זמני היום" - עלות השחר, הנץ, סוף זמן קריאת שמע ותפילה (גר"א ומג"א), חצות, מנחה גדולה/קטנה,
 * פלג המנחה, שקיעה, צאת הכוכבים וצאת הכוכבים לרבנו תם. המיקום מוגדר בהגדרות -> "זמני היום".
 */
public class ZmanimActivity extends BaseActivity {

    private TextView tvDate, tvEmpty, tvSource;
    private LinearLayout zmanimList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zmanim);
        setTitle(R.string.zmanim_title);

        tvDate = (TextView) findViewById(R.id.tvZmanimDate);
        tvEmpty = (TextView) findViewById(R.id.tvZmanimEmpty);
        tvSource = (TextView) findViewById(R.id.tvZmanimSource);
        zmanimList = (LinearLayout) findViewById(R.id.zmanimList);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        Calendar now = Calendar.getInstance();
        tvDate.setText(DateFormat.format("EEEE, d/M/yyyy", now));

        LocationPrefs.Coords coords = LocationPrefs.getEffectiveCoords(this);
        if (coords == null) {
            showEmpty(getString(R.string.zmanim_no_location));
            return;
        }

        SunTimes.Zmanim z = SunTimes.computeZmanim(coords.lat, coords.lon, now);
        if (!z.valid) {
            showEmpty(getString(R.string.zmanim_invalid_location));
            return;
        }

        tvEmpty.setVisibility(View.GONE);
        zmanimList.setVisibility(View.VISIBLE);
        tvSource.setVisibility(View.VISIBLE);
        LocationPrefs.Coords manual = LocationPrefs.getManualCoords(this);
        tvSource.setText(manual != null
                ? getString(R.string.location_status_manual,
                        String.format(Locale.US, "%.4f", manual.lat), String.format(Locale.US, "%.4f", manual.lon))
                : getString(R.string.location_status_area));

        zmanimList.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        int mainColor = resolveAttrColor(R.attr.colorTextMain);
        int accentColor = resolveAttrColor(R.attr.m3Primary);
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int nextColor = resolveAttrColor(R.attr.colorNext);
        boolean nextMarked = false;
        for (ZmanimList.Item item : ZmanimList.ALL) {
            int m = ZmanimList.minutes(z, item.key);
            boolean isNext = !nextMarked && m >= 0 && m > nowMin;
            if (isNext) nextMarked = true;
            addZman(inflater, item.labelRes, m, isNext ? nextColor : mainColor, isNext ? nextColor : accentColor);
        }
    }

    private void showEmpty(String message) {
        zmanimList.setVisibility(View.GONE);
        tvSource.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.VISIBLE);
        tvEmpty.setText(message);
    }

    private void addZman(LayoutInflater inflater, int labelRes, int minutesOfDay, int mainColor, int accentColor) {
        View row = inflater.inflate(R.layout.list_item_zman, zmanimList, false);
        TextView tvLabel = (TextView) row.findViewById(R.id.tvZmanLabel);
        TextView tvTime = (TextView) row.findViewById(R.id.tvZmanTime);
        tvLabel.setText(labelRes);
        tvLabel.setTextColor(mainColor);
        tvTime.setText(String.format(Locale.US, "%02d:%02d", minutesOfDay / 60, minutesOfDay % 60));
        tvTime.setTextColor(accentColor);
        zmanimList.addView(row);
    }
}
