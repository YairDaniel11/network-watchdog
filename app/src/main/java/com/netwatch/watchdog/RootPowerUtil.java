package com.netwatch.watchdog;

import android.content.Context;
import android.os.Build;

import java.io.DataOutputStream;

/**
 * שני תיקוני אמינות (הרשאת אלארמים מדויקים + פטור מחיסכון סוללה) שבדרך
 * כלל דורשים אישור המשתמש דרך דיאלוג מערכת - אבל במכשיר מקשים ללא מסך
 * מגע זו חוויה גרועה לנווט אליה. מאחר שהאפליקציה ממילא דורשת רוט (לרענון
 * הרשת עצמו), אפשר לתת את שתי ההרשאות האלה בשקט לגמרי דרך su.
 *
 * חשוב (תוקן אחרי דיווח בפועל): בגרסה קודמת זה הופעל מחדש בכל פתיחת מסך
 * (onResume) "ליתר ביטחון" - טעות, כי כל קריאת su גורמת למנהל ה-root
 * במכשיר להציג הודעה/טוסט משלו, וזה יצר הצפה של הודעות "הוענקו הרשאות..."
 * כל הזמן. עכשיו זה רץ *פעם אחת בלבד* - כשמפעילים מעקב לראשונה - ושתי
 * הפקודות רצות בתוך אותה הרשאת-על אחת (לא שתי קריאות su נפרדות), כדי
 * לצמצם עוד יותר את מספר הפעמים שבאמת פונים לרוט.
 */
public final class RootPowerUtil {

    private RootPowerUtil() {
    }

    /** גרסה לא-חוסמת - מריצה ב-thread נפרד, בטוחה לקרוא מה-UI thread. */
    public static void applyReliabilityFixesAsync(Context context) {
        final Context appContext = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                applyReliabilityFixesBlocking(appContext);
            }
        }, "ReliabilityFixThread").start();
    }

    /** גרסה חוסמת - יש לקרוא רק מ-thread ברקע. */
    public static void applyReliabilityFixesBlocking(Context context) {
        String pkg = context.getPackageName();
        StringBuilder cmd = new StringBuilder();
        if (Build.VERSION.SDK_INT >= 31) {
            // מעניק לאפליקציה הרשאת "אלארמים מדויקים" (Alarms & reminders) -
            // אותה הרשאה שבד"כ המשתמש נותן ידנית דרך הגדרות > אפליקציות.
            cmd.append("appops set ").append(pkg).append(" SCHEDULE_EXACT_ALARM allow; ");
        }
        // מוציא את האפליקציה מרשימת חיסכון הסוללה של המערכת - אותה פעולה
        // שדיאלוג ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS היה מבקש
        // מהמשתמש לאשר, רק בלי הדיאלוג.
        cmd.append("dumpsys deviceidle whitelist +").append(pkg);

        boolean ok = runRootCommand(cmd.toString());
        DiagnosticsLog.log(context, "תיקוני רוט (אלארם/סוללה): " + (ok ? "בוצע" : "נכשל"));
    }

    private static boolean runRootCommand(String shellCommand) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(shellCommand + "\n");
            os.writeBytes("exit\n");
            os.flush();
            int exit = process.waitFor();
            return exit == 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
