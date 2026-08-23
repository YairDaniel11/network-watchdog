package com.netwatch.watchdog;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * מסך יחיד: סטטוס רוט, כפתור רענון רשת ידני, ושני כפתורי מעקב עצמאיים
 * (רשת טלפונית / אינטרנט) שכל אחד מהם מפעיל/מכבה בנפרד את הבדיקה
 * התקופתית (ראו AlarmScheduler + NetworkCheckReceiver).
 *
 * במכוון בלי AsyncTask/Service - כל פעולה ברקע היא Thread רגיל שחוזר
 * ל-UI thread דרך Handler, כדי לשמור על טביעת רגל RAM מינימלית.
 */
public class MainActivity extends Activity implements View.OnClickListener {

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private TextView rootStatusText;
    private Button btnManualRefresh;
    private TextView manualRefreshStatusText;
    private Button btnPhoneMonitor;
    private Button btnInternetMonitor;

    private boolean manualRefreshInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.applyTheme(this);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(AppPrefs.PREFS_NAME, MODE_PRIVATE);

        rootStatusText = (TextView) findViewById(R.id.rootStatusText);
        btnManualRefresh = (Button) findViewById(R.id.btnManualRefresh);
        manualRefreshStatusText = (TextView) findViewById(R.id.manualRefreshStatusText);
        btnPhoneMonitor = (Button) findViewById(R.id.btnPhoneMonitor);
        btnInternetMonitor = (Button) findViewById(R.id.btnInternetMonitor);

        btnManualRefresh.setOnClickListener(this);
        btnPhoneMonitor.setOnClickListener(this);
        btnInternetMonitor.setOnClickListener(this);

        checkRootStatusAsync();
        refreshToggleButtonsUi();
    }

    @Override
    public void onClick(View v) {
        if (v == btnManualRefresh) {
            onManualRefreshClicked();
        } else if (v == btnPhoneMonitor) {
            onPhoneMonitorToggleClicked();
        } else if (v == btnInternetMonitor) {
            onInternetMonitorToggleClicked();
        }
    }

    // ---------- סטטוס רוט ----------

    private void checkRootStatusAsync() {
        // תצוגה ראשונית מיידית (לא חוסמת) - בדיקת su מלאה רצה אחר כך ב-thread נפרד.
        rootStatusText.setText(getString(R.string.root_status_checking));

        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean rooted = RootUtil.isRootGrantedBlocking();
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }
                        rootStatusText.setText(rooted
                                ? getString(R.string.root_status_yes)
                                : getString(R.string.root_status_no));
                        rootStatusText.setTextColor(getResources().getColor(
                                rooted ? R.color.status_green : R.color.status_red));
                    }
                });
            }
        }, "RootCheckThread").start();
    }

    // ---------- רענון ידני ----------

    private void onManualRefreshClicked() {
        if (manualRefreshInProgress) {
            return;
        }
        manualRefreshInProgress = true;
        btnManualRefresh.setEnabled(false);
        manualRefreshStatusText.setText(getString(R.string.manual_refresh_in_progress));

        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean success = AirplaneModeToggler.toggleAirplaneModeBlocking();
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        manualRefreshInProgress = false;
                        if (isFinishing()) {
                            return;
                        }
                        btnManualRefresh.setEnabled(true);
                        manualRefreshStatusText.setText(getString(
                                success ? R.string.manual_refresh_done : R.string.manual_refresh_failed));
                    }
                });
            }
        }, "ManualRefreshThread").start();
    }

    // ---------- מעקב רשת טלפונית ----------

    private void onPhoneMonitorToggleClicked() {
        boolean currentlyOn = prefs.getBoolean(AppPrefs.KEY_PHONE_MONITOR, false);
        if (currentlyOn) {
            setPhoneMonitorEnabled(false);
            return;
        }
        // מפעילים: קודם צריך READ_PHONE_STATE (הרשאה בזמן ריצה מ-API 23+).
        if (PermissionUtil.hasReadPhoneState(this)) {
            setPhoneMonitorEnabled(true);
        } else {
            Toast.makeText(this, R.string.phone_permission_needed, Toast.LENGTH_LONG).show();
            PermissionUtil.requestReadPhoneState(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionUtil.REQUEST_CODE_PHONE_STATE) {
            boolean granted = grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (granted) {
                setPhoneMonitorEnabled(true);
            }
            // אם נדחה - המתג פשוט נשאר כבוי, אין צורך בטיפול נוסף.
        }
    }

    private void setPhoneMonitorEnabled(boolean enabled) {
        prefs.edit().putBoolean(AppPrefs.KEY_PHONE_MONITOR, enabled).apply();
        AlarmScheduler.scheduleIfNeeded(this);
        refreshToggleButtonsUi();
    }

    // ---------- מעקב אינטרנט ----------

    private void onInternetMonitorToggleClicked() {
        boolean currentlyOn = prefs.getBoolean(AppPrefs.KEY_INTERNET_MONITOR, false);
        prefs.edit().putBoolean(AppPrefs.KEY_INTERNET_MONITOR, !currentlyOn).apply();
        AlarmScheduler.scheduleIfNeeded(this);
        refreshToggleButtonsUi();
    }

    // ---------- עדכון תצוגת הכפתורים ----------

    private void refreshToggleButtonsUi() {
        boolean phoneOn = prefs.getBoolean(AppPrefs.KEY_PHONE_MONITOR, false);
        boolean internetOn = prefs.getBoolean(AppPrefs.KEY_INTERNET_MONITOR, false);

        applyToggleState(btnPhoneMonitor, phoneOn, getString(R.string.phone_monitor_title));
        applyToggleState(btnInternetMonitor, internetOn, getString(R.string.internet_monitor_title));
    }

    private void applyToggleState(Button button, boolean on, String title) {
        button.setBackgroundResource(on ? R.drawable.bg_toggle_on : R.drawable.bg_toggle_off);
        button.setText(title + "\n(" + getString(on ? R.string.monitor_on : R.string.monitor_off) + ")");
    }
}
