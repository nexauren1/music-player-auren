package com.auren.musicplayer;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.core.content.ContextCompat;

public class SettingsActivity extends ComponentActivity {
    private TextView status;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = column();
        root.setPadding(dp(20), dp(16), dp(20), dp(22));
        root.setBackgroundColor(getColor(R.color.surface));

        TextView back = text("‹  AUREN", 17, R.color.text_primary);
        back.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(54)));

        TextView eyebrow = text("PREFERENCES", 11, R.color.auren_primary);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(eyebrow, margins(2, 14, 0, 5));

        TextView title = text("Settings", 31, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);
        TextView sub = text("Keep Auren ready for what comes next.", 14, R.color.text_secondary);
        root.addView(sub, margins(0, 3, 0, 22));

        LinearLayout card = rounded(getColor(R.color.card), 22);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setElevation(dp(2));

        TextView icon = text("↻", 28, R.color.auren_primary);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundDrawable(getColor(R.color.accent_soft), 16));
        card.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));

        TextView head = text("Updates", 19, R.color.text_primary);
        head.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(head, margins(0, 16, 0, 4));

        TextView version = text("Auren Music  •  Version " + BuildConfig.VERSION_NAME,
                13, R.color.text_secondary);
        card.addView(version);

        Button check = new Button(this);
        check.setText("Check for updates");
        check.setTextColor(Color.WHITE);
        check.setTextSize(14);
        check.setAllCaps(false);
        check.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        check.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.auren_primary));
        check.setOnClickListener(v -> UpdateManager.check(this, status));
        card.addView(check, margins(0, 18, 0, 0));

        status = text("You're up to date until we find a newer release.",
                12, R.color.text_secondary);
        card.addView(status, margins(0, 10, 0, 0));
        root.addView(card);

        LinearLayout about = rounded(getColor(R.color.accent_mint), 20);
        about.setPadding(dp(16), dp(14), dp(16), dp(14));
        TextView aboutText = text("Auren Music\nSimple. Personal. Built to evolve.",
                13, R.color.text_primary);
        aboutText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        about.addView(aboutText);
        root.addView(about, margins(0, 16, 0, 0));

        setContentView(root);
    }

    private LinearLayout column() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private LinearLayout rounded(int color, int radius) {
        LinearLayout v = column();
        v.setBackground(roundDrawable(color, radius));
        return v;
    }

    private android.graphics.drawable.GradientDrawable roundDrawable(int color, int radius) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        return bg;
    }

    private TextView text(String s, float z, int c) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(z);
        v.setTextColor(getColor(c));
        return v;
    }

    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }
}
