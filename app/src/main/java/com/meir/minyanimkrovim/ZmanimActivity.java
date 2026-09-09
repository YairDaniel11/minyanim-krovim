package com.meir.minyanimkrovim;

import android.app.Activity;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Locale;

/**
 * מסך "זמני היום" - עלות השחר, הנץ, סוף זמן קריאת שמע/תפילה, חצות,
 * מנחה גדולה/קטנה, פלג המנחה, שקיעה וצאת הכוכבים - מחושבים לפי המיקום
 * (מהקובץ שנטען, או קואורדינטות שהוזנו כאן ידנית).
 */
public class ZmanimActivity extends Activity {

    private TextView tvDate, tvEmpty, tvSource;
    private LinearLayout zmanimList;
    private EditText etLat, etLon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zmanim);
        setTitle(R.string.zmanim_title);

        tvDate = (TextView) findViewById(R.id.tvZmanimDate);
        tvEmpty = (TextView) findViewById(R.id.tvZmanimEmpty);
        tvSource = (TextView) findViewById(R.id.tvZmanimSource);
        zmanimList = (LinearLayout) findViewById(R.id.zmanimList);
        etLat = (EditText) findViewById(R.id.etLat);
        etLon = (EditText) findViewById(R.id.etLon);

        Button btnSave = (Button) findViewById(R.id.btnSaveLocation);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveLocation(); }
        });

        LocationPrefs.Coords manual = LocationPrefs.getManualCoords(this);
        if (manual != null) {
            etLat.setText(formatCoord(manual.lat));
            etLon.setText(formatCoord(manual.lon));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void saveLocation() {
        String latStr = etLat.getText().toString().trim();
        String lonStr = etLon.getText().toString().trim();
        try {
            double lat = Double.parseDouble(latStr);
            double lon = Double.parseDouble(lonStr);
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) throw new NumberFormatException();
            LocationPrefs.setManualCoords(this, lat, lon);
            Toast.makeText(this, R.string.zmanim_location_saved, Toast.LENGTH_SHORT).show();
            refresh();
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.zmanim_invalid_location, Toast.LENGTH_SHORT).show();
        }
    }

    private String formatCoord(double value) {
        return String.format(Locale.US, "%.4f", value);
    }

    private void refresh() {
        Calendar now = Calendar.getInstance();
        boolean is24Hour = DateFormat.is24HourFormat(this);
        tvDate.setText(DateFormat.format(is24Hour ? "EEEE, d/M/yyyy" : "EEEE, d/M/yyyy", now));

        LocationPrefs.Coords coords = LocationPrefs.getEffectiveCoords(this);
        if (coords == null) {
            zmanimList.setVisibility(View.GONE);
            tvSource.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        SunTimes.Zmanim z = SunTimes.computeZmanim(coords.lat, coords.lon, now);
        if (!z.valid) {
            zmanimList.setVisibility(View.GONE);
            tvSource.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(R.string.zmanim_invalid_location);
            return;
        }

        tvEmpty.setVisibility(View.GONE);
        zmanimList.setVisibility(View.VISIBLE);
        tvSource.setVisibility(View.VISIBLE);
        tvSource.setText(LocationPrefs.getManualCoords(this) != null ? "" : getString(R.string.zmanim_using_file_location));

        zmanimList.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        int mainColor = resolveAttrColor(R.attr.colorTextMain);
        int accentColor = resolveAttrColor(R.attr.colorAccent);

        addZman(inflater, R.string.zman_alot_hashachar, z.alotHashachar, mainColor, accentColor);
        addZman(inflater, R.string.zman_netz_hachama, z.netzHachama, mainColor, accentColor);
        addZman(inflater, R.string.zman_sof_zman_shema, z.sofZmanShemaGra, mainColor, accentColor);
        addZman(inflater, R.string.zman_sof_zman_tefila, z.sofZmanTefilaGra, mainColor, accentColor);
        addZman(inflater, R.string.zman_chatzot, z.chatzotHayom, mainColor, accentColor);
        addZman(inflater, R.string.zman_mincha_gedola, z.minchaGedola, mainColor, accentColor);
        addZman(inflater, R.string.zman_mincha_ketana, z.minchaKetana, mainColor, accentColor);
        addZman(inflater, R.string.zman_plag_hamincha, z.plagHamincha, mainColor, accentColor);
        addZman(inflater, R.string.zman_shkiat_hachama, z.shkiatHachama, mainColor, accentColor);
        addZman(inflater, R.string.zman_tzeit_hakochavim, z.tzeitHakochavim, mainColor, accentColor);
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

    private int resolveAttrColor(int attrResId) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attrResId, typedValue, true);
        return typedValue.data;
    }
}
