package com.netwatch.watchdog;

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
     * כדי להתריע פעם אחת בכל מעבר ולא בכל בדיקה (כל 5 דקות) כל עוד המצב לא השתנה.
     */
    public static final String KEY_LAST_NETWORK_STATE = "last_network_state";
    public static final String NETWORK_STATE_UP = "up";
    public static final String NETWORK_STATE_DOWN = "down";

    // ערכת נושא (מערכת/בהיר/כהה) - אותו מנגנון כמו באפליקציית הבייביסיטר.
    public static final String KEY_THEME_MODE = "theme_mode";
    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
}
