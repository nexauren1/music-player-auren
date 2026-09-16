package com.auren.musicplayer;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FeatureActivity extends Activity {
    private static final int GREEN = Color.rgb(32, 150, 42);
    private static final int BG = Color.rgb(247, 248, 250);
    private static final int TEXT = Color.rgb(35, 36, 40);
    private static final int MUTED = Color.rgb(105, 108, 116);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String title = getIntent().getStringExtra("title");
        String description = getIntent().getStringExtra("description");
        if (title == null) title = "Auren";
        if (description == null) description = "Esta área faz parte do Auren Music Player.";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(6), dp(4), dp(8), dp(4));
        bar.setBackgroundColor(GREEN);

        Button back = button("‹", 32, Color.WHITE);
        bar.addView(back, new LinearLayout.LayoutParams(dp(54), dp(54)));
        back.setOnClickListener(v -> finish());

        TextView heading = text(title, 18, Color.WHITE);
        heading.setTypeface(null, 1);
        bar.addView(heading, new LinearLayout.LayoutParams(0, dp(54), 1));
        root.addView(bar);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(28), dp(70), dp(28), dp(28));

        TextView icon = text("♪", 64, GREEN);
        content.addView(icon);

        TextView titleView = text(title, 24, TEXT);
        titleView.setTypeface(null, 1);
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(22);
        content.addView(titleView, titleParams);

        TextView desc = text(description, 15, MUTED);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, dp(12), 0, dp(22));
        content.addView(desc);

        TextView status = text("Estamos a construir esta função dentro do Auren.", 14, MUTED);
        status.setGravity(Gravity.CENTER);
        content.addView(status);

        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private Button button(String value, int size, int color) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(size);
        b.setTextColor(color);
        b.setAllCaps(false);
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
