package com.yk.halfhour;

import android.content.Context;
import android.content.SharedPreferences;

/** 所有设置都存在这里。星期用位掩码：bit0=周一 … bit6=周日。时间用"当天第几分钟"。 */
final class Prefs {
    private static final String NAME = "settings";
    private static final String K_ENABLED = "enabled";
    private static final String K_DAYS = "days";
    private static final String K_START = "start_min";
    private static final String K_END = "end_min";
    private static final String K_NEXT = "next_at";
    private static final String K_RINGING = "ringing";
    private static final String K_WATCH_MODE = "watch_mode";

    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    boolean enabled() { return sp.getBoolean(K_ENABLED, true); }
    void setEnabled(boolean v) { sp.edit().putBoolean(K_ENABLED, v).apply(); }

    /** 默认周一到周五 */
    int days() { return sp.getInt(K_DAYS, 0b0011111); }
    void setDays(int v) { sp.edit().putInt(K_DAYS, v).apply(); }

    boolean dayOn(int dayIndex) { return (days() & (1 << dayIndex)) != 0; }

    int startMin() { return sp.getInt(K_START, 9 * 60); }
    void setStartMin(int v) { sp.edit().putInt(K_START, v).apply(); }

    int endMin() { return sp.getInt(K_END, 18 * 60); }
    void setEndMin(int v) { sp.edit().putInt(K_END, v).apply(); }

    long nextAt() { return sp.getLong(K_NEXT, -1); }
    void setNextAt(long v) { sp.edit().putLong(K_NEXT, v).apply(); }

    boolean ringing() { return sp.getBoolean(K_RINGING, false); }
    void setRinging(boolean v) { sp.edit().putBoolean(K_RINGING, v).commit(); }

    // 升级安装时保留原来的手机强提醒模式，用户可在首页切换。
    boolean watchMode() { return sp.getBoolean(K_WATCH_MODE, false); }
    void setWatchMode(boolean v) { sp.edit().putBoolean(K_WATCH_MODE, v).apply(); }
}
