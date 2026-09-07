package com.meir.minyanimkrovim;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

/**
 * מתזמן עד 3 התראות יומיות (שחרית/מנחה/ערבית), כל אחת בשעה קבועה שהמשתמש
 * בוחר במסך ההגדרות. כל התראה היא alarm חד-פעמי (exact) שמתוזמן מחדש
 * ל-24 שעות קדימה מיד עם ההפעלה שלו (ב-AlarmReceiver) - כדי לשמור על דיוק
 * לאורך זמן בלי להסתמך על setRepeating הלא-מדויק.
 */
public class AlarmScheduler {

    public static final String PREFS_NAME = "minyanim_alarm_prefs";

    public static final String TYPE_SHACHARIT = "shacharit";
    public static final String TYPE_MINCHA = "mincha";
    public static final String TYPE_ARVIT = "arvit";

    private static final int REQUEST_CODE_SHACHARIT = 100;
    private static final int REQUEST_CODE_MINCHA = 101;
    private static final int REQUEST_CODE_ARVIT = 102;

    public static String keyEnabled(String type) { return "enabled_" + type; }
    public static String keyMinutes(String type) { return "minutes_" + type; }

    private static int requestCodeFor(String type) {
        if (TYPE_SHACHARIT.equals(type)) return REQUEST_CODE_SHACHARIT;
        if (TYPE_MINCHA.equals(type)) return REQUEST_CODE_MINCHA;
        return REQUEST_CODE_ARVIT;
    }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context, String type) {
        return prefs(context).getBoolean(keyEnabled(type), false);
    }

    public static int getMinutesOfDay(Context context, String type) {
        return prefs(context).getInt(keyMinutes(type), 6 * 60); // ברירת מחדל: 06:00
    }

    public static void setAlarm(Context context, String type, boolean enabled, int minutesOfDay) {
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.putBoolean(keyEnabled(type), enabled);
        editor.putInt(keyMinutes(type), minutesOfDay);
        editor.apply();

        if (enabled) {
            scheduleNext(context, type, minutesOfDay);
        } else {
            cancel(context, type);
        }
    }

    /** קורא מ-SharedPreferences ומתזמן מחדש את כל ההתראות המופעלות - לשימוש אחרי reboot. */
    public static void rescheduleAllFromPrefs(Context context) {
        for (String type : new String[]{TYPE_SHACHARIT, TYPE_MINCHA, TYPE_ARVIT}) {
            if (isEnabled(context, type)) {
                scheduleNext(context, type, getMinutesOfDay(context, type));
            }
        }
    }

    /** מתזמן alarm יחיד ל-HH:mm הקרוב (היום אם עוד לא עבר, אחרת מחר). */
    public static void scheduleNext(Context context, String type, int minutesOfDay) {
        Calendar target = Calendar.getInstance();
        int hour = minutesOfDay / 60;
        int minute = minutesOfDay % 60;
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        if (!target.after(Calendar.getInstance())) {
            target.add(Calendar.DAY_OF_YEAR, 1);
        }

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = buildPendingIntent(context, type);

        boolean canExact = true;
        if (Build.VERSION.SDK_INT >= 31 /* Build.VERSION_CODES.S */) {
            canExact = am.canScheduleExactAlarms();
        }

        if (canExact) {
            if (Build.VERSION.SDK_INT >= 23 /* M */) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.getTimeInMillis(), pi);
            } else if (Build.VERSION.SDK_INT >= 19 /* KITKAT - minSdk */) {
                am.setExact(AlarmManager.RTC_WAKEUP, target.getTimeInMillis(), pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, target.getTimeInMillis(), pi);
            }
        } else {
            // אין הרשאת alarm מדויק (Android 12+ שנשללה) - נופלים לתזמון לא-מדויק
            // במקום לקרוס; עדיף התראה שמאחרת מעט על היעדר התראה כלל.
            am.set(AlarmManager.RTC_WAKEUP, target.getTimeInMillis(), pi);
        }
    }

    public static void cancel(Context context, String type) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(buildPendingIntent(context, type));
    }

    private static PendingIntent buildPendingIntent(Context context, String type) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra(AlarmReceiver.EXTRA_PRAYER_TYPE, type);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23 /* M - FLAG_IMMUTABLE נוסף כאן */) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, requestCodeFor(type), intent, flags);
    }
}
