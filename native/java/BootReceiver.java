package com.linkrouter.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            if (Config.load(ctx).optBoolean("autoDetect")) {
                ctx.startForegroundService(new Intent(ctx, ClipWatchService.class));
            }
        } catch (Exception ignored) { }
    }
}
