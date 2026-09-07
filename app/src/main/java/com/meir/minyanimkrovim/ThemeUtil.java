package com.meir.minyanimkrovim;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

/** יישום ערכת נושא בלי AndroidX/AppCompat - שיטה גולמית שנתמכת מ-API 1. */
public class ThemeUtil {

    public static final String MODE_SYSTEM = "system";
    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";

    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_MODE = "theme_mode";

    public static void applyTheme(Activity activity) {
        activity.setTheme(isDarkMode(activity) ? R.style.AppTheme_Dark : R.style.AppTheme_Light);
    }

    public static String getMode(Context context) {
        return prefs(context).getString(KEY_MODE, MODE_SYSTEM);
    }

    public static void setMode(Context context, String mode) {
        prefs(context).edit().putString(KEY_MODE, mode).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static boolean isDarkMode(Context context) {
        String mode = getMode(context);
        if (MODE_LIGHT.equals(mode)) return false;
        if (MODE_DARK.equals(mode)) return true;
        return isSystemDarkMode(context);
    }

    private static boolean isSystemDarkMode(Context context) {
        // Configuration.UI_MODE_NIGHT_MASK זמין מ-API 8, אבל למכשיר ישן שאין בו
        // מושג Dark Mode ברמת מערכת (לפני API 29) הדגל פשוט לא יוגדר -
        // ברירת המחדל ההגיונית לענף הזה: להישאר על ערכת הנושא הכהה.
        int nightModeFlags = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
        }
        // מתחת ל-Android 10: אין הבחנה אמינה - ברירת מחדל היסטורית: כהה
        return true;
    }
}
