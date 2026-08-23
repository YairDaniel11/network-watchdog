package com.netwatch.watchdog;

import java.io.DataOutputStream;

/**
 * מפעיל מצב טיסה למשך 3 שניות ומכבה אותו חזרה, דרך פקודות רוט (su) - כדי
 * לגרום למכשיר "לתפוס" רשת מחדש בלי צורך באינטראקציה של המשתמש עם מסך
 * ההגדרות. דורש שהמכשיר יהיה בעל הרשאות רוט (ראו RootUtil).
 *
 * שתי הפקודות (settings put + am broadcast) יחד: הראשונה משנה את הדגל
 * הפנימי במערכת, השנייה משדרת לכל הרכיבים הרלוונטיים (כולל שבב הרדיו/מודם)
 * שהמצב השתנה בפועל - בלי ה-broadcast, חלק ממכשירים משאירים את הרדיו במצב
 * הקודם למרות שהדגל התעדכן.
 */
public final class AirplaneModeToggler {

    private AirplaneModeToggler() {
    }

    private static final long OFF_DELAY_MS = 3000L;

    /**
     * חוסמת למשך כ-3 שניות (זמן ההמתנה במצב טיסה) - יש לקרוא לזה תמיד
     * מ-thread ברקע (לעולם לא מה-UI thread), למשל מתוך Thread רגיל שנפתח
     * ב-MainActivity או מתוך NetworkCheckReceiver.
     *
     * @return true אם שתי הפקודות (הפעלה וכיבוי) הסתיימו בהצלחה.
     */
    public static boolean toggleAirplaneModeBlocking() {
        boolean onOk = runRootCommand(
                "settings put global airplane_mode_on 1 && "
                        + "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true");
        if (!onOk) {
            return false;
        }
        try {
            Thread.sleep(OFF_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return runRootCommand(
                "settings put global airplane_mode_on 0 && "
                        + "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false");
    }

    private static boolean runRootCommand(String shellCommand) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(shellCommand + "\n");
            os.writeBytes("exit\n");
            os.flush();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
