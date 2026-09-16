package com.auren.musicplayer;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.core.content.ContextCompat;

public class SettingsActivity extends ComponentActivity {
    private TextView status;
    private Button checkButton;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        checkForUpdates(false);
    }

    private void buildUi() {
        LinearLayout root = column();
        root.setBackgroundColor(getColor(R.color.surface));

        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(12), dp(6));
        bar.setBackgroundColor(getColor(R.color.auren_primary));
        bar.setElevation(dp(4));

        TextView back = text("‹", 34, android.R.color.white);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(52)));

        TextView barTitle = text("Configurações", 19, android.R.color.white);
        barTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(barTitle, new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column();
        content.setPadding(dp(18), dp(18), dp(18), dp(24));

        TextView eyebrow = text("AUREN MUSIC PLAYER", 11, R.color.auren_primary);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(eyebrow);

        TextView title = text("Configurações", 30, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title, margins(0, 3, 0, 5));
        content.addView(text("Mantenha o Auren atualizado e pronto para a próxima versão.",
                14, R.color.text_secondary), margins(0, 0, 0, 18));

        LinearLayout updateCard = rounded(Color.WHITE, 24);
        updateCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        updateCard.setElevation(dp(3));

        LinearLayout iconRow = row();
        TextView icon = text("↻", 28, R.color.auren_primary);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundDrawable(getColor(R.color.accent_soft), 17));
        iconRow.addView(icon, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout iconInfo = column();
        TextView updateTitle = text("Atualização do aplicativo", 18, R.color.text_primary);
        updateTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        iconInfo.addView(updateTitle);
        iconInfo.addView(text("Versão atual: " + BuildConfig.VERSION_NAME,
                13, R.color.text_secondary), margins(0, 3, 0, 0));
        iconRow.addView(iconInfo, margins(14, 0, 0, 0));
        updateCard.addView(iconRow);

        status = text("Verificando a versão mais recente…", 13, R.color.text_secondary);
        status.setPadding(dp(12), dp(12), dp(12), dp(12));
        status.setBackground(roundDrawable(getColor(R.color.accent_soft), 14));
        updateCard.addView(status, margins(0, 16, 0, 0));

        checkButton = new Button(this);
        checkButton.setText("Verificar atualizações");
        checkButton.setTextColor(Color.WHITE);
        checkButton.setTextSize(14);
        checkButton.setAllCaps(false);
        checkButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        checkButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.auren_primary));
        checkButton.setOnClickListener(v -> checkForUpdates(true));
        updateCard.addView(checkButton, margins(0, 12, 0, 0));

        TextView flow = text(
                "Se existir uma versão nova, o Auren baixa o APK, abre o instalador oficial do Android e deixa o sistema concluir a atualização.",
                12, R.color.text_secondary);
        updateCard.addView(flow, margins(0, 10, 0, 0));
        content.addView(updateCard);

        LinearLayout about = rounded(getColor(R.color.accent_mint), 22);
        about.setPadding(dp(16), dp(15), dp(16), dp(15));
        TextView aboutText = text("Auren Music\nSimples. Pessoal. Sempre evoluindo.\nVersão " + BuildConfig.VERSION_NAME,
                13, R.color.text_primary);
        aboutText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        about.addView(aboutText);
        content.addView(about, margins(0, 14, 0, 0));

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void checkForUpdates(boolean manual) {
        if (manual) {
            checkButton.setEnabled(false);
            checkButton.setText("Verificando…");
        }
        UpdateManager.check(this, status, () -> {
            if (checkButton != null) {
                checkButton.setEnabled(true);
                checkButton.setText("Verificar atualizações");
            }
        });
    }

    private LinearLayout row() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        return v;
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
