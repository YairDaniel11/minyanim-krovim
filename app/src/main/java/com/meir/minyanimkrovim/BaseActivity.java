package com.meir.minyanimkrovim;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * בסיס לכל המסכים: מחיל את ערכת הנושא שנבחרה, ויוצר את המסך מחדש אם ערכת הנושא
 * השתנתה בזמן שהמסך היה ברקע. שורת הכותרת מצוירת כאן בצבעי האפליקציה, כי ה-ActionBar של
 * ROM-ים ישנים (Android 4.4) מתעלם מהעיצוב.
 */
public abstract class BaseActivity extends Activity {

    private boolean appliedDark;
    private TextView headerTitle;
    private TextView headerAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme(this);
        appliedDark = ThemeUtil.isDarkMode(this);
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
    }

    @Override
    public void setContentView(int layoutResID) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(resolveAttrColor(R.attr.colorBgMain));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(resolveAttrColor(R.attr.m3Primary));
        int p = Dialogs.dp(this, 12);
        header.setPadding(p, 0, p, 0);

        headerTitle = new TextView(this);
        headerTitle.setTextColor(resolveAttrColor(R.attr.m3OnPrimary));
        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        headerTitle.setTypeface(headerTitle.getTypeface(), android.graphics.Typeface.BOLD);
        headerTitle.setSingleLine(true);
        headerTitle.setText(getTitle());
        header.addView(headerTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        headerAction = new TextView(this);
        headerAction.setTextColor(resolveAttrColor(R.attr.m3OnPrimary));
        headerAction.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        headerAction.setGravity(Gravity.CENTER);
        headerAction.setFocusable(true);
        headerAction.setVisibility(View.GONE);
        int a = Dialogs.dp(this, 10);
        headerAction.setPadding(a, 0, a, 0);
        header.addView(headerAction, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Dialogs.dp(this, 48)));

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        getLayoutInflater().inflate(layoutResID, content, true);
        super.setContentView(root);
    }

    @Override
    protected void onTitleChanged(CharSequence title, int color) {
        super.onTitleChanged(title, color);
        if (headerTitle != null) headerTitle.setText(title);
    }

    /** כפתור בצד שורת הכותרת (למשל "⋮" לתפריט). */
    protected void setHeaderAction(String text, final Runnable action) {
        headerAction.setText(text);
        headerAction.setVisibility(View.VISIBLE);
        headerAction.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        headerAction.setBackgroundResource(R.drawable.list_selector_file);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ThemeUtil.isDarkMode(this) != appliedDark) {
            recreate();
        }
    }

    protected int resolveAttrColor(int attrResId) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attrResId, tv, true);
        return tv.data;
    }

    /** מזהה ה-drawable שערכת הנושא הנוכחית מגדירה עבור attr מסוג reference (bgFilled/bgTonal/bgCard). */
    protected int resolveAttrDrawable(int attrResId) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attrResId, tv, true);
        return tv.resourceId;
    }
}
