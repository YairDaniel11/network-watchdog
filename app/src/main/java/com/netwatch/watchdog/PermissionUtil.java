package com.netwatch.watchdog;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * עוטף בקשת ההרשאה היחידה שדורשת דיאלוג בזמן ריצה באפליקציה הזו -
 * READ_PHONE_STATE, נחוצה רק אם המשתמש מפעיל "מעקב רשת טלפונית".
 * (INTERNET/ACCESS_NETWORK_STATE הן הרשאות "רגילות", ניתנות אוטומטית
 * בהתקנה בלי דיאלוג, בכל גרסת אנדרואיד.)
 */
public final class PermissionUtil {

    private PermissionUtil() {
    }

    public static final int REQUEST_CODE_PHONE_STATE = 2001;

    public static boolean hasReadPhoneState(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true; // לפני מודל ההרשאות בזמן ריצה - כבר אושר בהתקנה
        }
        return activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestReadPhoneState(Activity activity) {
        activity.requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_CODE_PHONE_STATE);
    }
}
