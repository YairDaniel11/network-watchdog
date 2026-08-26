package com.netwatch.watchdog;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

/**
 * יומן אבחון קל-משקל: שומר את פעולות הבדיקה האחרונות (עד MAX_LINES) כדי
 * שאפשר יהיה לראות בפועל מה קרה בכל בדיקה תקופתית - בלי logcat/מחשב,
 * ישירות במסך ההגדרות של האפליקציה. חשוב מאוד לאבחון מרחוק (בלי גישה
 * פיזית למכשיר): כשמשהו "לא עובד", היומן הזה מראה בדיוק מה כל בדיקה
 * החליטה (יש/אין רשת, האם בוצע רענון, האם su הצליח) במקום לנחש.
 */
public final class DiagnosticsLog {

    private DiagnosticsLog() {
    }

    private static final String KEY_LOG = "diagnostics_log";
    private static final int MAX_LINES = 40;
    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault());

    public static synchronized void log(Context context, String message) {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE);
        String existing = prefs.getString(KEY_LOG, "");

        String timestamp;
        synchronized (TIME_FORMAT) {
            timestamp = TIME_FORMAT.format(new java.util.Date());
        }
        String newLine = timestamp + " - " + message;

        ArrayList<String> lines = new ArrayList<String>();
        if (!existing.isEmpty()) {
            for (String line : existing.split("\n")) {
                lines.add(line);
            }
        }
        lines.add(newLine);
        while (lines.size() > MAX_LINES) {
            lines.remove(0);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(lines.get(i));
        }
        prefs.edit().putString(KEY_LOG, sb.toString()).apply();
    }

    public static String read(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE);
        String log = prefs.getString(KEY_LOG, "");
        return log.isEmpty() ? "(עדיין אין רשומות - היומן יתמלא אחרי שתופעל בדיקה)" : log;
    }

    public static void clear(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LOG, "").apply();
    }
}
