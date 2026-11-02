package com.linkrouter.app;

import android.content.pm.PackageManager;
import java.lang.reflect.Method;
import rikka.shizuku.Shizuku;

/** Runs commands with shell privileges through Shizuku. */
public class ShizukuShell {
    public static boolean running() {
        try { return Shizuku.pingBinder(); } catch (Throwable t) { return false; }
    }

    public static boolean ready() {
        try {
            return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) { return false; }
    }

    /** Shizuku.newProcess is private in recent API versions, so call it by reflection. */
    public static Process newProcess(String[] cmd) throws Exception {
        Method m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
        m.setAccessible(true);
        return (Process) m.invoke(null, cmd, null, null);
    }
}
