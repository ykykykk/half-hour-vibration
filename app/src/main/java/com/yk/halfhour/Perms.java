package com.yk.halfhour;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        /** 系统不提供查询接口，只能引导用户去看；引导过一次就算完成 */
        final boolean manualOnly;

        Perm(String key, int titleRes, int descRes, Check check, Request request) {
            this(key, titleRes, descRes, check, request, false);
        }

        Perm(String key, int titleRes, int descRes, Check check, Request request, boolean manualOnly) {
            this.key = key;
            this.titleRes = titleRes;
            this.descRes = descRes;
            this.check = check;
            this.request = request;
            this.manualOnly = manualOnly;
        }
    }

    // 小米 MIUI / 澎湃 HyperOS 自己加的权限，存在 AppOps 里
    private static final int MIUI_OP_AUTO_START = 10008;
    private static final int MIUI_OP_SHOW_WHEN_LOCKED = 10020;
    private static final int MIUI_OP_BACKGROUND_START_ACTIVITY = 10021;

    private static final String ASKED = "asked";

    static boolean asked(Context c, String key) {
        return c.getSharedPreferences(ASKED, Context.MODE_PRIVATE).getBoolean(key, false);
    }

    static void markAsked(Context c, String key) {
        c.getSharedPreferences(ASKED, Context.MODE_PRIVATE).edit().putBoolean(key, true).apply();
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
        if (isXiaomi()) {
            list.add(new Perm("miui_popup", R.string.perm_miui_popup, R.string.perm_miui_popup_desc,
                    c -> miuiOpAllowed(c, MIUI_OP_BACKGROUND_START_ACTIVITY, "miui_popup")
                            && miuiOpAllowed(c, MIUI_OP_SHOW_WHEN_LOCKED, "miui_popup"),
                    (a, asked) -> openXiaomiPermEditor(a)));
            list.add(new Perm("miui_autostart", R.string.perm_miui_autostart, R.string.perm_miui_autostart_desc,
                    c -> miuiOpAllowed(c, MIUI_OP_AUTO_START, "miui_autostart"),
                    (a, asked) -> openAutostart(a)));
            list.add(new Perm("miui_power", R.string.perm_miui_power, R.string.perm_miui_power_desc,
                    c -> asked(c, "miui_power"),
                    (a, asked) -> openXiaomiPowerSaver(a), true));
        }
        return list;
    }

    /** 读小米的隐藏 AppOps；读不到（系统改了接口）就退化成"引导过一次算完成"。 */
    private static boolean miuiOpAllowed(Context c, int op, String key) {
        try {
            AppOpsManager ops = c.getSystemService(AppOpsManager.class);
            Method m = AppOpsManager.class.getMethod("checkOpNoThrow",
                    int.class, int.class, String.class);
            int mode = (int) m.invoke(ops, op, Process.myUid(), c.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return asked(c, key);
        }
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
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco");
    }

    /** 小米：应用的"省电策略"页，要选"无限制"。 */
    static void openXiaomiPowerSaver(Activity a) {
        Intent i = new Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY")
                .setClassName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                .putExtra("package_name", a.getPackageName())
                .putExtra("package_label", a.getString(R.string.app_name));
        try {
            a.startActivity(i);
        } catch (RuntimeException e) {
            // 新版澎湃 OS 在"应用详情 → 省电策略"里
            openAppDetails(a);
        }
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
