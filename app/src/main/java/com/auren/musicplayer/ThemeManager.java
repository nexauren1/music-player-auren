package com.auren.musicplayer;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;

public final class ThemeManager {
    private static final String PREFS = "auren_player";
    private static final String ACCENT = "theme_accent";
    private static final String DARK = "theme_dark";
    private static final int DEFAULT_ACCENT = Color.rgb(91, 82, 227);

    private ThemeManager() {}

    public static int accent(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(ACCENT, DEFAULT_ACCENT);
    }

    public static void setAccent(Context context, int color) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(ACCENT, color).apply();
    }

    public static boolean isDark(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(DARK, false);
    }

    public static void setDark(Context context, boolean dark) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(DARK, dark).apply();
    }

    public static int resolve(Context context, int resourceId) {
        boolean dark = isDark(context);
        if (resourceId == R.color.auren_primary) return accent(context);
        if (resourceId == R.color.auren_secondary) return blend(accent(context), Color.WHITE, 0.35f);
        if (resourceId == R.color.surface) return dark
                ? blend(accent(context), Color.rgb(15, 18, 23), 0.04f)
                : blend(accent(context), Color.rgb(246, 248, 252), 0.02f);
        if (resourceId == R.color.surface_alt) return dark
                ? blend(accent(context), Color.rgb(25, 30, 38), 0.07f)
                : blend(accent(context), Color.rgb(238, 241, 247), 0.035f);
        if (resourceId == R.color.card) return dark
                ? blend(accent(context), Color.rgb(27, 32, 40), 0.14f)
                : blend(accent(context), Color.WHITE, 0.045f);
        if (resourceId == R.color.text_primary) return dark ? Color.rgb(244, 246, 249) : Color.rgb(17, 24, 39);
        if (resourceId == R.color.text_secondary) return dark ? Color.rgb(169, 179, 191) : Color.rgb(102, 112, 133);
        if (resourceId == R.color.line) return dark ? Color.rgb(48, 56, 68) : Color.rgb(229, 231, 235);
        if (resourceId == R.color.accent_soft) return blend(accent(context), surface(context), dark ? 0.24f : 0.16f);
        if (resourceId == R.color.accent_mint) return dark
                ? blend(accent(context), surface(context), 0.22f)
                : blend(accent(context), Color.rgb(220, 248, 241), 0.16f);
        if (resourceId == R.color.playing_background) return blend(accent(context), surface(context), dark ? 0.26f : 0.14f);
        if (resourceId == R.color.playing_text) return accent(context);
        return context.getColor(resourceId);
    }

    public static int surface(Context context) {
        return isDark(context) ? Color.rgb(15, 18, 23) : Color.rgb(246, 248, 252);
    }

    public static int card(Context context) {
        return isDark(context)
                ? blend(accent(context), Color.rgb(27, 32, 40), 0.14f)
                : blend(accent(context), Color.WHITE, 0.045f);
    }

    public static int textOnAccent(Context context) {
        int c = accent(context);
        double y = 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c);
        return y < 165 ? Color.WHITE : Color.rgb(17, 24, 39);
    }

    public static int artworkTint(Context context, int artworkColor) {
        return blend(artworkColor, accent(context), 0.35f);
    }

    public static boolean useDarkSystemBars(Context context) {
        return isDark(context);
    }

    public static void applyWindow(Activity activity) {
        Window window = activity.getWindow();
        int surface = surface(activity);
        window.setStatusBarColor(surface);
        window.setNavigationBarColor(surface);
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = window.getDecorView().getSystemUiVisibility();
            if (!isDark(activity)) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= 26) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    public static int blend(int foreground, int background, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int r = Math.round(Color.red(background) * (1f - t) + Color.red(foreground) * t);
        int g = Math.round(Color.green(background) * (1f - t) + Color.green(foreground) * t);
        int b = Math.round(Color.blue(background) * (1f - t) + Color.blue(foreground) * t);
        return Color.rgb(r, g, b);
    }

    public static String hex(int color) {
        return String.format("#%02X%02X%02X", Color.red(color), Color.green(color), Color.blue(color));
    }
}
