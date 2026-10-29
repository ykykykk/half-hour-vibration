package com.yk.halfhour;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

final class Scheduler {
    static final long INTERVAL_MS = 30 * 60 * 1000L;
    private static final long MINUTE_MS = 60 * 1000L;
    /** 最多往后找 8 天 */
    private static final int SEARCH_MINUTES = 8 * 24 * 60;

    private Scheduler() {}

    /**
     * 某一时刻是否落在"开启时段"里。
     * 开始 < 结束：当天时段；开始 > 结束：跨午夜（例如 22:00–02:00，星期按开始那天算）；
     * 开始 == 结束：所选日期全天。
     */
    static boolean inWindow(Prefs p, ZonedDateTime t) {
        return inWindow(p.days(), p.startMin(), p.endMin(), t);
    }

    static boolean inWindow(int days, int s, int e, ZonedDateTime t) {
        int day = t.getDayOfWeek().getValue() - 1; // 0=周一
        int prevDay = (day + 6) % 7;
        int m = t.getHour() * 60 + t.getMinute();
        boolean today = (days & (1 << day)) != 0;
        boolean yesterday = (days & (1 << prevDay)) != 0;
        if (s == e) return today;
        if (s < e) return today && m >= s && m < e;
        return (today && m >= s) || (yesterday && m < e);
    }

    /**
     * 从 fromMs 开始算下一次震动的时间：
     * 必须在开启时段内，并且距离 fromMs（或者距离本段时段开始，取较晚者）满 30 分钟。
     * 找不到返回 -1。
     */
    static long computeNext(Prefs p, long fromMs) {
        return computeNext(p.days(), p.startMin(), p.endMin(), fromMs, ZoneId.systemDefault());
    }

    static long computeNext(int days, int s, int e, long fromMs, ZoneId zone) {
        ZonedDateTime from = Instant.ofEpochMilli(fromMs).atZone(zone);
        ZonedDateTime t = from.truncatedTo(ChronoUnit.MINUTES);
        long openSince = inWindow(days, s, e, t) ? fromMs : -1;
        for (int i = 0; i <= SEARCH_MINUTES; i++, t = t.plusMinutes(1)) {
            long tMs = t.toInstant().toEpochMilli();
            if (!inWindow(days, s, e, t)) {
                openSince = -1;
                continue;
            }
            if (openSince < 0) openSince = tMs;
            long due = Math.max(openSince, fromMs) + INTERVAL_MS;
            // 时段边界是整分钟，这一整分钟都在时段内
            if (due < tMs + MINUTE_MS) return Math.max(due, tMs);
        }
        return -1;
    }

    /** 以当前时刻为起点重新排下一次震动（改设置、开机、点"去休息"后都调用它）。 */
    static void rescheduleFromNow(Context c) {
        scheduleFrom(c, System.currentTimeMillis());
    }

    static void scheduleFrom(Context c, long fromMs) {
        Prefs p = new Prefs(c);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent fire = firePendingIntent(c);
        am.cancel(fire);
        if (!p.enabled()) {
            p.setNextAt(-1);
            return;
        }
        long next = computeNext(p, fromMs);
        p.setNextAt(next);
        if (next < 0) return;

        PendingIntent show = PendingIntent.getActivity(c, 2,
                new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            // setAlarmClock：系统级"闹钟"，不受 Doze 省电影响，到点一定会触发
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(next, show), fire);
        } else {
            // 没有精确闹钟权限时的兜底，可能会被系统推迟几分钟
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, fire);
        }
    }

    static void cancel(Context c) {
        c.getSystemService(AlarmManager.class).cancel(firePendingIntent(c));
        new Prefs(c).setNextAt(-1);
    }

    private static PendingIntent firePendingIntent(Context c) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(AlarmReceiver.ACTION_FIRE);
        return PendingIntent.getBroadcast(c, 1, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
