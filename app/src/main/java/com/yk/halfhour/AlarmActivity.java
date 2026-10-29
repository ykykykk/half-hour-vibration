package com.yk.halfhour;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** 全屏闪烁 + "我现在去休息"按钮。只有点这个按钮才会停。 */
public class AlarmActivity extends Activity {
    static volatile boolean visible;

    private static final long FLASH_MS = 350;
    private static final int[] COLORS = {Color.WHITE, Color.rgb(230, 0, 0)};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private View root;
    private TextView title;
    private int colorIndex;

    private final BroadcastReceiver doneReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            finish();
        }
    };

    private final Runnable flash = new Runnable() {
        @Override
        public void run() {
            colorIndex = (colorIndex + 1) % COLORS.length;
            int bg = COLORS[colorIndex];
            root.setBackgroundColor(bg);
            title.setTextColor(bg == Color.WHITE ? Color.rgb(200, 0, 0) : Color.WHITE);
            handler.postDelayed(this, FLASH_MS);
        }
    };

    @SuppressWarnings("deprecation")
    @SuppressLint("UnspecifiedRegisterReceiverFlag") // 33 以下没有这个参数；广播本身已限定本包
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
        w.setAttributes(lp);

        setContentView(R.layout.activity_alarm);
        root = findViewById(R.id.root);
        title = findViewById(R.id.title);
        TextView time = findViewById(R.id.time);
        time.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        Button rest = findViewById(R.id.btn_rest);
        rest.setOnClickListener(v -> {
            AlarmService.rest(this);
            finish();
        });

        IntentFilter f = new IntentFilter(AlarmService.BROADCAST_DONE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(doneReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(doneReceiver, f);
        }

        if (Build.VERSION.SDK_INT >= 33) {
            // 屏蔽返回键/返回手势：必须点按钮
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_OVERLAY, () -> { });
        }
        ensureRinging();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        ensureRinging();
    }

    /** 被通知的全屏意图拉起来时，服务可能还没在跑（后台启动被拒），这里补启动。 */
    private void ensureRinging() {
        Prefs p = new Prefs(this);
        if (!p.ringing()) {
            finish();
            return;
        }
        if (!AlarmService.running) {
            try {
                startForegroundService(new Intent(this, AlarmService.class)
                        .setAction(AlarmService.ACTION_START));
            } catch (RuntimeException ignored) {
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // 必须点按钮
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        handler.removeCallbacks(flash);
        handler.post(flash);
    }

    @Override
    protected void onPause() {
        super.onPause();
        visible = false;
        handler.removeCallbacks(flash);
    }

    @Override
    protected void onDestroy() {
        visible = false;
        handler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(doneReceiver);
        } catch (RuntimeException ignored) {
        }
        super.onDestroy();
    }
}
