package com.linkrouter.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

/** The "open with" popup, built in code so it works in an activity or in an overlay window. */
final class PopupUi {
    private PopupUi() { }

    static void build(final Context c, FrameLayout root, JSONObject cfg, final String url, final Runnable dismiss) {
        root.removeAllViews();
        root.setBackgroundColor(0x99000000);
        root.setOnClickListener(v -> dismiss.run());

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(c, 20), dp(c, 18), dp(c, 20), dp(c, 12));
        card.setBackground(round(c, 0xFF1B1F2A, 24));
        card.setClickable(true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        lp.setMargins(dp(c, 22), 0, dp(c, 22), 0);
        root.addView(card, lp);

        card.addView(text(c, "Open link with", 18, Color.WHITE, true));
        TextView link = text(c, url, 12, 0xFF8A93A8, false);
        link.setMaxLines(2);
        link.setEllipsize(TextUtils.TruncateAt.END);
        link.setPadding(0, dp(c, 4), 0, dp(c, 6));
        card.addView(link);

        JSONArray apps = cfg.optJSONArray("apps");
        PackageManager pm = c.getPackageManager();
        if (apps != null) {
            for (int i = 0; i < apps.length(); i++) {
                JSONObject a = apps.optJSONObject(i);
                if (a == null) continue;
                final String pkg = a.optString("pkg").trim();
                if (pkg.isEmpty()) continue;
                String label = a.optString("label", "").trim();
                if (label.isEmpty()) label = pkg;
                Drawable icon = null;
                boolean inst = true;
                try { icon = pm.getApplicationIcon(pkg); } catch (Exception e) { inst = false; }
                final boolean ok = inst;
                card.addView(row(c, ok ? label : label + " (not installed)", icon, ok, v -> {
                    if (!ok) { Toast.makeText(c, pkg + " is not installed", Toast.LENGTH_SHORT).show(); return; }
                    launch(c, pkg, url);
                    dismiss.run();
                }));
            }
        }
        card.addView(row(c, "Browser", null, true, v -> { openBrowser(c, url); dismiss.run(); }));

        TextView close = text(c, "Dismiss", 14, 0xFF8A93A8, false);
        close.setGravity(Gravity.CENTER);
        close.setPadding(0, dp(c, 14), 0, dp(c, 8));
        close.setOnClickListener(v -> dismiss.run());
        card.addView(close);
    }

    static void launch(Context c, String pkg, String url) {
        PackageManager pm = c.getPackageManager();
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, url).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        for (Intent in : new Intent[]{send, view}) {
            try {
                if (in.resolveActivity(pm) != null) { c.startActivity(in); return; }
            } catch (Throwable ignored) { }
        }
        // Fallback: open the app and leave the link on the clipboard.
        try {
            ((ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("link", url));
            Intent l = pm.getLaunchIntentForPackage(pkg);
            if (l != null) {
                l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(l);
                Toast.makeText(c, "Link copied - paste it in the app", Toast.LENGTH_LONG).show();
            }
        } catch (Throwable ignored) { }
    }

    static void openBrowser(Context c, String url) {
        try {
            Intent i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER);
            i.setData(Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(c, "No browser found", Toast.LENGTH_SHORT).show();
        }
    }

    private static int dp(Context c, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    private static TextView text(Context c, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private static GradientDrawable round(Context c, int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    private static View row(Context c, String label, Drawable icon, boolean enabled, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        r.setBackground(round(c, 0xFF272C3A, 16));
        r.setAlpha(enabled ? 1f : 0.5f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 8);
        r.setLayoutParams(lp);
        if (icon != null) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(icon);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(c, 36), dp(c, 36));
            ilp.rightMargin = dp(c, 14);
            r.addView(iv, ilp);
        }
        r.addView(text(c, label, 16, Color.WHITE, false));
        r.setOnClickListener(click);
        return r;
    }
}
