package com.netwatch.watchdog;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
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
            DiagnosticsLog.log(context, "התרעת \"אין קליטה\" כבויה בהגדרות - לא מנגן");
            return;
        }
        play(context, "אין קליטה",
                prefs.getString(AppPrefs.KEY_ALERT_LOST_SOUND_URI, null),
                prefs.getString(AppPrefs.KEY_ALERT_LOST_MODE, AppPrefs.ALERT_MODE_SOUND_VIBRATE));
    }

    public static void playRestoredAlert(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(AppPrefs.KEY_ALERT_RESTORED_ENABLED, false)) {
            DiagnosticsLog.log(context, "התרעת \"חזרה קליטה\" כבויה בהגדרות - לא מנגן");
            return;
        }
        play(context, "חזרה קליטה",
                prefs.getString(AppPrefs.KEY_ALERT_RESTORED_SOUND_URI, null),
                prefs.getString(AppPrefs.KEY_ALERT_RESTORED_MODE, AppPrefs.ALERT_MODE_SOUND_VIBRATE));
    }

    private static void play(Context context, String eventLabel, String soundUriString, String mode) {
        boolean wantSound = !AppPrefs.ALERT_MODE_VIBRATE_ONLY.equals(mode);
        boolean wantVibrate = !AppPrefs.ALERT_MODE_SOUND_ONLY.equals(mode);
        String soundResult = "לא התבקש";
        String vibrateResult = "לא התבקש";

        if (wantSound) {
            try {
                Uri uri = (soundUriString != null && !soundUriString.isEmpty())
                        ? Uri.parse(soundUriString)
                        : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                Ringtone ringtone = RingtoneManager.getRingtone(context, uri);
                if (ringtone != null) {
                    // חשוב: בלי זה, הצליל מתנגן על ה"סטרים" של התראות
                    // רגילות - שמכבד את מצב "שקט/רטט" של המכשיר, ואז
                    // ה-play() "מצליח" מבחינת הקוד אבל שום דבר לא נשמע
                    // בפועל אם הרינגר במצב שקט. STREAM_ALARM מיועד
                    // בדיוק למקרה הזה - הוא נשמע גם במצב שקט (כמו שעון
                    // מעורר). setStreamType מוצא כ-deprecated (הוחלף
                    // ב-setAudioAttributes מ-API 21), אבל נשאר פעיל
                    // ותומך בכל הגרסאות כולל אנדרואיד 4.4 הישן - נבחר
                    // בכוונה כדי לא לפצל קוד בין גרסאות API.
                    ringtone.setStreamType(AudioManager.STREAM_ALARM);
                    ringtone.play();
                    soundResult = "נוגן (סטרים אלארם)";
                    try {
                        Thread.sleep(SOUND_DURATION_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        // עוצרים אחרי 3 שניות תמיד - גם אם הצליל עצמו ארוך יותר
                        // (למשל רינגטון). אם הצליל כבר הסתיים לבד קודם, stop() לא עושה כלום.
                        ringtone.stop();
                    }
                } else {
                    soundResult = "נכשל (RingtoneManager החזיר null - הצליל שנבחר כנראה לא קיים יותר)";
                }
            } catch (Exception e) {
                // אם הצליל שנבחר נמחק/לא זמין יותר - לא מפילים את הבדיקה בגללו,
                // פשוט מדלגים על חלק הצליל (הרטט, אם ביקשו, עדיין יתבצע).
                soundResult = "נכשל (" + e.getClass().getSimpleName() + ")";
            }
        }

        if (wantVibrate) {
            try {
                Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(VIBRATE_MS);
                    vibrateResult = "בוצע";
                } else {
                    vibrateResult = "אין מנוע רטט במכשיר";
                }
            } catch (Exception e) {
                // כנ"ל - לא קריטי, לא מפילים את הבדיקה.
                vibrateResult = "נכשל (" + e.getClass().getSimpleName() + ")";
            }
        }

        DiagnosticsLog.log(context, "התרעת \"" + eventLabel + "\": צליל=" + soundResult + " | רטט=" + vibrateResult);
    }
}
