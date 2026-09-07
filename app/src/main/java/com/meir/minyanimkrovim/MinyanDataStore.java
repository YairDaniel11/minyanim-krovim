package com.meir.minyanimkrovim;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * פורמט הקובץ (JSON) שהאפליקציה קוראת - נוצר ע"י כלי ה-HTML הנלווה:
 * {
 *   "shuls": [
 *     {
 *       "name": "חניכי הישיבות - חברון",
 *       "nusach": "אשכנז",
 *       "prayers": {
 *         "weekday": {"shacharit": "06:30", "mincha": "13:15", "arvit": "20:00"},
 *         "shabbat": {"shacharit": "08:30", "mincha": "13:30"}
 *       }
 *     }
 *   ]
 * }
 * כל שדה תפילה הוא אופציונלי - בית כנסת יכול להצהיר רק חלק מהתפילות.
 */
public class MinyanDataStore {

    private static final String FILE_NAME = "minyanim_data.json";

    /** מעתיק את תוכן ה-Uri שנבחר (מ-ACTION_OPEN_DOCUMENT/GET_CONTENT) לאחסון הפנימי של האפליקציה. */
    public static void importFromUri(Context context, Uri uri) throws IOException {
        InputStream in = context.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("לא ניתן לפתוח את הקובץ שנבחר");
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } finally {
            in.close();
        }
        // ולידציה בסיסית - שהקובץ הוא JSON תקין עם מפתח "shuls" - לפני שמירה
        try {
            JSONObject test = new JSONObject(sb.toString());
            if (!test.has("shuls")) throw new IOException("הקובץ לא מכיל את המפתח \"shuls\"");
        } catch (JSONException e) {
            throw new IOException("הקובץ אינו JSON תקין: " + e.getMessage());
        }

        FileOutputStream out = context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE);
        try {
            out.write(sb.toString().getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    public static boolean hasData(Context context) {
        return context.getFileStreamPath(FILE_NAME).exists();
    }

    /** קורא את הקובץ השמור ומחזיר רשימה שטוחה של כל המניינים (שני סוגי הימים). */
    public static List<MinyanEntry> loadAll(Context context) throws IOException, JSONException {
        List<MinyanEntry> result = new ArrayList<>();
        if (!hasData(context)) return result;

        InputStream in = context.openFileInput(FILE_NAME);
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        } finally {
            in.close();
        }

        JSONObject root = new JSONObject(sb.toString());
        JSONArray shuls = root.optJSONArray("shuls");
        if (shuls == null) return result;

        for (int i = 0; i < shuls.length(); i++) {
            JSONObject shul = shuls.getJSONObject(i);
            String name = shul.optString("name", "");
            String nusach = shul.optString("nusach", "");
            JSONObject prayers = shul.optJSONObject("prayers");
            if (prayers == null) continue;

            addDayType(result, prayers.optJSONObject("weekday"), name, nusach, "weekday");
            addDayType(result, prayers.optJSONObject("shabbat"), name, nusach, "shabbat");
        }
        return result;
    }

    private static void addDayType(List<MinyanEntry> out, JSONObject dayObj,
                                    String name, String nusach, String dayType) {
        if (dayObj == null) return;
        addPrayerIfPresent(out, dayObj, name, nusach, dayType, "shacharit");
        addPrayerIfPresent(out, dayObj, name, nusach, dayType, "mincha");
        addPrayerIfPresent(out, dayObj, name, nusach, dayType, "arvit");
    }

    private static void addPrayerIfPresent(List<MinyanEntry> out, JSONObject dayObj,
                                            String name, String nusach, String dayType, String prayerKey) {
        String value = dayObj.optString(prayerKey, null);
        if (value == null || value.trim().length() == 0) return;
        int minutes = parseTimeToMinutes(value);
        if (minutes < 0) return;
        out.add(new MinyanEntry(name, nusach, prayerKey, dayType, minutes));
    }

    /** ממיר "HH:mm" לדקות-מחצות. מחזיר -1 אם הפורמט לא תקין. */
    public static int parseTimeToMinutes(String hhmm) {
        try {
            String[] parts = hhmm.trim().split(":");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
            return h * 60 + m;
        } catch (Exception e) {
            return -1;
        }
    }

    /** שבת = יום שבת בשבוע (Calendar.SATURDAY). כל שאר הימים = יום חול. */
    public static String currentDayType(Calendar now) {
        return now.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ? "shabbat" : "weekday";
    }

    public static int currentMinutesOfDay(Calendar now) {
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
    }

    /** מסנן לרשימת המניינים של סוג היום הנוכחי בלבד, ממוין לפי שעה. */
    public static List<MinyanEntry> filterAndSortForToday(List<MinyanEntry> all, String dayType) {
        List<MinyanEntry> today = new ArrayList<>();
        for (MinyanEntry e : all) {
            if (e.dayType.equals(dayType)) today.add(e);
        }
        Collections.sort(today, new Comparator<MinyanEntry>() {
            @Override
            public int compare(MinyanEntry a, MinyanEntry b) {
                return Integer.compare(a.minutesOfDay, b.minutesOfDay);
            }
        });
        return today;
    }

    /** המניין הבא הכי קרוב מבין אלו מסוג היום הנתון, לאחר "עכשיו" (כולל שוויון). null אם אין. */
    public static MinyanEntry findNext(List<MinyanEntry> sortedToday, int nowMinutes) {
        for (MinyanEntry e : sortedToday) {
            if (e.minutesOfDay >= nowMinutes) return e;
        }
        return null;
    }

    /** המניין האחרון שכבר התחיל (השעה הכי גבוהה שהיא <= עכשיו). null אם עדיין לא התחיל כלום. */
    public static MinyanEntry findLast(List<MinyanEntry> sortedToday, int nowMinutes) {
        MinyanEntry last = null;
        for (MinyanEntry e : sortedToday) {
            if (e.minutesOfDay <= nowMinutes) last = e;
            else break;
        }
        return last;
    }

    /** המניין הבא הכי קרוב מסוג תפילה ספציפי (לשימוש בהתראות) - עשוי לחפש גם למחר אם היום נגמר. */
    public static MinyanEntry findNextOfPrayerType(List<MinyanEntry> all, Calendar now, String prayerType) {
        String dayType = currentDayType(now);
        int nowMinutes = currentMinutesOfDay(now);
        List<MinyanEntry> today = filterAndSortForToday(all, dayType);
        for (MinyanEntry e : today) {
            if (e.prayerType.equals(prayerType) && e.minutesOfDay >= nowMinutes) return e;
        }
        // לא נמצא היום - מסתכלים על סוג היום של מחר
        Calendar tomorrow = (Calendar) now.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        String tomorrowType = currentDayType(tomorrow);
        List<MinyanEntry> tmr = filterAndSortForToday(all, tomorrowType);
        for (MinyanEntry e : tmr) {
            if (e.prayerType.equals(prayerType)) return e;
        }
        return null;
    }
}
