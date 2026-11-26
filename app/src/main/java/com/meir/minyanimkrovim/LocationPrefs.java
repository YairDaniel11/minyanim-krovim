package com.meir.minyanimkrovim;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * קואורדינטות למסך "זמני היום". אם המשתמש הזין קואורדינטות ידנית במסך הזה -
 * הן קודמות; אחרת נופלים לקואורדינטות מקובץ המניינים שנטען (אם יש).
 * כך מסך זמני היום עובד גם למי שעדיין לא טען קובץ מניינים בכלל.
 */
public class LocationPrefs {

    private static final String PREFS_NAME = "location_prefs";
    private static final String KEY_LAT = "manual_lat";
    private static final String KEY_LON = "manual_lon";
    private static final String KEY_NAME = "manual_name";

    public static class Coords {
        public final double lat;
        public final double lon;
        public Coords(double lat, double lon) { this.lat = lat; this.lon = lon; }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static Coords getManualCoords(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.contains(KEY_LAT) || !p.contains(KEY_LON)) return null;
        return new Coords(
                Double.longBitsToDouble(p.getLong(KEY_LAT, 0)),
                Double.longBitsToDouble(p.getLong(KEY_LON, 0)));
    }

    public static void setManualCoords(Context context, double lat, double lon) {
        setManualCoords(context, lat, lon, null);
    }

    /** name = שם העיר/השכונה שנבחרו (או null אם הוזנו קואורדינטות ידנית). */
    public static void setManualCoords(Context context, double lat, double lon, String name) {
        prefs(context).edit().putString(KEY_NAME, name)
                .putLong(KEY_LAT, Double.doubleToLongBits(lat))
                .putLong(KEY_LON, Double.doubleToLongBits(lon))
                .apply();
    }

    public static void clearManualCoords(Context context) {
        prefs(context).edit().remove(KEY_LAT).remove(KEY_LON).remove(KEY_NAME).apply();
    }

    /** קואורדינטות ידניות אם הוגדרו, אחרת מקובץ המניינים שנטען, אחרת null. */
    public static Coords getEffectiveCoords(Context context) {
        Coords manual = getManualCoords(context);
        if (manual != null) return manual;
        double[] fromFile = MinyanDataStore.loadCoordinatesFromFile(context);
        if (fromFile != null) return new Coords(fromFile[0], fromFile[1]);
        return null;
    }

    /** שם המיקום הידני שנבחר מרשימה, או null. */
    public static String getManualName(Context context) {
        return prefs(context).getString(KEY_NAME, null);
    }

    /** טקסט "מיקום נוכחי" לתצוגה: שם העיר/האזור אם יש, אחרת קואורדינטות. */
    public static String statusText(Context c) {
        Coords manual = getManualCoords(c);
        if (manual != null) {
            String name = getManualName(c);
            if (name != null && name.length() > 0) return c.getString(R.string.location_status_named, name);
            return c.getString(R.string.location_status_manual,
                    String.format(java.util.Locale.US, "%.4f", manual.lat),
                    String.format(java.util.Locale.US, "%.4f", manual.lon));
        }
        if (getEffectiveCoords(c) != null) {
            if (AreaPrefs.hasArea(c)) return c.getString(R.string.location_status_named, AreaPrefs.getAreaName(c));
            return c.getString(R.string.location_status_area);
        }
        return c.getString(R.string.location_status_none);
    }
}
