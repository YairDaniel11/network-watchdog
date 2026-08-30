package com.netwatch.watchdog;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

/**
 * מתזמן/מבטל את הבדיקה החוזרת (קצב לבחירת המשתמש - ראו INTERVAL_OPTIONS_MINUTES
 * ומסך ההגדרות), דרך AlarmManager + BroadcastReceiver (NetworkCheckReceiver) -
 * במכוון *לא* Service שרץ ברצף ברקע, כדי לצרוך כמה שפחות RAM וסוללה.
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

    private static final int REQUEST_CODE = 1001;

    /** אפשרויות קצב הבדיקה שהמשתמש יכול לבחור במסך ההגדרות (בדקות). */
    public static final int[] INTERVAL_OPTIONS_MINUTES = {1, 5, 10, 15, 30, 60};

    public static int getIntervalMinutes(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(AppPrefs.KEY_CHECK_INTERVAL_MINUTES, AppPrefs.DEFAULT_CHECK_INTERVAL_MINUTES);
    }

    /**
     * שומר קצב בדיקה חדש ורושם מחדש את האלארם החוזר איתו מייד (אם מעקב
     * כלשהו פעיל) - אלארם חוזר לא "משנה קצב" מעצמו; צריך לבטל ולרשום
     * מחדש כדי שהקצב החדש ייכנס לתוקף מהבדיקה הבאה.
     */
    public static void setIntervalMinutes(Context context, int minutes) {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(AppPrefs.KEY_CHECK_INTERVAL_MINUTES, minutes).apply();
        // מבטלים במפורש לפני רישום מחדש (במקום לסמוך רק על FLAG_UPDATE_CURRENT
        // "לדרוס" את הקודם) - כדי להימנע ממצב קצה שבו ROM מסוים משאיר את
        // האלארם הישן רשום עם הקצב הישן לצד החדש.
        cancel(context);
        scheduleIfNeeded(context);
    }

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
        long intervalMs = getIntervalMinutes(context) * 60_000L;
        long firstTriggerAt = SystemClock.elapsedRealtime() + intervalMs;
        // setInexactRepeating (לא setRepeating) בכוונה: מ-API 19 ואילך שתי
        // הפונקציות מתנהגות זהה בפועל (כל אלארם חוזר הוא "לא מדויק" משיקולי
        // סוללה, גם אם קוראים ל-setRepeating) - השם "Inexact" רק משקף את זה
        // בצורה כנה יותר בקוד. הקריאה הזו אידמפוטנטית - קריאה חוזרת (למשל
        // מ-onResume בכל פתיחת מסך) רק מעדכנת/מוודאת את אותו אלארם, לא
        // יוצרת כפילות.
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
            // נדרש מ-API 31 ואילך עבור כל PendingIntent שנמסר לרכיב מערכת
            // (AlarmManager) ולא נועד להתעדכן עם extras חדשים.
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags);
    }
}
