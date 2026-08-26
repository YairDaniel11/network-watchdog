package com.netwatch.watchdog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;

/**
 * מתעורר כל 5 דקות ע"י אלארם חוזר אמיתי שנרשם פעם אחת ב-AlarmScheduler
 * (לא שרשור עצמי - ראו הערה מפורטת שם על למה זה תוקן). בודק - רק לפי
 * המתגים שהמשתמש הפעיל בפועל (מעקב טלפוני / מעקב אינטרנט, כל אחד
 * בנפרד) - האם יש רשת. רק אם *אין* רשת, מפעיל רענון.
 *
 * שני מקרים נבדקים בנפרד:
 *  1) מצב טיסה כבר דלוק (Settings.Global.AIRPLANE_MODE_ON) - למשל המשתמש
 *     הפעיל אותו ידנית בטעות ושכח לכבות. במקרה הזה פשוט מכבים אותו
 *     מיידית (forceAirplaneModeOff) - אין טעם "להדליק" משהו שכבר דלוק.
 *  2) מצב טיסה כבוי אבל בכל זאת אין קליטה/אינטרנט בפועל - מבצעים את
 *     הרענון המלא (דלוק->3 שניות->כבוי).
 *
 * חשוב (תוקן אחרי דיווח בפועל): כל קריאת su (בתוך AirplaneModeToggler)
 * עוברת עכשיו דרך RootShell עם תקרת זמן. לפני התיקון, אם מנהל ה-root
 * במכשיר נתקע (למשל ממתין לאישור שאף אחד לא יכול לתת לו מקריאה שמגיעה
 * מהרקע), ה-thread הזה היה נתקע לנצח - וזה בדיוק מה שהסביר למה יומן
 * האבחון נשאר ריק בדיוק כשהייתה בעיית רשת אמיתית: זו הפעם היחידה
 * שבאמת קוראים ל-su, ושורת היומן (שמגיעה רק אחרי שהקריאה חוזרת) לעולם
 * לא נכתבה. עכשיו כל קריאת su חוזרת תוך כמה שניות לכל היותר, גם אם
 * נכשלה/נתקעה - כך שהיומן תמיד מקבל שורה, בכל מחזור בדיקה.
 */
public class NetworkCheckReceiver extends BroadcastReceiver {

    private static final String WAKE_LOCK_TAG = "netwatch:check";
    // תקרת בטיחות ל-WakeLock - נדיבה יותר מסכום כל תקרות הזמן האפשריות
    // (טיסה: עד 12 שניות, + ניגון התרעה: עד 3 שניות, + מרווח בטיחות).
    private static final long WAKE_LOCK_SAFETY_TIMEOUT_MS = 25_000L;

    @Override
    public void onReceive(final Context context, Intent intent) {
        final SharedPreferences prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE);
        final boolean phoneMonitor = prefs.getBoolean(AppPrefs.KEY_PHONE_MONITOR, false);
        final boolean internetMonitor = prefs.getBoolean(AppPrefs.KEY_INTERNET_MONITOR, false);

        if (!phoneMonitor && !internetMonitor) {
            // שני המתגים כבויים (למשל כובו ממש בין הפעלה להפעלה) - מבטלים
            // את האלארם עצמו ליתר ביטחון, אין מה לבדוק.
            AlarmScheduler.scheduleIfNeeded(context);
            return;
        }

