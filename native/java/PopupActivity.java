package com.linkrouter.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;

/** Used for the share target ("Share -> Link Router") and the Test button. */
public class PopupActivity extends Activity {
    private FrameLayout root;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new FrameLayout(this);
        setContentView(root);
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handle(i);
    }

    private void handle(Intent i) {
        String url = null;
        if (Intent.ACTION_SEND.equals(i.getAction())) url = Config.extractUrl(i.getStringExtra(Intent.EXTRA_TEXT));
        else if (i.getStringExtra("url") != null) url = Config.extractUrl(i.getStringExtra("url"));
        if (url == null) { finish(); return; }
        PopupUi.build(this, root, Config.load(this), url, this::finish);
    }
}
