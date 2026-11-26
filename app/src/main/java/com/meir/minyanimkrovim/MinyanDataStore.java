package com.meir.minyanimkrovim;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 *  - בתוך מערך אפשר גם אובייקט {"t":"HH:mm","n":"הערה"} - שעה עם הערה משלה.
 *
 * מצב "נתונים ארציים" (ברירת המחדל): קובץ אחד לכל הארץ (assets/minyanim-israel.json,
 * או גרסה חדשה יותר שהורדה מ-GitHub), שבו לכל בית כנסת יש גם id, address, lat, lon.
 * מהקובץ נבחרים רק בתי הכנסת שבאזור שהמשתמש בחר (AreaPrefs), בלי אלה שהוא סימן להתעלמות.
 */
public class MinyanDataStore {

    private static final String FILE_NAME = "minyanim_data.json";
    /** גרסה ארצית שהורדה מ-GitHub (אם חדשה יותר מזו שבתוך האפליקציה). */
    static final String NATIONAL_FILE = "minyanim_national.json";
    /** גרסה ארצית שמוטמעת באפליקציה בזמן הבנייה. */
    static final String NATIONAL_ASSET = "minyanim-israel.json";

    private static JSONObject cachedNational;
    private static String cachedNationalKey;

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
        // טעינת קובץ אישי = המשתמש רוצה להשתמש בו (אפשר לחזור לנתונים הארציים בהגדרות)
        AreaPrefs.setMode(context, AreaPrefs.MODE_FILE);
    }

    /** האם נטען אי-פעם קובץ אישי (minyanim_data.json). */
    public static boolean hasPersonalFile(Context context) {
        return context.getFileStreamPath(FILE_NAME).exists();
    }

    /** יש מה להציג: נתונים ארציים תמיד (מוטמעים באפליקציה); קובץ אישי - רק אם נטען. */
    public static boolean hasData(Context context) {
        return AreaPrefs.usesNational(context) || (AreaPrefs.usesFile(context) && hasPersonalFile(context));
    }

    private static String readAll(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"), 16384);
            char[] buf = new char[16384];
            int n;
            while ((n = reader.read(buf)) > 0) sb.append(buf, 0, n);
        } finally {
            in.close();
        }
        return sb.toString();
    }

    private static JSONObject readPersonalRoot(Context context) throws IOException, JSONException {
        return new JSONObject(readAll(context.openFileInput(FILE_NAME)));
    }

    // ------------------------------------------------------------------ נתונים ארציים

    private static final Pattern GENERATED = Pattern.compile("\"generated\"\\s*:\\s*\"([^\"]+)\"");

    /** "generated" (תאריך YYYY-MM-DD) מתוך תחילת קובץ ארצי - המפתח נכתב ראשון בקובץ, אז מספיק לקרוא כמה בתים. */
    static String readGenerated(InputStream in) {
        try {
            byte[] head = new byte[300];
            int total = 0, n;
            while (total < head.length && (n = in.read(head, total, head.length - total)) > 0) total += n;
            Matcher m = GENERATED.matcher(new String(head, 0, total, "UTF-8"));
            return m.find() ? m.group(1) : "";
        } catch (Exception e) {
            return "";
        } finally {
            try { in.close(); } catch (IOException ignored) { }
        }
    }

    /** תאריך הנתונים הארציים שבתוך האפליקציה ("" אם חסר). */
    public static String embeddedGenerated(Context context) {
        try {
            return readGenerated(context.getAssets().open(NATIONAL_ASSET));
        } catch (IOException e) {
            return "";
        }
    }

    /** תאריך הנתונים שהורדו ("" אם לא הורדו). */
    public static String downloadedGenerated(Context context) {
        File f = context.getFileStreamPath(NATIONAL_FILE);
        if (!f.exists()) return "";
        try {
            return readGenerated(new java.io.FileInputStream(f));
        } catch (IOException e) {
            return "";
        }
    }

    /** תאריך הנתונים הארציים שבשימוש בפועל (החדש מבין המוטמע וההורדה). */
    public static String nationalGenerated(Context context) {
        String a = embeddedGenerated(context);
        String d = downloadedGenerated(context);
        return d.compareTo(a) > 0 ? d : a;
    }

    private static synchronized JSONObject readNationalRoot(Context context) throws IOException, JSONException {
        String dl = downloadedGenerated(context);
        boolean useDownloaded = dl.length() > 0 && dl.compareTo(embeddedGenerated(context)) > 0;
        File f = context.getFileStreamPath(NATIONAL_FILE);
        String key = useDownloaded ? "dl:" + f.lastModified() + ":" + f.length() : "asset";
        if (cachedNational != null && key.equals(cachedNationalKey)) return cachedNational;
        InputStream in = useDownloaded ? new java.io.FileInputStream(f) : context.getAssets().open(NATIONAL_ASSET);
        JSONObject root = new JSONObject(readAll(in));
        cachedNational = root;
        cachedNationalKey = key;
        return root;
    }

    /** בודק קובץ שהורד (טקסט JSON) ושומר אותו אם הוא תקין וחדש יותר ממה שיש. מחזיר true אם נשמר. */
    public static synchronized boolean saveDownloadedNational(Context context, String json) throws IOException {
        JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (JSONException e) {
            throw new IOException("הקובץ שהורד אינו JSON תקין");
        }
        JSONArray shuls = root.optJSONArray("shuls");
        String gen = root.optString("generated", "");
        if (shuls == null || shuls.length() < 100 || gen.length() == 0) {
            throw new IOException("הקובץ שהורד אינו קובץ נתונים תקין");
        }
        if (gen.compareTo(nationalGenerated(context)) <= 0) return false; // כבר מעודכנים
        FileOutputStream out = context.openFileOutput(NATIONAL_FILE, Context.MODE_PRIVATE);
        try {
            out.write(json.getBytes("UTF-8"));
        } finally {
            out.close();
        }
        return true;
    }

    // ------------------------------------------------------------------ בתי כנסת באזור

    /** בית כנסת מהנתונים הארציים - לרשימות הבחירה. */
    public static class ShulInfo {
        public final String id, name, address;
        public final double lat, lon;
        public double distanceKm;
        ShulInfo(String id, String name, String address, double lat, double lon) {
            this.id = id; this.name = name; this.address = address; this.lat = lat; this.lon = lon;
        }
    }

    /** מרחק (ק"מ) בין שתי נקודות - האברסין. */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static ShulInfo toInfo(JSONObject shul) {
        if (!shul.has("lat") || !shul.has("lon")) return null;
        return new ShulInfo(shul.optString("id", ""), shul.optString("name", ""),
                shul.optString("address", ""), shul.optDouble("lat"), shul.optDouble("lon"));
    }

    /** כל בתי הכנסת בנתונים הארציים (לחיפוש בחירת מרכז אזור). */
    public static List<ShulInfo> listAllNational(Context context) throws IOException, JSONException {
        List<ShulInfo> out = new ArrayList<>();
        JSONArray shuls = readNationalRoot(context).optJSONArray("shuls");
        if (shuls == null) return out;
        for (int i = 0; i < shuls.length(); i++) {
            ShulInfo si = toInfo(shuls.getJSONObject(i));
            if (si != null) out.add(si);
        }
        return out;
    }

    /** בתי הכנסת שברדיוס מהאזור שנבחר (כולל אלה שסומנו להתעלמות), ממוינים לפי מרחק. */
    public static List<ShulInfo> listInArea(Context context) throws IOException, JSONException {
        List<ShulInfo> out = new ArrayList<>();
        double[] c = AreaPrefs.getAreaCenter(context);
        if (c == null) return out;
        double radius = AreaPrefs.getRadiusKm(context);
        for (ShulInfo si : listAllNational(context)) {
            si.distanceKm = distanceKm(c[0], c[1], si.lat, si.lon);
            if (si.distanceKm <= radius) out.add(si);
        }
        Collections.sort(out, new Comparator<ShulInfo>() {
            @Override public int compare(ShulInfo a, ShulInfo b) { return Double.compare(a.distanceKm, b.distanceKm); }
        });
        return out;
    }

    /** אין שום דבר להציג כי לא נבחר אזור (ואין קובץ אישי פעיל) - צריך להציג למשתמש הנחיה לבחור אזור. */
    public static boolean needsAreaSelection(Context context) {
        boolean personalActive = AreaPrefs.usesFile(context) && hasPersonalFile(context);
        return AreaPrefs.usesNational(context) && !AreaPrefs.hasArea(context) && !personalActive;
    }

    /** האם בית הכנסת הזה (במצב ארצי) באזור הנבחר ולא ברשימת ההתעלמות. */
    private static boolean isShulVisible(JSONObject shul, double[] center, double radiusKm, Set<String> hidden) {
        if (hidden.contains(shul.optString("id", ""))) return false;
        if (!shul.has("lat") || !shul.has("lon")) return false;
        return distanceKm(center[0], center[1], shul.optDouble("lat"), shul.optDouble("lon")) <= radiusKm;
    }

    public static String loadLocation(Context context) {
        if (AreaPrefs.usesNational(context) && AreaPrefs.hasArea(context)) {
            float r = AreaPrefs.getRadiusKm(context);
            String km = (r == Math.floor(r)) ? String.valueOf((int) r) : String.valueOf(r);
            return AreaPrefs.getAreaName(context) + " (" + km + " ק\"מ)";
        }
        if (AreaPrefs.usesFile(context) && hasPersonalFile(context)) {
            try {
                return readPersonalRoot(context).optString("location", "");
            } catch (Exception e) {
                return "";
            }
        }
        return "";
    }

    /** קואורדינטות מהמקור הפעיל: מרכז האזור (נתונים ארציים), או coordinates מהקובץ האישי. null אם אין. */
    public static double[] loadCoordinatesFromFile(Context context) {
        if (AreaPrefs.usesNational(context) && AreaPrefs.hasArea(context)) return AreaPrefs.getAreaCenter(context);
        if (!AreaPrefs.usesFile(context) || !hasPersonalFile(context)) return null;
        try {
            JSONObject coords = readPersonalRoot(context).optJSONObject("coordinates");
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

    /**
     * גרסה שמקבלת תאריך ייחוס לחישוב שקיעה/נץ. לפי מקור הנתונים שנבחר: הנתונים הארציים (רק בתי
     * הכנסת שבאזור ולא ברשימת ההתעלמות), הקובץ האישי, או שניהם יחד.
     */
    public static List<MinyanEntry> loadAll(Context context, Calendar referenceDate) throws IOException, JSONException {
        List<MinyanEntry> result = new ArrayList<>();

        if (AreaPrefs.usesFile(context) && hasPersonalFile(context)) {
            JSONObject root = readPersonalRoot(context);
            // בקובץ אישי זמנים יחסיים (שקיעה/נץ) מחושבים לפי coordinates שבו
            appendShuls(result, root, computeSunTimes(root, referenceDate), null, 0, null);
        }

        double[] center = AreaPrefs.getAreaCenter(context);
        if (AreaPrefs.usesNational(context) && center != null) {
            // בנתונים הארציים כל השעות קבועות - שקיעה/נץ לא נחוצים
            appendShuls(result, readNationalRoot(context), null, center,
                    AreaPrefs.getRadiusKm(context), AreaPrefs.getHidden(context));
        }
        return result;
    }

    /** מוסיף את בתי הכנסת מ-root. center != null = סינון לפי רדיוס והתעלמות (נתונים ארציים). */
    private static void appendShuls(List<MinyanEntry> out, JSONObject root, SunTimes.Result sun,
                                    double[] center, double radiusKm, Set<String> hidden) throws JSONException {
        JSONArray shuls = root.optJSONArray("shuls");
        if (shuls == null) return;
        for (int i = 0; i < shuls.length(); i++) {
            JSONObject shul = shuls.getJSONObject(i);
            if (center != null && !isShulVisible(shul, center, radiusKm, hidden)) continue;
            String name = shul.optString("name", "");
            String nusach = shul.isNull("nusach") ? "" : shul.optString("nusach", "");
            JSONObject prayers = shul.optJSONObject("prayers");
            if (prayers == null) continue;

            addDayType(out, prayers.optJSONObject("weekday"), name, nusach, "weekday", sun);
            addDayType(out, prayers.optJSONObject("friday"), name, nusach, "friday", sun);
            addDayType(out, prayers.optJSONObject("shabbat"), name, nusach, "shabbat", sun);
        }
    }

    /** true אם יש בקובץ לפחות זמן יחסי אחד לשקיעה/נץ אך אין קואורדינטות - כדי להציג אזהרה למשתמש. */
    public static boolean needsCoordinatesButMissing(Context context) {
        if (!AreaPrefs.usesFile(context) || !hasPersonalFile(context)) return false;
        try {
            JSONObject root = readPersonalRoot(context);
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
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null) {
                    // {"t":"HH:mm","n":"הערה"} - שעה עם הערה משלה
                    TimeResolution r = resolveTime(obj.optString("t", "").trim(), sun);
                    if (r.minutes >= 0) {
                        out.add(new MinyanEntry(name, nusach, prayerKey, dayType, r.minutes,
                                firstNonEmpty(obj.optString("n", ""), r.autoNote)));
                    }
                    continue;
                }
                String s = arr.optString(i, "").trim();
                if (s.length() > 0) items.add(s);
            }
            if (items.isEmpty()) return;
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
