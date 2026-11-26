package com.meir.minyanimkrovim;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;

/**
 * בסיס לכל המסכים: מחיל את ערכת הנושא שנבחרה, ויוצר את המסך מחדש אם ערכת הנושא
 * השתנתה בזמן שהמסך היה ברקע (למשל: שינוי בהגדרות כשמסך הראשי/זמני היום עדיין בערימה).
 */
public abstract class BaseActivity extends Activity {

    private boolean appliedDark;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme(this);
        appliedDark = ThemeUtil.isDarkMode(this);
        super.onCreate(savedInstanceState);
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
