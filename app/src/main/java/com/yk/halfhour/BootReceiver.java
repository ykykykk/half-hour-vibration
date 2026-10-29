package com.yk.halfhour;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机、升级、改系统时间后，闹钟会被系统清掉，这里重新排。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction();
        boolean processWasKilled = Intent.ACTION_BOOT_COMPLETED.equals(a)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                || "android.intent.action.QUICKBOOT_POWERON".equals(a)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(a);
        if (processWasKilled) new Prefs(c).setRinging(false);
        Scheduler.rescheduleFromNow(c);
    }
}
