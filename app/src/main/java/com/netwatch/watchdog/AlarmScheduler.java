package com.netwatch.watchdog;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

/**
 * מתזמן/מבטל את הבדיקה החוזרת כל 5 דקות, דרך AlarmManager + BroadcastReceiver
 * (NetworkCheckReceiver) - במכוון *לא* Service שרץ ברצף ברקע, כדי לצרוך
 * כמה שפחות RAM וסוללה.
 *
 * ------------------------------------------------------------------
 * הערה חשובה (תוקן אחרי דיווח על אי-אמינות): בגרסה הקודמת השתמשתי
 * ב-setExactAndAllowWhileIdle עם "שרשור עצמי" - כל הפעלה של הבדיקה
 * הייתה צריכה לתזמן בעצמה, מתוך קוד האפליקציה, את ההפעלה הבאה. זו
 * נקודת כשל יחידה מסוכנת: setExactAndAllowWhileIdle היא לפי הגדרתה
 * חד-פעמית (Android לא מאפשר "exact repeating" החל מ-KitKat) - ואם
 * התהליך של האפליקציה נהרג ע"י המערכת (נפוץ מאוד למכשירים עם רקע
 * שרץ, בעיקר במכשירים עם RAM מוגבל) *לפני* שהקוד הספיק לתזמן את
 * ההפעלה הבאה, כל השרשרת נעצרת לצמיתות בלי שום דרך להתאושש - בדיוק
 * התסמין שדווח ("עבד פעם אחת ואז הפסיק").
 *
 * הפתרון: setInexactRepeating אמיתי - נרשם *פעם אחת* אצל מערכת
 * ההפעלה עצמה (AlarmManagerService), ולא תלוי בתהליך של האפליקציה
 * בשביל להמשיך להתעורר. גם אם התהליך נהרג לגמרי בין הפעלה להפעלה,
 * המערכת עדיין תעיר את ה-receiver שוב בעוד ~5 דקות. זה פחות "מדויק"
 * מבחינת תזמון (יכול להידחות קצת תחת Doze), אבל אמין בהרבה - ומדויקות
 * לשנייה ממילא לא קריטית כאן. את פגיעת ה-Doze ממזערים בנפרד עם פטור
 * מחיסכון סוללה (ראו RootPowerUtil, מופעל דרך רוט בשקט).
 * ------------------------------------------------------------------
 */
public final class AlarmScheduler {

    private AlarmScheduler() {
    }

    public static final long INTERVAL_MS = 5 * 60 * 1000L; // 5 דקות
    private static final int REQUEST_CODE = 1001;

    /**
     * קוראים לזה בכל שינוי של אחד ממתגי המעקב, ב-BootReceiver, וגם בכל
     * פתיחה של המסך הראשי/הגדרות (self-healing: אם משהו הרג את
     * האלארם מבחוץ - למשל "מנקה RAM" אגרסיבי של היצרן - כל פתיחה של
     * האפליקציה קושרת אותו מחדש).
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
        long firstTriggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS;
        // setInexactRepeating (לא setRepeating) בכוונה: מ-API 19 ואילך שתי
        // הפונקציות מתנהגות זהה בפועל (כל אלארם חוזר הוא "לא מדויק" משיקולי
        // סוללה, גם אם קוראים ל-setRepeating) - השם "Inexact" רק משקף את זה
        // בצורה כנה יותר בקוד. הקריאה הזו אידמפוטנטית - קריאה חוזרת (למשל
        // מ-onResume בכל פתיחת מסך) רק מעדכנת/מוודאת את אותו אלארם, לא
        // יוצרת כפילות.
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, firstTriggerAt, INTERVAL_MS, pendingIntent(context));
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
            // נדרש מ-API 31 ואילך עבור כל PendingIntent שנמסר לרכיב מערכת
            // (AlarmManager) ולא נועד להתעדכן עם extras חדשים.
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags);
    }
}
