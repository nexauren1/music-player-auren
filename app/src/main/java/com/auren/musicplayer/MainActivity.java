package com.auren.musicplayer;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class MainActivity extends ComponentActivity {
    private String appliedThemeKey;
    private static final int MUSIC_PERMISSION = 41;
    private static final int NOTIFICATION_PERMISSION = 42;
    private final List<Track> tracks = new ArrayList<>();
    private final List<Track> recentTracks = new ArrayList<>();
    private final Map<Long, Integer> playCounts = new HashMap<>();
    private final Handler handler = new Handler();

    private Player player;
    private MediaController mediaController;
    private ListenableFuture<MediaController> controllerFuture;
    private boolean notificationPermissionRequested;
    private boolean playbackSessionRestored;

    private final Player.Listener playbackListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_ENDED && player != null
                    && player.getRepeatMode() == Player.REPEAT_MODE_OFF) {
                runOnUiThread(() -> nextTrackInPlayer());
            } else {
                runOnUiThread(() -> {
                    updateMiniPlayer();
                    highlightPlayingTrack();
                });
            }
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            runOnUiThread(() -> {
                updateMiniPlayer();
            });
        }
    };
    private Track currentTrack;
    private ImageButton miniPlay;
    private ImageButton miniFavorite;
    private SeekBar miniProgress;
    private SeekBar nowPlayingSeek;
    private TextView nowPlayingPosition;
    private TextView nowPlayingDuration;
    private ImageView miniArt;
    private TextView miniTitle;
    private TextView miniArtist;
    private LinearLayout pageContainer;
    private LinearLayout miniContainer;
    private TextView homeTab;
    private TextView libraryTab;
    private TextView playlistTab;
    private Dialog nowPlayingDialog;
    private boolean userDragging;
    private float playbackSpeed = 1.0f;
    private float playbackPitch = 1.0f;
    private final List<Track> playQueue = new ArrayList<>();
    private long sleepTimerEndMs = 0L;
    private boolean sleepAtTrackEnd;
    private long abStartMs = -1L;
    private long abEndMs = -1L;
    private boolean abRepeatEnabled;
    private String librarySortMode = "title";

    private final Runnable progressUpdater = new Runnable() {
        @Override public void run() {
            if (nowPlayingDialog != null && nowPlayingDialog.isShowing()) updateNowPlayingProgress();
            checkAdvancedFeatures();
            handler.postDelayed(this, 500);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.applyWindow(this);
        appliedThemeKey = themeKey();
        buildShell();
        connectPlaybackController();
        requestMusicPermission();
        handler.post(progressUpdater);
    }

    private void connectPlaybackController() {
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                MediaController controller = controllerFuture.get();
                runOnUiThread(() -> {
                    mediaController = controller;
                    player = controller;
                    player.addListener(playbackListener);
                    applyPlaybackEffects();
                    syncCurrentTrackWithController();
                    updateMiniPlayer();
                    restorePlaybackSession();
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Não foi possível iniciar o mecanismo de áudio.", Toast.LENGTH_LONG).show());
            }
        }, Runnable::run);
    }

    @Override protected void onResume() {
        super.onResume();
        ThemeManager.applyWindow(this);
        String key = themeKey();
        if (appliedThemeKey != null && !appliedThemeKey.equals(key) && !isFinishing()) {
            appliedThemeKey = key;
            recreate();
        } else if (appliedThemeKey == null) {
            appliedThemeKey = key;
        }
    }

    @Override protected void onStop() {
        persistResumePosition();
        super.onStop();
    }

    private void persistResumePosition() {
        if (player == null || currentTrack == null) return;

        SharedPreferences prefs = getSharedPreferences("auren_player", MODE_PRIVATE);
        if (player.getPlaybackState() == Player.STATE_ENDED) {
            prefs.edit().remove("resume_id").remove("resume_position").remove("resume_playing")
                    .remove("track_position_" + currentTrack.id).apply();
            return;
        }

        long position = Math.max(0L, player.getCurrentPosition());
        prefs.edit()
                .putLong("resume_id", currentTrack.id)
                .putLong("resume_position", position)
                .putLong("track_position_" + currentTrack.id, position)
                .putBoolean("resume_playing", player.isPlaying())
                .commit();
    }

    private String themeKey() {
        return ThemeManager.hex(ThemeManager.accent(this)) + ":" + ThemeManager.isDark(this);
    }

    private void buildShell() {
        LinearLayout root = column();
        root.setBackgroundColor(ThemeManager.resolve(this, R.color.surface));

        root.addView(buildTopBar());

        pageContainer = column();
        root.addView(pageContainer, new LinearLayout.LayoutParams(-1, 0, 1));

        miniContainer = buildMiniPlayer();
        LinearLayout.LayoutParams miniLp = new LinearLayout.LayoutParams(-1, dp(78));
        miniLp.setMargins(dp(8), dp(4), dp(8), dp(4));
        root.addView(miniContainer, miniLp);
        root.addView(buildBottomNavigation());
        setContentView(root);
        showHome();
    }
    private View buildTopBar() {
        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(8), dp(12), dp(8));
        bar.setBackgroundColor(ThemeManager.card(this));
        bar.setElevation(dp(2));

        ImageButton menu = iconButton(android.R.drawable.ic_menu_sort_by_size, "Abrir menu");
        DrawableCompat.setTint(menu.getDrawable(), ThemeManager.resolve(this, R.color.auren_primary));
        menu.setOnClickListener(v -> showAppMenu(menu));
        bar.addView(menu, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = text("Nexauren", 20, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));

        ImageButton search = iconButton(android.R.drawable.ic_menu_search, "Pesquisar música");
        DrawableCompat.setTint(search.getDrawable(), ThemeManager.resolve(this, R.color.text_primary));
        search.setOnClickListener(v -> showSearchDialog());
        bar.addView(search, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return bar;
    }


    private View buildHomeHero() {
        LinearLayout card = rounded(ThemeManager.resolve(this, R.color.auren_primary), 26);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setElevation(dp(4));

        LinearLayout media = row();
        media.setGravity(Gravity.CENTER_VERTICAL);

        ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (miniArt != null && miniArt.getDrawable() != null) {
            art.setImageDrawable(miniArt.getDrawable());
        } else {
            art.setImageResource(android.R.drawable.ic_media_play);
            DrawableCompat.setTint(art.getDrawable(), Color.WHITE);
        }
        media.addView(art, new LinearLayout.LayoutParams(dp(88), dp(88)));

        LinearLayout info = column();
        info.setPadding(dp(14), 0, 0, 0);
        TextView eyebrow = text("AGORA NO NEXAUREN", 10, android.R.color.white);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        info.addView(eyebrow);

        String heroTitle = miniTitle == null ? "A música move você" : miniTitle.getText().toString();
        if (heroTitle.trim().isEmpty()) heroTitle = "A música move você";
        TextView title = text(heroTitle, 20, android.R.color.white);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        info.addView(title, margins(0, 4, 0, 2));

        String heroArtist = miniArtist == null ? "Descubra, ouça e aproveite" : miniArtist.getText().toString();
        if (heroArtist.trim().isEmpty()) heroArtist = "Descubra, ouça e aproveite";
        info.addView(text(heroArtist, 12, android.R.color.white));

        TextView action = text("Abrir reprodução  ›", 12, android.R.color.white);
        action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        info.addView(action, margins(0, 12, 0, 0));
        media.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

        card.addView(media);
        card.setOnClickListener(v -> {
            if (currentTrack != null) openNowPlaying();
        });
        return card;
    }

    private void showHome() {
        setActiveTab(homeTab);
        pageContainer.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout content = column();
        content.setPadding(dp(20), dp(16), dp(20), dp(18));

        LinearLayout header = row();
        LinearLayout brandBox = column();
        TextView small = text("NEXAUREN", 11, R.color.auren_primary);
        small.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView title = text("Boa música,\nsempre.", 30, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brandBox.addView(small);
        brandBox.addView(title, margins(0, 4, 0, 0));
        header.addView(brandBox, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(header);

        content.addView(buildHomeHero(), margins(0, 8, 0, 0));

        LinearLayout quick = row();
        quick.setGravity(Gravity.CENTER_VERTICAL);
        quick.addView(actionCard("▶", "Aleatório", v -> shufflePlay()), new LinearLayout.LayoutParams(0, dp(82), 1));
        quick.addView(actionCard("♥", "Favoritos", v -> showLibrary(true)), margins(10, 0, 0, 0));
        content.addView(quick, margins(0, 18, 0, 0));

        addSectionHeader(content, "Reproduzidas recentemente", "Ver tudo", v -> showLibrary());
        if (recentTracks.isEmpty()) {
            content.addView(emptyCard("As músicas reproduzidas recentemente aparecerão aqui."));
        } else {
            LinearLayout recentRow = row();
            HorizontalScrollView horizontal = new HorizontalScrollView(this);
            horizontal.setHorizontalScrollBarEnabled(false);
            for (int i = 0; i < Math.min(8, recentTracks.size()); i++) {
                Track t = recentTracks.get(i);
                recentRow.addView(horizontalTrackCard(t), margins(0, 0, 12, 0));
            }
            horizontal.addView(recentRow);
            content.addView(horizontal);
        }

        addSectionHeader(content, "Mais tocadas", "Seus favoritos", v -> showMostPlayed());
        List<Track> mostPlayed = sortedByPlayCount();
        if (mostPlayed.isEmpty() || playCounts.getOrDefault(mostPlayed.get(0).id, 0) == 0) {
            content.addView(emptyCard("Reproduza algumas músicas e sua lista de mais tocadas aparecerá aqui."));
        } else {
            for (int i = 0; i < Math.min(4, mostPlayed.size()); i++) {
                content.addView(trackRow(mostPlayed.get(i), i + 1));
            }
        }

        AchievementManager.Stats homeStats = AchievementManager.stats(this);
        addSectionHeader(content, "Conquistas & recordes",
                homeStats.unlocked + "/" + homeStats.totalAchievements + " desbloqueadas",
                v -> showAchievements());
        content.addView(homeAchievementCard(), margins(0, 0, 0, 2));
        content.addView(intelligenceHomeCard(), margins(0, 0, 0, 12));

        // AUREN_ALL_SONGS_HOME_START
        addSectionHeader(content, "Todas as músicas", tracks.size() + " músicas", v -> showLibrary());
        if (tracks.isEmpty()) {
            content.addView(emptyCard("Nenhuma música encontrada no dispositivo."));
        } else {
            int visible = Math.min(12, tracks.size());
            for (int i = 0; i < visible; i++) {
                content.addView(trackRow(tracks.get(i), 0));
            }
            if (tracks.size() > visible) {
                TextView more = text("Ver todas as " + tracks.size() + " músicas", 13, R.color.auren_primary);
                more.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                more.setGravity(Gravity.CENTER);
                more.setPadding(dp(8), dp(14), dp(8), dp(14));
                more.setOnClickListener(v -> showLibrary());
                content.addView(more);
            }
        }
        // AUREN_ALL_SONGS_HOME_END

        addSectionHeader(content, "Sugestões para você", "Atualizar", v -> showHome());
        List<Track> suggestions = suggestionTracks();
        if (suggestions.isEmpty()) {
            content.addView(emptyCard("As sugestões aparecerão quando sua biblioteca for carregada."));
        } else {
            for (int i = 0; i < Math.min(5, suggestions.size()); i++) {
                content.addView(trackRow(suggestions.get(i), 0));
            }
        }

        scroll.addView(content);
        pageContainer.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    private void showLibrary() {
        setActiveTab(libraryTab);
        pageContainer.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout content = column();
        content.setPadding(dp(20), dp(16), dp(20), dp(22));

        TextView eyebrow = text("SUA BIBLIOTECA", 11, R.color.auren_primary);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView title = text("Biblioteca", 30, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(eyebrow);
        content.addView(title, margins(0, 3, 0, 16));

        content.addView(librarySectionCard("Mais tocadas", "As músicas que você mais ouve", "♫", v -> showMostPlayed()));
        content.addView(librarySectionCard("Recentes", "O que você ouviu recentemente", "◷", v -> showRecent()), margins(0, 10, 0, 0));
        content.addView(librarySectionCard("Playlists", "Suas coleções de músicas", "▤", v -> showPlaylists()), margins(0, 10, 0, 0));
        content.addView(librarySectionCard("Sugestões", "Músicas escolhidas da sua biblioteca", "✦", v -> showSuggestions()), margins(0, 10, 0, 0));
        content.addView(librarySectionCard("Favoritos", "Músicas que você marcou com ♥", "♥", v -> showLibrary(true)), margins(0, 10, 0, 0));

        scroll.addView(content);
        pageContainer.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    private void showLibrary(boolean favoritesOnly) {
        setActiveTab(libraryTab);
        pageContainer.removeAllViews();

        LinearLayout content = column();
        content.setPadding(dp(20), dp(16), dp(20), dp(18));
        LinearLayout header = row();
        LinearLayout titles = column();
        TextView eyebrow = text("SEUS FAVORITOS", 11, R.color.auren_primary);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView title = text("Favoritos", 30, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titles.addView(eyebrow);
        titles.addView(title, margins(0, 3, 0, 0));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton search = iconButton(android.R.drawable.ic_menu_search, "Pesquisar música");
        search.setOnClickListener(v -> showSearchDialog());
        header.addView(search, new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = column();
        List<Track> source = new ArrayList<>();
        for (Track t : tracks) if (isFavorite(t)) source.add(t);
        if (source.isEmpty()) {
            list.addView(emptyCard("Ainda não há favoritos. Toque no coração durante a reprodução."));
        } else {
            for (Track t : source) list.addView(trackRow(t, 0));
        }
        scroll.addView(list);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        pageContainer.addView(content);
    }

    private View librarySectionCard(String title, String subtitle, String icon, View.OnClickListener listener) {
        LinearLayout card = rounded(ThemeManager.card(this), 20);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(12), dp(12));
        card.setElevation(dp(2));

        TextView iconView = text(icon, 25, R.color.auren_primary);
        iconView.setGravity(Gravity.CENTER);
        iconView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(iconView, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout info = column();
        TextView name = text(title, 16, R.color.text_primary);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        info.addView(name);
        info.addView(text(subtitle, 12, R.color.text_secondary), margins(0, 3, 0, 0));
        card.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

        TextView arrow = text("›", 28, R.color.text_secondary);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(48)));
        card.setOnClickListener(listener);
        return card;
    }


    private void showJourney() {
        showAchievements();
    }

    private void showAchievementToast() {
        Toast toast = Toast.makeText(this, "✨ Conquista desbloqueada!", Toast.LENGTH_SHORT);
        toast.show();
    }

    private View intelligenceHomeCard() {
        AurenAnalytics.Summary s = AurenAnalytics.summary(this);
        LinearLayout card = rounded(ThemeManager.resolve(this, R.color.accent_soft), 20);
        card.setPadding(dp(15), dp(13), dp(15), dp(13));
        TextView title = text("NEXAUREN INTELLIGENCE", 10, R.color.auren_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(title);
        TextView main = text(
                s.streakCurrent > 0 ? "Sequência de " + s.streakCurrent + " dia(s)" : "Comece a criar o seu histórico",
                16, R.color.text_primary);
        main.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(main, margins(0, 4, 0, 2));
        card.addView(text(
                AurenAnalytics.formatDuration(s.todayMs) + " hoje • " + AurenAnalytics.formatDuration(s.monthMs) + " este mês",
                11, R.color.text_secondary));
        TextView action = text("Abrir estatísticas e Journey  ›", 12, R.color.auren_primary);
        action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(action, margins(0, 8, 0, 0));
        card.setOnClickListener(v -> showAnalyticsDashboard());
        return card;
    }

    private void showAnalyticsDashboard() {
        showAnalyticsDashboard("month");
    }

    private void showAnalyticsDashboard(String range) {
        pageContainer.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column();
        content.setPadding(dp(20), dp(18), dp(20), dp(26));

        TextView eyebrow = text("NEXAUREN INTELLIGENCE", 11, R.color.auren_primary);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(eyebrow);
        TextView title = text("Estatísticas", 30, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title, margins(0, 3, 0, 6));
        content.addView(text("Hoje → Semana → Mês → Sempre", 13, R.color.text_secondary), margins(0, 0, 0, 14));

        LinearLayout tabs = row();
        String[] labels = {"Hoje", "Semana", "Mês", "Sempre"};
        String[] ids = {"today", "week", "month", "all"};
        for (int i = 0; i < labels.length; i++) {
            final String target = ids[i];
            View tab = chip(labels[i], target.equals(range), v -> showAnalyticsDashboard(target));
            tabs.addView(tab, new LinearLayout.LayoutParams(0, dp(42), 1));
        }
        content.addView(tabs, margins(0, 0, 0, 14));

        AurenAnalytics.Summary s = AurenAnalytics.summary(this);
        long time = "today".equals(range) ? s.todayMs : "week".equals(range) ? s.weekMs
                : "all".equals(range) ? s.totalMs : s.monthMs;
        int plays = "today".equals(range) ? s.todayPlays : "week".equals(range) ? s.weekPlays
                : "all".equals(range) ? s.totalPlays : s.monthPlays;

        LinearLayout stats = row();
        stats.addView(statCard("TEMPO", AurenAnalytics.formatDuration(time), "ouvido"), new LinearLayout.LayoutParams(0, dp(100), 1));
        stats.addView(statCard("REPRODUÇÕES", String.valueOf(plays), "neste período"), margins(8, 0, 0, 0));
        content.addView(stats);

        LinearLayout stats2 = row();
        AchievementManager.Stats a = AchievementManager.stats(this);
        stats2.addView(statCard("SEQUÊNCIA", AurenAnalytics.summary(this).streakCurrent + " dia(s)", "Auren Journey"), new LinearLayout.LayoutParams(0, dp(92), 1));
        stats2.addView(statCard("MÚSICAS", String.valueOf(a.uniqueTracks), "diferentes"), margins(8, 0, 0, 0));
        content.addView(stats2, margins(0, 8, 0, 14));

        content.addView(featureSectionTitle("Top 5 músicas por tempo ouvido"));
        List<AurenAnalytics.TrackScore> topTracks = AurenAnalytics.topTracks(this, analyticsTracks(), range, 5);
        if (topTracks.isEmpty()) content.addView(emptyCard("Ainda não há tempo suficiente para um ranking."));
        for (AurenAnalytics.TrackScore t : topTracks) content.addView(trackAnalyticsRow(t));

        content.addView(featureSectionTitle("Top artistas"));
        for (AurenAnalytics.AggregateScore a1 : AurenAnalytics.aggregateByArtist(this, analyticsTracks(), 5))
            content.addView(simpleAnalyticsRow(a1.name, AurenAnalytics.formatDuration(a1.timeMs)));

        content.addView(featureSectionTitle("Top álbuns"));
        for (AurenAnalytics.AggregateScore a2 : AurenAnalytics.aggregateByAlbum(this, analyticsTracks(), 5))
            content.addView(simpleAnalyticsRow(a2.name, AurenAnalytics.formatDuration(a2.timeMs)));

        content.addView(featureSectionTitle("Top géneros"));
        for (AurenAnalytics.AggregateScore a3 : AurenAnalytics.aggregateByGenre(this, analyticsTracks(), 5))
            content.addView(simpleAnalyticsRow(a3.name, AurenAnalytics.formatDuration(a3.timeMs)));

        content.addView(featureSectionTitle("O que ouvi ontem à noite"));
        boolean foundNight = false;
        long nowMs = System.currentTimeMillis();
        for (AurenAnalytics.Session session : AurenAnalytics.sessions(this)) {
            long age = nowMs - session.startMs;
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(session.startMs);
            int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
            if (age >= 0 && age <= 7L * 86400000L && (hour >= 20 || hour < 5)) {
                foundNight = true;
                content.addView(sessionRow(session));
            }
            if (foundNight && content.getChildCount() > 45) break;
        }
        if (!foundNight) content.addView(emptyCard("Quando houver uma sessão noturna, ela aparecerá aqui."));

        content.addView(featureSectionTitle("Favoritos automáticos"));
        for (Track t : automaticFavoriteTracks())
            content.addView(trackInsightRow(t, "Tendência automática"));
        content.addView(featureSectionTitle("Repartição visual"));
        content.addView(analyticsBars(s.todayMs, s.weekMs, s.monthMs));

        scroll.addView(content);
        pageContainer.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    private View trackAnalyticsRow(AurenAnalytics.TrackScore track) {
        LinearLayout row = rounded(ThemeManager.card(this), 16);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        TextView name = text(track.title, 14, R.color.text_primary);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView artist = text(track.artist, 11, R.color.text_secondary);
        LinearLayout info = column();
        info.addView(name);
        info.addView(artist, margins(0, 2, 0, 0));
        row.addView(info, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView time = text(AurenAnalytics.formatDuration(track.timeMs), 11, R.color.auren_primary);
        time.setGravity(Gravity.CENTER);
        row.addView(time, new LinearLayout.LayoutParams(dp(78), dp(48)));
        row.setOnClickListener(v -> {
            Track t = findTrack(track.id);
            if (t != null) play(t);
        });
        return row;
    }

    private View simpleAnalyticsRow(String name, String value) {
        LinearLayout row = rounded(ThemeManager.card(this), 16);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        TextView n = text(name.isEmpty() ? "Desconhecido" : name, 14, R.color.text_primary);
        n.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(n, new LinearLayout.LayoutParams(0, dp(44), 1));
        row.addView(text(value, 11, R.color.auren_primary), new LinearLayout.LayoutParams(dp(78), dp(44)));
        return row;
    }

    private View trackInsightRow(Track track, String label) {
        LinearLayout row = rounded(ThemeManager.resolve(this, R.color.accent_soft), 16);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        TextView n = text(safeTitle(track), 14, R.color.text_primary);
        n.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(n, new LinearLayout.LayoutParams(0, dp(46), 1));
        row.addView(text(label, 11, R.color.auren_primary), new LinearLayout.LayoutParams(dp(125), dp(46)));
        row.setOnClickListener(v -> play(track));
        return row;
    }

    private View sessionRow(AurenAnalytics.Session session) {
        java.text.DateFormat df = new java.text.SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
        LinearLayout row = rounded(ThemeManager.card(this), 16);
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        int count = session.trackIds.isEmpty() ? 0 : session.trackIds.split(",").length;
        row.addView(text(df.format(new java.util.Date(session.startMs)) + " • " + count + " músicas",
                13, R.color.text_primary));
        row.addView(text(AurenAnalytics.formatDuration(session.durationMs), 11, R.color.auren_primary),
                margins(0, 3, 0, 0));
        return row;
    }

    private View analyticsBars(long today, long week, long month) {
        LinearLayout box = rounded(ThemeManager.card(this), 18);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        long[] values = {today, week / 7L, month / 30L};
        String[] labels = {"Hoje", "Média semanal", "Média mensal"};
        long max = Math.max(1L, Math.max(values[0], Math.max(values[1], values[2])));
        for (int i = 0; i < values.length; i++) {
            LinearLayout line = row();
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.addView(text(labels[i], 12, R.color.text_secondary), new LinearLayout.LayoutParams(dp(96), dp(34)));
            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgress((int)Math.max(3L, Math.min(100L, values[i] * 100L / max)));
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(ThemeManager.accent(this)));
            line.addView(bar, new LinearLayout.LayoutParams(0, dp(28), 1));
            box.addView(line);
        }
        return box;
    }

    private List<TrackInfo> analyticsTracks() {
        List<TrackInfo> result = new ArrayList<>();
        for (Track t : tracks) result.add(new TrackInfo(t.id, safeTitle(t), safeArtist(t), safeAlbum(t), safeGenre(t)));
        return result;
    }

    private List<Track> automaticFavoriteTracks() {
        List<Track> result = new ArrayList<>();
        for (Track t : tracks) {
            int plays = playCounts.getOrDefault(t.id, 0);
            long minutes = AurenAnalytics.trackTime(this, t.id) / 60000L;
            if (plays >= 3 || minutes >= 15L) result.add(t);
        }
        Collections.sort(result, (x, y) -> {
            long sx = AurenAnalytics.trackTime(this, x.id) + playCounts.getOrDefault(x.id, 0) * 60000L;
            long sy = AurenAnalytics.trackTime(this, y.id) + playCounts.getOrDefault(y.id, 0) * 60000L;
            return Long.compare(sy, sx);
        });
        return result.subList(0, Math.min(8, result.size()));
    }

    private LinearLayout featureScreen(String title, String subtitle) {
        LinearLayout content = column();
        content.setPadding(dp(20), dp(18), dp(20), dp(26));
        TextView e = text("NEXAUREN", 11, R.color.auren_primary);
        e.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(e);
        TextView t = text(title, 30, R.color.text_primary);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(t, margins(0, 3, 0, 3));
        content.addView(text(subtitle, 13, R.color.text_secondary), margins(0, 0, 0, 16));
        return content;
    }

    private void startQueue(List<Track> source) {
        playQueue.clear();
        if (source == null || source.isEmpty()) return;
        List<Track> selected = new ArrayList<>(source);
        Track first = selected.remove(0);
        int max = Math.min(12, selected.size());
        for (int i = 0; i < max; i++) playQueue.add(selected.get(i));
        play(first);
    }

    private void showAurenMix() {
        pageContainer.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = featureScreen("Auren Mix", "Uma sequência inteligente, baseada no seu comportamento e no horário.");
        List<Track> queue = buildSmartQueue();
        content.addView(featureAction("▶", "Iniciar Mix", "Reorganizar as próximas músicas agora", v -> {
            List<Track> fresh = buildSmartQueue();
            startQueue(fresh);
        }), margins(0, 0, 0, 14));
        content.addView(featureSectionTitle("Próximas músicas"));
        if (queue.isEmpty()) content.addView(emptyCard("Adicione músicas ao dispositivo para criar o Mix."));
        for (Track t : queue) {
            content.addView(trackInsightRow(t,
                    "hora " + AurenAnalytics.hourScore(this, t.id,
                            java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
                            + " • " + playCounts.getOrDefault(t.id, 0) + " plays"));
        }
        scroll.addView(content);
        pageContainer.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    private List<Track> buildSmartQueue() {
        playQueue.clear();
        List<Track> pool = new ArrayList<>(tracks);
        final int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        Collections.sort(pool, (a, b) -> {
            long as = AurenAnalytics.hourScore(this, a.id, hour) * 100000L
                    + playCounts.getOrDefault(a.id, 0) * 5000L
                    + AurenAnalytics.trackTime(this, a.id) / 1000L;
            long bs = AurenAnalytics.hourScore(this, b.id, hour) * 100000L
                    + playCounts.getOrDefault(b.id, 0) * 5000L
                    + AurenAnalytics.trackTime(this, b.id) / 1000L;
            return Long.compare(bs, as);
        });
        if (currentTrack != null) pool.remove(currentTrack);
        int count = Math.min(12, pool.size());
        List<Track> selected = new ArrayList<>(pool.subList(0, count));
        if (selected.size() > 3) Collections.shuffle(selected);
        playQueue.addAll(selected);
        return selected;
    }

    private void showAurenFocus() {
        String[] options = {"Sem temporizador", "15 minutos", "30 minutos", "45 minutos", "60 minutos"};
        new AlertDialog.Builder(this)
                .setTitle("Auren Focus")
                .setMessage("Uma sessão limpa, com música local. O temporizador é opcional.")
                .setItems(options, (dialog, which) -> {
                    int[] mins = {0, 15, 30, 45, 60};
                    sleepAtTrackEnd = false;
                    sleepTimerEndMs = mins[which] == 0 ? 0L
                            : System.currentTimeMillis() + mins[which] * 60_000L;
                    List<Track> queue = buildSmartQueue();
                    if (player == null) return;
                    if (player.isPlaying()) {
                        Toast.makeText(this, mins[which] == 0 ? "Focus iniciado sem temporizador." : "Focus iniciado por " + mins[which] + " minutos.", Toast.LENGTH_SHORT).show();
                    } else if (currentTrack != null) {
                        player.play();
                    } else if (!queue.isEmpty()) {
                        startQueue(queue);
                    }
                })
                .show();
    }

    private void showAurenMemories() {
        pageContainer.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = featureScreen("Auren Memories", "Pequenas memórias da sua própria biblioteca.");
        AurenAnalytics.Summary s = AurenAnalytics.summary(this);
        content.addView(featureMetricCard("Nos últimos 7 dias", AurenAnalytics.formatDuration(s.weekMs), s.weekPlays + " reproduções"));
        content.addView(featureSectionTitle("O que ouvi ontem à noite"));
        long now = System.currentTimeMillis();
        int shown = 0;
        for (AurenAnalytics.Session session : AurenAnalytics.sessions(this)) {
            long age = now - session.startMs;
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(session.startMs);
            int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
            if (age >= 0 && age <= 8L * 86400000L && (hour >= 20 || hour < 5)) {
                content.addView(sessionRow(session));
                if (++shown >= 8) break;
            }
        }
        if (shown == 0) content.addView(emptyCard("Ainda não há uma sessão noturna guardada."));
        scroll.addView(content);
        pageContainer.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    private void showAurenReplay() {
        pageContainer.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = featureScreen("Auren Replay", "O resumo musical do seu mês.");
        AurenAnalytics.Summary s = AurenAnalytics.summary(this);
        content.addView(featureMetricCard("Este mês", AurenAnalytics.formatDuration(s.monthMs), s.monthPlays + " reproduções"));
        content.addView(featureMetricCard("Sempre", AurenAnalytics.formatDuration(s.totalMs), s.totalPlays + " reproduções"),
                margins(0, 8, 0, 8));
        content.addView(featureSectionTitle("Top 5"));
        for (AurenAnalytics.TrackScore t : AurenAnalytics.topTracks(this, analyticsTracks(), 5))
            content.addView(trackAnalyticsRow(t));
        content.addView(featureSectionTitle("Journey"));
        content.addView(simpleAnalyticsRow("Sequência atual", s.streakCurrent + " dia(s)"));
        content.addView(simpleAnalyticsRow("Melhor sequência", s.streakBest + " dia(s)"));
        scroll.addView(content);
        pageContainer.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    private void showAurenDiscovery() {
        pageContainer.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = featureScreen("Auren Discovery", "Descubra o que a sua biblioteca ainda esconde.");
        List<Track> list = new ArrayList<>(tracks);
        Collections.sort(list, (a, b) -> {
            long at = AurenAnalytics.trackTime(this, a.id);
            long bt = AurenAnalytics.trackTime(this, b.id);
            int ap = playCounts.getOrDefault(a.id, 0);
            int bp = playCounts.getOrDefault(b.id, 0);
            int cmp = Long.compare(at, bt);
            return cmp != 0 ? cmp : Integer.compare(ap, bp);
        });
        content.addView(featureAction("⌕", "Descobrir agora", "Colocar músicas pouco ouvidas na fila", v -> startQueue(list)),
                margins(0, 0, 0, 14));
        for (int i = 0; i < Math.min(15, list.size()); i++) {
            Track t = list.get(i);
            String label = playCounts.getOrDefault(t.id, 0) == 0 ? "Nunca tocada" :
                    playCounts.get(t.id) + " reproduções";
            content.addView(trackInsightRow(t, label));
        }
        scroll.addView(content);
        pageContainer.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    private void showAurenMood() {
        String[] moods = {"Energético", "Calmo", "Noite", "Viagem", "Foco", "Aleatório"};
        new AlertDialog.Builder(this)
                .setTitle("Auren Mood")
                .setMessage("Escolha um ambiente. A fila usa apenas a música que já existe no aparelho.")
                .setItems(moods, (dialog, which) -> {
                    List<Track> queue = buildMoodQueue(moods[which]);
                    if (queue.isEmpty()) {
                        Toast.makeText(this, "Não encontrei músicas suficientes.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (which == moods.length - 1) Collections.shuffle(queue);
                    startQueue(queue);
                    Toast.makeText(this, "Auren Mood: " + moods[which], Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private List<Track> buildMoodQueue(String mood) {
        List<Track> result = new ArrayList<>();
        String key = mood.toLowerCase(Locale.US);
        for (Track t : tracks) {
            String value = (safeTitle(t) + " " + safeArtist(t) + " " + safeAlbum(t) + " " + safeGenre(t)).toLowerCase(Locale.US);
            boolean match =
                    key.equals("energético") && containsAny(value, "dance", "party", "energy", "remix", "club", "house", "beat", "summer") ||
                    key.equals("calmo") && containsAny(value, "calm", "acoustic", "piano", "ambient", "sleep", "soft", "relax") ||
                    key.equals("noite") && containsAny(value, "night", "midnight", "moon", "dream", "after dark") ||
                    key.equals("viagem") && containsAny(value, "road", "travel", "trip", "drive", "journey", "summer") ||
                    key.equals("foco") && containsAny(value, "focus", "instrumental", "study", "lofi", "ambient");
            if (match) result.add(t);
        }
        if (result.size() < 3) {
            result = new ArrayList<>(tracks);
            Collections.sort(result, (a, b) -> Long.compare(AurenAnalytics.trackTime(this, a.id), AurenAnalytics.trackTime(this, b.id)));
        }
        Collections.shuffle(result);
        if (currentTrack != null) result.remove(currentTrack);
        return result.subList(0, Math.min(12, result.size()));
    }

    private boolean containsAny(String value, String... keys) {
        for (String k : keys) if (value.contains(k)) return true;
        return false;
    }

    private View featureAction(String icon, String title, String subtitle, View.OnClickListener listener) {
        LinearLayout card = rounded(ThemeManager.resolve(this, R.color.accent_soft), 20);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView i = text(icon, 24, R.color.auren_primary);
        i.setGravity(Gravity.CENTER);
        card.addView(i, new LinearLayout.LayoutParams(dp(48), dp(52)));
        LinearLayout info = column();
        TextView t = text(title, 15, R.color.text_primary);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        info.addView(t);
        info.addView(text(subtitle, 11, R.color.text_secondary), margins(0, 3, 0, 0));
        card.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        card.setOnClickListener(listener);
        return card;
    }

    private View featureMetricCard(String label, String value, String detail) {
        LinearLayout card = rounded(ThemeManager.resolve(this, R.color.accent_soft), 20);
        card.setPadding(dp(15), dp(13), dp(15), dp(13));
        TextView l = text(label, 10, R.color.auren_primary);
        l.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(l);
        TextView v = text(value, 23, R.color.text_primary);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(v, margins(0, 3, 0, 0));
        card.addView(text(detail, 11, R.color.text_secondary), margins(0, 2, 0, 0));
        return card;
    }

    private void showRecent() {
        setActiveTab(libraryTab);
        pageContainer.removeAllViews();
        LinearLayout content = column();
        content.setPadding(dp(20), dp(16), dp(20), dp(18));
        TextView eyebrow = text("SEU HISTÓRICO", 11, R.color.auren_primary);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView title = text("Recentes", 30, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(eyebrow);
        content.addView(title, margins(0, 3, 0, 14));
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = column();
        if (recentTracks.isEmpty()) {
            list.addView(emptyCard("As músicas reproduzidas aparecerão aqui."));
        } else {
            for (Track t : recentTracks) list.addView(trackRow(t, 0));
        }
        scroll.addView(list);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        pageContainer.addView(content);
    }

    private void showSuggestions() {
        setActiveTab(libraryTab);
        pageContainer.removeAllViews();
        LinearLayout content = column();
        content.setPadding(dp(20), dp(16), dp(20), dp(18));
        TextView eyebrow = text("PARA VOCÊ", 11, R.color.auren_primary);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView title = text("Sugestões", 30, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(eyebrow);
        content.addView(title, margins(0, 3, 0, 14));
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = column();
        List<Track> suggestions = suggestionTracks();
        if (suggestions.isEmpty()) {
            list.addView(emptyCard("As sugestões aparecerão quando sua biblioteca for carregada."));
        } else {
            for (Track t : suggestions) list.addView(trackRow(t, 0));
        }
        scroll.addView(list);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        pageContainer.addView(content);
    }
    private void showPlaylists() {
        setActiveTab(playlistTab);
        pageContainer.removeAllViews();
        LinearLayout content = column();
        content.setPadding(dp(20), dp(16), dp(20), dp(18));

        TextView eyebrow = text("SUA COLEÇÃO", 11, R.color.auren_primary);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView title = text("Playlists", 30, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(eyebrow);
        content.addView(title, margins(0, 3, 0, 0));

        LinearLayout create = rounded(ThemeManager.resolve(this, R.color.accent_soft), 20);
        create.setGravity(Gravity.CENTER_VERTICAL);
        TextView plus = text("+", 28, R.color.auren_primary);
        plus.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        plus.setGravity(Gravity.CENTER);
        create.addView(plus, new LinearLayout.LayoutParams(dp(54), dp(62)));
        LinearLayout createText = column();
        TextView ct = text("Criar playlist", 15, R.color.text_primary);
        ct.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        createText.addView(ct);
        createText.addView(text("Organize suas músicas", 12, R.color.text_secondary));
        create.addView(createText, new LinearLayout.LayoutParams(0, dp(62), 1));
        create.setOnClickListener(v -> showCreatePlaylistDialog());
        content.addView(create, margins(0, 18, 0, 12));

        content.addView(playlistCard("Músicas favoritas", "Músicas que você marcou como favorita", countFavorites(), true));
        content.addView(playlistCard("Reproduzidas recentemente", "Seu histórico de reprodução", recentTracks.size(), false), margins(0, 10, 0, 0));
        content.addView(playlistCard("Mais tocadas", "As músicas que você mais ouve", Math.min(10, tracks.size()), false), margins(0, 10, 0, 0));

        Set<String> names = getSharedPreferences("auren_player", MODE_PRIVATE)
                .getStringSet("playlist_names", new HashSet<>());
        if (names.isEmpty()) {
            content.addView(emptyCard("Crie uma playlist e ela aparecerá aqui."), margins(0, 14, 0, 0));
        } else {
            TextView yours = text("SUAS PLAYLISTS", 11, R.color.auren_primary);
            yours.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            content.addView(yours, margins(0, 18, 0, 8));
            for (String name : names) {
                content.addView(customPlaylistCard(name), margins(0, 0, 0, 10));
            }
        }
        pageContainer.addView(content);
    }



    private View buildBottomNavigation() {
        LinearLayout nav = row();
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(5), dp(8), dp(8));
        nav.setBackground(roundDrawable(ThemeManager.card(this), 22));
        nav.setElevation(dp(5));
        homeTab = navItem("HOME", "Início", v -> showHome());
        libraryTab = navItem("LIBRARY", "Biblioteca", v -> showLibrary());
        playlistTab = navItem("PLAYLIST", "Playlists", v -> showPlaylists());
        nav.addView(homeTab, new LinearLayout.LayoutParams(0, dp(58), 1));
        nav.addView(libraryTab, new LinearLayout.LayoutParams(0, dp(58), 1));
        nav.addView(playlistTab, new LinearLayout.LayoutParams(0, dp(58), 1));
        return nav;
    }
    private TextView navItem(String icon, String label, View.OnClickListener listener) {
        int iconRes = icon.equals("HOME") ? R.drawable.ic_home
                : icon.equals("LIBRARY") ? R.drawable.ic_library_music
                : R.drawable.ic_playlist_play;
        TextView v = text(label, 11, R.color.text_secondary);
        v.setGravity(Gravity.CENTER);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setCompoundDrawablesWithIntrinsicBounds(0, iconRes, 0, 0);
        v.setCompoundDrawablePadding(dp(3));
        v.setPadding(0, dp(4), 0, dp(4));
        v.setContentDescription(label);
        v.setOnClickListener(listener);
        return v;
    }
    private void setActiveTab(TextView active) {
        if (homeTab == null) return;
        int inactive = ThemeManager.resolve(this, R.color.text_secondary);
        int selected = ThemeManager.resolve(this, R.color.auren_primary);
        homeTab.setTextColor(inactive);
        libraryTab.setTextColor(inactive);
        playlistTab.setTextColor(inactive);
        homeTab.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(inactive));
        libraryTab.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(inactive));
        playlistTab.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(inactive));
        active.setTextColor(selected);
        active.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(selected));
    }

    private void showAppMenu(View anchor) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = column();
        root.setBackgroundColor(ThemeManager.resolve(this, R.color.surface));

        LinearLayout header = column();
        header.setPadding(dp(20), dp(28), dp(20), dp(20));
        header.setBackgroundColor(ThemeManager.resolve(this, R.color.auren_primary));

        TextView brand = text("NEXAUREN", 25, android.R.color.white);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(brand);
        TextView subtitle = text("Music Player", 13, android.R.color.white);
        header.addView(subtitle, margins(0, 2, 0, 0));
        TextView version = text("Versão " + BuildConfig.VERSION_NAME, 11, android.R.color.white);
        header.addView(version, margins(0, 12, 0, 0));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout items = column();
        items.setPadding(dp(10), dp(12), dp(10), dp(16));

        addDrawerSection(items, "BIBLIOTECA");
        addDrawerItem(items, "⌂", "Início", () -> { dialog.dismiss(); showHome(); });
        addDrawerItem(items, "♫", "Biblioteca", () -> { dialog.dismiss(); showLibrary(); });
        addDrawerItem(items, "♥", "Favoritos", () -> { dialog.dismiss(); showLibrary(true); });
        addDrawerItem(items, "▤", "Playlists", () -> { dialog.dismiss(); showPlaylists(); });

        addDrawerSection(items, "ATIVIDADE");
        addDrawerItem(items, "↗", "Mais tocadas", () -> { dialog.dismiss(); showMostPlayed(); });
        addDrawerItem(items, "◷", "Recentes", () -> { dialog.dismiss(); showRecent(); });
        addDrawerItem(items, "✦", "Sugestões", () -> { dialog.dismiss(); showSuggestions(); });
        addDrawerItem(items, "★", "Auren Journey", () -> { dialog.dismiss(); showJourney(); });

        addDrawerSection(items, "NEXAUREN INTELLIGENCE");
        addDrawerItem(items, "◉", "Estatísticas completas", () -> { dialog.dismiss(); showAnalyticsDashboard(); });
        addDrawerItem(items, "✦", "Auren Mix", () -> { dialog.dismiss(); showAurenMix(); });
        addDrawerItem(items, "◌", "Auren Focus", () -> { dialog.dismiss(); showAurenFocus(); });
        addDrawerItem(items, "◷", "Auren Memories", () -> { dialog.dismiss(); showAurenMemories(); });
        addDrawerItem(items, "↻", "Auren Replay", () -> { dialog.dismiss(); showAurenReplay(); });
        addDrawerItem(items, "⌕", "Auren Discovery", () -> { dialog.dismiss(); showAurenDiscovery(); });
        addDrawerItem(items, "◈", "Auren Mood", () -> { dialog.dismiss(); showAurenMood(); });

        addDrawerSection(items, "FERRAMENTAS");
        addDrawerItem(items, "≋", "Equalizador", () -> {
            dialog.dismiss();
            startActivity(new Intent(this, EqualizerActivity.class));
        });
        addDrawerItem(items, "↻", "Atualizar biblioteca", () -> {
            dialog.dismiss();
            loadMusic();
            Toast.makeText(this, "Biblioteca atualizada.", Toast.LENGTH_SHORT).show();
        });
        addDrawerItem(items, "⚙", "Configurações", () -> {
            dialog.dismiss();
            startActivity(new Intent(this, SettingsActivity.class));
        });
        addDrawerItem(items, "ⓘ", "Sobre Nexauren", () -> { dialog.dismiss(); showAboutDialog(); });

        scroll.addView(items);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setDimAmount(0.28f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setGravity(Gravity.START | Gravity.TOP);
            window.setLayout(
                    Math.min(dp(330), getResources().getDisplayMetrics().widthPixels - dp(24)),
                    -1
            );
        }
    }

    private void addDrawerSection(LinearLayout parent, String label) {
        TextView section = text(label, 10, R.color.text_secondary);
        section.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        section.setLetterSpacing(0.08f);
        parent.addView(section, margins(14, 12, 10, 4));
    }

    private void addDrawerItem(LinearLayout parent, String icon, String label, Runnable action) {
        LinearLayout item = row();
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(14), dp(8), dp(12), dp(8));
        item.setBackgroundColor(ThemeManager.card(this));

        TextView iconView = text(icon, 22, R.color.text_secondary);
        iconView.setGravity(Gravity.CENTER);
        item.addView(iconView, new LinearLayout.LayoutParams(dp(48), dp(50)));

        TextView name = text(label, 15, R.color.text_primary);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        item.addView(name, new LinearLayout.LayoutParams(0, dp(50), 1));

        TextView arrow = text("›", 25, R.color.text_secondary);
        item.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(50)));
        item.setOnClickListener(v -> action.run());
        parent.addView(item, margins(0, 3, 0, 3));
    }


    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Nexauren Music Player")
                .setMessage("Um player de música moderno, feito para a sua biblioteca local.\n\nVersion " + BuildConfig.VERSION_NAME + "\n\nMusic that moves with you.")
                .setPositiveButton("Fechar", null)
                .show();
    }

    private void showSearchDialog() {
        EditText input = new EditText(this);
        input.setHint("Música ou artista");
        input.setSingleLine(true);
        int pad = dp(18);
        input.setPadding(pad, pad, pad, pad);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Pesquisar sua música")
                .setView(input)
                .setNegativeButton("Fechar", null)
                .create();
        input.setOnEditorActionListener((v, actionId, event) -> {
            performSearch(input.getText().toString(), dialog);
            return true;
        });
        dialog.setOnShowListener(d -> input.requestFocus());
        dialog.show();
    }

    private void performSearch(String query, AlertDialog dialog) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.US);
        if (q.isEmpty()) return;
        List<Track> matches = new ArrayList<>();
        for (Track track : tracks) {
            String title = safeTitle(track).toLowerCase(Locale.US);
            String artist = safeArtist(track).toLowerCase(Locale.US);
            if (title.contains(q) || artist.contains(q)) matches.add(track);
        }
        dialog.dismiss();
        if (matches.isEmpty()) {
            Toast.makeText(this, "Nenhuma música encontrada.", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout list = column();
        list.setPadding(dp(18), dp(10), dp(18), dp(18));
        TextView resultTitle = text(matches.size() + " resultado" + (matches.size() == 1 ? "" : "s"), 13, R.color.auren_primary);
        resultTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        list.addView(resultTitle, margins(0, 4, 0, 8));
        for (Track track : matches) list.addView(trackRow(track, 0));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        new AlertDialog.Builder(this)
                .setTitle("Resultados da pesquisa")
                .setView(scroll)
                .setPositiveButton("Concluído", null)
                .show();
    }
    private LinearLayout buildMiniPlayer() {
        LinearLayout mini = column();
        mini.setBackground(roundDrawable(ThemeManager.card(this), 18));
        mini.setElevation(dp(10));
        mini.setPadding(0, 0, 0, dp(1));

        miniProgress = new SeekBar(this);
        miniProgress.setMax(1000);
        miniProgress.setEnabled(false);
        miniProgress.setPadding(0, 0, 0, 0);
        miniProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(ThemeManager.accent(this)));
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            miniProgress.setThumbTintList(android.content.res.ColorStateList.valueOf(ThemeManager.accent(this)));
        }
        mini.addView(miniProgress, new LinearLayout.LayoutParams(-1, dp(7)));

        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(5), dp(6), dp(5));

        miniArt = artwork(50);
        miniArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row.addView(miniArt, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout info = column();
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(10), 0, dp(5), 0);

        miniTitle = text("Nada a tocar", 13, R.color.text_primary);
        miniTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        miniTitle.setSingleLine(true);
        miniTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);

        miniArtist = text("Escolha uma música para começar", 11, R.color.text_secondary);
        miniArtist.setSingleLine(true);
        miniArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);

        info.addView(miniTitle);
        info.addView(miniArtist, margins(0, 1, 0, 0));
        row.addView(info, new LinearLayout.LayoutParams(0, dp(50), 1));

        ImageButton previous = iconButton(android.R.drawable.ic_media_previous, "Música anterior");
        previous.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        previous.setOnClickListener(v -> previousTrackInPlayer());
        row.addView(previous, new LinearLayout.LayoutParams(dp(38), dp(50)));

        miniFavorite = iconButton(android.R.drawable.btn_star_big_off, "Adicionar aos favoritos");
        miniFavorite.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        miniFavorite.setOnClickListener(v -> {
            if (currentTrack != null) {
                setFavorite(currentTrack, !isFavorite(currentTrack));
                updateMiniPlayer();
                Toast.makeText(this, isFavorite(currentTrack)
                        ? "Adicionado aos favoritos." : "Removido dos favoritos.", Toast.LENGTH_SHORT).show();
            }
        });
        row.addView(miniFavorite, new LinearLayout.LayoutParams(dp(38), dp(50)));

        miniPlay = iconButton(android.R.drawable.ic_media_play, "Reproduzir ou pausar");
        miniPlay.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                ThemeManager.resolve(this, R.color.auren_primary)));
        DrawableCompat.setTint(miniPlay.getDrawable(), ThemeManager.textOnAccent(this));
        miniPlay.setPadding(dp(11), dp(11), dp(11), dp(11));
        miniPlay.setOnClickListener(v -> togglePlayback());
        row.addView(miniPlay, new LinearLayout.LayoutParams(dp(50), dp(50)));

        ImageButton next = iconButton(android.R.drawable.ic_media_next, "Próxima música");
        next.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        next.setOnClickListener(v -> nextTrackInPlayer());
        row.addView(next, new LinearLayout.LayoutParams(dp(38), dp(50)));

        ImageButton functions = iconButton(android.R.drawable.ic_menu_more, "Funções do mini player");
        functions.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
        functions.setOnClickListener(v -> showMiniPlayerMenu(functions));
        row.addView(functions, new LinearLayout.LayoutParams(dp(36), dp(50)));

        mini.addView(row);
        mini.setOnClickListener(v -> openNowPlaying());
        miniArt.setOnClickListener(v -> openNowPlaying());
        miniTitle.setOnClickListener(v -> openNowPlaying());
        miniArtist.setOnClickListener(v -> openNowPlaying());
        return mini;
    }






    private void openNowPlaying() {
        if (currentTrack == null) {
            Toast.makeText(this, "Escolha uma música primeiro", Toast.LENGTH_SHORT).show();
            return;
        }
        if (nowPlayingDialog != null && nowPlayingDialog.isShowing()) return;

        nowPlayingDialog = new Dialog(this);
        nowPlayingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        nowPlayingDialog.setContentView(buildNowPlayingView());
        Window window = nowPlayingDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(-1, -1);
        }
        nowPlayingDialog.setOnDismissListener(d -> nowPlayingDialog = null);
        nowPlayingDialog.show();
        if (window != null) window.setLayout(-1, -1);
    }
    private View buildNowPlayingView() {
        ScrollView scroll = new ScrollView(this);
        int artworkColor = artworkAccentColor(currentTrack);
        scroll.setBackgroundColor(ThemeManager.blend(
                artworkColor, ThemeManager.surface(this), ThemeManager.isDark(this) ? 0.18f : 0.10f));

        LinearLayout root = column();
        root.setBackground(roundDrawable(ThemeManager.blend(
                artworkColor, ThemeManager.surface(this), ThemeManager.isDark(this) ? 0.24f : 0.075f), 28));
        root.setPadding(dp(20), dp(16), dp(20), dp(22));

        LinearLayout top = row();
        ImageButton close = iconButton(android.R.drawable.ic_menu_close_clear_cancel, "Fechar player");
        close.setOnClickListener(v -> closeNowPlaying());
        top.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView label = text("A TOCAR", 12, R.color.auren_primary);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(label, new LinearLayout.LayoutParams(0, dp(48), 1));

        ImageButton more = iconButton(android.R.drawable.ic_menu_more, "Opções do player");
        more.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, more);
            popup.getMenu().add("Reproduzir novamente");
            popup.getMenu().add(isFavorite(currentTrack) ? "Remover dos favoritos" : "Adicionar aos favoritos");
            popup.getMenu().add("Aleatório");
            popup.getMenu().add("Efeitos: velocidade e pitch");
            popup.getMenu().add("Fechar reprodução");
            popup.setOnMenuItemClickListener(item -> {
                String action = item.getTitle().toString();
                if (action.equals("Reproduzir novamente")) {
                    player.seekTo(0);
                    player.play();
                } else if (action.contains("favoritos")) {
                    setFavorite(currentTrack, !isFavorite(currentTrack));
                    updateMiniPlayer();
                } else if (action.equals("Aleatório")) {
                    shufflePlay();
                } else if (action.startsWith("Efeitos:")) {
                    showEffectsDialog();
                } else {
                    closeNowPlaying();
                }
                return true;
            });
            popup.show();
        });
        top.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(top);

        ImageView hero = artwork(1);
        hero.setImageURI(currentTrack.albumArtUri());
        if (hero.getDrawable() == null) hero.setImageResource(android.R.drawable.ic_media_play);
        hero.setPadding(0, 0, 0, 0);
        hero.setElevation(dp(8));
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, dp(300));
        heroParams.setMargins(0, dp(18), 0, dp(22));
        root.addView(hero, heroParams);

        LinearLayout titleRow = row();
        LinearLayout names = column();
        TextView title = text(safeTitle(currentTrack), 23, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView artist = text(safeArtist(currentTrack), 14, R.color.text_secondary);
        names.addView(title);
        names.addView(artist, margins(0, 3, 0, 0));
        titleRow.addView(names, new LinearLayout.LayoutParams(0, dp(62), 1));

        ImageButton favorite = iconButton(
                isFavorite(currentTrack) ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off,
                "Favoritar");
        favorite.setOnClickListener(v -> {
            setFavorite(currentTrack, !isFavorite(currentTrack));
            favorite.setImageResource(
                    isFavorite(currentTrack)
                            ? android.R.drawable.btn_star_big_on
                            : android.R.drawable.btn_star_big_off);
            showLibraryIfNeeded();
        });
        titleRow.addView(favorite, new LinearLayout.LayoutParams(dp(52), dp(62)));
        root.addView(titleRow);

        SeekBar seek = new SeekBar(this);
        nowPlayingSeek = seek;
        seek.setMax(1000);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(
                ThemeManager.resolve(this, R.color.auren_primary)));
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            seek.setThumbTintList(android.content.res.ColorStateList.valueOf(
                    ThemeManager.resolve(this, R.color.auren_primary)));
        }
        seek.setProgress(progressValue());
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                userDragging = fromUser;
                if (fromUser && player != null && player.getDuration() > 0) {
                    player.seekTo(player.getDuration() * progress / 1000L);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { userDragging = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) { userDragging = false; }
        });
        root.addView(seek, margins(0, 12, 0, 0));

        LinearLayout times = row();
        TextView position = text(formatTime(player.getCurrentPosition()), 11, R.color.text_secondary);
        TextView duration = text(formatTime(player.getDuration()), 11, R.color.text_secondary);
        nowPlayingPosition = position;
        nowPlayingDuration = duration;
        duration.setGravity(Gravity.RIGHT);
        times.addView(position, new LinearLayout.LayoutParams(0, dp(22), 1));
        times.addView(duration, new LinearLayout.LayoutParams(0, dp(22), 1));
        root.addView(times);

        LinearLayout controls = row();
        controls.setGravity(Gravity.CENTER);
        ImageButton previous = iconButton(android.R.drawable.ic_media_previous, "Anterior");
        previous.setOnClickListener(v -> previousTrackInPlayer());
        controls.addView(previous, new LinearLayout.LayoutParams(dp(64), dp(64)));

        ImageButton playPause = iconButton(
                player.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                "Reproduzir ou pausar");
        playPause.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(ThemeManager.resolve(this, R.color.auren_primary)));
        DrawableCompat.setTint(playPause.getDrawable(), ThemeManager.textOnAccent(this));
        playPause.setPadding(dp(18), dp(18), dp(18), dp(18));
        playPause.setOnClickListener(v -> {
            togglePlayback();
            playPause.setImageResource(
                    player.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
            DrawableCompat.setTint(playPause.getDrawable(), Color.WHITE);
        });
        controls.addView(playPause, new LinearLayout.LayoutParams(dp(76), dp(76)));

        ImageButton next = iconButton(android.R.drawable.ic_media_next, "Próxima música");
        next.setOnClickListener(v -> nextTrackInPlayer());
        controls.addView(next, new LinearLayout.LayoutParams(dp(64), dp(64)));
        root.addView(controls, margins(0, 8, 0, 0));

        LinearLayout extras = row();
        extras.setGravity(Gravity.CENTER);
        extras.addView(playerAction("↶", "Repetir", v -> {
            if (player != null) player.seekTo(0);
        }), new LinearLayout.LayoutParams(0, dp(58), 1));
        extras.addView(playerAction("⇄", "Aleatório", v -> shufflePlay()),
                new LinearLayout.LayoutParams(0, dp(58), 1));
        extras.addView(playerAction("☰", "Fila", v -> showQueueDialog()),
                new LinearLayout.LayoutParams(0, dp(58), 1));
        extras.addView(playerAction("⌁", "Mix", v -> showAurenMix()),
                new LinearLayout.LayoutParams(0, dp(58), 1));
        extras.addView(playerAction("⚙", "Equalizador", v -> startActivity(
                        new Intent(this, EqualizerActivity.class))),
                new LinearLayout.LayoutParams(0, dp(58), 1));
        root.addView(extras, margins(0, 8, 0, 0));

        LinearLayout featureRow = row();
        featureRow.setGravity(Gravity.CENTER);
        featureRow.addView(playerAction("★", "Journey", v -> showJourney()),
                new LinearLayout.LayoutParams(0, dp(52), 1));
        featureRow.addView(playerAction("◷", "Focus", v -> showAurenFocus()),
                new LinearLayout.LayoutParams(0, dp(52), 1));
        featureRow.addView(playerAction("◈", "Mood", v -> showAurenMood()),
                new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(featureRow, margins(0, 6, 0, 0));

        TextView hint = text("Nexauren • Música que acompanha você", 11, R.color.text_secondary);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, margins(0, 10, 0, 0));

        scroll.addView(root);
        return scroll;
    }
    private void showMiniPlayerMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Fila de reprodução");
        popup.getMenu().add("Temporizador de sono");
        popup.getMenu().add("Repetição");
        popup.getMenu().add("Repetir trecho A-B");
        popup.getMenu().add("Ordenar biblioteca");
        popup.getMenu().add("Atualizar biblioteca");
        popup.getMenu().add("Efeitos");
        popup.getMenu().add("Velocidade: " + formatEffectValue(playbackSpeed));
        popup.getMenu().add("Pitch: " + formatEffectValue(playbackPitch));
        popup.getMenu().add("Detalhes da música");
        popup.getMenu().add("Repor efeitos");
        popup.setOnMenuItemClickListener(item -> {
            String action = item.getTitle().toString();
            if (action.equals("Fila de reprodução")) {
                showQueueDialog();
            } else if (action.equals("Temporizador de sono")) {
                showSleepTimerDialog();
            } else if (action.equals("Repetição")) {
                showRepeatDialog();
            } else if (action.equals("Repetir trecho A-B")) {
                showAbRepeatDialog();
            } else if (action.equals("Ordenar biblioteca")) {
                showSortDialog();
            } else if (action.equals("Atualizar biblioteca")) {
                loadMusic();
                Toast.makeText(this, "Biblioteca atualizada.", Toast.LENGTH_SHORT).show();
            } else if (action.equals("Efeitos")) {
                showEffectsDialog();
            } else if (action.startsWith("Velocidade:")) {
                showSpeedDialog();
            } else if (action.startsWith("Pitch:")) {
                showPitchDialog();
            } else if (action.equals("Detalhes da música")) {
                if (currentTrack != null) showTrackDetails(currentTrack);
            } else if (action.equals("Repor efeitos")) {
                playbackSpeed = 1.0f;
                playbackPitch = 1.0f;
                applyPlaybackEffects();
                Toast.makeText(this, "Efeitos repostos.", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        popup.show();
    }







    private String formatEffectValue(float value) {
        return String.format(Locale.US, "%.2fx", value);
    }

    private void applyPlaybackEffects() {
        if (player != null) {
            player.setPlaybackParameters(new PlaybackParameters(playbackSpeed, playbackPitch));
        }
    }

    private void showEffectsDialog() {
        final String[] options = {
                "Velocidade — " + formatEffectValue(playbackSpeed),
                "Pitch — " + formatEffectValue(playbackPitch),
                "Repor velocidade e pitch"
        };
        new AlertDialog.Builder(this)
                .setTitle("Efeitos de reprodução")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showSpeedDialog();
                    else if (which == 1) showPitchDialog();
                    else {
                        playbackSpeed = 1.0f;
                        playbackPitch = 1.0f;
                        applyPlaybackEffects();
                    }
                })
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void showSpeedDialog() {
        final float[] values = {0.50f, 0.75f, 1.00f, 1.25f, 1.50f, 1.75f, 2.00f};
        final String[] labels = {"0.50x", "0.75x", "1.00x Normal", "1.25x", "1.50x", "1.75x", "2.00x"};
        int checked = 2;
        for (int i = 0; i < values.length; i++) if (Math.abs(values[i] - playbackSpeed) < 0.01f) checked = i;
        new AlertDialog.Builder(this)
                .setTitle("Velocidade")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    playbackSpeed = values[which];
                    applyPlaybackEffects();
                    dialog.dismiss();
                    Toast.makeText(this, "Velocidade: " + labels[which], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showPitchDialog() {
        final float[] values = {0.75f, 0.85f, 0.95f, 1.00f, 1.05f, 1.15f, 1.25f};
        final String[] labels = {"-5 semitons", "-3 semitons", "-1 semitom", "Normal", "+1 semitom", "+3 semitons", "+5 semitons"};
        int checked = 3;
        for (int i = 0; i < values.length; i++) if (Math.abs(values[i] - playbackPitch) < 0.01f) checked = i;
        new AlertDialog.Builder(this)
                .setTitle("Pitch")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    playbackPitch = values[which];
                    applyPlaybackEffects();
                    dialog.dismiss();
                    Toast.makeText(this, "Pitch: " + labels[which], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private int artworkAccentColor(Track track) {
        int fallback = ThemeManager.accent(this);
        if (track == null) return fallback;
        try {
            ImageView probe = artwork(1);
            probe.setImageURI(track.albumArtUri());
            if (probe.getDrawable() instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) probe.getDrawable()).getBitmap();
                if (bitmap != null && bitmap.getWidth() > 2 && bitmap.getHeight() > 2) {
                    int stepX = Math.max(1, bitmap.getWidth() / 8);
                    int stepY = Math.max(1, bitmap.getHeight() / 8);
                    long r = 0, g = 0, b = 0, n = 0;
                    for (int x = 0; x < bitmap.getWidth(); x += stepX) {
                        for (int y = 0; y < bitmap.getHeight(); y += stepY) {
                            int px = bitmap.getPixel(x, y);
                            r += Color.red(px); g += Color.green(px); b += Color.blue(px); n++;
                        }
                    }
                    if (n > 0) return Color.rgb((int)(r/n), (int)(g/n), (int)(b/n));
                }
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    private void closeNowPlaying() {
        if (nowPlayingDialog != null && nowPlayingDialog.isShowing()) nowPlayingDialog.dismiss();
    }

    private void updateNowPlayingProgress() {
        if (player == null) return;
        long duration = Math.max(0L, player.getDuration());
        long position = Math.max(0L, player.getCurrentPosition());
        if (nowPlayingSeek != null && duration > 0L && !userDragging) {
            nowPlayingSeek.setProgress(Math.max(0, Math.min(1000, (int) (position * 1000L / duration))));
        }
        if (nowPlayingPosition != null) nowPlayingPosition.setText(formatTime(position));
        if (nowPlayingDuration != null) nowPlayingDuration.setText(formatTime(duration));
    }

    private void previousTrackInPlayer() {
        if (tracks.isEmpty()) return;
        int index = currentTrack == null ? 0 : tracks.indexOf(currentTrack);
        if (index < 0) index = 0;
        play(tracks.get(index <= 0 ? tracks.size() - 1 : index - 1));
        refreshNowPlaying();
    }
    private void nextTrackInPlayer() {
        if (playQueue.isEmpty() && tracks.size() > 1) {
            buildSmartQueue();
        }
        if (!playQueue.isEmpty()) {
            Track next = playQueue.remove(0);
            if (sleepAtTrackEnd) {
                sleepAtTrackEnd = false;
                sleepTimerEndMs = 0L;
            }
            play(next);
            refreshNowPlaying();
            return;
        }
        if (tracks.isEmpty()) return;
        int index = currentTrack == null ? -1 : tracks.indexOf(currentTrack);
        play(tracks.get(index >= tracks.size() - 1 ? 0 : index + 1));
        refreshNowPlaying();
    }

    private void refreshNowPlaying() {
        if (nowPlayingDialog != null && nowPlayingDialog.isShowing()) {
            nowPlayingDialog.setContentView(buildNowPlayingView());
            Window window = nowPlayingDialog.getWindow();
            if (window != null) {
                window.setLayout(-1, -1);
            }
        } else if (currentTrack != null) {
            openNowPlaying();
        }
    }

    private void restoreListeningState() {
        SharedPreferences prefs = getSharedPreferences("auren_player", MODE_PRIVATE);
        playCounts.clear();
        String serializedCounts = prefs.getString("play_counts_v2", "");
        if (!serializedCounts.isEmpty()) {
            for (String entry : serializedCounts.split(";")) {
                String[] pair = entry.split("=", 2);
                if (pair.length != 2) continue;
                try {
                    long id = Long.parseLong(pair[0]);
                    int count = Integer.parseInt(pair[1]);
                    if (count > 0) playCounts.put(id, count);
                } catch (NumberFormatException ignored) {}
            }
        }
        // Backward-compatible fallback for versions that stored one key per track.
        for (Track track : tracks) {
            if (!playCounts.containsKey(track.id)) {
                int count = prefs.getInt("play_count_" + track.id, 0);
                if (count > 0) playCounts.put(track.id, count);
            }
        }

        recentTracks.clear();
        long lastPlayedId = prefs.getLong("last_played_id", -1L);
        for (Track track : tracks) {
            if (track.id == lastPlayedId) {
                currentTrack = track;
                break;
            }
        }
        String recent = prefs.getString("recent_tracks", "");
        if (recent == null || recent.trim().isEmpty()) return;
        for (String value : recent.split(",")) {
            try {
                long id = Long.parseLong(value.trim());
                for (Track track : tracks) {
                    if (track.id == id) {
                        recentTracks.add(track);
                        break;
                    }
                }
            } catch (NumberFormatException ignored) {
            }
            if (recentTracks.size() >= 20) break;
        }
    }

    private void persistListeningState(Track track) {
        if (track == null) return;
        StringBuilder history = new StringBuilder();
        for (Track recent : recentTracks) {
            if (history.length() > 0) history.append(',');
            history.append(recent.id);
        }

        StringBuilder counts = new StringBuilder();
        for (Map.Entry<Long, Integer> entry : playCounts.entrySet()) {
            if (counts.length() > 0) counts.append(';');
            counts.append(entry.getKey()).append('=').append(entry.getValue());
        }

        getSharedPreferences("auren_player", MODE_PRIVATE).edit()
                .putString("play_counts_v2", counts.toString())
                .putInt("play_count_" + track.id, playCounts.getOrDefault(track.id, 0))
                .putString("recent_tracks", history.toString())
                .putLong("last_played_id", track.id)
                .putLong("last_played_at", System.currentTimeMillis())
                .putInt("stats_schema", 2)
                .commit();
    }
    private void play(Track track) {
        if (track == null) return;
        if (player == null) {
            Toast.makeText(this, "O áudio ainda está a iniciar. Tente novamente.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean sameTrack = currentTrack != null && currentTrack.id == track.id;
        boolean ended = player.getPlaybackState() == Player.STATE_ENDED;
        if (sameTrack && player.getMediaItemCount() > 0 && !ended) {
            player.play();
            updateMiniPlayer();
            highlightPlayingTrack();
            return;
        }

        boolean newPlayEvent = !sameTrack || ended;
        boolean restoringTrack = sameTrack && player.getMediaItemCount() == 0 && !ended;
        if (!sameTrack && player.getMediaItemCount() > 0 && currentTrack != null) {
            persistResumePosition();
        }
        currentTrack = track;
        if (newPlayEvent) {
            playCounts.put(track.id, playCounts.getOrDefault(track.id, 0) + 1);
            recentTracks.remove(track);
            recentTracks.add(0, track);
            while (recentTracks.size() > 20) recentTracks.remove(recentTracks.size() - 1);
            AurenAnalytics.recordPlayStart(this, track.id, safeArtist(track));
            int beforeUnlocked = AchievementManager.stats(this).unlocked;
            AchievementManager.recordPlay(this, track.id);
            int afterUnlocked = AchievementManager.stats(this).unlocked;
            if (afterUnlocked > beforeUnlocked) showAchievementToast();
        }
        persistListeningState(track);

        Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id);
        MediaItem item = new MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(safeTitle(track))
                        .setArtist(safeArtist(track))
                        .setAlbumArtist(safeArtist(track))
                        .setArtworkUri(track.albumArtUri())
                        .build())
                .build();

        long resumePosition = 0L;
        android.content.SharedPreferences prefs = getSharedPreferences("auren_player", MODE_PRIVATE);
        long savedPerTrack = prefs.getLong("track_position_" + track.id, 0L);
        if (savedPerTrack > 0L) {
            resumePosition = savedPerTrack;
        } else if (restoringTrack && prefs.getLong("resume_id", -1L) == track.id) {
            resumePosition = Math.max(0L, prefs.getLong("resume_position", 0L));
        }
        item = item.buildUpon().setMediaId(String.valueOf(track.id)).build();
        if (resumePosition > 0L) {
            player.setMediaItem(item, resumePosition);
        } else {
            player.setMediaItem(item);
        }
        player.prepare();
        requestNotificationPermissionIfNeeded();
        player.play();
        prefs.edit().remove("resume_position").remove("resume_id").remove("resume_playing").apply();
        playbackSessionRestored = true;
        updateMiniPlayer();
        highlightPlayingTrack();
    }


    private void togglePlayback() {
        if (player == null) return;
        if (player.isPlaying()) player.pause();
        else if (player.getMediaItemCount() > 0) player.play();
        updateMiniPlayer();
    }

    private void highlightPlayingTrack() {
        if (pageContainer == null) return;
        updatePlayingViews(pageContainer);
    }

    private void updatePlayingViews(View view) {
        Object tag = view.getTag();
        if (tag instanceof Long) {
            boolean playing = currentTrack != null && ((Long) tag) == currentTrack.id;
            view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    getColor(playing ? R.color.playing_background : R.color.card)));
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View child = group.getChildAt(i);
                    if (child instanceof TextView) {
                        TextView tv = (TextView) child;
                        tv.setTextColor(ThemeManager.resolve(this, playing ? R.color.playing_text : R.color.text_primary));
                    }
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                updatePlayingViews(group.getChildAt(i));
            }
        }
    }
    private void updateMiniPlayer() {
        if (currentTrack == null || miniTitle == null || miniArtist == null || miniArt == null || miniPlay == null) return;
        miniTitle.setText(safeTitle(currentTrack));
        miniArtist.setText(safeArtist(currentTrack));
        miniArt.setImageURI(currentTrack.albumArtUri());
        if (miniArt.getDrawable() == null) miniArt.setImageResource(android.R.drawable.ic_media_play);
        boolean playing = player != null && player.isPlaying();
        if (miniProgress != null && player != null && player.getDuration() > 0L) {
            miniProgress.setProgress(Math.max(0, Math.min(1000,
                    (int) (player.getCurrentPosition() * 1000L / player.getDuration()))));
        }
        miniPlay.setImageResource(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        miniPlay.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                ThemeManager.resolve(this, R.color.auren_primary)));
        DrawableCompat.setTint(miniPlay.getDrawable(), Color.WHITE);
        if (miniFavorite != null) {
            boolean favorite = isFavorite(currentTrack);
            miniFavorite.setImageResource(favorite
                    ? android.R.drawable.btn_star_big_on
                    : android.R.drawable.btn_star_big_off);
            DrawableCompat.setTint(miniFavorite.getDrawable(),
                    favorite ? ThemeManager.resolve(this, R.color.auren_primary)
                            : ThemeManager.resolve(this, R.color.text_primary));
        }
    }


    private void syncCurrentTrackWithController() {
        if (player == null || player.getMediaItemCount() == 0) return;
        String mediaId = player.getCurrentMediaItem() == null ? null : player.getCurrentMediaItem().mediaId;
        if (mediaId == null || mediaId.trim().isEmpty()) return;
        try {
            long id = Long.parseLong(mediaId);
            for (Track track : tracks) {
                if (track.id == id) {
                    currentTrack = track;
                    playbackSessionRestored = true;
                    return;
                }
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void restorePlaybackSession() {
        if (playbackSessionRestored || player == null || tracks.isEmpty() || currentTrack == null) return;
        if (player.getMediaItemCount() > 0) {
            syncCurrentTrackWithController();
            updateMiniPlayer();
            highlightPlayingTrack();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("auren_player", MODE_PRIVATE);
        long resumeId = prefs.getLong("resume_id", -1L);
        if (resumeId != currentTrack.id) return;

        long position = Math.max(0L, prefs.getLong("resume_position", 0L));
        boolean shouldPlay = prefs.getBoolean("resume_playing", false);
        Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, currentTrack.id);
        MediaItem item = new MediaItem.Builder()
                .setUri(uri)
                .setMediaId(String.valueOf(currentTrack.id))
                .setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(safeTitle(currentTrack))
                        .setArtist(safeArtist(currentTrack))
                        .setAlbumArtist(safeArtist(currentTrack))
                        .setArtworkUri(currentTrack.albumArtUri())
                        .build())
                .build();

        if (position > 0L) player.setMediaItem(item, position);
        else player.setMediaItem(item);
        player.prepare();
        playbackSessionRestored = true;
        updateMiniPlayer();
        highlightPlayingTrack();
        if (shouldPlay) player.play();
    }

    private void shufflePlay() {
        if (tracks.isEmpty()) {
            Toast.makeText(this, "No music found.", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Track> copy = new ArrayList<>(tracks);
        Collections.shuffle(copy);
        play(copy.get(0));
        openNowPlaying();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !notificationPermissionRequested
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionRequested = true;
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION
            );
        }
    }

    private void requestMusicPermission() {
        String permission = android.os.Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) loadMusic();
        else ActivityCompat.requestPermissions(this, new String[]{permission}, MUSIC_PERMISSION);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] resultados) {
        super.onRequestPermissionsResult(requestCode, permissions, resultados);
        if (requestCode == MUSIC_PERMISSION && resultados.length > 0
                && resultados[0] == PackageManager.PERMISSION_GRANTED) {
            loadMusic();
        } else if (requestCode == MUSIC_PERMISSION) {
            Toast.makeText(this,
                    "É necessária a permissão de música para mostrar a sua biblioteca.",
                    Toast.LENGTH_LONG).show();
        }
    }


    private void updateAlbumAchievement() {
        if (tracks.isEmpty()) return;
        java.util.Map<String, Integer> totalByAlbum = new java.util.HashMap<>();
        java.util.Map<String, Integer> playedByAlbum = new java.util.HashMap<>();
        for (Track t : tracks) {
            String album = safeAlbum(t);
            totalByAlbum.put(album, totalByAlbum.getOrDefault(album, 0) + 1);
            if (playCounts.getOrDefault(t.id, 0) > 0) {
                playedByAlbum.put(album, playedByAlbum.getOrDefault(album, 0) + 1);
            }
        }
        boolean complete = false;
        for (String album : totalByAlbum.keySet()) {
            if (totalByAlbum.get(album) != null
                    && totalByAlbum.get(album) >= 2
                    && playedByAlbum.getOrDefault(album, 0) >= totalByAlbum.get(album)) {
                complete = true;
                break;
            }
        }
        getSharedPreferences("auren_player", MODE_PRIVATE).edit()
                .putBoolean("album_complete", complete
                        || getSharedPreferences("auren_player", MODE_PRIVATE).getBoolean("album_complete", false))
                .apply();
    }

    private String readGenre(long audioId) {
        try {
            Uri uri = MediaStore.Audio.Genres.getContentUriForAudioId("external", (int) audioId);
            String[] projection = {MediaStore.Audio.Genres.NAME};
            try (Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    String genre = cursor.getString(0);
                    return genre == null ? "" : genre.trim();
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private void loadMusic() {
        tracks.clear();
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    tracks.add(new Track(
                            id,
                            cursor.getString(1),
                            cursor.getString(2),
                            cursor.getLong(3),
                            cursor.getString(4),
                            readGenre(id),
                            cursor.getLong(5)));
                }
            }
        }
        applyLibrarySort();
        restoreListeningState();
        updateAlbumAchievement();
        if (pageContainer != null) showHome();
        updateMiniPlayer();
        restorePlaybackSession();
    }

    private void showQueueDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle("Fila de reprodução");
        if (playQueue.isEmpty()) {
            builder.setMessage("A fila está vazia. Use ⋮ em uma música e escolha Adicionar à fila ou Reproduzir a seguir.");
        } else {
            String[] labels = new String[playQueue.size()];
            for (int i = 0; i < playQueue.size(); i++) {
                Track track = playQueue.get(i);
                labels[i] = (i + 1) + ". " + safeTitle(track) + " — " + safeArtist(track);
            }
            builder.setItems(labels, (dialog, which) -> {
                Track selected = playQueue.remove(which);
                play(selected);
                refreshNowPlaying();
            });
        }
        builder.setNeutralButton("Limpar", (d, w) -> playQueue.clear());
        builder.setNegativeButton("Fechar", null);
        builder.show();
    }

    private void showSleepTimerDialog() {
        String active = sleepTimerEndMs > 0L
                ? "Temporizador ativo: " + formatRemaining(sleepTimerEndMs - System.currentTimeMillis())
                : "Sem temporizador ativo";
        String[] options = {"Desligar", "15 minutos", "30 minutos", "45 minutos", "60 minutos", "90 minutos", "Até a faixa terminar"};
        new AlertDialog.Builder(this)
                .setTitle("Temporizador de sono")
                .setMessage(active)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        sleepTimerEndMs = 0L;
                        sleepAtTrackEnd = false;
                        Toast.makeText(this, "Temporizador desligado.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (which == options.length - 1) {
                        sleepTimerEndMs = 0L;
                        sleepAtTrackEnd = true;
                        Toast.makeText(this, "A reprodução termina ao fim da faixa.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int[] minutes = {0, 15, 30, 45, 60, 90};
                    sleepAtTrackEnd = false;
                    sleepTimerEndMs = System.currentTimeMillis() + minutes[which] * 60_000L;
                    Toast.makeText(this, "Temporizador: " + minutes[which] + " min.", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private String formatRemaining(long ms) {
        long seconds = Math.max(0L, ms / 1000L);
        long minutes = seconds / 60L;
        seconds %= 60L;
        return minutes + ":" + String.format(Locale.US, "%02d", seconds);
    }

    private void checkAdvancedFeatures() {
        if (player == null) return;
        if (sleepTimerEndMs > 0L && System.currentTimeMillis() >= sleepTimerEndMs) {
            player.pause();
            sleepTimerEndMs = 0L;
            sleepAtTrackEnd = false;
            updateMiniPlayer();
            Toast.makeText(this, "Temporizador concluído.", Toast.LENGTH_SHORT).show();
        }
        if (abRepeatEnabled && abStartMs >= 0L && abEndMs > abStartMs
                && player.isPlaying() && player.getCurrentPosition() >= abEndMs) {
            player.seekTo(abStartMs);
        }
    }
    private void showRepeatDialog() {
        if (player == null) return;
        String[] labels = {"Desligado", "Repetir faixa"};
        int checked = player.getRepeatMode() == Player.REPEAT_MODE_ONE ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle("Repetição")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    player.setRepeatMode(which == 1 ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
                    dialog.dismiss();
                    Toast.makeText(this, "Repetição: " + labels[which], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }


    private void showAbRepeatDialog() {
        String start = abStartMs >= 0L ? formatTime(abStartMs) : "não definido";
        String end = abEndMs >= 0L ? formatTime(abEndMs) : "não definido";
        String[] options = {"Definir A em " + formatTime(player.getCurrentPosition()),
                "Definir B em " + formatTime(player.getCurrentPosition()),
                abRepeatEnabled ? "Desativar A-B" : "Ativar A-B", "Limpar A-B"};
        new AlertDialog.Builder(this)
                .setTitle("Repetição A-B")
                .setMessage("A: " + start + "\nB: " + end)
                .setItems(options, (dialog, which) -> {
                    long position = Math.max(0L, player.getCurrentPosition());
                    if (which == 0) {
                        abStartMs = position;
                        if (abEndMs <= abStartMs) abEndMs = -1L;
                        Toast.makeText(this, "Ponto A definido.", Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        if (abStartMs < 0L || position <= abStartMs) {
                            Toast.makeText(this, "Defina A antes de B e escolha um ponto depois de A.", Toast.LENGTH_SHORT).show();
                        } else {
                            abEndMs = position;
                            Toast.makeText(this, "Ponto B definido.", Toast.LENGTH_SHORT).show();
                        }
                    } else if (which == 2) {
                        if (abStartMs >= 0L && abEndMs > abStartMs) {
                            abRepeatEnabled = !abRepeatEnabled;
                            Toast.makeText(this, abRepeatEnabled ? "A-B ativado." : "A-B desativado.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Defina A e B primeiro.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        abStartMs = -1L;
                        abEndMs = -1L;
                        abRepeatEnabled = false;
                        Toast.makeText(this, "A-B limpo.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void showSortDialog() {
        String[] labels = {"Título", "Artista", "Duração curta → longa", "Duração longa → curta"};
        int checked = librarySortMode.equals("artist") ? 1
                : librarySortMode.equals("duration_asc") ? 2
                : librarySortMode.equals("duration_desc") ? 3 : 0;
        new AlertDialog.Builder(this)
                .setTitle("Ordenar biblioteca")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    librarySortMode = which == 1 ? "artist" : which == 2 ? "duration_asc" : which == 3 ? "duration_desc" : "title";
                    getSharedPreferences("auren_player", MODE_PRIVATE).edit()
                            .putString("library_sort", librarySortMode).apply();
                    applyLibrarySort();
                    showHome();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void applyLibrarySort() {
        librarySortMode = getSharedPreferences("auren_player", MODE_PRIVATE)
                .getString("library_sort", librarySortMode);
        Collections.sort(tracks, new Comparator<Track>() {
            @Override public int compare(Track a, Track b) {
                int resultado;
                if (librarySortMode.equals("artist")) {
                    resultado = safeArtist(a).compareToIgnoreCase(safeArtist(b));
                } else if (librarySortMode.equals("duration_asc")) {
                    resultado = Long.compare(a.durationMs, b.durationMs);
                } else if (librarySortMode.equals("duration_desc")) {
                    resultado = Long.compare(b.durationMs, a.durationMs);
                } else {
                    resultado = safeTitle(a).compareToIgnoreCase(safeTitle(b));
                }
                if (resultado != 0) return resultado;
                return safeTitle(a).compareToIgnoreCase(safeTitle(b));
            }
        });
    }


    private View homeAchievementCard() {
        AchievementManager.Stats stats = AchievementManager.stats(this);
        LinearLayout card = rounded(ThemeManager.resolve(this, R.color.accent_soft), 20);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = text("★", 25, R.color.auren_primary);
        badge.setGravity(Gravity.CENTER);
        card.addView(badge, new LinearLayout.LayoutParams(dp(48), dp(58)));

        LinearLayout info = column();
        TextView title = text(stats.totalPlays == 0
                        ? "Comece sua coleção de conquistas"
                        : "Seu progresso está crescendo",
                15, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        info.addView(title);
        info.addView(text(
                stats.today + " hoje • " + stats.week + " esta semana • recorde " + stats.bestDay + " no dia",
                11, R.color.text_secondary), margins(0, 3, 0, 0));
        info.addView(text(stats.unlocked + " de " + stats.totalAchievements + " conquistas desbloqueadas",
                11, R.color.auren_primary), margins(0, 3, 0, 0));
        card.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

        TextView arrow = text("›", 27, R.color.auren_primary);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(58)));
        card.setOnClickListener(v -> showAchievements());
        return card;
    }

    private View actionCard(String icon, String label, View.OnClickListener listener) {
        LinearLayout card = rounded(ThemeManager.resolve(this, R.color.accent_soft), 20);
        card.setGravity(Gravity.CENTER_VERTICAL);
        TextView i = text(icon, 23, R.color.auren_primary);
        i.setGravity(Gravity.CENTER);
        card.addView(i, new LinearLayout.LayoutParams(dp(42), dp(70)));
        TextView l = text(label, 13, R.color.text_primary);
        l.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(l);
        card.setOnClickListener(listener);
        return card;
    }

    private void addSectionHeader(LinearLayout parent, String title, String action, View.OnClickListener listener) {
        LinearLayout row = row();
        TextView t = text(title, 19, R.color.text_primary);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(t, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView a = text(action, 12, R.color.auren_primary);
        a.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        a.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        a.setOnClickListener(listener);
        row.addView(a, new LinearLayout.LayoutParams(dp(80), dp(42)));
        parent.addView(row, margins(0, 22, 0, 4));
    }

    private View horizontalTrackCard(Track track) {
        LinearLayout card = column();
        ImageView art = artwork(1);
        art.setImageURI(track.albumArtUri());
        if (art.getDrawable() == null) art.setImageResource(android.R.drawable.ic_media_play);
        card.addView(art, new LinearLayout.LayoutParams(dp(132), dp(132)));
        TextView title = text(safeTitle(track), 13, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(1);
        card.addView(title, margins(2, 7, 2, 0));
        TextView artist = text(safeArtist(track), 11, R.color.text_secondary);
        artist.setMaxLines(1);
        card.addView(artist, margins(2, 1, 2, 0));
        card.setOnClickListener(v -> play(track));
        return card;
    }
    private View trackRow(Track track, int number) {
        LinearLayout row = rounded(Color.WHITE, 16);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(7), dp(7), dp(4), dp(7));
        row.setTag(track.id);

        ImageView art = artwork(48);
        art.setImageURI(track.albumArtUri());
        if (art.getDrawable() == null) art.setImageResource(android.R.drawable.ic_media_play);
        row.addView(art, new LinearLayout.LayoutParams(dp(48), dp(48)));

        if (number > 0) {
            TextView n = text(String.valueOf(number), 12, R.color.auren_primary);
            n.setGravity(Gravity.CENTER);
            row.addView(n, new LinearLayout.LayoutParams(dp(28), dp(48)));
        }

        LinearLayout info = column();
        TextView title = text(safeTitle(track), 14, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView artist = text(safeArtist(track), 12, R.color.text_secondary);
        artist.setMaxLines(1);
        artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(title);
        info.addView(artist, margins(0, 2, 0, 0));
        row.addView(info, new LinearLayout.LayoutParams(0, dp(56), 1));

        if (number > 0) {
            TextView plays = text(playCounts.getOrDefault(track.id, 0) + " reproduções", 10, R.color.text_secondary);
            plays.setGravity(Gravity.CENTER);
            row.addView(plays, new LinearLayout.LayoutParams(dp(72), dp(48)));
        }

        ImageButton overflow = iconButton(android.R.drawable.ic_menu_more, "Mais opções");
        overflow.setPadding(dp(8), dp(8), dp(8), dp(8));
        overflow.setOnClickListener(v -> showTrackMenu(v, track));
        row.addView(overflow, new LinearLayout.LayoutParams(dp(46), dp(48)));
        row.setOnClickListener(v -> play(track));
        return row;
    }



    private boolean isCustomPlaylist(String name) {
        return getSharedPreferences("auren_player", MODE_PRIVATE)
                .getStringSet("playlist_names", new HashSet<>())
                .contains(name);
    }

    private View playlistCard(String title, String subtitle, int count, boolean favorite) {
        LinearLayout card = rounded(favorite ? ThemeManager.resolve(this, R.color.accent_soft) : ThemeManager.card(this), 18);
        card.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = text(favorite ? "♥" : "♫", 24, R.color.auren_primary);
        icon.setGravity(Gravity.CENTER);
        card.addView(icon, new LinearLayout.LayoutParams(dp(58), dp(66)));
        LinearLayout info = column();
        TextView t = text(title, 15, R.color.text_primary);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        info.addView(t);
        info.addView(text(subtitle, 12, R.color.text_secondary), margins(0, 2, 0, 0));
        card.addView(info, new LinearLayout.LayoutParams(0, dp(66), 1));
        TextView c = text(String.valueOf(count), 12, R.color.auren_primary);
        c.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        c.setGravity(Gravity.CENTER);
        card.addView(c, new LinearLayout.LayoutParams(dp(48), dp(66)));
        if (favorite) card.setOnClickListener(v -> showLibrary(true));
        else if (isCustomPlaylist(title)) card.setOnClickListener(v -> showPlaylistPage(title));
        return card;
    }

    private View playerAction(String icon, String label, View.OnClickListener listener) {
        LinearLayout box = column();
        box.setGravity(Gravity.CENTER);
        TextView i = text(icon, 21, R.color.auren_primary);
        i.setGravity(Gravity.CENTER);
        box.addView(i);
        TextView l = text(label, 10, R.color.text_secondary);
        l.setGravity(Gravity.CENTER);
        box.addView(l, margins(0, 2, 0, 0));
        box.setOnClickListener(listener);
        return box;
    }

    private View chip(String label, boolean active, View.OnClickListener listener) {
        LinearLayout c = rounded(active ? ThemeManager.resolve(this, R.color.accent_soft) : ThemeManager.resolve(this, R.color.surface_alt), 18);
        TextView t = text(label, 12, active ? R.color.auren_primary : R.color.text_secondary);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        c.addView(t, margins(14, 7, 14, 7));
        c.setOnClickListener(listener);
        return c;
    }

    private View emptyCard(String message) {
        LinearLayout card = rounded(ThemeManager.resolve(this, R.color.surface_alt), 18);
        TextView t = text(message, 13, R.color.text_secondary);
        t.setGravity(Gravity.CENTER);
        card.addView(t, margins(16, 18, 16, 18));
        return card;
    }

    private String safeAlbum(Track track) {
        return track == null || track.album == null || track.album.trim().isEmpty() ? "Álbum desconhecido" : track.album.trim();
    }

    private String safeGenre(Track track) {
        return track == null || track.genre == null || track.genre.trim().isEmpty() ? "Género não informado" : track.genre.trim();
    }

    private String safeTitle(Track track) {
        return track == null || track.title == null || track.title.trim().isEmpty() ? "Sem título" : track.title;
    }

    private String safeArtist(Track track) {
        return track == null || track.artist == null || track.artist.trim().isEmpty() ? "Artista desconhecido" : track.artist;
    }

    private List<Track> sortedByPlayCount() {
        List<Track> sorted = new ArrayList<>(tracks);
        Collections.sort(sorted, new Comparator<Track>() {
            @Override public int compare(Track a, Track b) {
                return Integer.compare(playCounts.getOrDefault(b.id, 0), playCounts.getOrDefault(a.id, 0));
            }
        });
        return sorted;
    }

    private List<Track> suggestionTracks() {
        List<Track> resultado = new ArrayList<>();
        for (Track track : tracks) {
            if (currentTrack != null && track.id == currentTrack.id) continue;
            if (!resultado.contains(track)) resultado.add(track);
            if (resultado.size() >= 8) break;
        }
        return resultado;
    }

    private int countFavorites() {
        int count = 0;
        for (Track t : tracks) if (isFavorite(t)) count++;
        return count;
    }

    private boolean isFavorite(Track track) {
        return getSharedPreferences("auren_player", MODE_PRIVATE).getBoolean("fav_" + track.id, false);
    }

    private void setFavorite(Track track, boolean value) {
        getSharedPreferences("auren_player", MODE_PRIVATE).edit().putBoolean("fav_" + track.id, value).apply();
    }

    private void showTrackMenu(View anchor, Track track) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Reproduzir");
        popup.getMenu().add("Reproduzir a seguir");
        popup.getMenu().add("Adicionar à fila");
        popup.getMenu().add("Adicionar à playlist");
        popup.getMenu().add(isFavorite(track) ? "Remover dos favoritos" : "Adicionar aos favoritos");
        popup.getMenu().add("Enviar");
        popup.getMenu().add("Detalhes");
        popup.setOnMenuItemClickListener(item -> {
            String action = item.getTitle().toString();
            if (action.equals("Reproduzir")) {
                play(track);
                openNowPlaying();
            } else if (action.equals("Reproduzir a seguir")) {
                addTrackToQueue(track, true);
                Toast.makeText(this, "Adicionado para reproduzir a seguir.", Toast.LENGTH_SHORT).show();
            } else if (action.equals("Adicionar à fila")) {
                addTrackToQueue(track, false);
                Toast.makeText(this, "Adicionado à fila.", Toast.LENGTH_SHORT).show();
            } else if (action.equals("Adicionar à playlist")) {
                showAddToPlaylistDialog(track);
            } else if (action.contains("favoritos")) {
                setFavorite(track, !isFavorite(track));
                showHome();
            } else if (action.equals("Enviar")) {
                shareTrack(track);
            } else if (action.equals("Detalhes")) {
                showTrackDetails(track);
            }
            return true;
        });
        popup.show();
    }
    private void addTrackToQueue(Track track, boolean next) {
        if (track == null) return;
        playQueue.remove(track);
        if (next) playQueue.add(0, track);
        else playQueue.add(track);
    }






    private void showCreatePlaylistDialog() {
        EditText input = new EditText(this);
        input.setHint("Nome da playlist");
        input.setSingleLine(true);
        input.setPadding(dp(18), dp(12), dp(18), dp(12));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Nova playlist")
                .setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Criar", null)
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                input.setError("Digite um nome");
                return;
            }
            savePlaylist(name);
            dialog.dismiss();
            showPlaylists();
            Toast.makeText(this, "Playlist criada.", Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }

    private void savePlaylist(String name) {
        android.content.SharedPreferences prefs = getSharedPreferences("auren_player", MODE_PRIVATE);
        Set<String> names = new HashSet<>(prefs.getStringSet("playlist_names", new HashSet<>()));
        names.add(name);
        prefs.edit().putStringSet("playlist_names", names)
                .putString("playlist_" + name, "")
                .apply();
    }

    private int playlistTrackCount(String name) {
        String value = getSharedPreferences("auren_player", MODE_PRIVATE)
                .getString("playlist_" + name, "");
        if (value == null || value.trim().isEmpty()) return 0;
        return value.split(",").length;
    }

    private void showAddToPlaylistDialog(Track track) {
        Set<String> names = getSharedPreferences("auren_player", MODE_PRIVATE)
                .getStringSet("playlist_names", new HashSet<>());
        if (names.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Nenhuma playlist")
                    .setMessage("Crie uma playlist primeiro para adicionar esta música.")
                    .setNegativeButton("Fechar", null)
                    .setPositiveButton("Criar playlist", (d, w) -> showCreatePlaylistDialog())
                    .show();
            return;
        }
        String[] choices = names.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Adicionar à playlist")
                .setItems(choices, (d, which) -> addTrackToPlaylist(track, choices[which]))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void addTrackToPlaylist(Track track, String name) {
        android.content.SharedPreferences prefs = getSharedPreferences("auren_player", MODE_PRIVATE);
        String current = prefs.getString("playlist_" + name, "");
        String id = String.valueOf(track.id);
        List<String> ids = new ArrayList<>();
        if (current != null && !current.trim().isEmpty()) {
            for (String value : current.split(",")) if (!value.isEmpty()) ids.add(value);
        }
        if (!ids.contains(id)) ids.add(id);
        prefs.edit().putString("playlist_" + name, android.text.TextUtils.join(",", ids)).apply();
        Toast.makeText(this, "Adicionado à playlist " + name + ".", Toast.LENGTH_SHORT).show();
        showPlaylists();
    }

    private void shareTrack(Track track) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, safeTitle(track) + " — " + safeArtist(track));
        startActivity(Intent.createChooser(intent, "Enviar música"));
    }

    private void showTrackDetails(Track track) {
        String details = "Título: " + safeTitle(track)
                + "\nArtista: " + safeArtist(track)
                + "\nDuração: " + formatTime(track.durationMs)
                + "\nID: " + track.id;
        new AlertDialog.Builder(this)
                .setTitle("Detalhes da música")
                .setMessage(details)
                .setPositiveButton("Fechar", null)
                .show();
    }

    private View customPlaylistCard(String name) {
        List<Track> items = getPlaylistTracks(name);
        long total = playlistDuration(items);

        LinearLayout card = rounded(Color.WHITE, 18);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setElevation(dp(2));

        LinearLayout cover = rounded(ThemeManager.resolve(this, R.color.accent_soft), 16);
        cover.setGravity(Gravity.CENTER);
        TextView icon = text("♫", 28, R.color.auren_primary);
        icon.setGravity(Gravity.CENTER);
        cover.addView(icon, new LinearLayout.LayoutParams(dp(62), dp(62)));
        card.addView(cover, new LinearLayout.LayoutParams(dp(62), dp(62)));

        LinearLayout info = column();
        info.setPadding(dp(12), 0, dp(8), 0);
        TextView title = text(name, 16, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(title);
        info.addView(text(items.size() + " músicas • " + formatDuration(total), 12, R.color.text_secondary), margins(0, 4, 0, 0));
        info.addView(text("Toque para abrir", 11, R.color.auren_primary), margins(0, 4, 0, 0));
        card.addView(info, new LinearLayout.LayoutParams(0, dp(78), 1));

        TextView arrow = text("›", 28, R.color.text_secondary);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(30), dp(70)));
        card.setOnClickListener(v -> showPlaylistPage(name));
        return card;
    }

    private void showPlaylistPage(String name) {
        setActiveTab(playlistTab);
        pageContainer.removeAllViews();

        LinearLayout content = column();
        content.setPadding(dp(20), dp(14), dp(20), dp(18));

        LinearLayout header = row();
        ImageButton back = iconButton(android.R.drawable.ic_media_previous, "Voltar para playlists");
        back.setOnClickListener(v -> showPlaylists());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout titles = column();
        TextView eyebrow = text("PLAYLIST", 10, R.color.auren_primary);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titles.addView(eyebrow);
        TextView title = text(name, 25, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(title, margins(0, 2, 0, 0));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(header);

        List<Track> items = getPlaylistTracks(name);
        long total = playlistDuration(items);
        LinearLayout summary = rounded(ThemeManager.resolve(this, R.color.accent_soft), 18);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.setPadding(dp(14), dp(10), dp(14), dp(10));
        TextView count = text(items.size() + " músicas", 13, R.color.text_primary);
        count.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        summary.addView(count, new LinearLayout.LayoutParams(0, dp(40), 1));
        TextView duration = text(formatDuration(total), 13, R.color.auren_primary);
        duration.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        duration.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        summary.addView(duration, new LinearLayout.LayoutParams(dp(90), dp(40)));
        content.addView(summary, margins(0, 14, 0, 12));

        if (items.isEmpty()) {
            content.addView(emptyCard("Esta playlist está vazia. Use ⋮ ao lado de uma música e escolha Adicionar à playlist."));
        } else {
            TextView songs = text("MÚSICAS", 11, R.color.auren_primary);
            songs.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            content.addView(songs, margins(0, 4, 0, 8));
            ScrollView scroll = new ScrollView(this);
            LinearLayout list = column();
            for (Track track : items) list.addView(trackRow(track, 0));
            scroll.addView(list);
            content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        pageContainer.addView(content, new LinearLayout.LayoutParams(-1, -1));
    }

    private List<Track> getPlaylistTracks(String name) {
        List<Track> resultado = new ArrayList<>();
        String value = getSharedPreferences("auren_player", MODE_PRIVATE)
                .getString("playlist_" + name, "");
        if (value == null || value.trim().isEmpty()) return resultado;
        for (String idValue : value.split(",")) {
            try {
                long id = Long.parseLong(idValue.trim());
                for (Track track : tracks) {
                    if (track.id == id) {
                        resultado.add(track);
                        break;
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return resultado;
    }

    private long playlistDuration(List<Track> items) {
        long total = 0;
        for (Track track : items) total += Math.max(0, track.durationMs);
        return total;
    }

    private String formatDuration(long ms) {
        if (ms <= 0) return "0:00";
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        minutes %= 60;
        seconds %= 60;
        if (hours > 0) return hours + ":" + String.format(Locale.US, "%02d:%02d", minutes, seconds);
        return minutes + ":" + String.format(Locale.US, "%02d", seconds);
    }

    private void showAchievements() {
        pageContainer.removeAllViews();
        LinearLayout content = column();
        content.setPadding(dp(20), dp(16), dp(20), dp(22));

        TextView eyebrow = text("AUREN JOURNEY", 11, R.color.auren_primary);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView title = text("Auren Journey", 28, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(eyebrow);
        content.addView(title, margins(0, 3, 0, 8));

        AchievementManager.Stats stats = AchievementManager.stats(this);

        LinearLayout metrics = row();
        metrics.addView(statCard("Hoje", stats.today + " rep.", "Reproduções"), new LinearLayout.LayoutParams(0, dp(92), 1));
        metrics.addView(statCard("Semana", stats.week + " rep.", "Reproduções"), margins(8, 0, 0, 0));
        metrics.addView(statCard("Mês", stats.month + " rep.", "Reproduções"), margins(8, 0, 0, 0));
        content.addView(metrics);

        LinearLayout records = row();
        records.addView(statCard("Recorde diário", String.valueOf(stats.bestDay), "melhor dia"), new LinearLayout.LayoutParams(0, dp(92), 1));
        records.addView(statCard("Recorde semanal", String.valueOf(stats.bestWeek), "melhor semana"), margins(8, 0, 0, 0));
        records.addView(statCard("Recorde mensal", String.valueOf(stats.bestMonth), "melhor mês"), margins(8, 0, 0, 0));
        content.addView(records, margins(0, 8, 0, 0));

        AurenAnalytics.Summary journey = AurenAnalytics.summary(this);
        LinearLayout journeyMetrics = row();
        journeyMetrics.addView(statCard("TEMPO HOJE", AurenAnalytics.formatDuration(journey.todayMs), "minutos ouvidos"),
                new LinearLayout.LayoutParams(0, dp(92), 1));
        journeyMetrics.addView(statCard("SEQUÊNCIA", journey.streakCurrent + " dia(s)", "dias seguidos"),
                margins(8, 0, 0, 0));
        content.addView(journeyMetrics, margins(0, 8, 0, 0));

        AurenAnalytics.Summary levelSummary = AurenAnalytics.summary(this);
        int journeyLevel = 1 + (levelSummary.totalPlays / 25);
        int levelProgress = levelSummary.totalPlays % 25;
        content.addView(featureMetricCard("NÍVEL " + journeyLevel,
                levelProgress + "/25",
                "reproduções para o próximo nível"), margins(0, 8, 0, 0));

        content.addView(text(
                stats.totalPlays + " reproduções • " + stats.uniqueTracks + " músicas diferentes • "
                        + stats.activeDays + " dias ativos", 12, R.color.text_secondary),
                margins(2, 10, 2, 4));

        LinearLayout achievementHeader = row();
        TextView achievementLabel = text("CONQUISTAS", 11, R.color.auren_primary);
        achievementLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        achievementHeader.addView(achievementLabel, new LinearLayout.LayoutParams(0, dp(40), 1));
        TextView unlocked = text(stats.unlocked + "/" + stats.totalAchievements + " desbloqueadas", 11, R.color.text_secondary);
        unlocked.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        achievementHeader.addView(unlocked, new LinearLayout.LayoutParams(-2, dp(40)));
        content.addView(achievementHeader, margins(0, 14, 0, 2));

        for (AchievementManager.Badge badge : AchievementManager.badges(this)) {
            content.addView(achievementRow(badge), margins(0, 5, 0, 5));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        pageContainer.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    private View statCard(String label, String value, String hint) {
        LinearLayout card = rounded(ThemeManager.resolve(this, R.color.accent_soft), 18);
        card.setPadding(dp(10), dp(10), dp(10), dp(8));
        TextView l = text(label, 10, R.color.text_secondary);
        l.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView v = text(value, 20, R.color.auren_primary);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView h = text(hint, 10, R.color.text_secondary);
        card.addView(l);
        card.addView(v, margins(0, 2, 0, 0));
        card.addView(h, margins(0, 1, 0, 0));
        return card;
    }

    private View achievementRow(AchievementManager.Badge badge) {
        int bg = badge.unlocked ? ThemeManager.resolve(this, R.color.accent_soft) : ThemeManager.card(this);
        LinearLayout card = rounded(bg, 18);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView icon = text(badge.unlocked ? "✓" : "○", 23,
                badge.unlocked ? R.color.auren_primary : R.color.text_secondary);
        icon.setGravity(Gravity.CENTER);
        card.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(54)));

        LinearLayout info = column();
        TextView name = text(badge.title, 14, R.color.text_primary);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        info.addView(name);
        info.addView(text(badge.description, 11, R.color.text_secondary), margins(0, 2, 0, 0));
        card.addView(info, new LinearLayout.LayoutParams(0, dp(54), 1));

        TextView goal = text(badge.requirement, 10,
                badge.unlocked ? R.color.auren_primary : R.color.text_secondary);
        goal.setGravity(Gravity.CENTER);
        goal.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(goal, new LinearLayout.LayoutParams(dp(76), dp(54)));
        if (badge.unlocked) {
            card.setAlpha(0f);
            card.setScaleX(0.97f);
            card.setScaleY(0.97f);
            card.post(() -> card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start());
        }
        return card;
    }

    private void showMostPlayed() {
        showLibrary();
        Toast.makeText(this, "Mais tocadas is ranked on the Início screen.", Toast.LENGTH_SHORT).show();
    }

    private void showLibraryIfNeeded() {
        // Favoritar state is persisted; the current page remains unchanged.
    }

    private int progressValue() {
        if (player == null || player.getDuration() <= 0) return 0;
        return (int) Math.min(1000, (player.getCurrentPosition() * 1000L) / player.getDuration());
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "0:00";
        long sec = ms / 1000;
        return (sec / 60) + ":" + String.format(Locale.US, "%02d", sec % 60);
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout row() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private GradientDrawable roundDrawable(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        return bg;
    }

    private LinearLayout rounded(int color, int radius) {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setBackground(roundDrawable(color, radius));
        return view;
    }

    private ImageView artwork(int size) {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ThemeManager.resolve(this, R.color.accent_soft));
        bg.setCornerRadius(dp(15));
        image.setBackground(bg);
        image.setImageResource(android.R.drawable.ic_media_play);
        image.setPadding(dp(12), dp(12), dp(12), dp(12));
        return image;
    }

    private ImageButton iconButton(int icon, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setContentDescription(description);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        DrawableCompat.setTint(button.getDrawable(), ThemeManager.resolve(this, R.color.text_primary));
        return button;
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ThemeManager.resolve(this, color));
        return view;
    }

    private LinearLayout.LayoutParams margins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
    @Override
    protected void onDestroy() {
        handler.removeCallbacks(progressUpdater);
        if (nowPlayingDialog != null && nowPlayingDialog.isShowing()) {
            nowPlayingDialog.dismiss();
        }
        if (player != null) {
            player.removeListener(playbackListener);
        }
        if (mediaController != null) {
            mediaController.release();
            mediaController = null;
        }
        if (controllerFuture != null && !controllerFuture.isDone()) {
            controllerFuture.cancel(false);
        }
        super.onDestroy();
    }


    public static final class TrackInfo {
        public final long id;
        public final String title;
        public final String artist;
        public final String album;
        public final String genre;
        public TrackInfo(long id, String title, String artist, String album, String genre) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.genre = genre;
        }
    }

    private static class Track {
        final long id;
        final String title;
        final String artist;
        final long albumId;
        final String album;
        final String genre;
        final long durationMs;

        Track(long id, String title, String artist, long albumId, String album, String genre, long durationMs) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.albumId = albumId;
            this.album = album;
            this.genre = genre;
            this.durationMs = durationMs;
        }

        Uri albumArtUri() {
            return ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId);
        }

        @Override public boolean equals(Object obj) {
            return obj instanceof Track && ((Track) obj).id == id;
        }

        @Override public int hashCode() {
            return Long.valueOf(id).hashCode();
        }
    }

}
