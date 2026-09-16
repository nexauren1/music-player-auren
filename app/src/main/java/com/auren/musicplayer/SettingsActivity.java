package com.auren.musicplayer;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private static final int GREEN = Color.rgb(32, 150, 42);
    private static final int BG = Color.rgb(247, 248, 250);
    private static final int TEXT = Color.rgb(35, 36, 40);
    private static final int MUTED = Color.rgb(105, 108, 116);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences prefs = getSharedPreferences("auren", MODE_PRIVATE);

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

        TextView title = text("CONFIGURAÇÕES", 18, Color.WHITE);
        title.setTypeface(null, 1);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(54), 1));
        root.addView(bar);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(20));

        addSection(content, "APARÊNCIA");
        Switch dark = new Switch(this);
        dark.setText("Tema escuro");
        dark.setTextSize(17);
        dark.setTextColor(TEXT);
        dark.setChecked(prefs.getBoolean("dark", false));
        dark.setPadding(0, dp(12), 0, dp(12));
        content.addView(dark, new LinearLayout.LayoutParams(-1, dp(60)));
        dark.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean("dark", checked).apply());

        addSection(content, "REPRODUÇÃO");
        addInfo(content, "Reprodução em segundo plano", "O player continuará a ser evoluído para funcionar com a tela bloqueada.");
        addInfo(content, "Biblioteca", "As músicas são lidas diretamente do armazenamento de áudio do dispositivo.");

        addSection(content, "PRIVACIDADE");
        addInfo(content, "Dados", "O Auren não precisa enviar a sua biblioteca de músicas para um servidor para reproduzir arquivos locais.");

        addSection(content, "SOBRE");
        addInfo(content, "Auren Music Player", "Versão 1.2.0");

        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void addSection(LinearLayout parent, String value) {
        TextView section = text(value, 13, GREEN);
        section.setTypeface(null, 1);
        section.setPadding(0, dp(20), 0, dp(8));
        parent.addView(section);
    }

    private void addInfo(LinearLayout parent, String title, String description) {
        TextView t = text(title, 16, TEXT);
        t.setTypeface(null, 1);
        t.setPadding(0, dp(10), 0, dp(2));
        parent.addView(t);
        TextView d = text(description, 13, MUTED);
        d.setPadding(0, 0, 0, dp(10));
        parent.addView(d);
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
