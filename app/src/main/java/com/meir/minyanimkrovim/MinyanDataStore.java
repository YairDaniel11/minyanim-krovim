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
 * פורמט הקובץ (JSON) שהאפליקציה קוראת:
 * {
 *   "location": "מתחם הסופרים",           // אופציונלי - מוצג ככותרת משנה
 *   "shuls": [
 *     {
 *       "name": "חניכי הישיבות - חברון",
 *       "nusach": "אשכנז",                 // אופציונלי, יכול גם להיות null
 *       "prayers": {
 *         "weekday": {...}, "friday": {...}, "shabbat": {...}   // כל אחד אופציונלי
 *       }
 *     }
 *   ]
 * }
 * כל אחד מ-shacharit/mincha/arvit בתוך אובייקט יום יכול להיות:
 *  - מחרוזת "HH:mm" (שעה קבועה - נכנסת לרשימה).
 *  - מחרוזת תיאור הלכתי בלי שעה (למשל "בזמן"/"בשקיעה") - מדולגת, האפליקציה
 *    לא מחשבת זמנים הלכתיים (ראו README).
 *  - מערך של שניים, אחד מהם "HH:mm" והשני תיאור הלכתי - נכנס כרשומה אחת עם
 *    השעה מהמערך והתיאור כהערה נלווית (למשל "08:00 (נץ החמה)").
 *  - מערך עם יותר מ"שעה" תקינה אחת (למשל כמה זמני ערבית) - כל שעה תקינה
 *    הופכת לרשומה נפרדת; איברים בלי שעה מדולגים.
 */
public class MinyanDataStore {

    private static final String FILE_NAME = "minyanim_data.json";

    /** מעתיק את תוכן ה-Uri שנבחר (מ-ACTION_OPEN_DOCUMENT/GET_CONTENT/דפדפן קבצים) לאחסון הפנימי. */
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

    private static JSONObject readRootJson(Context context) throws IOException, JSONException {
        InputStream in = context.openFileInput(FILE_NAME);
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        } finally {
            in.close();
        }
        return new JSONObject(sb.toString());
    }

    /** שם המתחם/האזור מהקובץ שנטען (מפתח "location") - "" אם לא הוגדר או שלא נטען קובץ. */
    public static String loadLocation(Context context) {
        if (!hasData(context)) return "";
        try {
            return readRootJson(context).optString("location", "");
        } catch (Exception e) {
            return "";
        }
    }

    /** קורא את הקובץ השמור ומחזיר רשימה שטוחה של כל המניינים (כל סוגי הימים). */
    public static List<MinyanEntry> loadAll(Context context) throws IOException, JSONException {
        List<MinyanEntry> result = new ArrayList<>();
        if (!hasData(context)) return result;

        JSONObject root = readRootJson(context);
        JSONArray shuls = root.optJSONArray("shuls");
        if (shuls == null) return result;

        for (int i = 0; i < shuls.length(); i++) {
            JSONObject shul = shuls.getJSONObject(i);
            String name = shul.optString("name", "");
            String nusach = shul.isNull("nusach") ? "" : shul.optString("nusach", "");
            JSONObject prayers = shul.optJSONObject("prayers");
            if (prayers == null) continue;

            addDayType(result, prayers.optJSONObject("weekday"), name, nusach, "weekday");
            addDayType(result, prayers.optJSONObject("friday"), name, nusach, "friday");
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
        if (!dayObj.has(prayerKey) || dayObj.isNull(prayerKey)) return;
        Object raw = dayObj.opt(prayerKey);

        if (raw instanceof JSONArray) {
            JSONArray arr = (JSONArray) raw;
            List<String> items = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "").trim();
                if (s.length() > 0) items.add(s);
            }
            // מערך של בדיוק שני איברים, אחד מהם שעה תקינה והשני לא - מתפרש
            // כ"שעה + תיאור הלכתי נלווה" ונכנס כרשומה אחת (בכל סדר בין השניים).
            if (items.size() == 2) {
                int m0 = parseTimeToMinutes(items.get(0));
                int m1 = parseTimeToMinutes(items.get(1));
                if (m0 >= 0 && m1 < 0) {
                    out.add(new MinyanEntry(name, nusach, prayerKey, dayType, m0, items.get(1)));
                    return;
                }
                if (m1 >= 0 && m0 < 0) {
                    out.add(new MinyanEntry(name, nusach, prayerKey, dayType, m1, items.get(0)));
                    return;
                }
            }
            // בכל מקרה אחר - כל איבר שהוא שעה תקינה הופך לרשומה נפרדת (כמה
            // מניינים מאותו סוג תפילה); איברים שהם תיאור הלכתי בלבד (בלי
            // שעה) מדולגים - האפליקציה תומכת רק בשעות קבועות (ראו README).
            for (String s : items) {
                int minutes = parseTimeToMinutes(s);
                if (minutes >= 0) out.add(new MinyanEntry(name, nusach, prayerKey, dayType, minutes, null));
            }
            return;
        }

        String value = dayObj.optString(prayerKey, "").trim();
        if (value.length() == 0) return;
        int minutes = parseTimeToMinutes(value);
        if (minutes >= 0) out.add(new MinyanEntry(name, nusach, prayerKey, dayType, minutes, null));
    }

    /** ממיר "HH:mm" לדקות-מחצות. מחזיר -1 אם הפורמט לא תקין (כולל תיאורים הלכתיים). */
    public static int parseTimeToMinutes(String hhmm) {
        try {
            String[] parts = hhmm.trim().split(":");
            if (parts.length != 2) return -1;
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
            return h * 60 + m;
        } catch (Exception e) {
            return -1;
        }
    }

    /** סוג היום לפי יום בשבוע נתון (Calendar.SUNDAY..SATURDAY) - לא בהכרח "היום" בפועל. */
    public static String dayTypeForDayOfWeek(int calendarDayOfWeek) {
        if (calendarDayOfWeek == Calendar.SATURDAY) return "shabbat";
        if (calendarDayOfWeek == Calendar.FRIDAY) return "friday";
        return "weekday";
    }

    /** שבת/שישי/חול לפי יום השבוע הנוכחי בפועל. */
    public static String currentDayType(Calendar now) {
        return dayTypeForDayOfWeek(now.get(Calendar.DAY_OF_WEEK));
    }

    public static int currentMinutesOfDay(Calendar now) {
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
    }

    /** מסנן לרשימת המניינים של סוג היום הנתון בלבד, ממוין לפי שעה. */
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
