package com.meir.minyanimkrovim;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

/**
 * דיאלוגים שמצוירים בקוד בצבעי ערכת הנושא של האפליקציה. הדיאלוגים של המערכת (AlertDialog)
 * ב-Android 4.4 מתעלמים מהעיצוב שלנו ונשארים אפורים, לכן לא משתמשים בהם.
 */
public final class Dialogs {

    private Dialogs() { }

    public interface OnChoose { void onChoose(int index); }

    static int dp(Context c, int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static int color(Context c, int attr) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    static int drawableRes(Context c, int attr) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(attr, tv, true);
        return tv.resourceId;
    }

    /** כפתור בסגנון הערכה: ממולא (ראשי) או טונאלי. */
    static Button button(Context c, CharSequence text, boolean filled) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setBackgroundResource(drawableRes(c, filled ? R.attr.bgFilled : R.attr.bgTonal));
        b.setTextColor(color(c, filled ? R.attr.m3OnPrimary : R.attr.m3OnSecondaryContainer));
        return b;
    }

    private static Dialog base(Context c, CharSequence title, LinearLayout box) {
        final Dialog d = new Dialog(c);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color(c, R.attr.colorBgCard));
        bg.setCornerRadius(dp(c, 20));
        bg.setStroke(dp(c, 1), color(c, R.attr.m3Outline));
        box.setBackgroundDrawable(bg);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 16);
        box.setPadding(p, p, p, p);
        if (title != null) {
            TextView t = new TextView(c);
            t.setText(title);
            t.setTextColor(color(c, R.attr.m3Primary));
            t.setTextSize(17);
            t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
            t.setPadding(0, 0, 0, dp(c, 10));
            box.addView(t);
        }
        d.setContentView(box);
        d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        d.getWindow().setLayout((int) (c.getResources().getDisplayMetrics().widthPixels * 0.92f),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        return d;
    }

    /** רשימת בחירה (עובדת עם מקשי החיצים ו-OK). */
    public static void list(final Context c, CharSequence title, CharSequence[] items, final OnChoose cb) {
        LinearLayout box = new LinearLayout(c);
        final Dialog d = base(c, title, box);
        final int textColor = color(c, R.attr.colorTextMain);
        final int pad = dp(c, 12);
        ListView lv = new ListView(c);
        lv.setAdapter(new ArrayAdapter<CharSequence>(c, 0, items) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = convertView instanceof TextView ? (TextView) convertView : new TextView(c);
                tv.setText(getItem(position));
                tv.setTextColor(textColor);
                tv.setTextSize(16);
                tv.setPadding(pad, pad, pad, pad);
                tv.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                return tv;
            }
        });
        lv.setSelector(R.drawable.list_selector_file);
        lv.setDivider(null);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                d.dismiss();
                cb.onChoose(pos);
            }
        });
        box.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        d.show();
    }

    /** אישור/ביטול. */
    public static void confirm(Context c, CharSequence title, CharSequence yes, CharSequence no,
                               final Runnable onYes) {
        LinearLayout box = new LinearLayout(c);
        final Dialog d = base(c, title, box);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button bYes = button(c, yes, true);
        Button bNo = button(c, no, false);
        bYes.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); if (onYes != null) onYes.run(); }
        });
        bNo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(dp(c, 4), 0, dp(c, 4), 0);
        row.addView(bYes, lp);
        row.addView(bNo, lp);
        box.addView(row);
        d.show();
    }
}
