package com.auren.musicplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class EffectsActivity extends Activity {
    private static final int GREEN = Color.rgb(26, 142, 55);
    private static final int BG = Color.rgb(246, 248, 247);
    private static final int CARD = Color.WHITE;
    private static final int TEXT = Color.rgb(24, 28, 26);
    private static final int MUTED = Color.rgb(103, 111, 106);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = column(BG);

        LinearLayout bar = row(GREEN);
        Button back = button("‹", 32, Color.WHITE);
        bar.addView(back, size(54, 54));
        back.setOnClickListener(v -> finish());

        TextView title = text("EFEITOS", 18, Color.WHITE);
        title.setTypeface(null, 1);
        title.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(title, weight(1, 54));
        root.addView(bar);

        LinearLayout content = column(BG);
        content.setPadding(dp(18), dp(18), dp(18), dp(24));

        addIntro(content);

        LinearLayout switches = card();
        Switch equalizer = new Switch(this);
        equalizer.setText("Equalizador");
        equalizer.setTextSize(17);
        equalizer.setTextColor(TEXT);
        equalizer.setChecked(PlayerManager.isEqualizerEnabled());
        switches.addView(equalizer, size(-1, 58));
        equalizer.setOnCheckedChangeListener((button, checked) -> {
            PlayerManager.setEqualizerEnabled(checked);
            if (checked) toast("Equalizador ativado");
        });

        Switch bass = new Switch(this);
        bass.setText("Reforço de graves");
        bass.setTextSize(17);
        bass.setTextColor(TEXT);
        bass.setChecked(PlayerManager.isBassBoostEnabled());
        switches.addView(bass, size(-1, 58));
        bass.setOnCheckedChangeListener((button, checked) -> {
            PlayerManager.setBassBoostEnabled(checked);
            if (checked) toast("Graves reforçados");
        });
        content.addView(switches, size(-1, -2));

        TextView presetTitle = text("PREDEFINIÇÕES", 13, GREEN);
        presetTitle.setTypeface(null, 1);
        presetTitle.setPadding(0, dp(22), 0, dp(8));
        content.addView(presetTitle);

        LinearLayout presets = card();
        addPreset(presets, "Normal", "Som equilibrado");
        addPreset(presets, "Bass", "Graves mais fortes");
        addPreset(presets, "Vocal", "Voz mais destacada");
        addPreset(presets, "Electronic", "Mais impacto e brilho");
        content.addView(presets, size(-1, -2));

        TextView note = text(
                "Os efeitos usam o processamento de áudio disponível no dispositivo. "
                        + "Alguns telemóveis podem limitar determinados efeitos.",
                12, MUTED);
        note.setPadding(dp(4), dp(18), dp(4), 0);
        content.addView(note);

        root.addView(content, weight(1, 0));
        setContentView(root);
    }

    private void addIntro(LinearLayout content) {
        TextView heading = text("Som ao teu gosto", 24, TEXT);
        heading.setTypeface(null, 1);
        content.addView(heading);
        TextView sub = text(
                "Ajusta a reprodução enquanto uma música estiver a tocar.",
                14, MUTED);
        sub.setPadding(0, dp(5), 0, dp(18));
        content.addView(sub);
    }

    private void addPreset(LinearLayout parent, String name, String description) {
        LinearLayout item = row(CARD);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(4), dp(4), dp(4), dp(4));

        LinearLayout info = column(CARD);
        TextView title = text(name, 16, TEXT);
        title.setTypeface(null, 1);
        TextView desc = text(description, 12, MUTED);
        info.addView(title);
        info.addView(desc);
        item.addView(info, weight(1, 58));

        Button apply = button("Aplicar", 13, GREEN);
        item.addView(apply, size(92, 48));
        apply.setOnClickListener(v -> {
            boolean ok = PlayerManager.applyEqualizerPreset(name);
            if (ok) {
                Toast.makeText(this, name + " aplicado", Toast.LENGTH_SHORT).show();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Efeito indisponível")
                        .setMessage("O dispositivo não disponibilizou o equalizador neste momento.")
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
        parent.addView(item, size(-1, 66));
    }

    private LinearLayout card() {
        LinearLayout value = column(CARD);
        value.setPadding(dp(12), dp(6), dp(12), dp(6));
        value.setBackground(round(CARD, 18));
        value.setElevation(dp(1));
        return value;
    }

    private LinearLayout row(int color) {
        LinearLayout value = new LinearLayout(this);
        value.setOrientation(LinearLayout.HORIZONTAL);
        value.setBackgroundColor(color);
        return value;
    }

    private LinearLayout column(int color) {
        LinearLayout value = new LinearLayout(this);
        value.setOrientation(LinearLayout.VERTICAL);
        value.setBackgroundColor(color);
        return value;
    }

    private Button button(String value, int size, int color) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(size);
        button.setTextColor(color);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private LinearLayout.LayoutParams size(int width, int height) {
        return new LinearLayout.LayoutParams(
                width < 0 ? width : dp(width),
                height < 0 ? height : dp(height));
    }

    private LinearLayout.LayoutParams weight(float value, int height) {
        return new LinearLayout.LayoutParams(
                0, height < 0 ? height : dp(height), value);
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radius) {
        android.graphics.drawable.GradientDrawable drawable =
                new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
