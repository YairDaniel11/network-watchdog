package com.netwatch.watchdog;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

/**
 * מסך ההגדרות: מעקבי הרשת (טלפוני/אינטרנט) והתרעות קול/רטט (כשאין
 * קליטה, וכשהיא חוזרת). כל שינוי נשמר מיידית ל-SharedPreferences - אין
 * כפתור "שמור" נפרד.
 */
public class SettingsActivity extends Activity {

    private static final int REQUEST_CODE_SOUND_LOST = 3001;
    private static final int REQUEST_CODE_SOUND_RESTORED = 3002;

    private SharedPreferences prefs;

    private Button btnPhoneMonitor;
    private Button btnInternetMonitor;

    private RadioGroup radioInterval;
    /** ממופה minutes -> ה-RadioButton המתאים, כדי לבחור/לקרוא בקלות. */
    private final java.util.Map<Integer, RadioButton> intervalButtons = new java.util.HashMap<Integer, RadioButton>();

    private CheckBox checkAlertLostEnabled;
    private TextView alertLostSoundName;
    private RadioGroup radioAlertLostMode;

    private CheckBox checkAlertRestoredEnabled;
    private TextView alertRestoredSoundName;
    private RadioGroup radioAlertRestoredMode;

    private TextView diagnosticsLogText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.applyTheme(this);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(AppPrefs.PREFS_NAME, MODE_PRIVATE);

        btnPhoneMonitor = (Button) findViewById(R.id.btnPhoneMonitor);
        btnInternetMonitor = (Button) findViewById(R.id.btnInternetMonitor);

        radioInterval = (RadioGroup) findViewById(R.id.radioInterval);
        buildIntervalOptions();

        checkAlertLostEnabled = (CheckBox) findViewById(R.id.checkAlertLostEnabled);
        Button btnAlertLostSound = (Button) findViewById(R.id.btnAlertLostSound);
        alertLostSoundName = (TextView) findViewById(R.id.alertLostSoundName);
        radioAlertLostMode = (RadioGroup) findViewById(R.id.radioAlertLostMode);

        checkAlertRestoredEnabled = (CheckBox) findViewById(R.id.checkAlertRestoredEnabled);
        Button btnAlertRestoredSound = (Button) findViewById(R.id.btnAlertRestoredSound);
        alertRestoredSoundName = (TextView) findViewById(R.id.alertRestoredSoundName);
        radioAlertRestoredMode = (RadioGroup) findViewById(R.id.radioAlertRestoredMode);

