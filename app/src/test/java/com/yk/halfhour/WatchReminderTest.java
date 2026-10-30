package com.yk.halfhour;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Looper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ServiceController;

import java.time.Duration;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class WatchReminderTest {
    private Application app;
    private Prefs prefs;
    private NotificationManager nm;
    private WatchReminder relay;
    private ServiceController<AlarmService> controller;

    @Before public void setUp() {
        app = RuntimeEnvironment.getApplication();
        prefs = new Prefs(app);
        prefs.setWatchMode(true);
        prefs.setRinging(true);
        prefs.setEnabled(true);
        prefs.setDays(0b1111111);
        prefs.setStartMin(0);
        prefs.setEndMin(0);
        nm = app.getSystemService(NotificationManager.class);
        shadowOf(nm).setNotificationsEnabled(true);
        relay = new WatchReminder(app);
        AlarmService.running = false;
    }

    @After public void tearDown() {
        relay.stop();
        if (controller != null) controller.destroy();
        AlarmService.running = false;
    }

    private Notification notification() {
        return shadowOf(nm).getNotification(WatchReminder.NOTIFICATION_ID);
    }

    @Test public void immediateThenEvery30Seconds_withoutChangingHalfHourSchedule() {
        prefs.setNextAt(123456L);
        relay.start();
        Notification first = notification();
        assertNotNull(first);
        assertEquals(Notification.CATEGORY_REMINDER, first.category);
        assertNull(first.fullScreenIntent);
        assertEquals(0, first.flags & (Notification.FLAG_ONGOING_EVENT
                | Notification.FLAG_FOREGROUND_SERVICE | Notification.FLAG_LOCAL_ONLY));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WatchReminder.REPEAT_MS - 1));
        assertSame(first, notification());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
        assertNotEquals(first.extras.getString(Notification.EXTRA_TEXT),
                notification().extras.getString(Notification.EXTRA_TEXT));
        assertEquals(1, shadowOf(nm).getAllNotifications().size());
        assertEquals(123456L, prefs.nextAt());
    }

    @Test public void stopping_cancelsNotificationAndPendingRepeats() {
        relay.start();
        relay.stop();
        assertNull(notification());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2));
        assertNull(notification());
    }

    @Test public void clearingRinging_preventsMoreNotifications() {
        relay.start();
        prefs.setRinging(false);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WatchReminder.REPEAT_MS));
        assertNull(notification());
    }

    @Test public void watchSwitchOff_preventsMoreNotifications() {
        relay.start();
        prefs.setWatchMode(false);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WatchReminder.REPEAT_MS));
        assertNull(notification());
    }

    @Test public void notificationPermissionRestored_resumesOnNextPulse() {
        shadowOf(nm).setNotificationsEnabled(false);
        relay.start();
        assertNull(notification());
        shadowOf(nm).setNotificationsEnabled(true);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WatchReminder.REPEAT_MS));
        assertNotNull(notification());
    }

    @Test public void blockedChannel_isNotSilentlyReenabled() {
        nm.createNotificationChannel(new NotificationChannel(WatchReminder.CHANNEL_ID,
                "Blocked", NotificationManager.IMPORTANCE_NONE));
        relay.start();
        assertNull(notification());
        assertFalse(WatchReminder.notificationsAllowed(app));
    }

    private AlarmService startService() {
        controller = Robolectric.buildService(AlarmService.class).create();
        AlarmService service = controller.get();
        service.onStartCommand(new Intent(AlarmService.ACTION_START), 0, 1);
        return service;
    }

    @Test public void phoneRest_stopsBothAndSchedules30MinutesFromConfirmation() {
        AlarmService service = startService();
        assertTrue(AlarmService.running);
        assertNotNull(notification());
        long before = System.currentTimeMillis();
        service.onStartCommand(new Intent(AlarmService.ACTION_REST), 0, 2);
        assertFalse(AlarmService.running);
        assertFalse(prefs.ringing());
        assertTrue(prefs.nextAt() >= before + Scheduler.INTERVAL_MS);
        assertNull(notification());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2));
        assertNull(notification());
    }

    @Test public void serviceDestroyed_cancelsWatchRepeats() {
        startService();
        assertNotNull(notification());
        controller.destroy();
        controller = null;
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2));
        assertNull(notification());
    }

    @Test public void originalPhoneMode_doesNotPublishWatchPulses() {
        prefs.setWatchMode(false);
        startService();
        assertTrue(AlarmService.running);
        assertNull(notification());
    }

    @Test public void watchSetup_keepsPhoneStrongReminderPermissions() {
        assertTrue(Steps.all(app).stream().anyMatch(s -> s.key.equals("overlay")));
        assertTrue(Steps.all(app).stream().anyMatch(s -> s.key.equals("vibration")));
        Steps.Step health = Steps.all(app).stream()
                .filter(s -> s.key.equals("watch_health")).findFirst().get();
        assertEquals(Steps.UNKNOWN, health.state(app));
        assertFalse(health.satisfied(app));
        Steps.markDone(app, health.key);
        assertTrue(health.satisfied(app));
        assertEquals(Steps.UNKNOWN, health.state(app));
    }
}
