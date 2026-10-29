package com.yk.halfhour;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/** 设置向导里的每一步：要用户做什么、怎么检查做没做、点按钮后打开哪里。 */
final class Steps {
    static final int REQ_NOTIF = 100;

    static final int OK = 1, MISSING = 0, UNKNOWN = -1;

    interface Check { int state(Context c); }
    interface Text { String get(Activity a); }
    interface Action { void run(Activity a); }

    static final class Step {
        final String key;
        final int titleRes;
        final Text howTo;
        /** null = 系统查不到，靠用户确认"我已完成" */
        final Check check;
        /** null = 没有可跳转的页面，只是提示 */
        final Action action;

        Step(String key, int titleRes, Text howTo, Check check, Action action) {
            this.key = key;
            this.titleRes = titleRes;
            this.howTo = howTo;
            this.check = check;
            this.action = action;
        }

        int state(Context c) {
            return check == null ? UNKNOWN : check.state(c);
        }

        /** 能检查的看真实状态；查不到的看用户是否点过"我已完成" */
        boolean satisfied(Context c) {
            int s = state(c);
            return s == OK || (s == UNKNOWN && isDone(c, key));
        }
    }

    private Steps() {}

    private static final String STORE = "guide";

    static boolean isDone(Context c, String key) {
        return c.getSharedPreferences(STORE, Context.MODE_PRIVATE).getBoolean("done_" + key, false);
    }

    static void markDone(Context c, String key) {
        c.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putBoolean("done_" + key, true).apply();
    }

    static boolean wasAsked(Context c, String key) {
        return c.getSharedPreferences(STORE, Context.MODE_PRIVATE).getBoolean("asked_" + key, false);
    }

    static void markAsked(Context c, String key) {
        c.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putBoolean("asked_" + key, true).apply();
    }

    private static int bool(boolean b) {
        return b ? OK : MISSING;
    }

    /** 通知权限被"不再询问"拒绝过：系统不会再弹窗，只能去设置页开 */
    private static boolean notifBlocked(Activity a) {
        return wasAsked(a, "notif")
                && !a.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS);
    }

    /** 按向导顺序排列；当前手机不需要的步骤不在列表里。 */
    static List<Step> all() {
        List<Step> list = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            list.add(new Step("notif", R.string.step_notif,
                    a -> a.getString(notifBlocked(a) ? R.string.step_notif_how_blocked : R.string.step_notif_how),
                    c -> bool(c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED),
                    a -> {
                        if (notifBlocked(a)) {
                            Oem.open(a, new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName()));
                        } else {
                            a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                    REQ_NOTIF);
                        }
                    }));
        }
        if (Build.VERSION.SDK_INT >= 31) {
            list.add(new Step("exact", R.string.step_exact, a -> a.getString(R.string.step_exact_how),
                    c -> bool(c.getSystemService(AlarmManager.class).canScheduleExactAlarms()),
                    a -> Oem.open(a, new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Oem.pkg(a)))));
        }
        list.add(new Step("battery", R.string.step_battery, a -> a.getString(R.string.step_battery_how),
                c -> bool(c.getSystemService(PowerManager.class)
                        .isIgnoringBatteryOptimizations(c.getPackageName())),
                Steps::requestIgnoreBattery));
        list.add(new Step("overlay", R.string.step_overlay, a -> a.getString(R.string.step_overlay_how),
                c -> bool(Settings.canDrawOverlays(c)),
                a -> Oem.open(a, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Oem.pkg(a)))));
        if (Build.VERSION.SDK_INT >= 34) {
            list.add(new Step("fullscreen", R.string.step_fullscreen,
                    a -> a.getString(R.string.step_fullscreen_how),
                    c -> bool(c.getSystemService(NotificationManager.class).canUseFullScreenIntent()),
                    a -> Oem.open(a, new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Oem.pkg(a)))));
        }
        if (Oem.isXiaomi()) {
            list.add(new Step("miui_popup", R.string.step_miui_popup,
                    a -> a.getString(R.string.step_miui_popup_how),
                    c -> {
                        int bg = Oem.miuiOp(c, Oem.MIUI_OP_BACKGROUND_START_ACTIVITY);
                        int lock = Oem.miuiOp(c, Oem.MIUI_OP_SHOW_WHEN_LOCKED);
                        if (bg == Oem.OP_UNKNOWN || lock == Oem.OP_UNKNOWN) return UNKNOWN;
                        return bool(bg == Oem.OP_ALLOWED && lock == Oem.OP_ALLOWED);
                    },
                    Oem::openXiaomiPermEditor));
            list.add(new Step("autostart", R.string.step_autostart,
                    a -> a.getString(R.string.step_autostart_how),
                    c -> {
                        int s = Oem.miuiOp(c, Oem.MIUI_OP_AUTO_START);
                        return s == Oem.OP_UNKNOWN ? UNKNOWN : bool(s == Oem.OP_ALLOWED);
                    },
                    Oem::openAutostart));
            list.add(new Step("miui_power", R.string.step_miui_power,
                    a -> a.getString(R.string.step_miui_power_how), null, Oem::openXiaomiPowerSaver));
        } else if (Oem.isOtherChineseRom()) {
            list.add(new Step("autostart", R.string.step_autostart,
                    a -> a.getString(R.string.step_autostart_how_other), null, Oem::openAutostart));
        }
        if (Oem.isXiaomi() || Oem.isOtherChineseRom()) {
            list.add(new Step("lock_recents", R.string.step_lock,
                    a -> a.getString(R.string.step_lock_how), null, null));
        }
        list.add(new Step("vibration", R.string.step_vibration,
                a -> a.getString(R.string.step_vibration_how), null, Oem::openSoundSettings));
        list.add(new Step("dnd", R.string.step_dnd,
                a -> a.getString(R.string.step_dnd_how), null, Oem::openDndSettings));
        return list;
    }

    @SuppressWarnings("BatteryLife")
    private static void requestIgnoreBattery(Activity a) {
        Oem.open(a, new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Oem.pkg(a)),
                new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
    }

    static int pendingCount(Context c) {
        int n = 0;
        for (Step s : all()) if (!s.satisfied(c)) n++;
        return n;
    }
}
