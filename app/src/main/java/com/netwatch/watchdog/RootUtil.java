package com.netwatch.watchdog;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * בדיקת רוט - נדרשת כי החלפת מצב טיסה באופן פרוגרמטי (בלי לפתוח את מסך
 * ההגדרות למשתמש) דורשת הרשאת מערכת (WRITE_SECURE_SETTINGS) שאפליקציה
 * רגילה לא יכולה לקבל - רק דרך גישת רוט (su). ראו AirplaneModeToggler.
 */
public final class RootUtil {

    private RootUtil() {
    }

    private static final long CHECK_TIMEOUT_MS = 6_000L;

    private static final String[] SU_PATHS = {
            "/system/xbin/su", "/system/bin/su", "/sbin/su",
            "/system/sd/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
            "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su",
            "/system/app/Superuser.apk", "/vendor/bin/su"
    };

    /** בדיקה מהירה (לא חוסמת su בפועל) - רק אם קובץ su קיים בנתיב ידוע. שימושית לתצוגה ראשונית מיידית ב-UI. */
    public static boolean quickCheckSuBinaryExists() {
        for (String path : SU_PATHS) {
            if (new File(path).exists()) {
                return true;
            }
        }
        return false;
    }

    /**
     * בדיקה מלאה - מריצה בפועל "su -c id" ובודקת ש-uid=0 חזר, כלומר
     * שהמשתמש אכן אישר (או שההרשאה כבר קיימת) גישת רוט לאפליקציה, לא רק
     * שקובץ su קיים על המכשיר. חוסמת עד CHECK_TIMEOUT_MS לכל היותר - יש
     * לקרוא רק מ-thread ברקע, לעולם לא מה-UI thread.
     *
     * חשוב: הבדיקה הישנה קראה שורת פלט (readLine) *לפני* שהמתינה לסיום
     * התהליך - readLine עצמו יכול להיתקע לנצח אם מנהל ה-root תקוע. עכשיו
     * קודם ממתינים (עם תקרת זמן, דרך RootShell) שהתהליך יסיים, ורק אחר
     * כך קוראים את הפלט שכבר מוכן ומחכה בבאפר - קריאה כזו לא יכולה
     * להיתקע כי התהליך כבר סיים לרוץ.
     */
    public static boolean isRootGrantedBlocking() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            RootShell.Result result = RootShell.waitForProcess(process, CHECK_TIMEOUT_MS);
            if (!result.success) {
                return false; // נכשל או נתקע בזמן - בכל מקרה אין רוט מאושר בפועל
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            return line != null && line.contains("uid=0");
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
