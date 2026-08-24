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
