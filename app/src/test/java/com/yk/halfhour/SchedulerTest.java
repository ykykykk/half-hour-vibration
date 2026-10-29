package com.yk.halfhour;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public class SchedulerTest {
    private static final ZoneId Z = ZoneId.of("Asia/Shanghai");
    private static final int WEEKDAYS = 0b0011111;
    private static final int ALL = 0b1111111;

    /** 2026-10-26 是周一 */
    private static long at(int day, int h, int m) {
        return ZonedDateTime.of(2026, 10, day, h, m, 0, 0, Z).toInstant().toEpochMilli();
    }

    private static long next(int days, int s, int e, long from) {
        return Scheduler.computeNext(days, s, e, from, Z);
    }

    @Test public void insideWindow_plus30() {
        assertEquals(at(26, 10, 30), next(WEEKDAYS, 9 * 60, 18 * 60, at(26, 10, 0)));
    }

    @Test public void keepsSeconds() {
        long from = at(26, 10, 0) + 17_500;
        assertEquals(from + Scheduler.INTERVAL_MS, next(WEEKDAYS, 9 * 60, 18 * 60, from));
    }

    @Test public void beforeWindow_startPlus30() {
        assertEquals(at(26, 9, 30), next(WEEKDAYS, 9 * 60, 18 * 60, at(26, 7, 12)));
    }

    @Test public void nearEnd_rollsToNextDay() {
        assertEquals(at(27, 9, 30), next(WEEKDAYS, 9 * 60, 18 * 60, at(26, 17, 45)));
    }

    @Test public void exactlyAtEndMinus30_isAllowed() {
        assertEquals(at(26, 17, 59), next(WEEKDAYS, 9 * 60, 18 * 60, at(26, 17, 29)));
    }

    @Test public void friday_skipsWeekend() {
        // 10-30 周五 → 11-02 周一
        assertEquals(at(30, 9, 30) + 3 * 86_400_000L, next(WEEKDAYS, 9 * 60, 18 * 60, at(30, 17, 50)));
    }

    @Test public void overnightWindow() {
        // 22:00–02:00，周一晚上 23:50 → 周二 00:20
        assertEquals(at(27, 0, 20), next(ALL, 22 * 60, 2 * 60, at(26, 23, 50)));
        // 01:45 → 当天 22:30
        assertEquals(at(27, 22, 30), next(ALL, 22 * 60, 2 * 60, at(27, 1, 45)));
    }

    @Test public void overnight_dayBelongsToStart() {
        // 只选周日：周日 22:00 开始，跨到周一 02:00 也算
        int sunday = 1 << 6;
        assertEquals(at(26, 0, 10), next(sunday, 22 * 60, 2 * 60, at(25, 23, 40)));
    }

    @Test public void allDay() {
        assertEquals(at(26, 0, 30), next(WEEKDAYS, 0, 0, at(25, 23, 45))); // 周日不选，周一 00:00 开始 +30
        assertEquals(at(27, 0, 10), next(WEEKDAYS, 0, 0, at(26, 23, 40))); // 周一→周二连续
    }

    @Test public void noDays_none() {
        assertEquals(-1, next(0, 9 * 60, 18 * 60, at(26, 10, 0)));
    }

    @Test public void windowShorterThan30_none() {
        assertEquals(-1, next(ALL, 9 * 60, 9 * 60 + 20, at(26, 8, 0)));
    }
}
