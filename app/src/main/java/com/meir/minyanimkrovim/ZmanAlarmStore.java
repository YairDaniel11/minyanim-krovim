package com.meir.minyanimkrovim;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * התראות לזמני היום (קריאת שמע, סוף זמן תפילה, שקיעה וכו'): זמן מסוים + כמה דקות לפניו,
 * קבועה (כל יום, לפי זמן היום של אותו יום) או חד-פעמית (פעם אחת בלבד).
 * נשמרות כ-JSON ב-SharedPreferences; לכל התראה PendingIntent משלה (alarm מדויק אחד קדימה).
 */
public class ZmanAlarmStore {

    private static final String PREFS = "zman_alarm_prefs";
    private static final String KEY_LIST = "alarms";
    private static final int REQUEST_BASE = 5000;

    public static class Alarm {
        public final int id;
        public final String key;     // מפתח מ-ZmanimList
        public final int before;     // דקות לפני הזמן (0 = בזמן עצמו)
        public final boolean repeat; // true = קבועה (כל יום), false = חד-פעמית

        public Alarm(int id, String key, int before, boolean repeat) {
            this.id = id;
            this.key = key;
            this.before = before;
            this.repeat = repeat;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized List<Alarm> list(Context c) {
        List<Alarm> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY_LIST, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Alarm(o.getInt("id"), o.getString("key"), o.optInt("before", 0), o.optBoolean("repeat", true)));
            }
        } catch (JSONException ignored) { }
        return out;
    }

    private static void save(Context c, List<Alarm> alarms) {
        JSONArray arr = new JSONArray();
        try {
            for (Alarm a : alarms) {
                arr.put(new JSONObject().put("id", a.id).put("key", a.key).put("before", a.before).put("repeat", a.repeat));
            }
        } catch (JSONException ignored) { }
        prefs(c).edit().putString(KEY_LIST, arr.toString()).apply();
    }

    public static synchronized Alarm get(Context c, int id) {
        for (Alarm a : list(c)) if (a.id == id) return a;
        return null;
    }

    /** מוסיף ומתזמן. מחזיר false אם אי אפשר לתזמן (אין מיקום מוגדר). */
    public static synchronized boolean add(Context c, String key, int before, boolean repeat) {
        if (LocationPrefs.getEffectiveCoords(c) == null) return false;
        List<Alarm> all = list(c);
        int id = 1;
        for (Alarm a : all) id = Math.max(id, a.id + 1);
        Alarm alarm = new Alarm(id, key, before, repeat);
        all.add(alarm);
        save(c, all);
        schedule(c, alarm);
        return true;
    }

    public static synchronized void remove(Context c, int id) {
        List<Alarm> all = list(c);
        List<Alarm> keep = new ArrayList<>();
        for (Alarm a : all) {
            if (a.id == id) cancel(c, a);
            else keep.add(a);
        }
        save(c, keep);
    }

    /** מתזמן מחדש את כולן (אחרי אתחול, שינוי מיקום, או פתיחת האפליקציה). */
    public static synchronized void rescheduleAll(Context c) {
        for (Alarm a : list(c)) schedule(c, a);
    }

    /** הרגע (millis) הבא של ההתראה, שגדול ממש מ-now; -1 אם אי אפשר לחשב (אין מיקום). */
    public static long nextTrigger(Context c, Alarm a, long nowMillis) {
        LocationPrefs.Coords coords = LocationPrefs.getEffectiveCoords(c);
        if (coords == null) return -1;
        for (int dayOffset = 0; dayOffset < 3; dayOffset++) {
            Calendar day = Calendar.getInstance();
            day.setTimeInMillis(nowMillis);
            day.add(Calendar.DAY_OF_YEAR, dayOffset);
            SunTimes.Zmanim z = SunTimes.computeZmanim(coords.lat, coords.lon, day);
            if (!z.valid) continue;
            int minutes = ZmanimList.minutes(z, a.key) - a.before;
            day.set(Calendar.HOUR_OF_DAY, 0);
            day.set(Calendar.MINUTE, 0);
            day.set(Calendar.SECOND, 0);
            day.set(Calendar.MILLISECOND, 0);
            day.add(Calendar.MINUTE, minutes);
            if (day.getTimeInMillis() > nowMillis + 1000) return day.getTimeInMillis();
        }
        return -1;
    }

    public static void schedule(Context c, Alarm a) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long when = nextTrigger(c, a, System.currentTimeMillis());
        PendingIntent pi = pendingIntent(c, a.id);
        if (when < 0) {
            am.cancel(pi);
            return;
        }
        AlarmScheduler.setAt(c, am, when, pi);
    }

    private static void cancel(Context c, Alarm a) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pendingIntent(c, a.id));
    }

    private static PendingIntent pendingIntent(Context c, int id) {
        Intent intent = new Intent(c, AlarmReceiver.class);
        intent.putExtra(AlarmReceiver.EXTRA_ZMAN_ALARM_ID, id);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, REQUEST_BASE + id, intent, flags);
    }

    private ZmanAlarmStore() { }
}
