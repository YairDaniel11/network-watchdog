package com.netwatch.watchdog;

import java.io.DataOutputStream;

/**
 * מפעיל/מכבה מצב טיסה דרך פקודות רוט (su) - כדי לגרום למכשיר "לתפוס" רשת
 * מחדש בלי צורך באינטראקציה של המשתמש עם מסך ההגדרות. דורש שהמכשיר יהיה
 * בעל הרשאות רוט (ראו RootUtil).
 *
 * הרענון המלא (toggleAirplaneModeBlocking) מורץ כשורת su *אחת* עם sleep
 * מובנה בתוכה (במקום שתי קריאות su נפרדות עם Thread.sleep בקוד Java
 * שביניהן) - כך שכל הרצף (הפעלה, broadcast, המתנה, כיבוי, broadcast) קורה
 * בתוך אותה הרשאת-על אחת, ולא תלוי בכך שהאפליקציה עצמה תישאר בחיים/ערה
 * בין שתי קריאות su נפרדות.
 *
 * שתי הפקודות (settings put + am broadcast) יחד בכל שלב: הראשונה משנה את
 * הדגל הפנימי במערכת, השנייה משדרת לכל הרכיבים הרלוונטיים (כולל שבב
 * הרדיו/מודם) שהמצב השתנה בפועל - בלי ה-broadcast, חלק מהמכשירים משאירים
 * את הרדיו במצב הקודם למרות שהדגל התעדכן.
 */
public final class AirplaneModeToggler {

    private AirplaneModeToggler() {
    }

    private static final int OFF_DELAY_SECONDS = 3;

    /**
     * הרענון המלא: מצב טיסה דלוק -> 3 שניות -> כבוי. חוסמת כ-3 שניות - יש
     * לקרוא לזה תמיד מ-thread ברקע (לעולם לא מה-UI thread).
     *
     * @return true אם הפקודה הסתיימה בהצלחה (exit code 0).
     */
    public static boolean toggleAirplaneModeBlocking() {
        return runRootCommand(
                "settings put global airplane_mode_on 1"
                        + " && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true"
                        + " && sleep " + OFF_DELAY_SECONDS
                        + " && settings put global airplane_mode_on 0"
                        + " && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false");
    }

    /**
     * מכבה מצב טיסה מיידית, בלי קודם להדליק אותו - לתרחיש הספציפי שבו
     * מצב הטיסה כבר דלוק (למשל הופעל ידנית ע"י המשתמש בטעות) ופשוט צריך
     * לצאת ממנו. לא חוסמת (פעולה מיידית, אין sleep).
     *
     * @return true אם הפקודה הסתיימה בהצלחה.
     */
    public static boolean forceAirplaneModeOff() {
        return runRootCommand(
                "settings put global airplane_mode_on 0"
                        + " && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false");
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
