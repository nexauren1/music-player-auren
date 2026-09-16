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
    private static final int LIGHT_BG = Color.rgb(247, 248, 250);
    private static final int DARK_BG = Color.rgb(24, 26, 29);
    private static final int LIGHT_TEXT = Color.rgb(35, 36, 40);
    private static final int DARK_TEXT = Color.rgb(242, 243, 245);
    private static final int MUTED_LIGHT = Color.rgb(105, 108, 116);
    private static final int MUTED_DARK = Color.rgb(180, 184, 191);

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("auren", MODE_PRIVATE);
        buildUi();
    }

    private void buildUi() {
        boolean dark = prefs.getBoolean("dark", false);
        int bg = dark ? DARK_BG : LIGHT_BG;
        int textColor = dark ? DARK_TEXT : LIGHT_TEXT;
        int muted = dark ? MUTED_DARK : MUTED_LIGHT;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

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

        addSection(content, "APARÊNCIA", textColor);
        Switch darkSwitch = new Switch(this);
        darkSwitch.setText("Tema escuro");
        darkSwitch.setTextSize(17);
        darkSwitch.setTextColor(textColor);
        darkSwitch.setChecked(dark);
        darkSwitch.setPadding(0, dp(12), 0, dp(12));
        content.addView(darkSwitch, new LinearLayout.LayoutParams(-1, dp(60)));
        darkSwitch.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("dark", checked).apply();
            recreate();
        });

        addSection(content, "REPRODUÇÃO", textColor);
        addInfo(content, "Reprodução em segundo plano",
                "O Auren mantém a reprodução ativa através do serviço de mídia.",
                textColor, muted);
        addInfo(content, "Controles",
                "Use play, pausa, anterior, próxima, aleatório e repetir no player.",
                textColor, muted);

        addSection(content, "BIBLIOTECA", textColor);
        addInfo(content, "Músicas locais",
                "A biblioteca é lida diretamente do MediaStore do dispositivo.",
                textColor, muted);
        addInfo(content, "Atualização",
                "Volte à página inicial para atualizar a lista de músicas disponíveis.",
                textColor, muted);

        addSection(content, "PRIVACIDADE", textColor);
        addInfo(content, "Dados",
                "A biblioteca local não precisa ser enviada para um servidor para reproduzir música.",
                textColor, muted);

        addSection(content, "SOBRE", textColor);
        addInfo(content, "Auren Music Player",
                "Versão 1.5.0 · Player local para Android",
                textColor, muted);

        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void addSection(LinearLayout parent, String value, int textColor) {
        TextView section = text(value, 13, GREEN);
        section.setTypeface(null, 1);
        section.setPadding(0, dp(20), 0, dp(8));
        parent.addView(section);
    }

    private void addInfo(LinearLayout parent, String title, String description,
                         int textColor, int muted) {
        TextView t = text(title, 16, textColor);
        t.setTypeface(null, 1);
        t.setPadding(0, dp(10), 0, dp(2));
        parent.addView(t);
        TextView d = text(description, 13, muted);
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
