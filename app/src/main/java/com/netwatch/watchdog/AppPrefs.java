package com.netwatch.watchdog;

import android.content.Context;

/**
 * מפתחות SharedPreferences משותפים - נגישים גם מ-MainActivity וגם
 * מ-NetworkCheckReceiver/BootReceiver, בלי תלות הדדית בין המחלקות.
 */
public final class AppPrefs {

    private AppPrefs() {
    }

    public static final String PREFS_NAME = "netwatch_prefs";

    /** האם מעקב רשת טלפונית (סלולרית) פעיל. */
    public static final String KEY_PHONE_MONITOR = "phone_monitor_enabled";

    /** האם מעקב אינטרנט (בדיקת מענה בפועל משרת) פעיל. */
    public static final String KEY_INTERNET_MONITOR = "internet_monitor_enabled";

    // --- תדירות בדיקה (מוגדרת-משתמש) ---

    /** מרווח הבדיקה בדקות, כפי שהמשתמש הגדיר במסך ההגדרות. */
    public static final String KEY_CHECK_INTERVAL_MINUTES = "check_interval_minutes";
    public static final int DEFAULT_CHECK_INTERVAL_MINUTES = 5;
    public static final int MIN_CHECK_INTERVAL_MINUTES = 1;
    public static final int MAX_CHECK_INTERVAL_MINUTES = 120;

    /**
     * חותמת-זמן (elapsedRealtime) של ניסיון הרענון האחרון - משמשת ל-cooldown:
     * מינימום שני מרווחי-בדיקה בין ניסיונות רענון, כדי שלא נרענן בלופ חוזר
     * (חשוב במיוחד כי הרענון עצמו מייצר שידורי CONNECTIVITY_CHANGE).
     */
    public static final String KEY_LAST_TOGGLE_AT = "last_toggle_at";

    /**
     * חותמת-זמן (currentTimeMillis) של הרצת תיקוני האמינות האחרונה דרך רוט -
     * ראה RootPowerUtil. מגביל את קריאות ה-root כדי שמנהל הרוט לא יציף
     * את המסך בהודעות "הוענקו הרשאות" בכל פתיחת מסך.
     */
    public static final String KEY_LAST_RELIABILITY_FIX_MS = "last_reliability_fix_ms";

    // --- התרעות קוליות/רטט ---

    /** האם להתריע כשמתגלה שאין קליטה. */
    public static final String KEY_ALERT_LOST_ENABLED = "alert_lost_enabled";
    /** URI (כמחרוזת) של הצליל שנבחר להתרעת "אין קליטה". ריק = ברירת מחדל של המערכת. */
    public static final String KEY_ALERT_LOST_SOUND_URI = "alert_lost_sound_uri";
    /** מצב ההתרעה: אחד מ-ALERT_MODE_*. */
    public static final String KEY_ALERT_LOST_MODE = "alert_lost_mode";

    /** האם להתריע כשהקליטה חוזרת. */
    public static final String KEY_ALERT_RESTORED_ENABLED = "alert_restored_enabled";
    public static final String KEY_ALERT_RESTORED_SOUND_URI = "alert_restored_sound_uri";
    public static final String KEY_ALERT_RESTORED_MODE = "alert_restored_mode";

    public static final String ALERT_MODE_SOUND_VIBRATE = "sound_vibrate";
    public static final String ALERT_MODE_SOUND_ONLY = "sound_only";
    public static final String ALERT_MODE_VIBRATE_ONLY = "vibrate_only";

    /**
     * מצב הרשת האחרון שנצפה ("up"/"down") - משמש לזהות מעברי מצב (ירידה/חזרה),
     * כדי להתריע פעם אחת בכל מעבר ולא בכל בדיקה.
     */
    public static final String KEY_LAST_NETWORK_STATE = "last_network_state";
    public static final String NETWORK_STATE_UP = "up";
    public static final String NETWORK_STATE_DOWN = "down";

    // ערכת נושא (מערכת/בהיר/כהה).
    public static final String KEY_THEME_MODE = "theme_mode";
    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    /**
     * מרווח הבדיקה בדקות, עם הגבלה לטווח התקין - כך שגם ערך פגום/ידני
     * בזיכרון לא יכול לגרום להתנהגות קיצונית (אפס/שלילי/ענק).
     */
    public static int getCheckIntervalMinutes(Context context) {
        try {
            int value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getInt(KEY_CHECK_INTERVAL_MINUTES, DEFAULT_CHECK_INTERVAL_MINUTES);
            if (value < MIN_CHECK_INTERVAL_MINUTES) {
                return MIN_CHECK_INTERVAL_MINUTES;
            }
            if (value > MAX_CHECK_INTERVAL_MINUTES) {
                return MAX_CHECK_INTERVAL_MINUTES;
            }
            return value;
        } catch (Exception e) {
            return DEFAULT_CHECK_INTERVAL_MINUTES;
        }
    }

    /** אותו דבר במילישניות - נוח ל-AlarmManager ולחישובי cooldown. */
    public static long getCheckIntervalMs(Context context) {
        return getCheckIntervalMinutes(context) * 60000L;
    }
}