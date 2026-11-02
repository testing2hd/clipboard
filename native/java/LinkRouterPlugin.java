package com.linkrouter.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import rikka.shizuku.Shizuku;

@CapacitorPlugin(name = "LinkRouter")
public class LinkRouterPlugin extends Plugin {

    private Context ctx() { return getContext(); }

    @PluginMethod
    public void getConfig(PluginCall call) {
        try { call.resolve(new JSObject(Config.load(ctx()).toString())); }
        catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void saveConfig(PluginCall call) {
        JSObject c = call.getObject("config");
        if (c == null) { call.reject("config missing"); return; }
        Config.save(ctx(), c);
        call.resolve();
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        try {
            JSObject o = new JSObject();
            o.put("serviceRunning", ClipWatchService.running);
            o.put("message", Config.getStatus(ctx()));
            o.put("overlay", Settings.canDrawOverlays(ctx()));
            o.put("shizukuRunning", ShizukuShell.running());
            o.put("shizukuGranted", ShizukuShell.ready());
            o.put("notifications", Build.VERSION.SDK_INT < 33
                    || ctx().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
            JSObject inst = new JSObject();
            JSONArray apps = Config.load(ctx()).optJSONArray("apps");
            PackageManager pm = ctx().getPackageManager();
            if (apps != null) {
                for (int i = 0; i < apps.length(); i++) {
                    String pkg = apps.getJSONObject(i).optString("pkg").trim();
                    boolean ok = true;
                    try { pm.getPackageInfo(pkg, 0); } catch (Exception e) { ok = false; }
                    inst.put(pkg, ok);
                }
            }
            o.put("installed", inst);
            call.resolve(o);
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void listApps(PluginCall call) {
        PackageManager pm = ctx().getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
        List<String[]> rows = new ArrayList<>();
        for (ResolveInfo ri : list) rows.add(new String[]{String.valueOf(ri.loadLabel(pm)), ri.activityInfo.packageName});
        Collections.sort(rows, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        try {
            JSArray arr = new JSArray();
            for (String[] r : rows) {
                JSObject o = new JSObject();
                o.put("label", r[0]);
                o.put("pkg", r[1]);
                arr.put(o);
            }
            JSObject res = new JSObject();
            res.put("apps", arr);
            call.resolve(res);
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void startService(PluginCall call) {
        try {
            JSONObject c = Config.load(ctx());
            c.put("autoDetect", true);
            Config.save(ctx(), c);
            Config.setStatus(ctx(), "Starting...");
            ctx().startForegroundService(new Intent(ctx(), ClipWatchService.class));
            call.resolve();
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void stopService(PluginCall call) {
        try {
            JSONObject c = Config.load(ctx());
            c.put("autoDetect", false);
            Config.save(ctx(), c);
            ctx().stopService(new Intent(ctx(), ClipWatchService.class));
            Config.setStatus(ctx(), "Off");
            call.resolve();
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void openOverlaySettings(PluginCall call) {
        startSettings(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx().getPackageName())));
        call.resolve();
    }

    @PluginMethod
    public void openShizuku(PluginCall call) {
        Intent i = ctx().getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if (i == null) { call.reject("Shizuku is not installed"); return; }
        startSettings(i);
        call.resolve();
    }

    @PluginMethod
    public void requestShizuku(PluginCall call) {
        try {
            if (!Shizuku.pingBinder()) { call.reject("Shizuku is not running"); return; }
            if (!ShizukuShell.ready()) Shizuku.requestPermission(1001);
            call.resolve();
        } catch (Throwable t) { call.reject(String.valueOf(t.getMessage())); }
    }

    @PluginMethod
    public void requestNotifications(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33 && getActivity() != null) {
            getActivity().requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1002);
        }
        call.resolve();
    }

    @PluginMethod
    public void testPopup(PluginCall call) {
        Intent i = new Intent(ctx(), PopupActivity.class).putExtra("url", "https://youtu.be/dQw4w9WgXcQ")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx().startActivity(i);
        call.resolve();
    }

    private void startSettings(Intent i) {
        try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx().startActivity(i); } catch (Throwable ignored) { }
    }
}
