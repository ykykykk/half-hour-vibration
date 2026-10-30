package com.yk.halfhour;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 闹钟到点：开始手机震动和闪屏，并按设置联动手表。 */
public class AlarmReceiver extends BroadcastReceiver {
    static final String ACTION_FIRE = "com.yk.halfhour.FIRE";

    @Override
    public void onReceive(Context c, Intent intent) {
        if (!ACTION_FIRE.equals(intent.getAction())) return;
        Prefs p = new Prefs(c);
        if (!p.enabled()) return;
        AlarmService.start(c);
    }
}
