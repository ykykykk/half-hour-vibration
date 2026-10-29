package com.yk.halfhour;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class MainActivity extends Activity implements Guide.Listener {
    private static final String[] DAY_NAMES = {"一", "二", "三", "四", "五", "六", "日"};

    private Prefs prefs;
    private Guide guide;
    private Switch master;
    private TextView startValue, endValue;
    private LinearLayout permList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = new Prefs(this);
        guide = new Guide(this, this);
        AlarmService.ensureChannel(this);

        master = findViewById(R.id.master);
        startValue = findViewById(R.id.start_value);
        endValue = findViewById(R.id.end_value);
        permList = findViewById(R.id.perm_list);

        master.setChecked(prefs.enabled());
        master.setOnCheckedChangeListener((b, on) -> {
            prefs.setEnabled(on);
            if (!on) AlarmService.stop(this);
            settingsChanged();
        });

        buildDayChips();
        findViewById(R.id.time_start).setOnClickListener(v -> pickTime(true));
        findViewById(R.id.time_end).setOnClickListener(v -> pickTime(false));
        findViewById(R.id.btn_guide).setOnClickListener(v -> guide.startAll());
        findViewById(R.id.btn_test).setOnClickListener(v -> confirmTest());

        // 打开 APP 时，有没完成的设置就自动进入向导（一步一步来）
        if (savedInstanceState == null && Steps.pendingCount(this) > 0) {
            getWindow().getDecorView().post(guide::startAll);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        guide.onReturn();
        refreshTimes();
        refreshPerms();
        // 漏掉的闹钟（比如被系统清掉了）补排一下；正常情况下不动，避免打开 APP 就重置倒计时
        if (prefs.ringing() && !AlarmService.running) {
            AlarmService.start(this);
        } else if (!prefs.ringing() && prefs.enabled()
                && prefs.nextAt() < System.currentTimeMillis()) {
            Scheduler.rescheduleFromNow(this);
        }
        refreshHero();
    }

    @Override
    protected void onDestroy() {
        guide.dismiss();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 被"不再询问"拒绝时系统不弹窗、也不会触发 onResume，这里兜底
        getWindow().getDecorView().post(guide::onReturn);
    }

    // ---------- Guide.Listener ----------

    @Override
    public void onGuideChanged() {
        refreshPerms();
        refreshHero();
    }

    @Override
    public void onGuideWantsTest() {
        AlarmService.start(this);
    }

    // ---------- 界面 ----------

    private void buildDayChips() {
        LinearLayout row = findViewById(R.id.days);
        int size = dp(40);
        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;
            CheckBox chip = new CheckBox(this);
            chip.setButtonDrawable(null);
            chip.setBackgroundResource(R.drawable.chip_bg);
            chip.setTextColor(getColorStateList(R.color.chip_text));
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            chip.setText(DAY_NAMES[i]);
            chip.setChecked((prefs.days() & bit) != 0);
            chip.setOnCheckedChangeListener((b, on) -> {
                prefs.setDays(on ? (prefs.days() | bit) : (prefs.days() & ~bit));
                settingsChanged();
            });
            FrameLayout cell = new FrameLayout(this);
            cell.addView(chip, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
            row.addView(cell, new LinearLayout.LayoutParams(0, size, 1));
        }
    }

    private void refreshPerms() {
        permList.removeAllViews();
        int left = 0;
        for (Steps.Step s : Steps.all()) {
            View row = getLayoutInflater().inflate(R.layout.row_perm, permList, false);
            TextView name = row.findViewById(R.id.perm_name);
            TextView state = row.findViewById(R.id.perm_state);
            View dot = row.findViewById(R.id.perm_dot);
            boolean ok = s.satisfied(this);
            boolean verified = s.state(this) != Steps.UNKNOWN;
            if (!ok) left++;
            name.setText(s.titleRes);
            dot.setBackgroundResource(ok ? R.drawable.dot_ok : R.drawable.dot_pending);
            state.setText(ok ? (verified ? R.string.state_ok : R.string.state_confirmed)
                    : (verified ? R.string.state_missing : R.string.state_todo));
            state.setTextColor(getColor(ok ? R.color.ok : R.color.pending));
            row.setOnClickListener(v -> guide.startSingle(s));
            permList.addView(row);
        }
        TextView summary = findViewById(R.id.perm_summary);
        if (left == 0) {
            summary.setText(R.string.perm_all_ok);
            summary.setTextColor(getColor(R.color.ok));
            summary.setBackgroundResource(R.drawable.pill_ok);
        } else {
            summary.setText(getString(R.string.perm_left, left));
            summary.setTextColor(getColor(R.color.warn_text));
            summary.setBackgroundResource(R.drawable.pill_pending);
        }
        findViewById(R.id.btn_guide).setVisibility(left == 0 ? View.GONE : View.VISIBLE);
    }

    private void refreshHero() {
        View hero = findViewById(R.id.hero);
        TextView label = findViewById(R.id.hero_label);
        TextView time = findViewById(R.id.hero_time);
        TextView sub = findViewById(R.id.hero_sub);
        hero.setBackgroundResource(prefs.enabled() ? R.drawable.hero_on : R.drawable.hero_off);
        if (!prefs.enabled()) {
            label.setText(R.string.hero_off);
            time.setText(R.string.hero_off_big);
            sub.setText(R.string.hero_off_sub);
            return;
        }
        if (prefs.ringing()) {
            label.setText(R.string.hero_ringing);
            time.setText(R.string.hero_ringing_big);
            sub.setText(R.string.hero_ringing_sub);
            return;
        }
        long next = prefs.nextAt();
        if (next < 0) {
            label.setText(R.string.hero_none);
            time.setText(R.string.hero_none_big);
            sub.setText(R.string.hero_none_sub);
            return;
        }
        ZonedDateTime t = Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault());
        LocalDate today = LocalDate.now();
        String day;
        if (t.toLocalDate().equals(today)) day = getString(R.string.today);
        else if (t.toLocalDate().equals(today.plusDays(1))) day = getString(R.string.tomorrow);
        else day = t.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA));
        label.setText(R.string.hero_next);
        time.setText(t.format(DateTimeFormatter.ofPattern("HH:mm")));
        sub.setText(day + " · " + t.format(DateTimeFormatter.ofPattern("EEEE", Locale.CHINA)));
    }

    private void refreshTimes() {
        startValue.setText(fmt(prefs.startMin()));
        endValue.setText(fmt(prefs.endMin()));
        TextView hint = findViewById(R.id.time_hint);
        int s = prefs.startMin(), e = prefs.endMin();
        hint.setText(s == e ? R.string.hint_all_day : s > e ? R.string.hint_overnight : R.string.hint_normal);
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

    private void confirmTest() {
        Ui.Spec spec = new Ui.Spec();
        spec.title = getString(R.string.test_title);
        spec.body = getString(R.string.test_body);
        spec.primary = getString(R.string.test_start);
        spec.onPrimary = () -> AlarmService.start(this);
        spec.secondary = getString(R.string.cancel);
        Ui.show(this, spec);
    }

    private void settingsChanged() {
        if (!prefs.ringing()) Scheduler.rescheduleFromNow(this);
        refreshHero();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
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
