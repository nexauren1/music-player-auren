package com.auren.musicplayer;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.ComponentActivity;

public class SettingsActivity extends ComponentActivity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = column();
        root.setBackgroundColor(getColor(R.color.surface));

        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(12), dp(6));
        bar.setBackgroundColor(Color.WHITE);
        bar.setElevation(dp(2));

        TextView back = text("‹", 34, R.color.text_primary);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Voltar");
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(52)));

        TextView barTitle = text("Configurações", 19, R.color.text_primary);
        barTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(barTitle, new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column();
        content.setPadding(dp(18), dp(20), dp(18), dp(24));

        TextView eyebrow = text("AUREN MUSIC PLAYER", 11, R.color.auren_primary);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(eyebrow);

        TextView title = text("Configurações", 30, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title, margins(0, 3, 0, 8));

        content.addView(text(
                "Um espaço simples para informações do aplicativo e do seu áudio.",
                14, R.color.text_secondary), margins(0, 0, 0, 18));

        content.addView(infoCard(
                "Atualizações",
                "As versões oficiais do Auren serão distribuídas pelo Google Play.",
                "Não há instalador APK automático dentro do aplicativo."
        ));

        content.addView(infoCard(
                "Reprodução em segundo plano",
                "O Auren usa o serviço de mídia do Android para manter a reprodução ativa e integrar os controles do sistema.",
                "A reprodução pode continuar enquanto você navega fora do aplicativo."
        ), margins(0, 12, 0, 0));

        content.addView(infoCard(
                "Biblioteca local",
                "O aplicativo lê as músicas disponíveis no seu dispositivo para criar a biblioteca.",
                "Favoritos, histórico e playlists são guardados localmente."
        ), margins(0, 12, 0, 0));

        LinearLayout about = rounded(getColor(R.color.accent_mint), 22);
        about.setPadding(dp(16), dp(15), dp(16), dp(15));
        TextView aboutText = text(
                "Auren Music\nSimples. Pessoal. Sempre a evoluir.\nVersão " + BuildConfig.VERSION_NAME,
                13, R.color.text_primary);
        aboutText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        about.addView(aboutText);
        content.addView(about, margins(0, 16, 0, 0));

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private View infoCard(String title, String body, String note) {
        LinearLayout card = rounded(Color.WHITE, 22);
        card.setPadding(dp(18), dp(17), dp(18), dp(17));
        card.setElevation(dp(2));

        TextView heading = text(title, 17, R.color.text_primary);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(heading);

        card.addView(text(body, 13, R.color.text_secondary), margins(0, 6, 0, 0));
        TextView small = text(note, 12, R.color.auren_primary);
        small.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(small, margins(0, 10, 0, 0));
        return card;
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
