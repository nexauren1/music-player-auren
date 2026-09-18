package com.auren.musicplayer;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.activity.ComponentActivity;

public class EqualizerActivity extends ComponentActivity {

    private LinearLayout bandContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.applyWindow(this);
        buildUi();
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

        LinearLayout titleBox = column();
        TextView title = text("Equalizador", 19, ThemeManager.resolve(this, R.color.text_primary));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleBox.addView(title);
        TextView sub = text("Som ajustado à sua música", 11, ThemeManager.resolve(this, R.color.text_secondary));
        titleBox.addView(sub, margins(0, 1, 0, 0));
        bar.addView(titleBox, new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column();
        content.setPadding(dp(18), dp(18), dp(18), dp(30));

        TextView intro = text(
                "Escolha um preset ou ajuste cada banda. As alterações ficam guardadas e voltam quando o áudio é iniciado.",
                13, ThemeManager.resolve(this, R.color.text_secondary));
        content.addView(intro, margins(0, 0, 0, 14));

        LinearLayout presets = row();
        presets.setGravity(Gravity.CENTER_VERTICAL);
        String[] names = AudioEffectsManager.presetNames();
        for (int i = 0; i < names.length; i++) {
            final int preset = i;
            Button b = new Button(this);
            b.setText(names[i]);
            b.setTextSize(11);
            b.setAllCaps(false);
            b.setTextColor(ThemeManager.resolve(this, R.color.text_primary));
            b.setOnClickListener(v -> {
                AudioEffectsManager.applyPreset(this, preset);
                rebuildBands();
            });
            presets.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        content.addView(horizontalScroll(presets), margins(0, 0, 0, 18));

        bandContainer = column();
        content.addView(bandContainer);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        rebuildBands();
    }

    private void rebuildBands() {
        if (bandContainer == null) return;
        bandContainer.removeAllViews();

        int count = AudioEffectsManager.getNumberOfBands();
        if (count <= 0) {
            LinearLayout card = rounded(ThemeManager.card(this), 22);
            card.setPadding(dp(18), dp(18), dp(18), dp(18));
            TextView t = text(
                    "O equalizador do dispositivo ainda não está disponível.

Reproduza uma música e abra esta tela novamente. Alguns aparelhos não oferecem efeitos de áudio por sessão.",
                    14, ThemeManager.resolve(this, R.color.text_secondary));
            card.addView(t);
            bandContainer.addView(card);
            return;
        }

        for (int i = 0; i < count; i++) {
            final int band = i;
            String frequency = formatFrequency(AudioEffectsManager.getCenterFrequencyHz(i));
            LinearLayout line = row();
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(dp(12), dp(8), dp(12), dp(8));
            line.setBackground(roundDrawable(ThemeManager.card(this), 18));

            TextView label = text(frequency, 12, ThemeManager.resolve(this, R.color.text_secondary));
            label.setGravity(Gravity.CENTER);
            line.addView(label, new LinearLayout.LayoutParams(dp(58), dp(44)));

            SeekBar seek = new SeekBar(this);
            seek.setMax(AudioEffectsManager.getMaxLevel() - AudioEffectsManager.getMinLevel());
            seek.setProgress(AudioEffectsManager.getLevel(this, i) - AudioEffectsManager.getMinLevel());
            seek.setContentDescription("Banda " + frequency);
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                    if (fromUser) AudioEffectsManager.setLevel(
                            EqualizerActivity.this, band,
                            AudioEffectsManager.getMinLevel() + progress);
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
            line.addView(seek, new LinearLayout.LayoutParams(0, dp(52), 1));
            bandContainer.addView(line, margins(0, 0, 0, 8));
        }
    }

    private String formatFrequency(int hz) {
        if (hz >= 1000) {
            return String.format(java.util.Locale.US, "%.1fk", hz / 1000f);
        }
        return hz + "Hz";
    }

    private android.widget.HorizontalScrollView horizontalScroll(View child) {
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(child);
        return scroll;
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
