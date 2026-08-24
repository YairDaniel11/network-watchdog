package com.netwatch.watchdog;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * מסך ראשי - מכוון להיות מינימלי ככל האפשר: כפתור רענון ידני למעלה,
 * שורת סטטוס רוט מתחתיו, וכפתור "הגדרות" בתחתית שפותח את SettingsActivity
 * (שם נמצאים מעקבי הרשת והתרעות הקול/רטט).
 */
public class MainActivity extends Activity implements View.OnClickListener {

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private TextView rootStatusText;
    private Button btnManualRefresh;
    private TextView manualRefreshStatusText;

    private boolean manualRefreshInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.applyTheme(this);
        setContentView(R.layout.activity_main);

        rootStatusText = (TextView) findViewById(R.id.rootStatusText);
        btnManualRefresh = (Button) findViewById(R.id.btnManualRefresh);
        manualRefreshStatusText = (TextView) findViewById(R.id.manualRefreshStatusText);
        Button btnSettings = (Button) findViewById(R.id.btnSettings);

        btnManualRefresh.setOnClickListener(this);
        btnSettings.setOnClickListener(this);

        checkRootStatusAsync();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // תיקון-עצמי: כמה מיצרני מכשירים (במיוחד ROM-ים סיניים אגרסיביים
        // עם "מנקה זיכרון" מובנה) יכולים להרוג את האלארם החוזר מבחוץ בלי
        // שהאפליקציה תדע, ואפילו להחזיר את האפליקציה לרשימת חיסכון
        // הסוללה מדי פעם. כל פתיחה של המסך הראשי רושמת את האלארם מחדש
        // (קריאה אידמפוטנטית וזולה - לא עושה כלום אם המעקב כבוי) ומריצה
        // שוב את תיקוני הרוט ברקע אם מעקב כלשהו פעיל.
        AlarmScheduler.scheduleIfNeeded(this);
        RootPowerUtil.applyIfAnyMonitorEnabledAsync(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btnManualRefresh) {
            onManualRefreshClicked();
        } else if (v.getId() == R.id.btnSettings) {
            startActivity(new Intent(this, SettingsActivity.class));
        }
    }

    // ---------- סטטוס רוט ----------

    private void checkRootStatusAsync() {
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
}
