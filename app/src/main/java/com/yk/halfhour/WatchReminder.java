package com.yk.halfhour;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** 响铃期间重复发送普通通知，由运动健康转发。不能直接控制或检测手表马达。 */
final class WatchReminder {
    static final String CHANNEL_ID = "watch_reminders_v1";
    static final int NOTIFICATION_ID = 1002;
    static final long REPEAT_MS = 30_000;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean active;
    private int sequence;

    WatchReminder(Context context) {
        this.context = context;
    }

    void start() {
        if (active) return;
        Prefs p = new Prefs(context);
        if (!p.watchMode() || !p.ringing()) return;
        active = true;
        sequence = 0;
        repeat.run();
    }

    private final Runnable repeat = new Runnable() {
        @Override public void run() {
            if (!active) return;
            Prefs p = new Prefs(context);
            if (!p.watchMode() || !p.ringing()) {
                stop();
                return;
            }
            publish(++sequence);
            // 不密集刷通知；手机/手表或运动健康仍可能进一步限频。
            handler.postDelayed(this, REPEAT_MS);
        }
    };

    void stop() {
        active = false;
        handler.removeCallbacks(repeat);
        cancel(context);
    }

    static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                c.getString(R.string.watch_channel_name), NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(c.getString(R.string.watch_channel_desc));
        ch.setSound(null, null);
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 250});
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    static boolean notificationsAllowed(Context c) {
        ensureChannel(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm.areNotificationsEnabled()
                && nm.getNotificationChannel(CHANNEL_ID).getImportance()
                != NotificationManager.IMPORTANCE_NONE;
    }

    private void publish(int count) {
        if (!notificationsAllowed(context)) return;
        PendingIntent open = PendingIntent.getActivity(context, NOTIFICATION_ID,
                new Intent(context, AlarmActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        // 内容随每次推送变化，减少转发器将同一条消息去重的机会。
        String text = context.getString(R.string.watch_notification, count,
                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        Notification n = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(context.getString(R.string.alarm_title))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setLocalOnly(false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setTimeoutAfter(REPEAT_MS * 2)
                .setContentIntent(open)
                .build();
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        try {
            // 重新发布以产生新事件；手机上只保留一条联动通知。
            nm.cancel(NOTIFICATION_ID);
            nm.notify(NOTIFICATION_ID, n);
        } catch (SecurityException ignored) {
            // 用户可能刚撤回通知权限；手机强提醒与后续调度继续。
        }
    }

    static void cancel(Context c) {
        c.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
    }
}
