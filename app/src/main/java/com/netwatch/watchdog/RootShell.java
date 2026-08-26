package com.netwatch.watchdog;

import java.io.DataOutputStream;

/**
 * הרצת פקודות רוט (su) עם תקרת זמן קשיחה - זה קריטי: הגרסה הקודמת קראה
 * ל-Process.waitFor() בלי שום timeout. אם מנהל ה-root במכשיר נתקע (למשל
 * מחכה לאישור אינטראקטיבי שאף אחד לא נמצא לאשר אותו כי זו קריאה מהרקע),
 * ה-thread שלנו היה נתקע *לנצח* - וזה בדיוק מה שהסביר למה יומן האבחון
 * נשאר ריק בדיוק כשאין רשת: זו הפעם היחידה שבאמת קוראים ל-su (לנסות
 * לתקן), וההמתנה שם הייתה חוסמת ללא סוף, אז שורת היומן (שמגיעה אחרי
 * הקריאה בקוד) לעולם לא הייתה נכתבת.
 *
 * מכשירי API < 26 (המכשיר הישן שלנו כולל) לא תומכים ב-Process.waitFor
 * עם timeout (זו תוספת של Java 8 / API 26) - אז ממתינים בעצמנו ב-polling
 * קצר (בדיקת exitValue() כל 150ms) עד לתקרה, ואם עברה - הורגים את
 * התהליך ומחזירים כשלון, במקום להיתקע.
 */
public final class RootShell {

    private RootShell() {
    }

    private static final long POLL_INTERVAL_MS = 150L;

    public static final class Result {
        public final boolean success;
        public final boolean timedOut;

        private Result(boolean success, boolean timedOut) {
            this.success = success;
            this.timedOut = timedOut;
        }
    }

    /**
     * מריץ פקודת shell תחת su, עם תקרת זמן. חוסמת עד timeoutMs לכל היותר -
     * יש לקרוא רק מ-thread ברקע.
     */
    public static Result exec(String shellCommand, long timeoutMs) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(shellCommand + "\n");
            os.writeBytes("exit\n");
            os.flush();
            return waitForProcess(process, timeoutMs);
        } catch (Exception e) {
            return new Result(false, false);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /** גרסה ל-"su -c &lt;command&gt;" ישיר (בלי כתיבה ל-stdin) - למשל בדיקת רוט. */
    public static Result execDirect(String[] command, long timeoutMs) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(command);
            return waitForProcess(process, timeoutMs);
        } catch (Exception e) {
            return new Result(false, false);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /**
     * ממתינה לסיום תהליך שכבר רץ (exec נעשה בחוץ), עם תקרת זמן - ציבורית
     * כדי ש-RootUtil יוכל להשתמש בה גם כשהוא צריך לקרוא stdout אחרי
     * הסיום (ולא רק exit code). לא הורגת את התהליך אם הצליח לצאת לבד -
     * ההרג קורה רק במקרה timeout, כדי לא לאבד תוכן stdout שממתין בבאפר.
     */
    public static Result waitForProcess(Process process, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                int exitCode = process.exitValue();
                return new Result(exitCode == 0, false);
            } catch (IllegalThreadStateException stillRunning) {
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return new Result(false, false);
                }
            }
        }
        // עברה התקרה בלי שהתהליך יצא - כנראה תקוע (למשל מחכה לאישור
        // אינטראקטיבי שאף אחד לא יכול לתת לו מהרקע). הורגים ומוותרים,
        // במקום להיתקע לנצח.
        process.destroy();
        return new Result(false, true);
    }
}