        // רשומת יומן *מיידית* - לפני שנעשה משהו אחר. כך שגם אם קורה
        // קריסה בלתי צפויה בהמשך, לפחות רואים שהאלארם התעורר בכלל.
        DiagnosticsLog.log(context, "אלארם התעורר - מתחיל בדיקה");

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
                    runCheck(context, prefs, phoneMonitor, internetMonitor);
                } catch (Throwable t) {
                    // רשת בטיחות אחרונה: תקלה בלתי צפויה כלשהי (לא אמורה
                    // לקרות, כל שלב כבר מטופל בנפרד) - עדיין נרשמת ליומן
                    // במקום להיעלם בשקט.
                    DiagnosticsLog.log(context, "שגיאה לא צפויה בבדיקה: " + t.getClass().getSimpleName()
                            + (t.getMessage() != null ? (" - " + t.getMessage()) : ""));
                } finally {
                    // האלארם החוזר עצמו לא תלוי בקוד הזה בכלל (נרשם פעם
                    // אחת אצל המערכת - ראו AlarmScheduler) - כאן רק
                    // משחררים משאבים של הבדיקה הנוכחית.
                    if (wakeLock != null && wakeLock.isHeld()) {
                        wakeLock.release();
                    }
                    pendingResult.finish();
                }
            }
        }, "NetworkCheckThread").start();
    }

    private void runCheck(Context context, SharedPreferences prefs, boolean phoneMonitor, boolean internetMonitor) {
        boolean airplaneOn = NetworkStatusChecker.isAirplaneModeOn(context);
        boolean phoneDown = phoneMonitor && !NetworkStatusChecker.isPhoneNetworkAvailable(context);
        boolean internetDown = internetMonitor && !NetworkStatusChecker.isInternetReachable();
        boolean isDown = airplaneOn || phoneDown || internetDown;

        String previousState = prefs.getString(AppPrefs.KEY_LAST_NETWORK_STATE, AppPrefs.NETWORK_STATE_UP);
        boolean wasDown = AppPrefs.NETWORK_STATE_DOWN.equals(previousState);

        if (isDown && !wasDown) {
            // מעבר חדש: היה תקין -> עכשיו אין קליטה. מתריעים פעם אחת.
            AlertPlayer.playLostAlert(context);
        } else if (!isDown && wasDown) {
            // מעבר חדש: היה למטה -> עכשיו חזר. מתריעים פעם אחת.
            AlertPlayer.playRestoredAlert(context);
        }
        prefs.edit()
                .putString(AppPrefs.KEY_LAST_NETWORK_STATE, isDown ? AppPrefs.NETWORK_STATE_DOWN : AppPrefs.NETWORK_STATE_UP)
                .apply();

        String action;
        if (airplaneOn) {
            // כבר במצב טיסה (למשל הופעל ידנית) - רק מכבים, לא "מהבהבים".
            RootShell.Result result = AirplaneModeToggler.forceAirplaneModeOffWithDetail();
            action = "מצב טיסה היה דלוק -> ניסיון כיבוי: " + describe(result);
        } else if (phoneDown || internetDown) {
            RootShell.Result result = AirplaneModeToggler.toggleAirplaneModeWithDetail();
            action = "זוהתה נפילת רשת -> ניסיון רענון: " + describe(result);
        } else {
            action = "אין פעולה (הכל תקין)";
        }

        // רשומת יומן אבחון - זה מה שמאפשר לראות בפועל, בלי מחשב/logcat,
        // אם הבדיקה התקופתית בכלל רצה וכל בדיקה הסיקה נכון.
        DiagnosticsLog.log(context, String.format(java.util.Locale.getDefault(),
                "בדיקה: טיסה=%s טלפון-חסר=%s אינטרנט-חסר=%s | %s",
                yesNo(airplaneOn), yesNo(phoneDown), yesNo(internetDown), action));
    }

    private static String describe(RootShell.Result result) {
        if (result.success) {
            return "הצליח";
        }
        if (result.timedOut) {
            // זה המידע הכי חשוב לאבחון: su עצמו לא הגיב בזמן - כנראה
            // מנהל ה-root במכשיר חוסם/משהה בקשות רקע. תוקן שלא ייתקע
            // לנצח, אבל הפעולה עצמה עדיין לא הצליחה בפועל.
            return "נתקע בזמן (su לא הגיב - כנראה מנהל ה-root חוסם בקשות רקע)";
        }
        return "נכשל (su רץ אך החזיר שגיאה - כנראה אין הרשאות רוט מוענקות)";
    }

    private static String yesNo(boolean b) {
        return b ? "כן" : "לא";
    }
}
