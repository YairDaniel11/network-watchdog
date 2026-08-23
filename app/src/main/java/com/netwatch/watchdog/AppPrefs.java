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

    // ערכת נושא (מערכת/בהיר/כהה) - אותו מנגנון כמו באפליקציית הבייביסיטר.
    public static final String KEY_THEME_MODE = "theme_mode";
    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
}
