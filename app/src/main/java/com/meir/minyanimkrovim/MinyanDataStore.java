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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * פורמט הקובץ (JSON) שהאפליקציה קוראת:
 * {
 *   "location": "השכונה שלכם",                 // אופציונלי - מוצג ככותרת משנה
 *   "coordinates": { "lat": 31.9, "lon": 34.8 }, // אופציונלי - נדרש רק אם יש זמנים יחסיים לשקיעה/נץ
 *   "shuls": [
 *     {
 *       "name": "חניכי הישיבות - חברון",
 *       "nusach": "אשכנז",
 *       "prayers": {
 *         "weekday": {...}, "friday": {...}, "shabbat": {...}
 *       }
 *     }
 *   ]
 * }
 * כל אחד מ-shacharit/mincha/arvit בתוך אובייקט יום יכול להיות:
 *  - מחרוזת "HH:mm" (שעה קבועה).
 *  - מחרוזת זמן יחסי לשקיעה/נץ: "שקיעה" / "שקיעה-20" / "שקיעה+10" /
 *    "נץ+30" וכו' (גם באנגלית: shkia/sunset, netz/sunrise). מחושב מחדש
 *    בכל רענון לפי התאריך של היום ("coordinates" נדרש לחישוב הזה).
 *  - מערך של שניים, אחד מהם זמן תקין (קבוע או יחסי) והשני תיאור הלכתי
 *    חופשי - נכנס כרשומה אחת עם הזמן והתיאור כהערה נלווית.
 *  - מערך עם כמה זמנים תקינים - כל אחד הופך לרשומה נפרדת.
 */
public class MinyanDataStore {

    private static final String FILE_NAME = "minyanim_data.json";

    // "שקיעה"/"שקיעה-20"/"shkia+10" וכו' - קבוצה 1: שם הטוקן, קבוצה 2: +/- מספר (אופציונלי)
    private static final Pattern SUN_TOKEN = Pattern.compile(
            "^(שקיעה|נץ|הנץ|shkia|sunset|netz|sunrise)\\s*([+-]\\s*\\d+)?$",
            Pattern.CASE_INSENSITIVE);

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

    public static String loadLocation(Context context) {
        if (!hasData(context)) return "";
        try {
            return readRootJson(context).optString("location", "");
        } catch (Exception e) {
            return "";
        }
    }

    /** קואורדינטות מהקובץ שנטען (אם יש) - null אם אין קובץ/אין שדה coordinates. */
    public static double[] loadCoordinatesFromFile(Context context) {
        if (!hasData(context)) return null;
        try {
            JSONObject coords = readRootJson(context).optJSONObject("coordinates");
            if (coords == null) return null;
            double lat = coords.optDouble("lat", Double.NaN);
            double lon = coords.optDouble("lon", Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) return null;
            return new double[]{lat, lon};
        } catch (Exception e) {
            return null;
        }
    }

    /** קורא את הקובץ השמור ומחזיר רשימה שטוחה של כל המניינים, עם זמני שקיעה/נץ מחושבים ליום הנוכחי. */
    public static List<MinyanEntry> loadAll(Context context) throws IOException, JSONException {
        return loadAll(context, Calendar.getInstance());
    }

    /** גרסה שמקבלת תאריך ייחוס לחישוב שקיעה/נץ - שימושי לבדיקות ולתצוגת ימים אחרים. */
    public static List<MinyanEntry> loadAll(Context context, Calendar referenceDate) throws IOException, JSONException {
        List<MinyanEntry> result = new ArrayList<>();
        if (!hasData(context)) return result;

        JSONObject root = readRootJson(context);
        JSONArray shuls = root.optJSONArray("shuls");
        if (shuls == null) return result;

        SunTimes.Result sun = computeSunTimes(root, referenceDate);

        for (int i = 0; i < shuls.length(); i++) {
            JSONObject shul = shuls.getJSONObject(i);
            String name = shul.optString("name", "");
            String nusach = shul.isNull("nusach") ? "" : shul.optString("nusach", "");
            JSONObject prayers = shul.optJSONObject("prayers");
            if (prayers == null) continue;

            addDayType(result, prayers.optJSONObject("weekday"), name, nusach, "weekday", sun);
            addDayType(result, prayers.optJSONObject("friday"), name, nusach, "friday", sun);
            addDayType(result, prayers.optJSONObject("shabbat"), name, nusach, "shabbat", sun);
        }
        return result;
    }

