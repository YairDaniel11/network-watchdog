package com.netwatch.watchdog;

import android.content.Context;

/**
 * מפעיל/מכבה מצב טיסה דרך פקודות רוט (su) - כדי לגרום למכשיר "לתפוס" רשת
 * מחדש בלי צורך באינטראקציה של המשתמש עם מסך ההגדרות. דורש שהמכשיר יהיה
 * בעל הרשאות רוט (ראו RootUtil).
 *
 * כל קריאת su עוברת דרך RootShell עם תקרת זמן - קריטי: בלי זה, אם מנהל
 * ה-root במכשיר נתקע (ממתין לאישור שאף אחד לא יכול לתת לו מקריאה
 * שמגיעה מהרקע), הבדיקה כולה נתקעת לנצח.
 *
 * חשוב (תוקן אחרי דיווח בפועל): ההצלחה/כישלון נקבעים לפי המצב *בפועל*
 * (קריאה חוזרת של Settings.Global.AIRPLANE_MODE_ON אחרי הניסיון) - לא
 * רק לפי קוד היציאה של פקודת ה-shell. הסיבה: הפקודה מריצה שני שלבים
 * (שינוי ה-setting, ואז שידור broadcast שמודיע לרדיו על השינוי) - אם
 * שלב ה-broadcast נכשל (למשל מגבלת הרשאות SELinux על ROM מסוים) אבל
 * שלב שינוי ה-setting עצמו כן הצליח, קוד היציאה המשולב יראה "נכשל"
 * למרות שהמצב בפועל כן השתנה. בנוסף, השלבים רצים עם ";" ולא "&&" - כדי
 * ששלב מאוחר עדיין ירוץ גם אם שלב קודם נכשל, במקום שהשרשרת כולה תיעצר.
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
     */
    public static boolean toggleAirplaneModeBlocking(Context context) {
        return toggleAirplaneModeWithDetail(context).success;
    }

    /** גרסה עם פרטי תקלה (הצליח/נכשל/נתקע-בזמן), מבוססת על המצב בפועל - לשימוש ביומן האבחון. */
    public static RootShell.Result toggleAirplaneModeWithDetail(Context context) {
        RootShell.Result shellResult = RootShell.exec(
                "settings put global airplane_mode_on 1"
                        + " ; am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true"
                        + " ; sleep " + OFF_DELAY_SECONDS
                        + " ; settings put global airplane_mode_on 0"
                        + " ; am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false",
                TOGGLE_TIMEOUT_MS);
        boolean actuallyOff = !NetworkStatusChecker.isAirplaneModeOn(context);
        // אמת-קרקע מנצחת: אם המצב בפועל בסוף הוא "כבוי" (מה שרצינו),
        // זו הצלחה - גם אם ה-shell עצמו החזיר קוד יציאה שאינו אפס.
        return new RootShell.Result(actuallyOff, shellResult.timedOut && !actuallyOff);
    }

    /**
     * מכבה מצב טיסה מיידית, בלי קודם להדליק אותו - לתרחיש הספציפי שבו
     * מצב הטיסה כבר דלוק (למשל הופעל ידנית ע"י המשתמש בטעות).
     */
    public static boolean forceAirplaneModeOff(Context context) {
        return forceAirplaneModeOffWithDetail(context).success;
    }

    /** גרסה עם פרטי תקלה, מבוססת על המצב בפועל - לשימוש ביומן האבחון. */
    public static RootShell.Result forceAirplaneModeOffWithDetail(Context context) {
        RootShell.Result shellResult = RootShell.exec(
                "settings put global airplane_mode_on 0"
                        + " ; am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false",
                FORCE_OFF_TIMEOUT_MS);
        boolean actuallyOff = !NetworkStatusChecker.isAirplaneModeOn(context);
        return new RootShell.Result(actuallyOff, shellResult.timedOut && !actuallyOff);
    }
}
