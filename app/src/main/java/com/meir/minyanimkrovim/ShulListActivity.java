package com.meir.minyanimkrovim;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * רשימת בתי כנסת מהנתונים הארציים, בשני מצבים:
 *  - MODE_MANAGE: בתי הכנסת שבאזור שנבחר, עם תיבת סימון "להציג" - הסרת הסימון = התעלמות מבית הכנסת.
 *  - MODE_PICK_CENTER: כל בתי הכנסת בארץ, עם חיפוש בשם/כתובת - הבחירה קובעת את מרכז האזור.
 */
public class ShulListActivity extends BaseActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_MANAGE = "manage";
    public static final String MODE_PICK_CENTER = "pick";
    public static final String EXTRA_RESULT_NAME = "name";
    public static final String EXTRA_RESULT_LAT = "lat";
    public static final String EXTRA_RESULT_LON = "lon";

    private boolean manageMode;
    private List<MinyanDataStore.ShulInfo> all = new ArrayList<>();
    private final List<MinyanDataStore.ShulInfo> shown = new ArrayList<>();
    private Set<String> hidden;
    private ShulAdapter adapter;
    private EditText etSearch;
    private TextView tvSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shul_list);

        manageMode = !MODE_PICK_CENTER.equals(getIntent().getStringExtra(EXTRA_MODE));
        setTitle(manageMode ? R.string.shul_list_title_manage : R.string.shul_list_title_pick);

        etSearch = (EditText) findViewById(R.id.etSearch);
        tvSummary = (TextView) findViewById(R.id.tvShulSummary);
        ListView list = (ListView) findViewById(R.id.listShuls);
        Button btnShowAll = (Button) findViewById(R.id.btnShowAll);

        hidden = AreaPrefs.getHidden(this);
        try {
            all = manageMode ? MinyanDataStore.listInArea(this) : MinyanDataStore.listAllNational(this);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.label_load_error, e.getMessage()), Toast.LENGTH_LONG).show();
        }
        if (!manageMode) {
            Collections.sort(all, new Comparator<MinyanDataStore.ShulInfo>() {
                @Override public int compare(MinyanDataStore.ShulInfo a, MinyanDataStore.ShulInfo b) {
                    return a.name.compareTo(b.name);
                }
            });
        }

        adapter = new ShulAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onRowClicked(shown.get(position));
            }
        });

        if (manageMode) {
            btnShowAll.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    hidden.clear();
                    AreaPrefs.clearHidden(ShulListActivity.this);
                    applyFilter();
                }
            });
        } else {
            btnShowAll.setVisibility(View.GONE);
        }

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { applyFilter(); }
        });
        applyFilter();
    }

    private void onRowClicked(MinyanDataStore.ShulInfo si) {
        if (manageMode) {
            if (!hidden.remove(si.id)) hidden.add(si.id);
            AreaPrefs.setHidden(this, hidden);
            applyFilter();
        } else {
            Intent result = new Intent();
            String label = si.address.length() > 0 ? si.address : si.name;
            result.putExtra(EXTRA_RESULT_NAME, getString(R.string.area_around, label));
            result.putExtra(EXTRA_RESULT_LAT, si.lat);
            result.putExtra(EXTRA_RESULT_LON, si.lon);
            setResult(RESULT_OK, result);
            finish();
        }
    }

    private void applyFilter() {
        String q = etSearch.getText().toString().trim().toLowerCase(Locale.getDefault());
        String[] terms = q.length() == 0 ? new String[0] : q.split("\\s+");
        shown.clear();
        for (MinyanDataStore.ShulInfo si : all) {
            String hay = (si.name + " " + si.address).toLowerCase(Locale.getDefault());
            boolean ok = true;
            for (String t : terms) {
                if (!hay.contains(t)) { ok = false; break; }
            }
            if (ok) shown.add(si);
        }
        if (manageMode) {
            int hiddenInArea = 0;
            for (MinyanDataStore.ShulInfo si : all) if (hidden.contains(si.id)) hiddenInArea++;
            tvSummary.setText(getString(R.string.shul_list_summary_manage, all.size() - hiddenInArea, all.size()));
        } else {
            tvSummary.setText(getString(R.string.shul_list_summary_pick, shown.size()));
        }
        adapter.notifyDataSetChanged();
    }

    private class ShulAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView != null ? convertView
                    : LayoutInflater.from(ShulListActivity.this).inflate(R.layout.list_item_shul, parent, false);
            MinyanDataStore.ShulInfo si = shown.get(position);
            TextView name = (TextView) row.findViewById(R.id.tvShulRowName);
            TextView info = (TextView) row.findViewById(R.id.tvShulRowInfo);
            CheckBox cb = (CheckBox) row.findViewById(R.id.cbShulShown);
            cb.setFocusable(false);
            cb.setClickable(false);
            name.setTextColor(resolveAttrColor(R.attr.colorTextMain));
            info.setTextColor(resolveAttrColor(R.attr.colorTextSecondary));
            name.setText(si.name);
            if (manageMode) {
                cb.setVisibility(View.VISIBLE);
                cb.setChecked(!hidden.contains(si.id));
                String dist = si.distanceKm < 1
                        ? getString(R.string.dist_meters, Math.round(si.distanceKm * 10) * 100)
                        : getString(R.string.dist_km, si.distanceKm);
                info.setText(si.address.length() > 0 ? si.address + " · " + dist : dist);
            } else {
                cb.setVisibility(View.GONE);
                info.setText(si.address);
            }
            return row;
        }
    }
}
