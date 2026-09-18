package com.auren.musicplayer;

import android.content.Context;
import android.media.audiofx.Equalizer;

public final class AudioEffectsManager {
    private static final String PREFS = "auren_player";
    private static final String[] PRESETS = {
            "Flat", "Bass Boost", "Vocal", "Dance", "Acoustic", "Rock", "Pop", "Treble"
    };
    private static Equalizer equalizer;
    private static int audioSessionId = 0;

    private AudioEffectsManager() {}

    public static synchronized void attach(Context context, int sessionId) {
        if (sessionId <= 0) return;
        if (equalizer != null && audioSessionId == sessionId) {
            applySaved(context);
            return;
        }
        release();
        try {
            equalizer = new Equalizer(0, sessionId);
            equalizer.setEnabled(true);
            audioSessionId = sessionId;
            applySaved(context);
        } catch (RuntimeException ignored) {
            equalizer = null;
            audioSessionId = 0;
        }
    }

    public static synchronized boolean available() {
        return equalizer != null;
    }

    public static synchronized short getNumberOfBands() {
        return equalizer == null ? 0 : equalizer.getNumberOfBands();
    }

    public static synchronized int getMinLevel() {
        if (equalizer == null) return -1500;
        return equalizer.getBandLevelRange()[0];
    }

    public static synchronized int getMaxLevel() {
        if (equalizer == null) return 1500;
        return equalizer.getBandLevelRange()[1];
    }

    public static synchronized int getLevel(Context context, int band) {
        if (equalizer == null) return savedLevel(context, band);
        return equalizer.getBandLevel((short) band);
    }

    public static synchronized void setLevel(Context context, int band, int level) {
        saveLevel(context, band, level);
        if (equalizer == null) return;
        try {
            equalizer.setBandLevel((short) band, (short) Math.max(getMinLevel(), Math.min(getMaxLevel(), level)));
        } catch (RuntimeException ignored) {
        }
    }

    public static synchronized int getCenterFrequencyHz(int band) {
        if (equalizer == null) return 0;
        try {
            return equalizer.getCenterFreq((short) band) / 1000;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    public static String[] presetNames() {
        return PRESETS.clone();
    }

    public static synchronized void applyPreset(Context context, int preset) {
        short count = getNumberOfBands();
        if (count <= 0) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putInt("eq_preset", preset).apply();
            return;
        }
        for (int i = 0; i < count; i++) {
            float x = count == 1 ? 0.5f : (float) i / (count - 1);
            int level = presetLevel(preset, x);
            setLevel(context, i, level);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt("eq_preset", preset).apply();
    }

    public static synchronized int savedPreset(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt("eq_preset", 0);
    }

    private static int presetLevel(int preset, float x) {
        switch (preset) {
            case 1: return curve(x, 1200, -200);
            case 2: return curve(x, -300, 300);
            case 3: return wave(x, 900, 600, 0);
            case 4: return wave(x, -150, 450, -100);
            case 5: return wave(x, 550, -250, 350);
            case 6: return wave(x, 150, 450, 150);
            case 7: return curve(x, -200, 1200);
            default: return 0;
        }
    }

    private static int curve(float x, int edge, int other) {
        float center = 1f - Math.abs(x * 2f - 1f);
        return Math.round(other + (edge - other) * (1f - center));
    }

    private static int wave(float x, int low, int mid, int high) {
        if (x < 0.5f) return Math.round(low + (mid - low) * (x * 2f));
        return Math.round(mid + (high - mid) * ((x - 0.5f) * 2f));
    }

    private static int savedLevel(Context context, int band) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt("eq_band_" + band, 0);
    }

    private static void saveLevel(Context context, int band, int level) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt("eq_band_" + band, level).apply();
    }

    private static void applySaved(Context context) {
        if (equalizer == null) return;
        short count = equalizer.getNumberOfBands();
        for (int i = 0; i < count; i++) {
            int level = savedLevel(context, i);
            try {
                equalizer.setBandLevel((short) i, (short) Math.max(getMinLevel(), Math.min(getMaxLevel(), level)));
            } catch (RuntimeException ignored) {}
        }
    }

    public static synchronized void release() {
        if (equalizer != null) {
            try {
                equalizer.setEnabled(false);
                equalizer.release();
            } catch (RuntimeException ignored) {}
            equalizer = null;
            audioSessionId = 0;
        }
    }
}