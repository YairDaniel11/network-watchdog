package com.netwatch.watchdog;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;

/**
 * מתזמן/מבטל את הבדיקה החוזרת כל 5 דקות, דרך AlarmManager + BroadcastReceiver
 * (NetworkCheckReceiver) - במכוון *לא* Service שרץ ברצף ברקע, כדי לצרוך
 * כמה שפחות RAM וסוללה: בין הבדיקות אין שום קוד של האפליקציה רץ בכלל,
 * המערכת פשוט מעירה את ה-receiver לכמה שניות כל 5 דקות ואז חוזרת לישון.
 *
 * משתמשים ב-setExactAndAllowWhileIdle (לא setInexactRepeating) ומתזמנים
 * מחדש בכל פעם מתוך NetworkCheckReceiver עצמו (שרשרת "one-shot" ולא
 * repeating אמיתי) - כי setInexactRepeating עלול להידחות משמעותית
 * (לפעמים שעות) תחת Doze/App Standby באנדרואיד מודרני, ולא מספיק אמין
 * למטרה של "לזהות אובדן קליטה תוך כמה דקות". ראו גם RootPowerUtil
 * לתיקוני אמינות נוספים (הרשאת אלארמים מדויקים + פטור מחיסכון סוללה).
 */
public final class AlarmScheduler {

    private AlarmScheduler() {
    }

    public static final long INTERVAL_MS = 5 * 60 * 1000L; // 5 דקות
    private static final int REQUEST_CODE = 1001;

    /** קוראים לזה בכל שינוי של אחד ממתגי המעקב, וגם ב-BootReceiver. */
    public static void scheduleIfNeeded(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE);
        boolean phoneOn = prefs.getBoolean(AppPrefs.KEY_PHONE_MONITOR, false);
        boolean netOn = prefs.getBoolean(AppPrefs.KEY_INTERNET_MONITOR, false);
        if (phoneOn || netOn) {
            scheduleNext(context);
        } else {
            cancel(context);
        }
    }

    /**
     * מתזמן את הבדיקה *הבאה* בעוד 5 דקות. נקרא גם מכאן (הפעלה ראשונה)
     * וגם מ-NetworkCheckReceiver בסוף כל בדיקה (כדי להמשיך את השרשרת).
     */
    public static void scheduleNext(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        long triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS;
        PendingIntent pi = pendingIntent(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // setExactAndAllowWhileIdle - מדויק ומתעורר גם במצב Doze (בכפוף
            // ל"קצב" השהיות שהמערכת אוכפת בכל זאת ב-Doze עמוק, אבל משמעותית
            // אמין יותר מ-setInexactRepeating).
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
        } else {
            am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
        }
    }

    private static void cancel(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        am.cancel(pendingIntent(context));
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, NetworkCheckReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // נדרש מ-API 31 ואילך עבור כל PendingIntent שנמסר לרכיב מערכת
            // (AlarmManager) ולא נועד להתעדכן עם extras חדשים.
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags);
    }
}
