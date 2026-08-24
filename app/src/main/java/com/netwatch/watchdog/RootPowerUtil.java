package com.netwatch.watchdog;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.DataOutputStream;

/**
 * שני תיקוני אמינות ל"מעקב רשת לא עובד בכלל" (חשד שעלה בפועל): באנדרואיד
 * מודרני (וב-targetSdk 34 שהאפליקציה משתמשת בו כדי למנוע את דיאלוג
 * התאימות) המערכת נוטה לדחות/להשהות באגרסיביות אלארמים של אפליקציות
 * ברקע, במיוחד:
 *  1) אלארמים מדויקים (exact) דורשים הרשאה מיוחדת מ-API 31 ואילך.
 *  2) חיסכון סוללה (Doze/App Standby) יכול לדחות עד כדי שעות אלארמים
 *     של אפליקציה שלא סומנה כ"פטורה מאופטימיזציית סוללה".
 *
 * בדרך כלל שני אלה דורשים אישור המשתמש דרך דיאלוג מערכת - אבל במכשיר
 * מקשים ללא מסך מגע זו חוויה גרועה לנווט אליה. מאחר שהאפליקציה ממילא
 * דורשת רוט (לרענון הרשת עצמו), אפשר לתת את שתי ההרשאות האלה בשקט
 * לגמרי דרך su, בלי לגרור את המשתמש לשום דיאלוג.
 */
public final class RootPowerUtil {

    private RootPowerUtil() {
    }

    /**
     * מריץ את שני תיקוני האמינות. קוראים לזה פעם אחת כשמפעילים מעקב
     * כלשהו לראשונה, ומדי פעם שוב (ראו applyIfAnyMonitorEnabledAsync).
     * לא חוסם UI - יש להריץ מ-thread ברקע. שקט לחלוטין - כשלון בצד אחד
     * לא עוצר את השני, וכשלון בשניהם לא קריטי (האלארם עדיין ירוץ, פשוט
     * עלול להיות פחות מדויק בזמן).
     */
    public static void applyReliabilityFixesBlocking(Context context) {
        String pkg = context.getPackageName();

        if (Build.VERSION.SDK_INT >= 31) {
            // מעניק לאפליקציה הרשאת "אלארמים מדויקים" (Alarms & reminders) -
            // אותה הרשאה שבד"כ המשתמש נותן ידנית דרך הגדרות > אפליקציות.
            runRootCommand("appops set " + pkg + " SCHEDULE_EXACT_ALARM allow");
        }

        // מוציא את האפליקציה מרשימת חיסכון הסוללה של המערכת - אותה פעולה
        // שדיאלוג ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS היה מבקש
        // מהמשתמש לאשר, רק בלי הדיאלוג.
        runRootCommand("dumpsys deviceidle whitelist +" + pkg);
    }

    /**
     * גרסה נוחה שבודקת קודם אם יש בכלל מעקב פעיל (אחרת אין טעם), ומריצה
     * את הבדיקה/תיקון ב-thread נפרד כדי לא לחסום את ה-UI. קוראים לזה
     * מ-onResume של המסך הראשי ומסך ההגדרות - חלק מיצרני מכשירים (ROM-ים
     * אגרסיביים) יכולים להחזיר את האפליקציה לרשימת חיסכון סוללה מדי פעם
     * מבלי לשאול, אז "מרעננים" את הפטור בכל פתיחת מסך, לא רק בפעם הראשונה.
     */
    public static void applyIfAnyMonitorEnabledAsync(final Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE);
        boolean anyOn = prefs.getBoolean(AppPrefs.KEY_PHONE_MONITOR, false)
                || prefs.getBoolean(AppPrefs.KEY_INTERNET_MONITOR, false);
        if (!anyOn) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                applyReliabilityFixesBlocking(appContext);
            }
        }, "ReliabilityFixThread").start();
    }

    private static void runRootCommand(String shellCommand) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(shellCommand + "\n");
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
        } catch (Exception e) {
            // לא קריטי - ראו הערת ה-Javadoc למעלה.
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
