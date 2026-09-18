package com.auren.musicplayer;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import androidx.activity.ComponentActivity;

public class SettingsActivity extends ComponentActivity {
    private TextView accentPreview;
    private TextView updateStatus;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ThemeManager.applyWindow(this);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (updateStatus != null) UpdateManager.resumePending(this, updateStatus);
    }

    private void buildUi() {
        LinearLayout root = column();
        root.setBackgroundColor(ThemeManager.surface(this));

        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(12), dp(6));
        bar.setBackgroundColor(ThemeManager.card(this));
        bar.setElevation(dp(3));

        TextView back = text("‹", 34, ThemeManager.resolve(this, R.color.text_primary));
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Voltar");
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(52)));

        TextView title = text("Configurações", 20, ThemeManager.resolve(this, R.color.text_primary));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column();
        content.setPadding(dp(18), dp(18), dp(18), dp(28));

        addSection(content, "APARÊNCIA", "Personalize a identidade visual do Auren.");

        LinearLayout themeCard = card();
        TextView themeTitle = text("Tema", 16, ThemeManager.resolve(this, R.color.text_primary));
        themeTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        themeCard.addView(themeTitle);
        TextView themeDesc = text("Claro ou escuro, com acento da cor que quiser.", 13,
                ThemeManager.resolve(this, R.color.text_secondary));
        themeCard.addView(themeDesc, margins(0, 5, 0, 12));

        Switch dark = new Switch(this);
        dark.setText("Modo escuro");
        dark.setTextSize(14);
        dark.setTextColor(ThemeManager.resolve(this, R.color.text_primary));
        dark.setChecked(ThemeManager.isDark(this));
        dark.setOnCheckedChangeListener((button, checked) -> {
            ThemeManager.setDark(this, checked);
            recreate();
        });
        themeCard.addView(dark, margins(0, 0, 0, 8));

        accentPreview = text("Cor de destaque: " + ThemeManager.hex(ThemeManager.accent(this)),
                13, ThemeManager.accent(this));
        accentPreview.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        themeCard.addView(accentPreview, margins(0, 4, 0, 8));

        LinearLayout colorActions = row();
        Button hexButton = button("Código HEX");
        hexButton.setOnClickListener(v -> showAccentDialog());
        colorActions.addView(hexButton, new LinearLayout.LayoutParams(0, -2, 1));

        Button paletteButton = button("Escolher cor");
        paletteButton.setOnClickListener(v -> showAccentPaletteDialog());
        LinearLayout.LayoutParams paletteLp = new LinearLayout.LayoutParams(0, -2, 1);
        paletteLp.setMargins(dp(8), 0, 0, 0);
        colorActions.addView(paletteButton, paletteLp);
        themeCard.addView(colorActions);
        content.addView(themeCard, margins(0, 0, 0, 14));

        addSection(content, "ÁUDIO", "Ferramentas para controlar o som.");

        LinearLayout audioCard = card();
        audioCard.addView(text("Equalizador profissional", 16, ThemeManager.resolve(this, R.color.text_primary)));
        audioCard.addView(text(
                "Presets + bandas do hardware do dispositivo. As preferências ficam guardadas.",
                13, ThemeManager.resolve(this, R.color.text_secondary)), margins(0, 5, 0, 10));
        Button eq = button("Abrir equalizador");
        eq.setOnClickListener(v -> startActivity(new Intent(this, EqualizerActivity.class)));
        audioCard.addView(eq);
        content.addView(audioCard, margins(0, 0, 0, 14));

        addSection(content, "ATUALIZAÇÕES", "Distribuição gratuita enquanto a Google Play fica para o futuro.");

        LinearLayout updates = card();
        updates.addView(text("Auren Updates", 16, ThemeManager.resolve(this, R.color.text_primary)));
        updates.addView(text(
                "Verifica o GitHub Releases. Quando houver uma versão nova, o Android pode abrir o instalador para concluir a atualização.",
                13, ThemeManager.resolve(this, R.color.text_secondary)), margins(0, 5, 0, 10));
        updateStatus = text("Versão instalada: " + BuildConfig.VERSION_NAME, 12,
                ThemeManager.resolve(this, R.color.text_secondary));
        updates.addView(updateStatus, margins(0, 0, 0, 8));
        Button check = button("Verificar atualização");
        check.setOnClickListener(v -> UpdateManager.check(this, updateStatus));
        updates.addView(check);
        Button downloads = button("Abrir GitHub Releases");
        downloads.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/nexauren1/music-player-auren/releases"));
            startActivity(i);
        });
        updates.addView(downloads, margins(0, 8, 0, 0));
        content.addView(updates, margins(0, 0, 0, 14));

        addSection(content, "BIBLIOTECA & DADOS", "As suas preferências e estatísticas são locais.");

        LinearLayout data = card();
        data.addView(text("Estatísticas de reprodução", 16, ThemeManager.resolve(this, R.color.text_primary)));
        data.addView(text(
                "Mais tocadas, recentes, favoritos, playlists e preferências ficam guardados no armazenamento interno do Auren.",
                13, ThemeManager.resolve(this, R.color.text_secondary)), margins(0, 5, 0, 10));
        Button reset = button("Limpar estatísticas");
        reset.setOnClickListener(v -> confirmReset());
        data.addView(reset);
        content.addView(data, margins(0, 0, 0, 14));

        LinearLayout about = rounded(ThemeManager.resolve(this, R.color.accent_mint), 22);
        about.setPadding(dp(16), dp(15), dp(16), dp(15));
        TextView aboutText = text(
                "Auren Music Player\nLocal • Privado • Personalizável\nVersão " + BuildConfig.VERSION_NAME,
                13, ThemeManager.resolve(this, R.color.text_primary));
        aboutText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        about.addView(aboutText);
        content.addView(about);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void showAccentDialog() {
        EditText input = new EditText(this);
        input.setHint("#06B6D4");
        input.setSingleLine(true);
        input.setText(ThemeManager.hex(ThemeManager.accent(this)));
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("Código HEX")
                .setMessage("Para quem conhece códigos de cor. Exemplo: #06B6D4")
                .setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Aplicar", (d, w) -> {
                    try {
                        String raw = input.getText().toString().trim();
                        int color = Color.parseColor(raw);
                        applyAccentInPlace(Color.rgb(Color.red(color), Color.green(color), Color.blue(color)));
                    } catch (IllegalArgumentException e) {
                        new AlertDialog.Builder(this)
                                .setTitle("Cor inválida")
                                .setMessage("Use o formato #RRGGBB.")
                                .setPositiveButton("OK", null)
                                .show();
                    }
                })
                .show();
    }

    private void showAccentPaletteDialog() {
        int[][] palette = {
                {Color.rgb(37, 99, 235), "Azul".hashCode()},
                {Color.rgb(6, 182, 212), "Azul ciano".hashCode()},
                {Color.rgb(29, 78, 216), "Azul escuro".hashCode()},
                {Color.rgb(124, 58, 237), "Roxo".hashCode()},
                {Color.rgb(99, 102, 241), "Índigo".hashCode()},
                {Color.rgb(236, 72, 153), "Rosa".hashCode()},
                {Color.rgb(239, 68, 68), "Vermelho".hashCode()},
                {Color.rgb(249, 115, 22), "Laranja".hashCode()},
                {Color.rgb(234, 179, 8), "Amarelo".hashCode()},
                {Color.rgb(34, 197, 94), "Verde".hashCode()},
                {Color.rgb(16, 185, 129), "Esmeralda".hashCode()},
                {Color.rgb(20, 184, 166), "Turquesa".hashCode()}
        };
        String[] names = {"Azul", "Azul ciano", "Azul escuro", "Roxo", "Índigo", "Rosa", "Vermelho", "Laranja", "Amarelo", "Verde", "Esmeralda", "Turquesa"};

        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(2);
        int current = ThemeManager.accent(this);
        for (int i = 0; i < palette.length; i++) {
            int color = palette[i][0];
            TextView swatch = new TextView(this);
            swatch.setText(names[i] + "\n" + ThemeManager.hex(color) + (isSameColor(color, current) ? "  ✓" : ""));
            swatch.setTextSize(14);
            swatch.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            swatch.setTextColor(contrast(color));
            swatch.setGravity(Gravity.CENTER);
            swatch.setPadding(dp(10), dp(10), dp(10), dp(10));
            swatch.setBackground(roundDrawable(color, 18));
            swatch.setContentDescription(names[i] + " " + ThemeManager.hex(color));
            swatch.setOnClickListener(v -> applyAccentInPlace(color));
            android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(78);
            lp.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f);
            lp.setMargins(dp(5), dp(5), dp(5), dp(5));
            grid.addView(swatch, lp);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setPadding(dp(4), dp(4), dp(4), dp(4));
        scroll.addView(grid);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Escolher uma cor")
                .setMessage("Escolha uma cor pronta. O código HEX aparece em cada opção.")
                .setView(scroll)
                .setNegativeButton("Fechar", null)
                .create();
        dialog.show();
    }

    private void applyAccentInPlace(int color) {
        ThemeManager.setAccent(this, color);
        if (accentPreview != null) {
            accentPreview.setText("Cor de destaque: " + ThemeManager.hex(color));
            accentPreview.setTextColor(color);
        }
        buildUi();
    }

    private boolean isSameColor(int a, int b) {
        return Color.red(a) == Color.red(b) && Color.green(a) == Color.green(b) && Color.blue(a) == Color.blue(b);
    }

    private int contrast(int bg) {
        double y = 0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg);
        return y < 160 ? Color.WHITE : Color.BLACK;
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Limpar estatísticas?")
                .setMessage("Isto remove contagens de reprodução e o histórico de recentes. Favoritos e playlists não serão removidos.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Limpar", (d, w) -> {
                    android.content.SharedPreferences prefs = getSharedPreferences("auren_player", MODE_PRIVATE);
                    android.content.SharedPreferences.Editor editor = prefs.edit();
                    java.util.Set<String> keys = prefs.getAll().keySet();
                    for (String key : keys) {
                        if (key.startsWith("play_count_") || key.startsWith("eq_band_")
                                || key.equals("play_counts_v2")
                                || key.equals("recent_tracks")
                                || key.equals("last_played_id")
                                || key.equals("last_played_at")
                                || key.equals("stats_schema")) {
                            editor.remove(key);
                        }
                    }
                    editor.apply();
                    recreate();
                })
                .show();
    }

    private void addSection(LinearLayout content, String eyebrow, String desc) {
        TextView e = text(eyebrow, 11, ThemeManager.accent(this));
        e.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(e, margins(0, 3, 0, 3));
        content.addView(text(desc, 13, ThemeManager.resolve(this, R.color.text_secondary)),
                margins(0, 0, 0, 10));
    }

    private LinearLayout card() {
        LinearLayout c = rounded(ThemeManager.card(this), 22);
        c.setPadding(dp(17), dp(16), dp(17), dp(16));
        c.setElevation(dp(2));
        return c;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(contrast(ThemeManager.accent(this)));
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemeManager.accent(this)));
        return b;
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

    private TextView text(String s, float size, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
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
