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
    private static final int DEFAULT_ACCENT =
            Color.rgb(91, 82, 227);

    private ThemeManager() {}

    public static int accent(Context context) {
        return context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE)
                .getInt(
                        ACCENT,
                        DEFAULT_ACCENT);
    }

    public static void setAccent(
            Context context,
            int color) {

        context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE)
                .edit()
                .putInt(
                        ACCENT,
                        Color.rgb(
                                Color.red(color),
                                Color.green(color),
                                Color.blue(color)))
                .apply();
    }

    public static boolean isDark(
            Context context) {
        return context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE)
                .getBoolean(
                        DARK,
                        false);
    }

    public static void setDark(
            Context context,
            boolean dark) {
        context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE)
                .edit()
                .putBoolean(
                        DARK,
                        dark)
                .apply();
    }

    public static int secondary(
            Context context) {

        return shiftHue(
                accent(context),
                26f,
                0.92f,
                0.10f);
    }

    public static int surface(
            Context context) {

        int base = isDark(context)
                ? Color.rgb(16, 20, 27)
                : Color.rgb(246, 248, 252);

        return blend(
                accent(context),
                base,
                isDark(context)
                        ? 0.045f
                        : 0.035f);
    }

    public static int surfaceAlt(
            Context context) {

        int base = isDark(context)
                ? Color.rgb(25, 30, 39)
                : Color.rgb(236, 240, 247);

        return blend(
                accent(context),
                base,
                isDark(context)
                        ? 0.075f
                        : 0.065f);
    }

    public static int card(
            Context context) {

        int base = isDark(context)
                ? Color.rgb(29, 35, 45)
                : Color.WHITE;

        return blend(
                accent(context),
                base,
                isDark(context)
                        ? 0.13f
                        : 0.075f);
    }

    public static int accentSoft(
            Context context) {

        return blend(
                accent(context),
                surface(context),
                isDark(context)
                        ? 0.30f
                        : 0.16f);
    }

    public static int accentMint(
            Context context) {

        int target =
                isDark(context)
                        ? surfaceAlt(context)
                        : Color.rgb(
                                223,
                                247,
                                244);

        return blend(
                accent(context),
                target,
                isDark(context)
                        ? 0.26f
                        : 0.18f);
    }

    public static int playingBackground(
            Context context) {

        return blend(
                accent(context),
                surface(context),
                isDark(context)
                        ? 0.30f
                        : 0.18f);
    }

    public static int outline(
            Context context) {

        int base = isDark(context)
                ? Color.rgb(61, 70, 84)
                : Color.rgb(219, 224, 233);

        return blend(
                accent(context),
                base,
                0.18f);
    }

    public static int resolve(
            Context context,
            int resourceId) {

        if (resourceId == R.color.auren_primary) {
            return accent(context);
        }

        if (resourceId == R.color.auren_secondary) {
            return secondary(context);
        }

        if (resourceId == R.color.surface) {
            return surface(context);
        }

        if (resourceId == R.color.surface_alt) {
            return surfaceAlt(context);
        }

        if (resourceId == R.color.card) {
            return card(context);
        }

        if (resourceId == R.color.text_primary) {
            return isDark(context)
                    ? Color.rgb(245, 247, 250)
                    : Color.rgb(18, 24, 38);
        }

        if (resourceId == R.color.text_secondary) {
            return isDark(context)
                    ? Color.rgb(171, 181, 194)
                    : Color.rgb(97, 108, 130);
        }

        if (resourceId == R.color.line) {
            return outline(context);
        }

        if (resourceId == R.color.accent_soft) {
            return accentSoft(context);
        }

        if (resourceId == R.color.accent_mint) {
            return accentMint(context);
        }

        if (resourceId == R.color.playing_background) {
            return playingBackground(context);
        }

        if (resourceId == R.color.playing_text) {
            return accent(context);
        }

        return context.getColor(resourceId);
    }

    public static int textOnAccent(
            Context context) {

        return readableOn(accent(context));
    }

    public static int textOnColor(int color) {
        return readableOn(color);
    }

    public static int artworkTint(
            Context context,
            int artworkColor) {

        return blend(
                artworkColor,
                accent(context),
                0.35f);
    }

    public static int atmosphere(
            Context context,
            int artworkColor) {

        return blend(
                artworkColor,
                surface(context),
                isDark(context)
                        ? 0.28f
                        : 0.16f);
    }

    public static boolean useDarkSystemBars(
            Context context) {
        return isDark(context);
    }

    public static void applyWindow(
            Activity activity) {

        Window window =
                activity.getWindow();

        int surface =
                surface(activity);

        window.setStatusBarColor(
                surface);

        window.setNavigationBarColor(
                surface);

        if (Build.VERSION.SDK_INT >= 23) {
            int flags =
                    window.getDecorView()
                            .getSystemUiVisibility();

            if (!isDark(activity)) {
                flags |=
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;

                if (Build.VERSION.SDK_INT >= 26) {
                    flags |=
                            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            } else {
                flags &=
                        ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;

                if (Build.VERSION.SDK_INT >= 26) {
                    flags &=
                            ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }

            window.getDecorView()
                    .setSystemUiVisibility(
                            flags);
        }
    }

    public static int blend(
            int foreground,
            int background,
            float amount) {

        float t = Math.max(
                0f,
                Math.min(1f, amount));

        int r = Math.round(
                Color.red(background) * (1f - t)
                        + Color.red(foreground) * t);

        int g = Math.round(
                Color.green(background) * (1f - t)
                        + Color.green(foreground) * t);

        int b = Math.round(
                Color.blue(background) * (1f - t)
                        + Color.blue(foreground) * t);

        return Color.rgb(r, g, b);
    }

    public static String hex(int color) {
        return String.format(
                "#%02X%02X%02X",
                Color.red(color),
                Color.green(color),
                Color.blue(color));
    }

    private static int readableOn(int bg) {
        double y =
                0.299 * Color.red(bg)
                        + 0.587 * Color.green(bg)
                        + 0.114 * Color.blue(bg);

        return y < 158
                ? Color.WHITE
                : Color.rgb(
                        18,
                        24,
                        38);
    }

    private static int shiftHue(
            int color,
            float degrees,
            float saturationScale,
            float valueScale) {

        float[] hsv = new float[3];
        Color.colorToHSV(
                color,
                hsv);

        hsv[0] =
                (hsv[0] + degrees) % 360f;

        hsv[1] =
                Math.max(
                        0f,
                        Math.min(
                                1f,
                                hsv[1] * saturationScale));

        hsv[2] =
                Math.max(
                        0f,
                        Math.min(
                                1f,
                                hsv[2] * valueScale
                                        + (1f - valueScale) * 0.84f));

        return Color.HSVToColor(hsv);
    }
}
