package com.auren.musicplayer;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.ComponentActivity;

public class SettingsActivity extends ComponentActivity {
    private TextView status;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(24), dp(22), dp(22));
        root.setBackgroundColor(getColor(R.color.surface));

        TextView back = text("‹  Settings", 22, R.color.text_primary);
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView title = text("App settings", 30, R.color.text_primary); title.setTypeface(null, 1); root.addView(title);
        TextView sub = text("Keep Auren ready for what comes next.", 14, R.color.text_secondary); root.addView(sub, margins(0, 3, 0, 22));

        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(18), dp(18), dp(18), dp(18)); card.setBackgroundColor(getColor(R.color.card));
        TextView head = text("Updates", 19, R.color.text_primary); head.setTypeface(null, 1); card.addView(head);
        TextView version = text("Current version  " + BuildConfig.VERSION_NAME, 14, R.color.text_secondary); card.addView(version, margins(0, 6, 0, 14));
        Button check = new Button(this); check.setText("Check for updates");
        check.setOnClickListener(v -> UpdateManager.check(this, status)); card.addView(check);
        status = text("You're up to date until we find a newer release.", 13, R.color.text_secondary); card.addView(status, margins(0, 10, 0, 0));
        root.addView(card);

        TextView about = text("Auren Music  •  Built to evolve", 13, R.color.text_secondary);
        root.addView(about, margins(0, 26, 0, 0));
        setContentView(root);
    }
    private TextView text(String s, float z, int c) { TextView v = new TextView(this); v.setText(s); v.setTextSize(z); v.setTextColor(getColor(c)); return v; }
    private LinearLayout.LayoutParams margins(int l,int t,int r,int b){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p; }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
}
