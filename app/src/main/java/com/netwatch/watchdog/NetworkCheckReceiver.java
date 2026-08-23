package com.netwatch.watchdog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;

/**
 * מתעורר כל 5 דקות ע"י AlarmManager (ראו AlarmScheduler). בודק - רק לפי
 * המתגים שהמשתמש הפעיל בפועל (מעקב טלפוני / מעקב אינטרנט, כל אחד בנפרד) -
 * האם יש רשת. רק אם *אין* רשת (ולא בכל מקרה, בניגוד לגרסה הקודמת של
 * האפליקציה), מפעיל מצב טיסה ל-3 שניות כדי לגרום למכשיר לתפוס רשת מחדש.
 *
 * עובד עם goAsync() + WakeLock קצר כי הבדיקה (במיוחד בדיקת האינטרנט + 3
 * שניות ההמתנה אם צריך לרענן) לוקחת יותר מכמה מילישניות שמותר ל-onReceive
 * הרגיל לחסום - וגם כדי שהמכשיר לא ירדם באמצע הבדיקה אם המסך כבוי.
 */
public class NetworkCheckReceiver extends BroadcastReceiver {

    private static final String WAKE_LOCK_TAG = "netwatch:check";
    // תקרת בטיחות ל-WakeLock - קצת יותר משוואת התרחיש הכי ארוך האפשרי
    // (בדיקת אינטרנט + החלפת מצב טיסה), כדי שלעולם לא נחזיק אותו לנצח
    // אם קרתה תקלה בלתי צפויה בשרשור הרקע.
    private static final long WAKE_LOCK_SAFETY_TIMEOUT_MS = 30_000L;

    @Override
    public void onReceive(final Context context, Intent intent) {
        final SharedPreferences prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE);
        final boolean phoneMonitor = prefs.getBoolean(AppPrefs.KEY_PHONE_MONITOR, false);
        final boolean internetMonitor = prefs.getBoolean(AppPrefs.KEY_INTERNET_MONITOR, false);

        if (!phoneMonitor && !internetMonitor) {
            // שני המתגים כבויים - אין מה לבדוק. מבטלים גם את האלארם עצמו
            // ליתר ביטחון (מצב שלא אמור לקרות בזרימה הרגילה, כי כיבוי מתג
            // מבטל את האלארם ישירות דרך AlarmScheduler, אבל רשת בטיחות זולה).
            AlarmScheduler.scheduleIfNeeded(context);
            return;
        }

        final PowerManager pm = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
        final PowerManager.WakeLock wakeLock = pm != null
                ? pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                : null;
        if (wakeLock != null) {
            wakeLock.acquire(WAKE_LOCK_SAFETY_TIMEOUT_MS);
        }

        final PendingResult pendingResult = goAsync();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean phoneDown = phoneMonitor && !NetworkStatusChecker.isPhoneNetworkAvailable(context);
                    boolean internetDown = internetMonitor && !NetworkStatusChecker.isInternetReachable();

                    if (phoneDown || internetDown) {
                        AirplaneModeToggler.toggleAirplaneModeBlocking();
                    }
                } finally {
                    if (wakeLock != null && wakeLock.isHeld()) {
                        wakeLock.release();
                    }
                    pendingResult.finish();
                }
            }
        }, "NetworkCheckThread").start();
    }
}
