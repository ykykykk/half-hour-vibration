package com.yk.halfhour;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/** 需要的所有权限：怎么检查、怎么申请。 */
final class Perms {
    static final int REQ_NOTIF = 100;

    interface Check { boolean granted(Context c); }
    interface Request { void request(Activity a, boolean alreadyAsked); }

    static final class Perm {
        final String key;
        final int titleRes;
        final int descRes;
        final Check check;
        final Request request;

        Perm(String key, int titleRes, int descRes, Check check, Request request) {
            this.key = key;
            this.titleRes = titleRes;
            this.descRes = descRes;
            this.check = check;
            this.request = request;
        }
    }

    private Perms() {}

    /** 按自动申请的顺序排列；当前系统版本不需要的权限不在列表里。 */
    static List<Perm> all() {
        List<Perm> list = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            list.add(new Perm("notif", R.string.perm_notif, R.string.perm_notif_desc,
                    c -> c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED,
                    (a, asked) -> {
                        if (!asked || a.shouldShowRequestPermissionRationale(
                                Manifest.permission.POST_NOTIFICATIONS)) {
                            a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                    REQ_NOTIF);
                        } else {
                            // 被"不再询问"拒绝过，只能去设置页里开
                            open(a, new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName()));
                        }
                    }));
        }
        if (Build.VERSION.SDK_INT >= 31) {
            list.add(new Perm("exact", R.string.perm_exact, R.string.perm_exact_desc,
                    c -> c.getSystemService(AlarmManager.class).canScheduleExactAlarms(),
                    (a, asked) -> open(a, new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            pkg(a)))));
        }
        list.add(new Perm("battery", R.string.perm_battery, R.string.perm_battery_desc,
                c -> c.getSystemService(PowerManager.class)
                        .isIgnoringBatteryOptimizations(c.getPackageName()),
                (a, asked) -> {
                    if (!open(a, new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg(a)))) {
                        open(a, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    }
                }));
        list.add(new Perm("overlay", R.string.perm_overlay, R.string.perm_overlay_desc,
                Settings::canDrawOverlays,
                (a, asked) -> open(a, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg(a)))));
        if (Build.VERSION.SDK_INT >= 34) {
            list.add(new Perm("fullscreen", R.string.perm_fullscreen, R.string.perm_fullscreen_desc,
                    c -> c.getSystemService(NotificationManager.class).canUseFullScreenIntent(),
                    (a, asked) -> open(a, new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            pkg(a)))));
        }
        return list;
    }

    static boolean allGranted(Context c) {
        for (Perm p : all()) if (!p.check.granted(c)) return false;
        return true;
    }

    private static Uri pkg(Context c) {
        return Uri.parse("package:" + c.getPackageName());
    }

    /** 打开设置页；打不开就退到本应用的"应用详情"页。 */
    static boolean open(Activity a, Intent i) {
        try {
            a.startActivity(i);
            return true;
        } catch (RuntimeException e) {
            openAppDetails(a);
            return false;
        }
    }

    static void openAppDetails(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(a)));
        } catch (RuntimeException ignored) {
        }
    }

    // ---------- 国产手机的"自启动 / 后台弹出界面 / 锁屏显示"，系统没有标准接口，只能跳到厂商页面 ----------

    private static final String[][] AUTOSTART = {
            {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
            {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
            {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
            {"com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
            {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
            {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
            {"com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"},
            {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
            {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
            {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
            {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"},
            {"com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"},
            {"com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"},
            {"com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"},
    };

    static void openAutostart(Activity a) {
        for (String[] c : AUTOSTART) {
            Intent i = new Intent().setComponent(new ComponentName(c[0], c[1]))
                    .putExtra("packageName", a.getPackageName())
                    .putExtra("package_name", a.getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                a.startActivity(i);
                return;
            } catch (RuntimeException ignored) {
            }
        }
        openAppDetails(a);
    }

    static boolean isXiaomi() {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase();
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco");
    }

    /** 小米/红米：权限编辑页，里面有"后台弹出界面""锁屏显示""显示悬浮窗"。 */
    static void openXiaomiPermEditor(Activity a) {
        String[] activities = {
                "com.miui.permcenter.permissions.PermissionsEditorActivity",
                "com.miui.permcenter.permissions.AppPermissionsEditorActivity",
        };
        for (String act : activities) {
            Intent i = new Intent("miui.intent.action.APP_PERM_EDITOR")
                    .setClassName("com.miui.securitycenter", act)
                    .putExtra("extra_pkgname", a.getPackageName());
            try {
                a.startActivity(i);
                return;
            } catch (RuntimeException ignored) {
            }
        }
        openAppDetails(a);
    }
}
