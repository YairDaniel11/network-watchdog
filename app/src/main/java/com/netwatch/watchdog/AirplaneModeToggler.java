package com.netwatch.watchdog;

/**
 * מפעיל/מכבה מצב טיסה דרך פקודות רוט (su) - כדי לגרום למכשיר "לתפוס" רשת
 * מחדש בלי צורך באינטראקציה של המשתמש עם מסך ההגדרות. דורש שהמכשיר יהיה
 * בעל הרשאות רוט (ראו RootUtil).
 *
 * כל קריאת su עוברת דרך RootShell עם תקרת זמן - קריטי: בלי זה, אם מנהל
 * ה-root במכשיר נתקע (ממתין לאישור שאף אחד לא יכול לתת לו מקריאה
 * שמגיעה מהרקע), הבדיקה כולה נתקעת לנצח - זה בדיוק מה שגרם ליומן
 * האבחון להישאר ריק בכל פעם שבאמת הייתה בעיית רשת (הפעם היחידה שבאמת
 * קוראים ל-su).
 */
public final class AirplaneModeToggler {

    private AirplaneModeToggler() {
    }

    private static final int OFF_DELAY_SECONDS = 3;
    // תקרת זמן ל-su עצמו: 3 שניות ה-sleep המובנה בפקודה + מרווח נדיב
    // ל-su/settings/am (הפעלת רדיו יכולה להיות איטית במיוחד תחת עומס).
    private static final long TOGGLE_TIMEOUT_MS = 12_000L;
    private static final long FORCE_OFF_TIMEOUT_MS = 6_000L;

    /**
     * הרענון המלא: מצב טיסה דלוק -> 3 שניות -> כבוי. חוסמת עד
     * TOGGLE_TIMEOUT_MS - יש לקרוא לזה תמיד מ-thread ברקע.
     *
     * @return true אם הפקודה הסתיימה בהצלחה (exit code 0).
     */
    public static boolean toggleAirplaneModeBlocking() {
        RootShell.Result result = RootShell.exec(
                "settings put global airplane_mode_on 1"
                        + " && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true"
                        + " && sleep " + OFF_DELAY_SECONDS
                        + " && settings put global airplane_mode_on 0"
                        + " && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false",
                TOGGLE_TIMEOUT_MS);
        return result.success;
    }

    /** גרסה עם פרטי תקלה (הצליח/נכשל/נתקע-בזמן) - לשימוש ביומן האבחון. */
    public static RootShell.Result toggleAirplaneModeWithDetail() {
        return RootShell.exec(
                "settings put global airplane_mode_on 1"
                        + " && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true"
                        + " && sleep " + OFF_DELAY_SECONDS
                        + " && settings put global airplane_mode_on 0"
                        + " && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false",
                TOGGLE_TIMEOUT_MS);
    }

    /**
     * מכבה מצב טיסה מיידית, בלי קודם להדליק אותו - לתרחיש הספציפי שבו
     * מצב הטיסה כבר דלוק (למשל הופעל ידנית ע"י המשתמש בטעות).
     *
     * @return true אם הפקודה הסתיימה בהצלחה.
     */
    public static boolean forceAirplaneModeOff() {
        return forceAirplaneModeOffWithDetail().success;
    }

    /** גרסה עם פרטי תקלה - לשימוש ביומן האבחון. */
    public static RootShell.Result forceAirplaneModeOffWithDetail() {
        return RootShell.exec(
                "settings put global airplane_mode_on 0"
                        + " && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false",
                FORCE_OFF_TIMEOUT_MS);
    }
}
