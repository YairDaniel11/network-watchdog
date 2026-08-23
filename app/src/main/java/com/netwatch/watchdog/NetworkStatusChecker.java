package com.netwatch.watchdog;

import android.content.Context;
import android.telephony.TelephonyManager;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * שתי בדיקות עצמאיות זו מזו - מפעילות/מכבות אחת דרך "מעקב רשת טלפונית"
 * והשנייה דרך "מעקב אינטרנט" (שני הכפתורים במסך הראשי). אפשר להפעיל אחת
 * בלי השנייה.
 */
public final class NetworkStatusChecker {

    private NetworkStatusChecker() {
    }

    private static final int HTTP_TIMEOUT_MS = 4000;

    /**
     * בדיקה קלה וסינכרונית (לא חוסמת רשת) - האם יש SIM פעיל שרשום לרשת
     * סלולרית כלשהי. זו בדיקה היוריסטית מכוונת ולא בדיקת "יש קליטה מלאה":
     * המטרה היא לזהות מצב "המכשיר נשר לגמרי מהרשת" (למשל אחרי כניסה
     * לאזור מת ויציאה ממנו בלי שהמודם התאושש לבד), לא לייעל כל תנודה
     * בעוצמת האות - ולכן היא לא דורשת PhoneStateListener/Looper כבד.
     */
    public static boolean isPhoneNetworkAvailable(Context context) {
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) {
                return true; // אין דרך לבדוק - לא נתקע במצב "כאילו אין רשת"
            }
            if (tm.getSimState() != TelephonyManager.SIM_STATE_READY) {
                return true; // אין SIM מוכן בכלל - לא רלוונטי, לא נוגעים
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
     * בדיקת אינטרנט אמיתית - לא רק "יש רשת רשומה" אלא שיש בפועל מענה
     * משרת. חוסמת (עד HTTP_TIMEOUT_MS) - יש לקרוא רק מ-thread ברקע.
     */
    public static boolean isInternetReachable() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://www.google.com/generate_204");
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
