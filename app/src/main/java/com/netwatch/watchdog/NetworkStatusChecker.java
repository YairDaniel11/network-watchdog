package com.netwatch.watchdog;

import android.content.Context;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * שתי בדיקות עצמאיות זו מזו - מפעילות/מכבות אחת דרך "מעקב רשת טלפונית"
 * והשנייה דרך "מעקב אינטרנט" (שני הכפתורים במסך הראשי). אפשר להפעיל אחת
 * בלי השנייה. בנוסף - בדיקת מצב טיסה ישירה (ראו isAirplaneModeOn).
 */
public final class NetworkStatusChecker {

    private NetworkStatusChecker() {
    }

    private static final int HTTP_TIMEOUT_MS = 4000;

    /**
     * בדיקה קלה וסינכרונית (לא חוסמת רשת) - האם יש רישום לרשת סלולרית
     * כלשהי כרגע. חשוב: בכוונה *לא* בודקים כאן את מצב ה-SIM
     * (getSimState) כתנאי לדילוג - במכשירים רבים מצב ה-SIM מדווח כ"לא
     * מוכן" גם כשה-SIM פיזית תקין, פשוט כי הרדיו כבוי (בדיוק המצב של
     * מצב טיסה ידני שרצינו לתפוס!). התנאי היחיד לדילוג הוא שלמכשיר אין
     * בכלל יכולת סלולרית מבחינת חומרה (PHONE_TYPE_NONE) - עובדה קבועה
     * של המכשיר, לא מצב זמני שיכול להסוות אירוע אמיתי של אובדן קליטה.
     */
    public static boolean isPhoneNetworkAvailable(Context context) {
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null || tm.getPhoneType() == TelephonyManager.PHONE_TYPE_NONE) {
                return true; // אין רדיו סלולרי בחומרה בכלל - לא רלוונטי, לא נוגעים
            }
            boolean hasNetworkType = tm.getNetworkType() != TelephonyManager.NETWORK_TYPE_UNKNOWN;
            String operator = tm.getNetworkOperator();
            boolean hasOperator = operator != null && !operator.trim().isEmpty();
            return hasNetworkType || hasOperator;
        } catch (SecurityException e) {
            return true; // אין הרשאת READ_PHONE_STATE - לא נתקע, לא נוגעים
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * בדיקה ישירה של דגל מצב הטיסה במערכת (לא דורשת שום הרשאה - Settings.Global
     * ציבורי לקריאה מ-API 17 ואילך). זו הבדיקה שפותרת את התרחיש "העברתי
     * למצב טיסה ידנית" - בלי קשר לשאלה אם TelephonyManager "מבין" את זה:
     * אם הדגל דלוק בזמן שמעקב כלשהו פעיל, המעקב אמור לכבות אותו.
     */
    public static boolean isAirplaneModeOn(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * בדיקת אינטרנט אמיתית - לא רק "יש רשת רשומה" אלא שיש בפועל תגובה.
     * חוסמת - יש לקרוא רק מ-thread ברקע.
     *
     * שיטה: קודם ping (שכבת IP גרידא, בלי DNS ובלי HTTPS/TLS בכלל) לכתובת
     * IP קבועה (8.8.8.8) - *לא* דרך su, כי ping הוא אחת הפקודות המעטות
     * שמותקנות עם הרשאת הרצה לכל אפליקציה רגילה באנדרואיד (כולל אנדרואיד
     * 4.4 הישן). זה נבחר בכוונה במקום בדיקת HTTPS שהייתה כאן קודם: על
     * מכשיר אנדרואיד 4.4 ישן שמדבר עם שרתי גוגל של 2026, ה-handshake של
     * TLS עצמו יכול להיכשל (גרסאות/צירי הצפנה ישנים שגוגל כבר לא תומכת
     * בהם) *גם כשיש אינטרנט תקין בפועל* - מה שהופך את הבדיקה הקודמת
     * ללא-אמינה בדיוק במכשיר היעד. ping לכתובת IP עוקף את זה לגמרי.
     * רק אם ping לא זמין בכלל (חלק מה-ROM-ים מסירים אותו) נופלים חזרה
     * לבדיקת HTTP רגיל (לא HTTPS) מול כתובת בדיקת הקישוריות של אנדרואיד
     * עצמו - אותה כתובת שהמערכת משתמשת בה פנימית לבדיקת captive portal,
     * ולכן נבדקת ותומכת בכל גרסת אנדרואיד כולל הישנות ביותר.
     */
    public static boolean isInternetReachable() {
        Boolean pingResult = tryPing();
        if (pingResult != null) {
            return pingResult;
        }
        return tryPlainHttpFallback();
    }

    /** @return true/false אם ping רץ בהצלחה ונתן תשובה חד-משמעית, null אם הפקודה עצמה לא זמינה/נכשלה להריץ. */
    private static Boolean tryPing() {
        Process process = null;
        try {
            // -c 1: חבילה אחת בלבד. -w 3: תקרת זמן כוללת של 3 שניות (אות
            // קטנה, לא גדולה - נתמך גם ב-toolbox הישן של אנדרואיד 4.4,
            // בניגוד ל--W עם אות גדולה שקיים רק בגרסאות ping מודרניות יותר).
            process = Runtime.getRuntime().exec(new String[]{"ping", "-c", "1", "-w", "3", "8.8.8.8"});
            // תקרת זמן נוספת ברמת הקוד (לא סומכים רק על "-w 3" של ping
            // עצמו - ROM-ים מסוימים מתעלמים מהדגל) - אותו מנגנון בטוח
            // מפני תקיעה שמשמש גם לקריאות su (ראו RootShell).
            RootShell.Result result = RootShell.waitForProcess(process, 5_000L);
            if (result.timedOut) {
                return null; // לא הצליחו לקבל תשובה חד-משמעית - נופלים ל-HTTP
            }
            return result.success;
        } catch (Exception e) {
            return null; // ping לא זמין/נכשל להרצה - לא מסיקים כלום, עוברים ל-HTTP
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static boolean tryPlainHttpFallback() {
        HttpURLConnection connection = null;
        try {
            // אותה כתובת שאנדרואיד עצמו משתמש בה לבדיקת קישוריות/captive
            // portal פנימית - HTTP רגיל (לא HTTPS), כדי לעקוף בעיות TLS
            // אפשריות במכשיר ישן.
            URL url = new URL("http://connectivitycheck.gstatic.com/generate_204");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(HTTP_TIMEOUT_MS);
            connection.setReadTimeout(HTTP_TIMEOUT_MS);
            connection.setRequestProperty("Connection", "close");
            connection.setInstanceFollowRedirects(false);
            connection.connect();
            int code = connection.getResponseCode();
            return code == 204 || code == 200;
        } catch (Exception e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
