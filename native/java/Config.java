package com.linkrouter.app;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Only the app list is user-editable. Stored as JSON in SharedPreferences. */
public class Config {
    private static final String PREFS = "linkrouter";
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"']+");
    private static final String[] DOMAINS = {"youtube.com", "youtu.be", "facebook.com", "fb.watch", "fb.com",
            "tiktok.com", "instagram.com", "twitter.com", "x.com", "vimeo.com", "dailymotion.com"};

    public static JSONObject defaults() {
        try {
            JSONObject o = new JSONObject();
            JSONArray apps = new JSONArray();
            apps.put(app("Vidmate", "com.video.fun.app"));
            apps.put(app("Seal Plus", "com.maheshtechnicals.sealplus"));
            o.put("apps", apps);
            o.put("autoDetect", false);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static JSONObject app(String label, String pkg) throws Exception {
        JSONObject a = new JSONObject();
        a.put("label", label);
        a.put("pkg", pkg);
        return a;
    }

    public static JSONObject load(Context c) {
        try {
            String s = c.getSharedPreferences(PREFS, 0).getString("config", null);
            if (s != null) {
                JSONObject o = new JSONObject(s);
                if (o.optJSONArray("apps") != null) return o;
            }
        } catch (Exception ignored) { }
        return defaults();
    }

    public static void save(Context c, JSONObject o) {
        c.getSharedPreferences(PREFS, 0).edit().putString("config", o.toString()).commit();
    }

    public static void setStatus(Context c, String msg) {
        c.getSharedPreferences(PREFS, 0).edit().putString("status", msg).apply();
    }

    public static String getStatus(Context c) {
        return c.getSharedPreferences(PREFS, 0).getString("status", "Off");
    }

    public static String extractUrl(CharSequence t) {
        if (t == null) return null;
        Matcher m = URL.matcher(t);
        return m.find() ? m.group() : null;
    }

    public static boolean matches(String url) {
        try {
            String host = Uri.parse(url).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            for (String d : DOMAINS) if (host.equals(d) || host.endsWith("." + d)) return true;
        } catch (Exception ignored) { }
        return false;
    }
}
