package com.yk.halfhour;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.CombinedVibration;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * 前台服务：负责震动、点亮屏幕、把闪屏界面顶到最前面，直到用户点"我现在去休息"。
 */
public class AlarmService extends Service {
    static final String ACTION_START = "com.yk.halfhour.START";
    static final String ACTION_REST = "com.yk.halfhour.REST";
    static final String ACTION_STOP = "com.yk.halfhour.STOP";
    static final String BROADCAST_DONE = "com.yk.halfhour.DONE";

    static final String CHANNEL_ID = "alarm";
    private static final int NOTIF_ID = 1;

    /** 震 1.2 秒、停 0.3 秒，循环；强度 255 = 安卓允许的最大振幅 */
    private static final long[] TIMINGS = {0, 1200, 300};
    private static final int[] AMPLITUDES = {0, 255, 0};
    /** 每隔一段时间重新下发一次震动，防止被系统/ROM 中途掐掉 */
    private static final long REVIBRATE_MS = 20_000;
    /** 每隔一段时间检查闪屏界面是否还在最前面、屏幕是否亮着 */
    private static final long WATCHDOG_MS = 4_000;

    static volatile boolean running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock cpuLock;
    private long lastVibrateAt;

    static void start(Context c) {
        new Prefs(c).setRinging(true);
        // 兜底：万一服务被杀，30 分钟后闹钟还会再响；用户点"去休息"时会按点击时间重排
        Scheduler.rescheduleFromNow(c);
        Intent i = new Intent(c, AlarmService.class).setAction(ACTION_START);
        try {
            c.startForegroundService(i);
        } catch (RuntimeException e) {
            // 极少数情况下系统不允许后台启动前台服务：退而求其次，发全屏通知把闪屏界面拉起来，
            // 界面起来以后会再启动本服务
            postNotification(c);
        }
    }

    static void rest(Context c) {
        c.startService(new Intent(c, AlarmService.class).setAction(ACTION_REST));
    }

    static void stop(Context c) {
        if (running) c.startService(new Intent(c, AlarmService.class).setAction(ACTION_STOP));
        new Prefs(c).setRinging(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_REST.equals(action) || ACTION_STOP.equals(action)) {
            Prefs p = new Prefs(this);
            p.setRinging(false);
            if (ACTION_REST.equals(action)) Scheduler.rescheduleFromNow(this);
            finishRinging();
            return START_NOT_STICKY;
        }

        Notification n = buildNotification(this);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
        if (!new Prefs(this).ringing()) {
            // 过期的启动请求（用户已经点过"去休息"了）
            finishRinging();
            return START_NOT_STICKY;
        }
        if (!running) {
            running = true;
            PowerManager pm = getSystemService(PowerManager.class);
            cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "halfhour:ring");
            cpuLock.acquire(6 * 60 * 60 * 1000L);
            vibrate();
            handler.post(watchdog);
        }
        wakeScreen();
        showAlarmScreen();
        return START_STICKY;
    }

    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (System.currentTimeMillis() - lastVibrateAt >= REVIBRATE_MS) vibrate();
            PowerManager pm = getSystemService(PowerManager.class);
            if (!pm.isInteractive()) wakeScreen();
            if (!AlarmActivity.visible) showAlarmScreen();
            handler.postDelayed(this, WATCHDOG_MS);
        }
    };

    private void vibrate() {
        lastVibrateAt = System.currentTimeMillis();
        if (Build.VERSION.SDK_INT >= 33) {
            VibratorManager vm = getSystemService(VibratorManager.class);
            Vibrator v = vm.getDefaultVibrator();
            VibrationEffect effect = buildEffect(v);
            // USAGE_ALARM：静音/勿扰/熄屏时系统也不会掐掉（普通通知震动会被掐）
            VibrationAttributes attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM);
            // 同时驱动手机里所有的震动马达
            vm.vibrate(CombinedVibration.createParallel(effect), attrs);
        } else {
            Vibrator v;
            if (Build.VERSION.SDK_INT >= 31) {
                v = getSystemService(VibratorManager.class).getDefaultVibrator();
            } else {
                v = getSystemService(Vibrator.class);
            }
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            v.vibrate(buildEffect(v), attrs);
        }
    }

    private static VibrationEffect buildEffect(Vibrator v) {
        if (v.hasAmplitudeControl()) {
            return VibrationEffect.createWaveform(TIMINGS, AMPLITUDES, 1);
        }
        return VibrationEffect.createWaveform(TIMINGS, 1);
    }

    private void cancelVibration() {
        if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager.class).cancel();
        } else {
            getSystemService(Vibrator.class).cancel();
        }
    }

    /** 点亮屏幕（熄屏/锁屏状态下也会亮起来） */
    @SuppressWarnings("deprecation")
    private void wakeScreen() {
        PowerManager pm = getSystemService(PowerManager.class);
        PowerManager.WakeLock screen = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "halfhour:screen");
        screen.acquire(5_000);
    }

    private void showAlarmScreen() {
        Intent i = new Intent(this, AlarmActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        try {
            // 有"显示在其他应用上层"权限时，系统允许从后台直接弹出界面；
            // 没有的话系统会忽略这次调用，靠通知里的全屏意图把界面拉起来
            startActivity(i);
        } catch (RuntimeException ignored) {
        }
    }

    private void finishRinging() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        cancelVibration();
        if (cpuLock != null && cpuLock.isHeld()) cpuLock.release();
        cpuLock = null;
        sendBroadcast(new Intent(BROADCAST_DONE).setPackage(getPackageName()));
        stopForeground(STOP_FOREGROUND_REMOVE);
        getSystemService(NotificationManager.class).cancel(NOTIF_ID);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (running) {
            running = false;
            handler.removeCallbacksAndMessages(null);
            cancelVibration();
            if (cpuLock != null && cpuLock.isHeld()) cpuLock.release();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------- 通知 ----------

    static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                c.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(c.getString(R.string.channel_desc));
        ch.setSound(null, null);
        ch.enableVibration(false); // 震动由服务自己控制，强度更大
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.setBypassDnd(true);
        nm.createNotificationChannel(ch);
    }

    static Notification buildNotification(Context c) {
        ensureChannel(c);
        Intent screen = new Intent(c, AlarmActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent full = PendingIntent.getActivity(c, 10, screen,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent rest = PendingIntent.getService(c, 11,
                new Intent(c, AlarmService.class).setAction(ACTION_REST),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(c, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(c.getString(R.string.alarm_title))
                .setContentText(c.getString(R.string.alarm_text))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(full)
                .setFullScreenIntent(full, true)
                .addAction(new Notification.Action.Builder(null,
                        c.getString(R.string.btn_rest), rest).build())
                .build();
    }

    private static void postNotification(Context c) {
        try {
            c.getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification(c));
        } catch (RuntimeException ignored) {
        }
    }
}
