package com.meir.minyanimkrovim;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * הגדרות המשתמש למצב "נתונים ארציים": אזור מגורים (נקודת מרכז + רדיוס),
 * רשימת בתי כנסת להתעלמות, ומקור הנתונים (ארצי / קובץ אישי שנטען ידנית).
 */
public class AreaPrefs {

    public static final String MODE_NATIONAL = "national";
    public static final String MODE_FILE = "file";
    /** שניהם יחד: הנתונים הארציים (לפי האזור) + הקובץ האישי. */
    public static final String MODE_BOTH = "both";

    public static final float[] RADIUS_OPTIONS_KM = {0.5f, 1f, 1.5f, 2f, 3f, 5f, 10f};
    private static final float DEFAULT_RADIUS_KM = 1.5f;

    private static final String PREFS_NAME = "area_prefs";
    private static final String KEY_MODE = "mode";
    private static final String KEY_NAME = "area_name";
    private static final String KEY_LAT = "area_lat";
    private static final String KEY_LON = "area_lon";
    private static final String KEY_RADIUS = "radius_km";
    private static final String KEY_HIDDEN = "hidden_ids";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * מצב הנתונים. ברירת מחדל: ארצי; אבל למי שכבר טען קובץ אישי לפני העדכון
     * (ועוד לא בחר מצב) - נשאר על הקובץ שלו, כדי לא לשבור לו את האפליקציה.
     */
    public static String getMode(Context c) {
        String m = prefs(c).getString(KEY_MODE, null);
        if (m != null) return m;
        return MinyanDataStore.hasPersonalFile(c) ? MODE_FILE : MODE_NATIONAL;
    }

    /** האם מוצגים הנתונים הארציים (מצב "ארצי" או "שניהם"). */
    public static boolean usesNational(Context c) {
        String m = getMode(c);
        return MODE_NATIONAL.equals(m) || MODE_BOTH.equals(m);
    }

    /** האם מוצג הקובץ האישי (מצב "קובץ" או "שניהם"). */
    public static boolean usesFile(Context c) {
        String m = getMode(c);
        return MODE_FILE.equals(m) || MODE_BOTH.equals(m);
    }

    public static void setMode(Context c, String mode) {
        prefs(c).edit().putString(KEY_MODE, mode).apply();
    }

    public static boolean hasArea(Context c) {
        SharedPreferences p = prefs(c);
        return p.contains(KEY_LAT) && p.contains(KEY_LON);
    }

    public static String getAreaName(Context c) {
        return prefs(c).getString(KEY_NAME, "");
    }

    /** [lat, lon] או null אם לא נבחר אזור. */
    public static double[] getAreaCenter(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.contains(KEY_LAT) || !p.contains(KEY_LON)) return null;
        return new double[]{
                Double.longBitsToDouble(p.getLong(KEY_LAT, 0)),
                Double.longBitsToDouble(p.getLong(KEY_LON, 0))};
    }

    public static void setArea(Context c, String name, double lat, double lon) {
        prefs(c).edit()
                .putString(KEY_NAME, name)
                .putLong(KEY_LAT, Double.doubleToLongBits(lat))
                .putLong(KEY_LON, Double.doubleToLongBits(lon))
                .apply();
        // זמני היום (שקיעה/נץ) יחושבו לפי האזור שנבחר, ולא לפי נקודה ידנית ישנה
        LocationPrefs.clearManualCoords(c);
    }

    public static float getRadiusKm(Context c) {
        return prefs(c).getFloat(KEY_RADIUS, DEFAULT_RADIUS_KM);
    }

    public static void setRadiusKm(Context c, float km) {
        prefs(c).edit().putFloat(KEY_RADIUS, km).apply();
    }

    /** מזהי בתי כנסת שהמשתמש ביקש להתעלם מהם (עותק - בטוח לשינוי). */
    public static Set<String> getHidden(Context c) {
        Set<String> s = prefs(c).getStringSet(KEY_HIDDEN, null);
        return s == null ? new HashSet<String>() : new HashSet<String>(s);
    }

    public static void setHidden(Context c, Set<String> ids) {
        prefs(c).edit().putStringSet(KEY_HIDDEN, new HashSet<String>(ids)).apply();
    }

    public static void clearHidden(Context c) {
        prefs(c).edit().remove(KEY_HIDDEN).apply();
    }

    private AreaPrefs() { }
}
