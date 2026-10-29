package com.yk.halfhour;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int[] DAY_IDS = {
            R.id.day0, R.id.day1, R.id.day2, R.id.day3, R.id.day4, R.id.day5, R.id.day6};

    /** 本次打开 APP 已经自动申请过的权限，避免用户拒绝后反复弹 */
    private final Set<String> autoAsked = new HashSet<>();

    private Prefs prefs;
    private Switch master;
    private Button startBtn, endBtn;
    private TextView status;
    private LinearLayout permList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = new Prefs(this);
        AlarmService.ensureChannel(this);

        master = findViewById(R.id.master);
        startBtn = findViewById(R.id.btn_start);
        endBtn = findViewById(R.id.btn_end);
        status = findViewById(R.id.status);
        permList = findViewById(R.id.perm_list);

        master.setChecked(prefs.enabled());
        master.setOnCheckedChangeListener((b, on) -> {
            prefs.setEnabled(on);
            if (!on) AlarmService.stop(this);
            settingsChanged();
        });

        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;
            CheckBox cb = findViewById(DAY_IDS[i]);
            cb.setChecked((prefs.days() & bit) != 0);
            cb.setOnCheckedChangeListener((b, on) -> {
                prefs.setDays(on ? (prefs.days() | bit) : (prefs.days() & ~bit));
                settingsChanged();
            });
        }

        startBtn.setOnClickListener(v -> pickTime(true));
        endBtn.setOnClickListener(v -> pickTime(false));

        findViewById(R.id.btn_grant_all).setOnClickListener(v -> {
            autoAsked.clear();
            askNextMissing(true);
        });
        findViewById(R.id.btn_autostart).setOnClickListener(v -> Perms.openAutostart(this));
        View xiaomi = findViewById(R.id.btn_xiaomi);
        xiaomi.setVisibility(Perms.isXiaomi() ? View.VISIBLE : View.GONE);
        xiaomi.setOnClickListener(v -> Perms.openXiaomiPermEditor(this));
        findViewById(R.id.btn_app_details).setOnClickListener(v -> Perms.openAppDetails(this));

        findViewById(R.id.btn_test).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage(R.string.test_confirm)
                .setPositiveButton(R.string.test_now, (d, w) -> AlarmService.start(this))
                .setNegativeButton(android.R.string.cancel, null)
                .show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTimes();
        refreshPerms();
        // 漏掉的闹钟（比如被系统清掉了）补排一下；正常情况下不动，避免打开 APP 就重置倒计时
        if (prefs.ringing() && !AlarmService.running) {
            AlarmService.start(this);
        } else if (!prefs.ringing() && prefs.enabled()
                && prefs.nextAt() < System.currentTimeMillis()) {
            Scheduler.rescheduleFromNow(this);
        }
        refreshStatus();
        askNextMissing(false);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshPerms();
    }

    /** 自动依次申请：每次只弹一个，用户处理完回到本页面（onResume）再弹下一个。 */
    private void askNextMissing(boolean userClicked) {
        for (Perms.Perm p : Perms.all()) {
            if (p.check.granted(this)) continue;
            if (autoAsked.contains(p.key)) continue;
            autoAsked.add(p.key);
            boolean askedBefore = getSharedPreferences("asked", MODE_PRIVATE).getBoolean(p.key, false);
            getSharedPreferences("asked", MODE_PRIVATE).edit().putBoolean(p.key, true).apply();
            p.request.request(this, askedBefore);
            return;
        }
        if (userClicked && Perms.allGranted(this)) {
            new AlertDialog.Builder(this).setMessage(R.string.all_granted)
                    .setPositiveButton(android.R.string.ok, null).show();
        }
    }

    private void refreshPerms() {
        permList.removeAllViews();
        List<Perms.Perm> all = Perms.all();
        for (Perms.Perm p : all) {
            View row = getLayoutInflater().inflate(R.layout.row_perm, permList, false);
            TextView name = row.findViewById(R.id.perm_name);
            TextView desc = row.findViewById(R.id.perm_desc);
            Button btn = row.findViewById(R.id.perm_btn);
            boolean ok = p.check.granted(this);
            name.setText(getString(p.titleRes) + (ok ? "  ✓" : "  ✗"));
            name.setTextColor(ok ? Color.rgb(0, 140, 60) : Color.rgb(210, 0, 0));
            desc.setText(p.descRes);
            btn.setText(ok ? R.string.perm_granted : R.string.perm_go);
            btn.setEnabled(!ok);
            btn.setOnClickListener(v -> {
                boolean asked = getSharedPreferences("asked", MODE_PRIVATE).getBoolean(p.key, false);
                getSharedPreferences("asked", MODE_PRIVATE).edit().putBoolean(p.key, true).apply();
                p.request.request(this, asked);
            });
            permList.addView(row);
        }
    }

    private void pickTime(boolean start) {
        int cur = start ? prefs.startMin() : prefs.endMin();
        new TimePickerDialog(this, (tp, h, m) -> {
            if (start) prefs.setStartMin(h * 60 + m);
            else prefs.setEndMin(h * 60 + m);
            refreshTimes();
            settingsChanged();
        }, cur / 60, cur % 60, true).show();
    }

    private void settingsChanged() {
        if (!prefs.ringing()) Scheduler.rescheduleFromNow(this);
        refreshStatus();
    }

    private void refreshTimes() {
        startBtn.setText(getString(R.string.start_time, fmt(prefs.startMin())));
        endBtn.setText(getString(R.string.end_time, fmt(prefs.endMin())));
        TextView hint = findViewById(R.id.time_hint);
        int s = prefs.startMin(), e = prefs.endMin();
        hint.setText(s == e ? R.string.hint_all_day : s > e ? R.string.hint_overnight : R.string.hint_normal);
    }

    private void refreshStatus() {
        if (!prefs.enabled()) {
            status.setText(R.string.status_off);
            return;
        }
        if (prefs.ringing()) {
            status.setText(R.string.status_ringing);
            return;
        }
        long next = prefs.nextAt();
        if (next < 0) {
            status.setText(R.string.status_none);
            return;
        }
        String when = Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("M月d日 EEEE HH:mm", Locale.CHINA));
        status.setText(getString(R.string.status_next, when));
    }

    private static String fmt(int min) {
        return String.format(Locale.US, "%02d:%02d", min / 60, min % 60);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }
}
