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
 *     זה מתקן את התרחיש שבו מעקב פעיל לא החזיר את המכשיר ממצב טיסה ידני.
 *  2) מצב טיסה כבוי אבל בכל זאת אין קליטה/אינטרנט בפועל - מבצעים את
 *     הרענון המלא (דלוק->3 שניות->כבוי).
 *
 * בנוסף עוקבים אחרי מעברי מצב (עלה/ירד) כדי להפעיל התרעת קול/רטט פעם
 * אחת בכל מעבר, לא בכל בדיקה - ראו AlertPlayer + AppPrefs.KEY_LAST_NETWORK_STATE.
 */
public class NetworkCheckReceiver extends BroadcastReceiver {

    private static final String WAKE_LOCK_TAG = "netwatch:check";
    // תקרת בטיחות ל-WakeLock - קצת יותר מהתרחיש הכי ארוך האפשרי (בדיקת
    // אינטרנט + רענון מלא + ניגון התרעה), כדי שלעולם לא נחזיק אותו לנצח
    // אם קרתה תקלה בלתי צפויה בשרשור הרקע.
    private static final long WAKE_LOCK_SAFETY_TIMEOUT_MS = 30_000L;

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
        boolean actionOk = true;
        if (airplaneOn) {
            // כבר במצב טיסה (למשל הופעל ידנית) - רק מכבים, לא "מהבהבים".
            actionOk = AirplaneModeToggler.forceAirplaneModeOff();
            action = "מצב טיסה היה דלוק -> ניסיון כיבוי: " + (actionOk ? "הצליח" : "נכשל");
        } else if (phoneDown || internetDown) {
            actionOk = AirplaneModeToggler.toggleAirplaneModeBlocking();
            action = "זוהתה נפילת רשת -> ניסיון רענון: " + (actionOk ? "הצליח" : "נכשל");
        } else {
            action = "אין פעולה (הכל תקין)";
        }

        // רשומת יומן אבחון - זה מה שמאפשר לראות בפועל, בלי מחשב/logcat,
        // אם הבדיקה התקופתית בכלל רצה וכל בדיקה הסיקה נכון. מקיף גם
        // מקרה כשל: אם שורה חדשה לא מופיעה כל 5 דקות, סימן שהאלארם עצמו
        // לא מתעורר (בעיית מערכת/ROM), לא בעיה בלוגיקה של הבדיקה.
        DiagnosticsLog.log(context, String.format(java.util.Locale.getDefault(),
                "בדיקה: טיסה=%s טלפון-חסר=%s אינטרנט-חסר=%s | %s",
                yesNo(airplaneOn), yesNo(phoneDown), yesNo(internetDown), action));
    }

    private static String yesNo(boolean b) {
        return b ? "כן" : "לא";
    }
}
