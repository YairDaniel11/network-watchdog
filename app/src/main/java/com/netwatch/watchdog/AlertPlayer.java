package com.netwatch.watchdog;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Vibrator;

/**
 * מנגן התרעת קול/רטט לפי ההגדרות שנשמרו במסך ההגדרות - נקרא רק במעברי
 * מצב (ירידת קליטה / חזרת קליטה), לא בכל בדיקה - ראו NetworkCheckReceiver.
 */
public final class AlertPlayer {

    private AlertPlayer() {
    }

    private static final long VIBRATE_MS = 800L;
    // המשתמש ביקש שהצליל יישמע 3 שניות בלבד, לא במלואו (חלק מהצלילים
    // שאפשר לבחור מהמכשיר - כמו רינגטונים - יכולים להיות ארוכים בהרבה).
    private static final long SOUND_DURATION_MS = 3000L;

    public static void playLostAlert(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(AppPrefs.KEY_ALERT_LOST_ENABLED, false)) {
            return;
        }
        play(context,
                prefs.getString(AppPrefs.KEY_ALERT_LOST_SOUND_URI, null),
                prefs.getString(AppPrefs.KEY_ALERT_LOST_MODE, AppPrefs.ALERT_MODE_SOUND_VIBRATE));
    }

    public static void playRestoredAlert(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(AppPrefs.KEY_ALERT_RESTORED_ENABLED, false)) {
            return;
        }
        play(context,
                prefs.getString(AppPrefs.KEY_ALERT_RESTORED_SOUND_URI, null),
                prefs.getString(AppPrefs.KEY_ALERT_RESTORED_MODE, AppPrefs.ALERT_MODE_SOUND_VIBRATE));
    }

    private static void play(Context context, String soundUriString, String mode) {
        boolean wantSound = !AppPrefs.ALERT_MODE_VIBRATE_ONLY.equals(mode);
        boolean wantVibrate = !AppPrefs.ALERT_MODE_SOUND_ONLY.equals(mode);

        if (wantSound) {
            try {
                Uri uri = (soundUriString != null && !soundUriString.isEmpty())
                        ? Uri.parse(soundUriString)
                        : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                Ringtone ringtone = RingtoneManager.getRingtone(context, uri);
                if (ringtone != null) {
                    ringtone.play();
                    try {
                        Thread.sleep(SOUND_DURATION_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        // עוצרים אחרי 3 שניות תמיד - גם אם הצליל עצמו ארוך יותר
                        // (למשל רינגטון). אם הצליל כבר הסתיים לבד קודם, stop() לא עושה כלום.
                        ringtone.stop();
                    }
                }
            } catch (Exception e) {
                // אם הצליל שנבחר נמחק/לא זמין יותר - לא מפילים את הבדיקה בגללו,
                // פשוט מדלגים על חלק הצליל (הרטט, אם ביקשו, עדיין יתבצע).
            }
        }

        if (wantVibrate) {
            try {
                Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(VIBRATE_MS);
                }
            } catch (Exception e) {
                // כנ"ל - לא קריטי, לא מפילים את הבדיקה.
            }
        }
    }
}
