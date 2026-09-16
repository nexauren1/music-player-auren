package com.auren.musicplayer;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
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
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
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
import androidx.media3.exoplayer.ExoPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends ComponentActivity {
    private static final int MUSIC_PERMISSION = 41;
    private final List<Track> tracks = new ArrayList<>();
    private final List<Track> recentTracks = new ArrayList<>();
    private final Map<Long, Integer> playCounts = new HashMap<>();
    private final Handler handler = new Handler();

    private ExoPlayer player;
    private Track currentTrack;
    private ImageButton miniPlay;
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

    private final Runnable progressUpdater = new Runnable() {
        @Override public void run() {
            if (nowPlayingDialog != null && nowPlayingDialog.isShowing()) updateNowPlayingProgress();
            handler.postDelayed(this, 500);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        player = new ExoPlayer.Builder(this).build();
        buildShell();
        requestMusicPermission();
        handler.post(progressUpdater);
    }

    private void buildShell() {
        LinearLayout root = column();
        root.setBackgroundColor(getColor(R.color.surface));

        root.addView(buildTopBar());

        pageContainer = column();
        root.addView(pageContainer, new LinearLayout.LayoutParams(-1, 0, 1));

        miniContainer = buildMiniPlayer();
        root.addView(miniContainer, margins(12, 4, 12, 4));
        root.addView(buildBottomNavigation());
        setContentView(root);
        showHome();
    }

    private View buildTopBar() {
        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(6), dp(10), dp(6));
        bar.setBackgroundColor(getColor(R.color.auren_primary));
        bar.setElevation(dp(4));

        ImageButton menu = iconButton(android.R.drawable.ic_menu_sort_by_size, "Open menu");
        menu.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.auren_primary)));
        DrawableCompat.setTint(menu.getDrawable(), Color.WHITE);
        menu.setOnClickListener(v -> showAppMenu(menu));
        bar.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = text("Auren Music", 19, android.R.color.white);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));

        ImageButton search = iconButton(android.R.drawable.ic_menu_search, "Search music");
        search.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.auren_primary)));
        DrawableCompat.setTint(search.getDrawable(), Color.WHITE);
        search.setOnClickListener(v -> showSearchDialog());
        bar.addView(search, new LinearLayout.LayoutParams(dp(48), dp(48)));

        return bar;
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
        TextView small = text("AUREN MUSIC", 11, R.color.auren_primary);
        small.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView title = text("Good music,\nanytime.", 30, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brandBox.addView(small);
        brandBox.addView(title, margins(0, 4, 0, 0));
        header.addView(brandBox, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(header);

        LinearLayout quick = row();
        quick.setGravity(Gravity.CENTER_VERTICAL);
        quick.addView(actionCard("▶", "Shuffle", v -> shufflePlay()), new LinearLayout.LayoutParams(0, dp(82), 1));
        quick.addView(actionCard("♥", "Favorites", v -> showLibrary(true)), margins(10, 0, 0, 0));
        content.addView(quick, margins(0, 18, 0, 0));

        addSectionHeader(content, "Recently played", "See all", v -> showLibrary());
        if (recentTracks.isEmpty()) {
            content.addView(emptyCard("Your recently played songs will appear here."));
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

        addSectionHeader(content, "Most played", "Your favorites", v -> showMostPlayed());
        List<Track> mostPlayed = sortedByPlayCount();
        if (mostPlayed.isEmpty() || playCounts.getOrDefault(mostPlayed.get(0).id, 0) == 0) {
            content.addView(emptyCard("Play some songs and your most played list will grow."));
        } else {
            for (int i = 0; i < Math.min(4, mostPlayed.size()); i++) {
                content.addView(trackRow(mostPlayed.get(i), i + 1));
            }
        }

        addSectionHeader(content, "Suggestions for you", "Refresh", v -> showHome());
        List<Track> suggestions = suggestionTracks();
        if (suggestions.isEmpty()) {
            content.addView(emptyCard("Suggestions will appear after your library is loaded."));
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

        TextView eyebrow = text("YOUR LIBRARY", 11, R.color.auren_primary);
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
        TextView eyebrow = text("YOUR FAVORITES", 11, R.color.auren_primary);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView title = text("Favoritos", 30, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titles.addView(eyebrow);
        titles.addView(title, margins(0, 3, 0, 0));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton search = iconButton(android.R.drawable.ic_menu_search, "Search music");
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
        LinearLayout card = rounded(Color.WHITE, 20);
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

    private void showRecent() {
        setActiveTab(libraryTab);
        pageContainer.removeAllViews();
        LinearLayout content = column();
        content.setPadding(dp(20), dp(16), dp(20), dp(18));
        TextView eyebrow = text("YOUR HISTORY", 11, R.color.auren_primary);
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
        TextView eyebrow = text("FOR YOU", 11, R.color.auren_primary);
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

        TextView eyebrow = text("YOUR COLLECTION", 11, R.color.auren_primary);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView title = text("Playlists", 30, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(eyebrow);
        content.addView(title, margins(0, 3, 0, 0));

        LinearLayout create = rounded(0xFFEEECFF, 20);
        create.setGravity(Gravity.CENTER_VERTICAL);
        TextView plus = text("+", 26, R.color.auren_primary);
        plus.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        create.addView(plus, margins(16, 10, 8, 10));
        LinearLayout createText = column();
        TextView ct = text("Create playlist", 15, R.color.text_primary);
        ct.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        createText.addView(ct);
        createText.addView(text("Organize music your way", 12, R.color.text_secondary));
        create.addView(createText, margins(0, 10, 12, 10));
        create.setOnClickListener(v -> Toast.makeText(this, "Playlist creation will be connected to saved storage next.", Toast.LENGTH_SHORT).show());
        content.addView(create, margins(0, 18, 0, 12));

        content.addView(playlistCard("Liked songs", "Songs you marked as favorite", countFavorites(), true));
        content.addView(playlistCard("Recently played", "Your latest listening history", recentTracks.size(), false), margins(0, 10, 0, 0));
        content.addView(playlistCard("Most played", "The tracks you play the most", Math.min(10, tracks.size()), false), margins(0, 10, 0, 0));
        pageContainer.addView(content);
    }

    private View buildBottomNavigation() {
        LinearLayout nav = row();
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(5), dp(8), dp(8));
        nav.setBackgroundColor(Color.WHITE);
        homeTab = navItem("⌂", "Home", v -> showHome());
        libraryTab = navItem("♫", "Library", v -> showLibrary());
        playlistTab = navItem("▤", "Playlists", v -> showPlaylists());
        nav.addView(homeTab, new LinearLayout.LayoutParams(0, dp(58), 1));
        nav.addView(libraryTab, new LinearLayout.LayoutParams(0, dp(58), 1));
        nav.addView(playlistTab, new LinearLayout.LayoutParams(0, dp(58), 1));
        return nav;
    }

    private TextView navItem(String icon, String label, View.OnClickListener listener) {
        TextView v = text(icon + "\n" + label, 12, R.color.text_secondary);
        v.setGravity(Gravity.CENTER);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setOnClickListener(listener);
        return v;
    }

    private void setActiveTab(TextView active) {
        if (homeTab == null) return;
        homeTab.setTextColor(getColor(R.color.text_secondary));
        libraryTab.setTextColor(getColor(R.color.text_secondary));
        playlistTab.setTextColor(getColor(R.color.text_secondary));
        active.setTextColor(getColor(R.color.auren_primary));
    }

    private void showAppMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Início");
        menu.getMenu().add("Biblioteca");
        menu.getMenu().add("Favoritos");
        menu.getMenu().add("Playlists");
        menu.getMenu().add("Mais tocadas");
        menu.getMenu().add("Configurações");
        menu.getMenu().add("Sobre Auren");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.equals("Início")) showHome();
            else if (title.equals("Biblioteca")) showLibrary();
            else if (title.equals("Favoritos")) showLibrary(true);
            else if (title.equals("Playlists")) showPlaylists();
            else if (title.equals("Mais tocadas")) showMostPlayed();
            else if (title.equals("Configurações")) startActivity(new Intent(this, SettingsActivity.class));
            else if (title.equals("Sobre Auren")) showAboutDialog();
            return true;
        });
        menu.show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Auren Music")
                .setMessage("A clean, modern music player built around your local library.\n\nVersion " + BuildConfig.VERSION_NAME + "\n\nMusic that moves with you.")
                .setPositiveButton("Close", null)
                .show();
    }

    private void showSearchDialog() {
        EditText input = new EditText(this);
        input.setHint("Song or artist");
        input.setSingleLine(true);
        int pad = dp(18);
        input.setPadding(pad, pad, pad, pad);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Search your music")
                .setView(input)
                .setNegativeButton("Close", null)
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
            Toast.makeText(this, "No songs found.", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout list = column();
        list.setPadding(dp(18), dp(10), dp(18), dp(18));
        TextView resultTitle = text(matches.size() + " result" + (matches.size() == 1 ? "" : "s"), 13, R.color.auren_primary);
        resultTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        list.addView(resultTitle, margins(0, 4, 0, 8));
        for (Track track : matches) list.addView(trackRow(track, 0));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        new AlertDialog.Builder(this)
                .setTitle("Search results")
                .setView(scroll)
                .setPositiveButton("Done", null)
                .show();
    }

    private LinearLayout buildMiniPlayer() {
        LinearLayout mini = rounded(0xFFFFFFFF, 20);
        mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setPadding(dp(8), dp(7), dp(8), dp(7));
        mini.setElevation(dp(7));

        miniArt = artwork(48);
        mini.addView(miniArt, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout info = column();
        info.setGravity(Gravity.CENTER_VERTICAL);
        miniTitle = text("Nothing playing", 14, R.color.text_primary);
        miniTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        miniArtist = text("Choose a song to start", 12, R.color.text_secondary);
        info.addView(miniTitle);
        info.addView(miniArtist, margins(0, 2, 0, 0));
        mini.addView(info, new LinearLayout.LayoutParams(0, dp(52), 1));

        miniPlay = iconButton(android.R.drawable.ic_media_play, "Play or pause");
        miniPlay.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.auren_primary)));
        DrawableCompat.setTint(miniPlay.getDrawable(), Color.WHITE);
        miniPlay.setOnClickListener(v -> togglePlayback());
        mini.addView(miniPlay, new LinearLayout.LayoutParams(dp(48), dp(48)));

        mini.setOnClickListener(v -> openNowPlaying());
        miniArt.setOnClickListener(v -> openNowPlaying());
        miniTitle.setOnClickListener(v -> openNowPlaying());
        miniArtist.setOnClickListener(v -> openNowPlaying());
        return mini;
    }

    private void openNowPlaying() {
        if (currentTrack == null) {
            Toast.makeText(this, "Choose a song first", Toast.LENGTH_SHORT).show();
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
        scroll.setBackgroundColor(getColor(R.color.surface));
        LinearLayout root = column();
        root.setPadding(dp(20), dp(16), dp(20), dp(22));

        LinearLayout top = row();
        ImageButton close = iconButton(android.R.drawable.ic_menu_close_clear_cancel, "Close player");
        close.setOnClickListener(v -> closeNowPlaying());
        top.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView label = text("NOW PLAYING", 12, R.color.auren_primary);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(label, new LinearLayout.LayoutParams(0, dp(48), 1));
        ImageButton more = iconButton(android.R.drawable.ic_menu_more, "More options");
        more.setOnClickListener(v -> Toast.makeText(this, "More player options coming soon.", Toast.LENGTH_SHORT).show());
        top.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(top);

        ImageView hero = artwork(1);
        hero.setImageURI(currentTrack.albumArtUri());
        if (hero.getDrawable() == null) hero.setImageResource(android.R.drawable.ic_media_play);
        hero.setPadding(0, 0, 0, 0);
        hero.setElevation(dp(10));
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

        ImageButton favorite = iconButton(isFavorite(currentTrack) ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off, "Favorite");
        favorite.setOnClickListener(v -> {
            setFavorite(currentTrack, !isFavorite(currentTrack));
            favorite.setImageResource(isFavorite(currentTrack) ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
            showLibraryIfNeeded();
        });
        titleRow.addView(favorite, new LinearLayout.LayoutParams(dp(52), dp(62)));
        root.addView(titleRow);

        SeekBar seek = new SeekBar(this);
        seek.setMax(1000);
        seek.setProgress(progressValue());
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                userDragging = fromUser;
                if (fromUser && player.getDuration() > 0) player.seekTo(player.getDuration() * progress / 1000L);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { userDragging = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) { userDragging = false; }
        });
        root.addView(seek, margins(0, 12, 0, 0));

        LinearLayout times = row();
        TextView position = text(formatTime(player.getCurrentPosition()), 11, R.color.text_secondary);
        TextView duration = text(formatTime(player.getDuration()), 11, R.color.text_secondary);
        duration.setGravity(Gravity.RIGHT);
        times.addView(position, new LinearLayout.LayoutParams(0, dp(22), 1));
        times.addView(duration, new LinearLayout.LayoutParams(0, dp(22), 1));
        root.addView(times);

        LinearLayout controls = row();
        controls.setGravity(Gravity.CENTER);
        ImageButton previous = iconButton(android.R.drawable.ic_media_previous, "Previous");
        previous.setOnClickListener(v -> previousTrackInPlayer());
        controls.addView(previous, new LinearLayout.LayoutParams(dp(64), dp(64)));
        ImageButton playPause = iconButton(player.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, "Play or pause");
        playPause.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.auren_primary)));
        DrawableCompat.setTint(playPause.getDrawable(), Color.WHITE);
        playPause.setPadding(dp(18), dp(18), dp(18), dp(18));
        playPause.setOnClickListener(v -> {
            togglePlayback();
            playPause.setImageResource(player.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
            DrawableCompat.setTint(playPause.getDrawable(), Color.WHITE);
        });
        controls.addView(playPause, new LinearLayout.LayoutParams(dp(76), dp(76)));
        ImageButton next = iconButton(android.R.drawable.ic_media_next, "Next");
        next.setOnClickListener(v -> nextTrackInPlayer());
        controls.addView(next, new LinearLayout.LayoutParams(dp(64), dp(64)));
        root.addView(controls, margins(0, 8, 0, 0));

        LinearLayout extras = row();
        extras.setGravity(Gravity.CENTER);
        extras.addView(playerAction("↶", "Replay", v -> player.seekTo(0)), new LinearLayout.LayoutParams(0, dp(58), 1));
        extras.addView(playerAction("⇄", "Shuffle", v -> shufflePlay()), new LinearLayout.LayoutParams(0, dp(58), 1));
        extras.addView(playerAction("☰", "Queue", v -> Toast.makeText(this, tracks.size() + " songs in library", Toast.LENGTH_SHORT).show()), new LinearLayout.LayoutParams(0, dp(58), 1));
        root.addView(extras, margins(0, 8, 0, 0));

        TextView hint = text("Auren • Music that moves with you", 11, R.color.text_secondary);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, margins(0, 10, 0, 0));

        scroll.addView(root);
        return scroll;
    }

    private void closeNowPlaying() {
        if (nowPlayingDialog != null && nowPlayingDialog.isShowing()) nowPlayingDialog.dismiss();
    }

    private void updateNowPlayingProgress() {
        // The dialog is intentionally rebuilt only for navigation; progress is kept by the player.
    }

    private void previousTrackInPlayer() {
        if (tracks.isEmpty()) return;
        int index = currentTrack == null ? 0 : tracks.indexOf(currentTrack);
        if (index < 0) index = 0;
        play(tracks.get(index <= 0 ? tracks.size() - 1 : index - 1));
        refreshNowPlaying();
    }

    private void nextTrackInPlayer() {
        if (tracks.isEmpty()) return;
        int index = currentTrack == null ? -1 : tracks.indexOf(currentTrack);
        play(tracks.get(index >= tracks.size() - 1 ? 0 : index + 1));
        refreshNowPlaying();
    }

    private void refreshNowPlaying() {
        closeNowPlaying();
        openNowPlaying();
    }

    private void play(Track track) {
        if (track == null || player == null) return;
        currentTrack = track;
        int count = playCounts.getOrDefault(track.id, 0);
        playCounts.put(track.id, count + 1);
        recentTracks.remove(track);
        recentTracks.add(0, track);
        while (recentTracks.size() > 20) recentTracks.remove(recentTracks.size() - 1);

        Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id);
        player.setMediaItem(MediaItem.fromUri(uri));
        player.prepare();
        player.play();
        updateMiniPlayer();
    }

    private void togglePlayback() {
        if (player == null) return;
        if (player.isPlaying()) player.pause();
        else if (player.getMediaItemCount() > 0) player.play();
        updateMiniPlayer();
    }

    private void updateMiniPlayer() {
        if (currentTrack == null) return;
        miniTitle.setText(safeTitle(currentTrack));
        miniArtist.setText(safeArtist(currentTrack));
        miniArt.setImageURI(currentTrack.albumArtUri());
        if (miniArt.getDrawable() == null) miniArt.setImageResource(android.R.drawable.ic_media_play);
        miniPlay.setImageResource(player.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        if (homeTab != null && pageContainer != null) {
            // Keep the current page; the mini player must never trap the user.
        }
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

    private void requestMusicPermission() {
        String permission = android.os.Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) loadMusic();
        else ActivityCompat.requestPermissions(this, new String[]{permission}, MUSIC_PERMISSION);
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == MUSIC_PERMISSION && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) loadMusic();
    }

    private void loadMusic() {
        tracks.clear();
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM_ID
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
                    tracks.add(new Track(
                            cursor.getLong(0),
                            cursor.getString(1),
                            cursor.getString(2),
                            cursor.getLong(3)));
                }
            }
        }
        if (pageContainer != null) showHome();
    }

    private View actionCard(String icon, String label, View.OnClickListener listener) {
        LinearLayout card = rounded(0xFFEEECFF, 20);
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
        card.setOnClickListener(v -> { play(track); openNowPlaying(); });
        return card;
    }

    private View trackRow(Track track, int number) {
        LinearLayout row = rounded(0xFFFFFFFF, 16);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(7), dp(7), dp(8), dp(7));
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
        TextView artist = text(safeArtist(track), 12, R.color.text_secondary);
        artist.setMaxLines(1);
        info.addView(title);
        info.addView(artist, margins(0, 2, 0, 0));
        row.addView(info, new LinearLayout.LayoutParams(0, dp(56), 1));
        if (number > 0) {
            TextView plays = text(playCounts.getOrDefault(track.id, 0) + " plays", 10, R.color.text_secondary);
            plays.setGravity(Gravity.CENTER);
            row.addView(plays, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
        ImageButton play = iconButton(android.R.drawable.ic_media_play, "Play song");
        play.setOnClickListener(v -> { play(track); openNowPlaying(); });
        row.addView(play, new LinearLayout.LayoutParams(dp(44), dp(48)));
        row.setOnClickListener(v -> { play(track); openNowPlaying(); });
        return row;
    }

    private View playlistCard(String title, String subtitle, int count, boolean favorite) {
        LinearLayout card = rounded(favorite ? 0xFFEEECFF : 0xFFFFFFFF, 18);
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
        LinearLayout c = rounded(active ? 0xFFEEECFF : 0xFFF5F5F7, 18);
        TextView t = text(label, 12, active ? R.color.auren_primary : R.color.text_secondary);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        c.addView(t, margins(14, 7, 14, 7));
        c.setOnClickListener(listener);
        return c;
    }

    private View emptyCard(String message) {
        LinearLayout card = rounded(0xFFF5F5F7, 18);
        TextView t = text(message, 13, R.color.text_secondary);
        t.setGravity(Gravity.CENTER);
        card.addView(t, margins(16, 18, 16, 18));
        return card;
    }

    private String safeTitle(Track track) {
        return track == null || track.title == null || track.title.trim().isEmpty() ? "Untitled" : track.title;
    }

    private String safeArtist(Track track) {
        return track == null || track.artist == null || track.artist.trim().isEmpty() ? "Unknown artist" : track.artist;
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
        List<Track> result = new ArrayList<>();
        for (Track track : tracks) {
            if (currentTrack != null && track.id == currentTrack.id) continue;
            if (!result.contains(track)) result.add(track);
            if (result.size() >= 8) break;
        }
        return result;
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

    private void showMostPlayed() {
        showLibrary();
        Toast.makeText(this, "Most played is ranked on the Home screen.", Toast.LENGTH_SHORT).show();
    }

    private void showLibraryIfNeeded() {
        // Favorite state is persisted; the current page remains unchanged.
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

    private LinearLayout rounded(int color, int radius) {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        view.setBackground(bg);
        return view;
    }

    private ImageView artwork(int size) {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.accent_soft));
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
        DrawableCompat.setTint(button.getDrawable(), getColor(R.color.text_primary));
        return button;
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(getColor(color));
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

    @Override protected void onDestroy() {
        handler.removeCallbacks(progressUpdater);
        if (nowPlayingDialog != null && nowPlayingDialog.isShowing()) nowPlayingDialog.dismiss();
        if (player != null) player.release();
        super.onDestroy();
    }

    private static class Track {
        final long id;
        final String title;
        final String artist;
        final long albumId;

        Track(long id, String title, String artist, long albumId) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.albumId = albumId;
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
