package com.meir.minyanimkrovim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** receiver סטטי - שידור מוגן (BOOT_COMPLETED) מגיע גם אם האפליקציה לא רצה ברקע. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            AlarmScheduler.rescheduleAllFromPrefs(context);
        }
    }
}
