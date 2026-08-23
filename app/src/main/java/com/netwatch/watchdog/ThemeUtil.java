package com.netwatch.watchdog;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/**
 * פותר ומחיל את ערכת הנושא (בהיר/כהה) לפי הגדרות המכשיר, בלי תלות ב-AndroidX/
 * AppCompat (שהוסרו בכוונה מהפרויקט - ראו הערה ב-app/build.gradle). קוראים
 * לזה ב-onCreate, מיד אחרי super.onCreate ולפני setContentView.
 */
public final class ThemeUtil {

    private ThemeUtil() {
    }

    public static void applyTheme(Activity activity) {
        activity.setTheme(resolveDarkMode(activity) ? R.style.AppTheme_Dark : R.style.AppTheme_Light);
    }

    private static boolean resolveDarkMode(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(AppPrefs.PREFS_NAME, Activity.MODE_PRIVATE);
        String mode = prefs.getString(AppPrefs.KEY_THEME_MODE, AppPrefs.THEME_SYSTEM);

        if (AppPrefs.THEME_DARK.equals(mode)) {
            return true;
        }
        if (AppPrefs.THEME_LIGHT.equals(mode)) {
            return false;
        }

        // "מערכת": נבדק דרך Configuration.uiMode (API 8+, לא דורש AndroidX).
        // במכשיר ישן (כולל מכשיר המקשים היעד) הדגל הזה לא מוגדר בפועל,
        // ואז נשארים עם הכהה כברירת מחדל.
        int nightModeFlags = activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            return true;
        }
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_NO) {
            return false;
        }
        return true;
    }
}
