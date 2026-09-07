package com.meir.minyanimkrovim;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

/** יישום ערכת נושא בלי AndroidX/AppCompat - שיטה גולמית שנתמכת מ-API 1. */
public class ThemeUtil {

    public static void applyTheme(Activity activity) {
        activity.setTheme(isDarkMode(activity) ? R.style.AppTheme_Dark : R.style.AppTheme_Light);
    }

    private static boolean isDarkMode(Context context) {
        // Configuration.UI_MODE_NIGHT_MASK זמין מ-API 8, אבל למכשיר ישן שאין בו
        // מושג Dark Mode ברמת מערכת (לפני API 29) הדגל פשוט לא יוגדר -
        // ברירת המחדל ההגיונית לענף הזה: להישאר על ערכת הנושא הכהה.
        int nightModeFlags = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
        }
        // מתחת ל-Android 10: אין הבחנה אמינה - ברירת מחדל להיסטורית: כהה
        return true;
    }
}
