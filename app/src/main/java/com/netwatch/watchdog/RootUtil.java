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
     * שקובץ su קיים על המכשיר. חוסמת - יש לקרוא רק מ-thread ברקע, לעולם
     * לא מה-UI thread.
     */
    public static boolean isRootGrantedBlocking() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            int exit = process.waitFor();
            return exit == 0 && line != null && line.contains("uid=0");
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
