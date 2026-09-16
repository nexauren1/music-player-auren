package com.auren.musicplayer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AurenHomeV4Activity extends Activity implements PlayerManager.Listener {
    private static final int REQUEST_AUDIO = 200;
    private static final int REQUEST_NOTIFICATIONS = 201;
    private static final int GREEN = Color.rgb(26, 142, 55);
    private static final int GREEN_DARK = Color.rgb(18, 104, 40);
    private static final int BG = Color.rgb(246, 248, 247);
    private static final int CARD = Color.WHITE;
    private static final int TEXT = Color.rgb(24, 28, 26);
    private static final int MUTED = Color.rgb(103, 111, 106);

    private final List<PlayerManager.Song> songs = new ArrayList<>();
    private final List<PlayerManager.Song> visible = new ArrayList<>();
    private final Map<String, Bitmap> artworkCache = new HashMap<>();
    private final ExecutorService artworkExecutor = Executors.newFixedThreadPool(3);

    private SharedPreferences prefs;
    private LinearLayout content;
    private LinearLayout miniPlayer;
    private ImageView miniArtwork;
    private TextView miniTitle;
    private TextView miniArtist;
    private TextView miniPlay;
    private TextView libraryCount;
    private EditText search;
    private String selectedTab = "Músicas";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("auren", MODE_PRIVATE);
        buildUi();
        PlayerManager.setListener(this);
        requestAudioPermission();
        requestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateMiniPlayer();
    }

    @Override
    protected void onDestroy() {
        PlayerManager.setListener(null);
        artworkExecutor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = column(BG);
        root.setFitsSystemWindows(true);

        LinearLayout header = column(GREEN);
        header.setPadding(dp(18), dp(18), dp(18), dp(14));

        LinearLayout top = row();
        TextView menu = action("☰", Color.WHITE, 25);
        TextView brand = text("AUREN", 22, Color.WHITE);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(menu, weight(0, 48));
        top.addView(brand, weight(1, 48));
        TextView settings = action("⚙", Color.WHITE, 23);
        top.addView(settings, size(48, 48));
        header.addView(top);

        TextView welcome = text("A tua música, do teu jeito.", 26, Color.WHITE);
        welcome.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        welcome.setPadding(0, dp(12), 0, dp(2));
        header.addView(welcome);
        TextView subtitle = text("Biblioteca local • simples • rápida", 13, Color.rgb(221, 244, 226));
        header.addView(subtitle);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Pesquisar músicas, artistas ou álbuns");
        search.setTextSize(14);
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setBackground(round(Color.WHITE, 18));
        LinearLayout.LayoutParams searchParams = size(-1, 50);
        searchParams.topMargin = dp(16);
        header.addView(search, searchParams);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) { renderLibrary(); }
            public void afterTextChanged(Editable e) {}
        });

        root.addView(header);

        LinearLayout tabs = row(BG);
        tabs.setPadding(dp(14), dp(12), dp(14), dp(8));
        String[] names = {"Músicas", "Favoritos", "Recentes"};
        for (String name : names) {
            TextView tab = text(name, 13, name.equals(selectedTab) ? Color.WHITE : TEXT);
            tab.setGravity(Gravity.CENTER);
            tab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tab.setBackground(round(name.equals(selectedTab) ? GREEN : CARD, 18));
            tab.setOnClickListener(v -> { selectedTab = name; renderTabs(tabs, names); renderLibrary(); });
            tabs.addView(tab, weight(1, 42));
        }
        root.addView(tabs);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = column(BG);
        content.setPadding(dp(16), dp(4), dp(16), dp(118));
        scroll.addView(content);
        root.addView(scroll, weight(1, 0));

        miniPlayer = buildMiniPlayer();
        root.addView(miniPlayer);

        menu.setOnClickListener(v -> showMenu());
        settings.setOnClickListener(v -> openSettings());
        setContentView(root);
        renderLibrary();
        updateMiniPlayer();
    }

    private void renderTabs(LinearLayout tabs, String[] names) {
        tabs.removeAllViews();
        for (String name : names) {
            TextView tab = text(name, 13, name.equals(selectedTab) ? Color.WHITE : TEXT);
            tab.setGravity(Gravity.CENTER);
            tab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tab.setBackground(round(name.equals(selectedTab) ? GREEN : CARD, 18));
            tab.setOnClickListener(v -> { selectedTab = name; renderTabs(tabs, names); renderLibrary(); });
            tabs.addView(tab, weight(1, 42));
        }
    }

    private void renderLibrary() {
        if (content == null) return;
        content.removeAllViews();
        String query = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.getDefault());
        visible.clear();
        Set<String> favorites = favorites();
        for (PlayerManager.Song song : songs) {
            boolean matches = query.isEmpty()
                    || song.title.toLowerCase(Locale.getDefault()).contains(query)
                    || song.artist.toLowerCase(Locale.getDefault()).contains(query)
                    || song.album.toLowerCase(Locale.getDefault()).contains(query);
            if (!matches) continue;
            if (selectedTab.equals("Favoritos") && !favorites.contains(song.uri.toString())) continue;
            visible.add(song);
        }

        TextView title = text(selectedTab, 22, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title, size(-1, 34));

        libraryCount = text(visible.size() + " músicas", 12, MUTED);
        content.addView(libraryCount, size(-1, 24));

        if (songs.isEmpty()) {
            addEmpty("A carregar a tua biblioteca…", "As músicas guardadas no dispositivo aparecerão aqui.");
            return;
        }
        if (visible.isEmpty()) {
            addEmpty("Nada encontrado", "Experimenta outro nome de música, artista ou álbum.");
            return;
        }

        for (PlayerManager.Song song : visible) addSongCard(song, favorites.contains(song.uri.toString()));
    }

    private void addSongCard(PlayerManager.Song song, boolean favorite) {
        LinearLayout card = row(CARD);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(9), dp(8), dp(9));
        card.setBackground(round(CARD, 18));
        card.setElevation(dp(1));
        LinearLayout.LayoutParams cp = size(-1, 76);
        cp.bottomMargin = dp(8);
        content.addView(card, cp);

        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setImageResource(android.R.drawable.ic_media_play);
        cover.setColorFilter(GREEN);
        cover.setBackground(round(Color.rgb(235, 241, 236), 14));
        cover.setTag(song.uri.toString());
        card.addView(cover, size(58, 58));

        LinearLayout info = column(CARD);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(12), 0, dp(5), 0);
        TextView name = text(song.title, 15, TEXT);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        TextView meta = text(song.artist + " • " + song.album, 12, MUTED);
        meta.setMaxLines(1);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(name);
        info.addView(meta);
        card.addView(info, weight(1, 58));

        TextView heart = action(favorite ? "♥" : "♡", favorite ? GREEN : MUTED, 22);
        heart.setOnClickListener(v -> toggleFavorite(song));
        card.addView(heart, size(44, 52));
        TextView more = action("⋮", MUTED, 24);
        more.setOnClickListener(v -> showSongMenu(song));
        card.addView(more, size(38, 52));

        card.setOnClickListener(v -> playSong(song));
        loadArtwork(song, cover);
    }

    private LinearLayout buildMiniPlayer() {
        LinearLayout mini = row(CARD);
        mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setPadding(dp(10), dp(7), dp(8), dp(7));
        mini.setElevation(dp(10));

        miniArtwork = new ImageView(this);
        miniArtwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniArtwork.setImageResource(android.R.drawable.ic_media_play);
        miniArtwork.setColorFilter(GREEN);
        mini.addView(miniArtwork, size(48, 48));

        LinearLayout info = column(CARD);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(10), 0, dp(4), 0);
        miniTitle = text("Nada a tocar", 14, TEXT);
        miniTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        miniTitle.setMaxLines(1);
        miniTitle.setEllipsize(TextUtils.TruncateAt.END);
        miniArtist = text("Escolhe uma música", 11, MUTED);
        miniArtist.setMaxLines(1);
        miniArtist.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(miniTitle);
        info.addView(miniArtist);
        mini.addView(info, weight(1, 48));

        miniPlay = action("▶", GREEN, 23);
        mini.addView(miniPlay, size(52, 52));
        miniPlay.setOnClickListener(v -> PlayerManager.toggle());
        TextView open = action("⌃", MUTED, 20);
        mini.addView(open, size(38, 52));
        open.setOnClickListener(v -> openPlayer());
        mini.setOnClickListener(v -> openPlayer());
        return mini;
    }

    private void updateMiniPlayer() {
        if (miniTitle == null) return;
        PlayerManager.Song song = PlayerManager.getCurrentSong();
        if (song == null) {
            miniTitle.setText("Nada a tocar");
            miniArtist.setText("Escolhe uma música");
            miniPlay.setText("▶");
            return;
        }
        miniTitle.setText(song.title);
        miniArtist.setText(song.artist);
        miniPlay.setText(PlayerManager.isPlaying() ? "Ⅱ" : "▶");
        loadArtwork(song, miniArtwork);
    }

    private void playSong(PlayerManager.Song song) {
        if (song == null) return;
        PlayerManager.setQueue(songs.toArray(new PlayerManager.Song[0]));
        PlayerManager.play(this, song);
        prefs.edit().putString("last_played", song.uri.toString()).apply();
        updateMiniPlayer();
    }

    private void openPlayer() {
        if (PlayerManager.getCurrentSong() == null) {
            Toast.makeText(this, "Escolhe uma música primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(this, PlayerActivity.class));
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void showMenu() {
        final String[] items = {"Player", "Definições", "Atualizar biblioteca"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("AUREN")
                .setItems(items, (d, which) -> {
                    if (which == 0) openPlayer();
                    else if (which == 1) openSettings();
                    else loadSongs();
                })
                .show();
    }

    private void showSongMenu(PlayerManager.Song song) {
        String[] items = {"Reproduzir", "Adicionar/remover favorito", "Abrir player"};
        new android.app.AlertDialog.Builder(this)
                .setTitle(song.title)
                .setItems(items, (d, which) -> {
                    if (which == 0) playSong(song);
                    else if (which == 1) toggleFavorite(song);
                    else { playSong(song); openPlayer(); }
                })
                .show();
    }

    private void toggleFavorite(PlayerManager.Song song) {
        Set<String> set = favorites();
        String key = song.uri.toString();
        if (!set.add(key)) set.remove(key);
        prefs.edit().putStringSet("favorites", set).apply();
        renderLibrary();
    }

    private Set<String> favorites() {
        return new HashSet<>(prefs.getStringSet("favorites", new HashSet<>()));
    }

    private void addEmpty(String heading, String message) {
        LinearLayout box = column(CARD);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(24), dp(30), dp(24), dp(30));
        box.setBackground(round(CARD, 22));
        TextView h = text("♪", 38, GREEN);
        h.setGravity(Gravity.CENTER);
        box.addView(h, size(-1, 50));
        TextView title = text(heading, 18, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        box.addView(title);
        TextView msg = text(message, 13, MUTED);
        msg.setGravity(Gravity.CENTER);
        box.addView(msg);
        content.addView(box, size(-1, 170));
    }

    private void loadSongs() {
        songs.clear();
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION
        };
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                MediaStore.Audio.Media.IS_MUSIC + " != 0",
                null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (cursor == null) return;
            int id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            while (cursor.moveToNext()) {
                long songId = cursor.getLong(id);
                Uri uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(songId));
                songs.add(new PlayerManager.Song(
                        safe(cursor.getString(title), "Sem título"),
                        safe(cursor.getString(artist), "Artista desconhecido"),
                        safe(cursor.getString(album), "Álbum desconhecido"),
                        cursor.getLong(duration), uri));
            }
        } catch (Exception ignored) {
        }
        PlayerManager.setQueue(songs.toArray(new PlayerManager.Song[0]));
        renderLibrary();
    }

    private void requestAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permission}, REQUEST_AUDIO);
        } else {
            loadSongs();
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_AUDIO && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) loadSongs();
    }

    private void loadArtwork(PlayerManager.Song song, ImageView view) {
        String key = song.uri.toString();
        Bitmap cached = artworkCache.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            view.clearColorFilter();
            return;
        }
        artworkExecutor.execute(() -> {
            Bitmap bitmap = extractArtwork(song.uri);
            if (bitmap == null) return;
            artworkCache.put(key, bitmap);
            runOnUiThread(() -> {
                if (key.equals(view.getTag()) || view == miniArtwork) {
                    view.setImageBitmap(bitmap);
                    view.clearColorFilter();
                }
            });
        });
    }

    private Bitmap extractArtwork(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            byte[] data = retriever.getEmbeddedPicture();
            if (data == null) return null;
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (bitmap == null) return null;
            return Bitmap.createScaledBitmap(bitmap, 300, 300, true);
        } catch (Exception ignored) {
            return null;
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onPlayerChanged() {
        runOnUiThread(this::updateMiniPlayer);
    }

    @Override
    public void onPlayerError(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private LinearLayout row() { return row(BG); }
    private LinearLayout row(int color) { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setBackgroundColor(color); return v; }
    private LinearLayout column(int color) { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setBackgroundColor(color); return v; }
    private TextView text(String value, float size, int color) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t; }
    private TextView action(String value, int color, float size) { TextView t = text(value, size, color); t.setGravity(Gravity.CENTER); t.setClickable(true); return t; }
    private LinearLayout.LayoutParams size(int w, int h) { return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h)); }
    private LinearLayout.LayoutParams weight(float weight, int h) { return new LinearLayout.LayoutParams(0, dp(h), weight); }
    private android.graphics.drawable.GradientDrawable round(int color, int radius) { android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private String safe(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value; }
}