        diagnosticsLogText = (TextView) findViewById(R.id.diagnosticsLogText);
        Button btnClearDiagnostics = (Button) findViewById(R.id.btnClearDiagnostics);
        btnClearDiagnostics.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DiagnosticsLog.clear(SettingsActivity.this);
                refreshDiagnosticsLog();
            }
        });

        btnPhoneMonitor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPhoneMonitorToggleClicked();
            }
        });
        btnInternetMonitor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onInternetMonitorToggleClicked();
            }
        });

        checkAlertLostEnabled.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(AppPrefs.KEY_ALERT_LOST_ENABLED, isChecked).apply();
            }
        });
        checkAlertRestoredEnabled.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(AppPrefs.KEY_ALERT_RESTORED_ENABLED, isChecked).apply();
            }
        });

        btnAlertLostSound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSoundPicker(REQUEST_CODE_SOUND_LOST, AppPrefs.KEY_ALERT_LOST_SOUND_URI);
            }
        });
        btnAlertRestoredSound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSoundPicker(REQUEST_CODE_SOUND_RESTORED, AppPrefs.KEY_ALERT_RESTORED_SOUND_URI);
            }
        });

        radioAlertLostMode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                prefs.edit().putString(AppPrefs.KEY_ALERT_LOST_MODE, modeForCheckedId(checkedId,
                        R.id.radioLostSoundVibrate, R.id.radioLostSoundOnly, R.id.radioLostVibrateOnly)).apply();
            }
        });
        radioAlertRestoredMode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                prefs.edit().putString(AppPrefs.KEY_ALERT_RESTORED_MODE, modeForCheckedId(checkedId,
                        R.id.radioRestoredSoundVibrate, R.id.radioRestoredSoundOnly, R.id.radioRestoredVibrateOnly)).apply();
            }
        });

        refreshAllUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // אותו תיקון-עצמי *קל* כמו במסך הראשי (רק רישום האלארם, לא רוט) - ראו שם.
        AlarmScheduler.scheduleIfNeeded(this);
        refreshAllUi();
    }

    private String modeForCheckedId(int checkedId, int soundVibrateId, int soundOnlyId, int vibrateOnlyId) {
        if (checkedId == soundOnlyId) {
            return AppPrefs.ALERT_MODE_SOUND_ONLY;
        } else if (checkedId == vibrateOnlyId) {
            return AppPrefs.ALERT_MODE_VIBRATE_ONLY;
        }
        return AppPrefs.ALERT_MODE_SOUND_VIBRATE;
    }

    // ---------- קצב הבדיקה ----------

    /**
     * בונה את כפתורי הרדיו של קצב הבדיקה בקוד (לא ב-XML) לפי
     * AlarmScheduler.INTERVAL_OPTIONS_MINUTES - כך שיש מקור אמת יחיד
     * לרשימת האפשרויות, ולא צריך לשמור אותה מסונכרנת בשני מקומות.
     */
    private void buildIntervalOptions() {
        int textColor = resolveThemeColor(R.attr.colorTextPrimary);
        for (final int minutes : AlarmScheduler.INTERVAL_OPTIONS_MINUTES) {
            RadioButton button = new RadioButton(this);
            button.setId(View.generateViewId());
            button.setText(intervalLabel(minutes));
            button.setTextColor(textColor);
            button.setTag(minutes);
            radioInterval.addView(button);
            intervalButtons.put(minutes, button);
        }
        radioInterval.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                View selected = group.findViewById(checkedId);
                if (selected == null || selected.getTag() == null) {
                    return;
                }
                int minutes = (Integer) selected.getTag();
                if (minutes == AlarmScheduler.getIntervalMinutes(SettingsActivity.this)) {
                    return; // לא באמת השתנה (למשל בחירה תוכנתית בזמן refreshAllUi)
                }
                AlarmScheduler.setIntervalMinutes(SettingsActivity.this, minutes);
                DiagnosticsLog.log(SettingsActivity.this, "קצב הבדיקה שונה ל" + intervalLabel(minutes));
            }
        });
    }

    private String intervalLabel(int minutes) {
        if (minutes == 1) {
            return getString(R.string.interval_one_minute);
        }
        if (minutes == 60) {
            return getString(R.string.interval_one_hour);
        }
        return String.format(java.util.Locale.getDefault(), getString(R.string.interval_minutes_format), minutes);
    }

    private void refreshIntervalSelection() {
        int currentMinutes = AlarmScheduler.getIntervalMinutes(this);
        RadioButton match = intervalButtons.get(currentMinutes);
        if (match != null && !match.isChecked()) {
            match.setChecked(true);
        }
    }

    private int resolveThemeColor(int attrResId) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(attrResId, typedValue, true);
        return typedValue.data;
    }

    // ---------- מעקב רשת טלפונית ----------

    private void onPhoneMonitorToggleClicked() {
        boolean currentlyOn = prefs.getBoolean(AppPrefs.KEY_PHONE_MONITOR, false);
        if (currentlyOn) {
            setPhoneMonitorEnabled(false);
            return;
        }
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
        }
    }

    private void setPhoneMonitorEnabled(boolean enabled) {
        prefs.edit().putBoolean(AppPrefs.KEY_PHONE_MONITOR, enabled).apply();
        DiagnosticsLog.log(this, "מעקב רשת טלפונית -> " + (enabled ? "הופעל" : "כובה"));
        onAnyMonitorChanged(enabled);
    }

    // ---------- מעקב אינטרנט ----------

    private void onInternetMonitorToggleClicked() {
        boolean currentlyOn = prefs.getBoolean(AppPrefs.KEY_INTERNET_MONITOR, false);
        boolean newState = !currentlyOn;
        prefs.edit().putBoolean(AppPrefs.KEY_INTERNET_MONITOR, newState).apply();
        DiagnosticsLog.log(this, "מעקב אינטרנט -> " + (newState ? "הופעל" : "כובה"));
        onAnyMonitorChanged(newState);
    }

    /**
     * נקרא בכל שינוי של אחד ממתגי המעקב. מתזמן/מבטל את האלארם (זול, לא
     * דורש רוט) תמיד. תיקוני הרוט (RootPowerUtil - דורשים su) מופעלים
     * *רק* כשמתג עובר ל"פעיל" (turningOn==true) - לא בכל שינוי, ובוודאי
     * לא שוב ושוב. זה מכוון: כל קריאת su גורמת למנהל ה-root במכשיר
     * להציג הודעה/טוסט - אז ממזערים את מספר הפעמים שבאמת קוראים לזה.
     */
    private void onAnyMonitorChanged(boolean turningOn) {
        AlarmScheduler.scheduleIfNeeded(this);
        refreshMonitorButtonsUi();
        if (turningOn) {
            RootPowerUtil.applyReliabilityFixesAsync(this);
        }
    }

    // ---------- בחירת צליל ----------

    private void openSoundPicker(int requestCode, String prefKey) {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);

        String existing = prefs.getString(prefKey, null);
        if (existing != null && !existing.isEmpty()) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existing));
        } else {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        }
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null) {
            return;
        }
        Uri pickedUri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        String prefKey = null;
        if (requestCode == REQUEST_CODE_SOUND_LOST) {
            prefKey = AppPrefs.KEY_ALERT_LOST_SOUND_URI;
        } else if (requestCode == REQUEST_CODE_SOUND_RESTORED) {
            prefKey = AppPrefs.KEY_ALERT_RESTORED_SOUND_URI;
        }
        if (prefKey == null) {
            return;
        }
        prefs.edit().putString(prefKey, pickedUri != null ? pickedUri.toString() : "").apply();
        refreshSoundLabels();
    }

    // ---------- עדכון תצוגה ----------

    private void refreshAllUi() {
        refreshMonitorButtonsUi();
        refreshIntervalSelection();
        refreshSoundLabels();
        refreshDiagnosticsLog();

        checkAlertLostEnabled.setChecked(prefs.getBoolean(AppPrefs.KEY_ALERT_LOST_ENABLED, false));
        checkAlertRestoredEnabled.setChecked(prefs.getBoolean(AppPrefs.KEY_ALERT_RESTORED_ENABLED, false));

        setRadioSelection(radioAlertLostMode, prefs.getString(AppPrefs.KEY_ALERT_LOST_MODE, AppPrefs.ALERT_MODE_SOUND_VIBRATE),
                R.id.radioLostSoundVibrate, R.id.radioLostSoundOnly, R.id.radioLostVibrateOnly);
        setRadioSelection(radioAlertRestoredMode, prefs.getString(AppPrefs.KEY_ALERT_RESTORED_MODE, AppPrefs.ALERT_MODE_SOUND_VIBRATE),
                R.id.radioRestoredSoundVibrate, R.id.radioRestoredSoundOnly, R.id.radioRestoredVibrateOnly);
    }

    private void refreshDiagnosticsLog() {
        // מציג מהחדש לישן (הפוך מסדר האחסון) - כך שהרשומה האחרונה תמיד
        // למעלה, בלי צורך לגלול למטה כדי לראות מה קרה עכשיו.
        String stored = DiagnosticsLog.read(this);
        String[] lines = stored.split("\n");
        StringBuilder reversed = new StringBuilder();
        for (int i = lines.length - 1; i >= 0; i--) {
            reversed.append(lines[i]);
            if (i > 0) {
                reversed.append("\n");
            }
        }
        diagnosticsLogText.setText(reversed.toString());
    }

    private void setRadioSelection(RadioGroup group, String mode, int soundVibrateId, int soundOnlyId, int vibrateOnlyId) {
        if (AppPrefs.ALERT_MODE_SOUND_ONLY.equals(mode)) {
            group.check(soundOnlyId);
        } else if (AppPrefs.ALERT_MODE_VIBRATE_ONLY.equals(mode)) {
            group.check(vibrateOnlyId);
        } else {
            group.check(soundVibrateId);
        }
    }

    private void refreshMonitorButtonsUi() {
        boolean phoneOn = prefs.getBoolean(AppPrefs.KEY_PHONE_MONITOR, false);
        boolean internetOn = prefs.getBoolean(AppPrefs.KEY_INTERNET_MONITOR, false);
        applyToggleState(btnPhoneMonitor, phoneOn, getString(R.string.phone_monitor_title));
        applyToggleState(btnInternetMonitor, internetOn, getString(R.string.internet_monitor_title));
    }

    private void applyToggleState(Button button, boolean on, String title) {
        button.setBackgroundResource(on ? R.drawable.bg_toggle_on : R.drawable.bg_toggle_off);
        button.setText(title + "\n(" + getString(on ? R.string.monitor_on : R.string.monitor_off) + ")");
    }

    private void refreshSoundLabels() {
        alertLostSoundName.setText(soundLabelFor(prefs.getString(AppPrefs.KEY_ALERT_LOST_SOUND_URI, null)));
        alertRestoredSoundName.setText(soundLabelFor(prefs.getString(AppPrefs.KEY_ALERT_RESTORED_SOUND_URI, null)));
    }

    private String soundLabelFor(String uriString) {
        if (uriString == null || uriString.isEmpty()) {
            return getString(R.string.alert_sound_default);
        }
        try {
            Ringtone ringtone = RingtoneManager.getRingtone(this, Uri.parse(uriString));
            if (ringtone != null) {
                CharSequence title = ringtone.getTitle(this);
                if (title != null) {
                    return title.toString();
                }
            }
        } catch (Exception e) {
            // אם הצליל נמחק/לא זמין - פשוט נופלים חזרה לברירת המחדל בתצוגה.
        }
        return getString(R.string.alert_sound_default);
    }
}