    /** true אם יש בקובץ לפחות זמן יחסי אחד לשקיעה/נץ אך אין קואורדינטות - כדי להציג אזהרה למשתמש. */
    public static boolean needsCoordinatesButMissing(Context context) {
        try {
            JSONObject root = readRootJson(context);
            if (root.optJSONObject("coordinates") != null) return false;
            JSONArray shuls = root.optJSONArray("shuls");
            if (shuls == null) return false;
            for (int i = 0; i < shuls.length(); i++) {
                JSONObject prayers = shuls.getJSONObject(i).optJSONObject("prayers");
                if (prayers == null) continue;
                for (String dayKey : new String[]{"weekday", "friday", "shabbat"}) {
                    JSONObject dayObj = prayers.optJSONObject(dayKey);
                    if (dayObj == null) continue;
                    for (String prayerKey : new String[]{"shacharit", "mincha", "arvit"}) {
                        if (containsSunToken(dayObj.opt(prayerKey))) return true;
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private static boolean containsSunToken(Object raw) {
        if (raw == null) return false;
        if (raw instanceof JSONArray) {
            JSONArray arr = (JSONArray) raw;
            for (int i = 0; i < arr.length(); i++) {
                if (SUN_TOKEN.matcher(arr.optString(i, "").trim()).matches()) return true;
            }
            return false;
        }
        return SUN_TOKEN.matcher(String.valueOf(raw).trim()).matches();
    }

    private static SunTimes.Result computeSunTimes(JSONObject root, Calendar referenceDate) {
        JSONObject coords = root.optJSONObject("coordinates");
        if (coords == null) return null;
        double lat = coords.optDouble("lat", Double.NaN);
        double lon = coords.optDouble("lon", Double.NaN);
        if (Double.isNaN(lat) || Double.isNaN(lon)) return null;
        return SunTimes.compute(lat, lon, referenceDate);
    }

    private static void addDayType(List<MinyanEntry> out, JSONObject dayObj, String name,
                                    String nusach, String dayType, SunTimes.Result sun) {
        if (dayObj == null) return;
        addPrayerIfPresent(out, dayObj, name, nusach, dayType, "shacharit", sun);
        addPrayerIfPresent(out, dayObj, name, nusach, dayType, "mincha", sun);
        addPrayerIfPresent(out, dayObj, name, nusach, dayType, "arvit", sun);
    }

    private static void addPrayerIfPresent(List<MinyanEntry> out, JSONObject dayObj, String name,
                                            String nusach, String dayType, String prayerKey,
                                            SunTimes.Result sun) {
        if (!dayObj.has(prayerKey) || dayObj.isNull(prayerKey)) return;
        Object raw = dayObj.opt(prayerKey);

        if (raw instanceof JSONArray) {
            JSONArray arr = (JSONArray) raw;
            List<String> items = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "").trim();
                if (s.length() > 0) items.add(s);
            }
            if (items.size() == 2) {
                TimeResolution r0 = resolveTime(items.get(0), sun);
                TimeResolution r1 = resolveTime(items.get(1), sun);
                if (r0.minutes >= 0 && r1.minutes < 0) {
                    out.add(new MinyanEntry(name, nusach, prayerKey, dayType, r0.minutes,
                            firstNonEmpty(items.get(1), r0.autoNote)));
                    return;
                }
                if (r1.minutes >= 0 && r0.minutes < 0) {
                    out.add(new MinyanEntry(name, nusach, prayerKey, dayType, r1.minutes,
                            firstNonEmpty(items.get(0), r1.autoNote)));
                    return;
                }
            }
            for (String s : items) {
                TimeResolution r = resolveTime(s, sun);
                if (r.minutes >= 0) out.add(new MinyanEntry(name, nusach, prayerKey, dayType, r.minutes, r.autoNote));
            }
            return;
        }

        String value = dayObj.optString(prayerKey, "").trim();
        if (value.length() == 0) return;
        TimeResolution r = resolveTime(value, sun);
        if (r.minutes >= 0) out.add(new MinyanEntry(name, nusach, prayerKey, dayType, r.minutes, r.autoNote));
    }

    private static String firstNonEmpty(String preferred, String fallback) {
        if (preferred != null && preferred.trim().length() > 0) return preferred.trim();
        return fallback;
    }

    private static class TimeResolution {
        final int minutes;   // -1 אם לא ניתן לפענח/לחשב
        final String autoNote; // תיאור אוטומטי (רק לזמנים יחסיים לשקיעה/נץ), אפשר null

        TimeResolution(int minutes, String autoNote) {
            this.minutes = minutes;
            this.autoNote = autoNote;
        }
    }

    /** מפענח ערך זמן בודד - שעה קבועה "HH:mm" או ביטוי יחסי לשקיעה/נץ. */
    private static TimeResolution resolveTime(String raw, SunTimes.Result sun) {
        int fixed = parseTimeToMinutes(raw);
        if (fixed >= 0) return new TimeResolution(fixed, null);

        Matcher m = SUN_TOKEN.matcher(raw.trim());
        if (!m.matches()) return new TimeResolution(-1, null);
        if (sun == null || !sun.valid) return new TimeResolution(-1, null);

        String token = m.group(1).toLowerCase(java.util.Locale.US);
        boolean isSunset = token.startsWith("שקיע") || token.equals("shkia") || token.equals("sunset");
        int base = isSunset ? sun.sunsetMinutes : sun.sunriseMinutes;

        int offset = 0;
        String offsetGroup = m.group(2);
        if (offsetGroup != null) {
            offsetGroup = offsetGroup.replaceAll("\\s+", "");
            offset = Integer.parseInt(offsetGroup); // כולל הסימן (+/-)
        }

        int minutes = ((base + offset) % 1440 + 1440) % 1440;
        String baseLabel = isSunset ? "שקיעה" : "נץ";
        String note;
        if (offset == 0) {
            note = baseLabel;
        } else if (offset > 0) {
            note = offset + " דק' אחרי " + baseLabel;
        } else {
            note = (-offset) + " דק' לפני " + baseLabel;
        }
        return new TimeResolution(minutes, note);
    }

    /** ממיר "HH:mm" לדקות-מחצות. מחזיר -1 אם הפורמט לא תקין (כולל ביטויים יחסיים/תיאורים הלכתיים). */
    public static int parseTimeToMinutes(String hhmm) {
        try {
            String[] parts = hhmm.trim().split(":");
            if (parts.length != 2) return -1;
            int h = Integer.parseInt(parts[0].trim());
            int m = Integer.parseInt(parts[1].trim());
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

    public static MinyanEntry findNext(List<MinyanEntry> sortedToday, int nowMinutes) {
        for (MinyanEntry e : sortedToday) {
            if (e.minutesOfDay >= nowMinutes) return e;
        }
        return null;
    }

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
