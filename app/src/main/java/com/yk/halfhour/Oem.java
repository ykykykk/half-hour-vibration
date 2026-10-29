package com.yk.halfhour;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import java.lang.reflect.Method;
import java.util.Locale;

/** 打开各种系统/厂商设置页。厂商页面没有标准接口，按已知路径逐个尝试，都不行就退到应用详情页。 */
final class Oem {
    // 小米 MIUI / 澎湃 HyperOS 自己加的权限，存在 AppOps 里
    static final int MIUI_OP_AUTO_START = 10008;
    static final int MIUI_OP_SHOW_WHEN_LOCKED = 10020;
    static final int MIUI_OP_BACKGROUND_START_ACTIVITY = 10021;

    /** 读小米隐藏 AppOps 的结果 */
    static final int OP_ALLOWED = 1, OP_DENIED = 0, OP_UNKNOWN = -1;

    private Oem() {}

    private static String manufacturer() {
        return Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
    }

    static boolean isXiaomi() {
        String m = manufacturer();
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco");
    }

    /** 会额外拦截后台应用的国产系统（小米单独处理） */
    static boolean isOtherChineseRom() {
        String m = manufacturer();
        for (String b : new String[]{"huawei", "honor", "oppo", "realme", "oneplus", "vivo", "iqoo", "meizu"}) {
            if (m.contains(b)) return true;
        }
        return false;
    }

    static int miuiOp(Context c, int op) {
        try {
            AppOpsManager ops = c.getSystemService(AppOpsManager.class);
            Method m = AppOpsManager.class.getMethod("checkOpNoThrow",
                    int.class, int.class, String.class);
            int mode = (int) m.invoke(ops, op, Process.myUid(), c.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED ? OP_ALLOWED : OP_DENIED;
        } catch (Throwable t) {
            return OP_UNKNOWN;
        }
    }

    static Uri pkg(Context c) {
        return Uri.parse("package:" + c.getPackageName());
    }

    /** 依次尝试打开，成功一个就返回；全部失败打开应用详情页。 */
    static void open(Activity a, Intent... intents) {
        for (Intent i : intents) {
            try {
                a.startActivity(i);
                return;
            } catch (RuntimeException ignored) {
            }
        }
        openAppDetails(a);
    }

    static void openAppDetails(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(a)));
        } catch (RuntimeException ignored) {
        }
    }

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
    };

    static void openAutostart(Activity a) {
        Intent[] list = new Intent[AUTOSTART.length];
        for (int k = 0; k < AUTOSTART.length; k++) {
            list[k] = new Intent().setComponent(new ComponentName(AUTOSTART[k][0], AUTOSTART[k][1]))
                    .putExtra("packageName", a.getPackageName())
                    .putExtra("package_name", a.getPackageName());
        }
        open(a, list);
    }

    /** 小米权限编辑页：里面有"后台弹出界面""锁屏显示" */
    static void openXiaomiPermEditor(Activity a) {
        open(a,
                new Intent("miui.intent.action.APP_PERM_EDITOR")
                        .setClassName("com.miui.securitycenter",
                                "com.miui.permcenter.permissions.PermissionsEditorActivity")
                        .putExtra("extra_pkgname", a.getPackageName()),
                new Intent("miui.intent.action.APP_PERM_EDITOR")
                        .setClassName("com.miui.securitycenter",
                                "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
                        .putExtra("extra_pkgname", a.getPackageName()));
    }

    /** 小米：本应用的"省电策略"页 */
    static void openXiaomiPowerSaver(Activity a) {
        open(a, new Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY")
                .setClassName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                .putExtra("package_name", a.getPackageName())
                .putExtra("package_label", a.getString(R.string.app_name)));
    }

    static void openSoundSettings(Activity a) {
        open(a, new Intent(Settings.ACTION_SOUND_SETTINGS));
    }

    static void openDndSettings(Activity a) {
        open(a, new Intent("android.settings.ZEN_MODE_SETTINGS"),
                new Intent(Settings.ACTION_SOUND_SETTINGS));
    }
}
