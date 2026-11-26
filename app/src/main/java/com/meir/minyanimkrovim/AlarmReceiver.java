package com.meir.minyanimkrovim;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AlarmReceiver extends BroadcastReceiver {

    public static final String EXTRA_PRAYER_TYPE = "prayer_type";
    /** התראה לזמן יום (ZmanAlarmStore) - מזהה ההתראה. */
    public static final String EXTRA_ZMAN_ALARM_ID = "zman_alarm_id";
    private static final String CHANNEL_ID = "minyanim_alarms";

    @Override
    public void onReceive(Context context, Intent intent) {
        int zmanAlarmId = intent.getIntExtra(EXTRA_ZMAN_ALARM_ID, -1);
        String prayerType = intent.getStringExtra(EXTRA_PRAYER_TYPE);
        if (zmanAlarmId < 0 && prayerType == null) return;

        PowerManager.WakeLock wakeLock = null;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MinyanimKrovim::AlarmLock");
                wakeLock.acquire(20000); // תקרת ביטחון - 20 שניות, משוחרר ידנית קודם לכן
            }
            if (zmanAlarmId >= 0) {
                handleZmanAlarm(context, zmanAlarmId);
            } else {
                handlePrayerAlarm(context, prayerType);
            }
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    // ------------------------------------------------------------------ התראות לזמני היום

    private void handleZmanAlarm(Context context, int id) {
        ZmanAlarmStore.Alarm alarm = ZmanAlarmStore.get(context, id);
        if (alarm == null) return;

        String label = context.getString(ZmanimList.labelRes(alarm.key));
        Calendar zmanTime = Calendar.getInstance();
        zmanTime.add(Calendar.MINUTE, alarm.before);
        String clock = String.format(Locale.US, "%02d:%02d",
                zmanTime.get(Calendar.HOUR_OF_DAY), zmanTime.get(Calendar.MINUTE));
        String text = alarm.before == 0
                ? context.getString(R.string.zman_alarm_now, clock)
                : context.getString(R.string.zman_alarm_in, alarm.before, clock);
        postNotification(context, 5000 + id, label, text);

        if (alarm.repeat) {
            ZmanAlarmStore.schedule(context, alarm); // היום הבא, לפי זמן היום של אותו יום
        } else {
            ZmanAlarmStore.remove(context, id);      // חד-פעמית - נמחקת אחרי שהופעלה
        }
    }

    // ------------------------------------------------------------------ התראות מניינים (שחרית/מנחה/ערבית)

    private void handlePrayerAlarm(Context context, String prayerType) {
        List<MinyanEntry> all;
        try {
            all = MinyanDataStore.loadAll(context);
        } catch (Exception e) {
            all = null;
        }

        if (all != null && !all.isEmpty()) {
            Calendar now = Calendar.getInstance();
            MinyanEntry next = MinyanDataStore.findNextOfPrayerType(all, now, prayerType);
            String title = context.getString(R.string.notif_title_prefix) + " - " + labelForPrayerType(context, prayerType);
            String text;
            if (next == null) {
                text = context.getString(R.string.no_next_minyan);
            } else {
                String nusachPart = next.nusach.length() > 0 ? " (" + next.nusach + ")" : "";
                text = next.shulName + nusachPart + " - " + next.timeLabel();
            }
            postNotification(context, prayerType.hashCode(), title, text);
        }

        // מתזמנים מחדש ל-24 שעות קדימה מיד - שומר על דיוק לאורך זמן
        int minutesOfDay = AlarmScheduler.getMinutesOfDay(context, prayerType);
        if (AlarmScheduler.isEnabled(context, prayerType)) {
            AlarmScheduler.scheduleNext(context, prayerType, minutesOfDay);
        }
    }

    private void postNotification(Context context, int notificationId, String title, String text) {
        // Android 13+ דורש הרשאת POST_NOTIFICATIONS בזמן ריצה - אם נשללה, מדלגים בשקט
        if (Build.VERSION.SDK_INT >= 33 /* TIRAMISU */) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, context.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }

        Intent openIntent = new Intent(context, MainActivity.class);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, openIntent, piFlags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }
        Notification notification = builder
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build();

        manager.notify(notificationId, notification);
    }

    private String labelForPrayerType(Context context, String type) {
        if (AlarmScheduler.TYPE_SHACHARIT.equals(type)) return context.getString(R.string.prayer_shacharit);
        if (AlarmScheduler.TYPE_MINCHA.equals(type)) return context.getString(R.string.prayer_mincha);
        return context.getString(R.string.prayer_arvit);
    }
}
