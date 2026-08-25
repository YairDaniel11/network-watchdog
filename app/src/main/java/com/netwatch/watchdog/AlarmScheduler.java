package com.netwatch.watchdog;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

/**
 * מתזמן/מבטל את הבדיקה החוזרת, דרך AlarmManager + BroadcastReceiver
 * (NetworkCheckReceiver) - במכוון *לא* Service שרץ ברצף ברקע, כדי לצרוך
 * כמה שפחות RAM וסוללה.
 *
 * המרווח אינו קבוע בקוד - הוא מוגדר ע"י המשתמש במסך ההגדרות
 * (AppPrefs.KEY_CHECK_INTERVAL_MINUTES, 1-120 דקות, ברירת מחדל 5).
 *
 * ------------------------------------------------------------------
 * למה setInexactRepeating ולא אלארם מדויק שמתזמן את עצמו: אלארם חוזר
 * אמיתי נרשם פעם אחת אצל מערכת ההפעלה עצמה (AlarmManagerService) ולא
 * תלוי בתהליך האפליקציה - גם אם התהליך נהרג בין הפעלה להפעלה, המערכת
 * עדיין תעיר את ה-receiver שוב. התזמון עלול להידחות קצת תחת Doze;
 * את זה ממזערים בנפרד עם פטור מחיסכון סוללה (ראה RootPowerUtil).
 * שים לב: על חלק מהמכשירים אלארם "לא מדויק" עם מרווח קצר מ-15 דקות
 * עשוי להתמתח קלות ע"י המערכת - זו מגבלה מכוונת של אמינות מול דיוק.
 * ------------------------------------------------------------------
 */
public final class AlarmScheduler {

    private AlarmScheduler() {
    }

    /** ברירת המחדל במילישניות - לתיעוד בלבד; הערך בפועל נלקח מההעדפות. */
    public static final long DEFAULT_INTERVAL_MS = 5 * 60 * 1000L;
    private static final int REQUEST_CODE = 1001;

    /**
     * קוראים לזה בכל שינוי של אחד ממתגי המעקב, בכל שינוי של תדירות הבדיקה,
     * ב-BootReceiver, וגם בכל פתיחה של המסך הראשי/הגדרות (self-healing).
     * הקריאה אידמפוטנטית - עדכון האלארם הקיים, לא יצירת כפילות.
     */
    public static void scheduleIfNeeded(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE);
        boolean phoneOn = prefs.getBoolean(AppPrefs.KEY_PHONE_MONITOR, false);
        boolean netOn = prefs.getBoolean(AppPrefs.KEY_INTERNET_MONITOR, false);
        if (phoneOn || netOn) {
            scheduleRepeating(context);
        } else {
            cancel(context);
        }
    }

    private static void scheduleRepeating(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        long intervalMs = AppPrefs.getCheckIntervalMs(context);
        long firstTriggerAt = SystemClock.elapsedRealtime() + intervalMs;
        // setInexactRepeating (לא setRepeating) בכוונה: מ-API 19 ואילך שתי
        // הפונקציות מתנהגות זהה בפועל - השם "Inexact" רק משקף זאת בצורה כנה.
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, firstTriggerAt, intervalMs, pendingIntent(context));
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // נדרש מ-API 31 ואילך עבור כל PendingIntent שנמסר לרכיב מערכת.
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags);
    }
}