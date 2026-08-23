package com.netwatch.watchdog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * אלארמים של AlarmManager לא שורדים כיבוי/הדלקה מחדש של המכשיר - חייבים
 * לתזמן אותם מחדש אחרי אתחול, אם מעקב כלשהו היה פעיל לפני הכיבוי.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            AlarmScheduler.scheduleIfNeeded(context);
        }
    }
}
