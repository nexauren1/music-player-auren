package com.auren.musicplayer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.view.Window;
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

    private static final int GREEN = Color.rgb(24, 139, 58);
    private static final int GREEN_DARK = Color.rgb(13, 91, 39);
    private static final int GREEN_SOFT = Color.rgb(232, 246, 236);
    private static final int BG = Color.rgb(247, 249, 248);
    private static final int CARD = Color.WHITE;
    private static final int TEXT = Color.rgb(24, 30, 26);
    private static final int MUTED = Color.rgb(101, 111, 105);

    private final List<PlayerManager.Song> songs = new ArrayList<>();
    private final List<PlayerManager.Song> visible = new ArrayList<>();
    private final Map<String, Bitmap> artworkCache = new HashMap<>();
    private final ExecutorService artworkExecutor =
            Executors.newFixedThreadPool(3);

    private SharedPreferences prefs;
    private LinearLayout content;
    private LinearLayout miniContainer;
    private ImageView miniArtwork;
    private TextView miniTitle;
    private TextView miniArtist;
    private TextView miniPlay;
    private EditText search;
    private TextView libraryStatus;
    private String selectedTab = "Músicas";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("auren", MODE_PRIVATE);
        styleSystemBars();
        buildUi();
        PlayerManager.setListener(this);

        // Never request audio and notification permissions at the same time.
        // Android may keep only one permission dialog active, which previously
        // caused the music permission flow to be skipped on some devices.
        if (hasAudioPermission()) {
            loadSongs();
            requestNotificationPermission();
        } else {
            requestAudioPermission();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateMiniPlayer();
        if (hasAudioPermission() && songs.isEmpty()) loadSongs();
    }

    @Override
    protected void onDestroy() {
        PlayerManager.setListener(null);
        artworkExecutor.shutdownNow();
        super.onDestroy();
    }

    private void styleSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(GREEN_DARK);
        window.setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 26) {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    private void buildUi() {
        LinearLayout root = column(BG);

        LinearLayout header = column(GREEN);
        header.setPadding(dp(18), dp(14), dp(18), dp(18));

        LinearLayout top = row(GREEN);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView menu = action("☰", Color.WHITE, 25);
        top.addView(menu, size(48, 48));

        LinearLayout brandBox = column(GREEN);
        brandBox.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("AUREN", 21, Color.WHITE);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView brandSub = text("MUSIC PLAYER", 9,
                Color.rgb(211, 239, 219));
        brandSub.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brandBox.addView(brand);
        brandBox.addView(brandSub);
        top.addView(brandBox, weight(1, 48));

        TextView settings = action("⚙", Color.WHITE, 24);
        top.addView(settings, size(48, 48));
        header.addView(top);

        TextView welcome = text("A tua música.", 29, Color.WHITE);
        welcome.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams welcomeParams = size(-1, 42);
        welcomeParams.topMargin = dp(18);
        header.addView(welcome, welcomeParams);

        TextView subtitle = text(
                "Tudo o que tens no teu dispositivo, num só lugar.",
                14, Color.rgb(220, 243, 226));
        header.addView(subtitle, size(-1, 28));

        LinearLayout searchBox = row(Color.WHITE);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setPadding(dp(14), 0, dp(8), 0);
        searchBox.setBackground(round(Color.WHITE, 18));
        LinearLayout.LayoutParams searchBoxParams = size(-1, 54);
        searchBoxParams.topMargin = dp(14);
        header.addView(searchBox, searchBoxParams);

        TextView searchIcon = action("⌕", GREEN, 24);
        searchBox.addView(searchIcon, size(34, 50));

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Pesquisar músicas, artistas ou álbuns");
        search.setTextSize(14);
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setPadding(dp(5), 0, dp(8), 0);
        searchBox.addView(search, weight(1, 50));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st,
                                          int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st,
                                      int before, int count) {
                renderLibrary();
            }

            @Override
            public void afterTextChanged(Editable e) {
            }
        });

        root.addView(header);

        LinearLayout tabs = row(BG);
        tabs.setPadding(dp(16), dp(12), dp(16), dp(8));
        renderTabs(tabs);
        root.addView(tabs);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = column(BG);
        content.setPadding(dp(16), dp(4), dp(16), dp(28));
        scroll.addView(content);
        root.addView(scroll, weight(1, 0));

        miniContainer = buildMiniPlayer();
        root.addView(miniContainer);

        menu.setOnClickListener(v -> showMenu());
        settings.setOnClickListener(v -> openSettings());

        setContentView(root);
        renderLibrary();
        updateMiniPlayer();
    }

    private void renderTabs(LinearLayout tabs) {
        tabs.removeAllViews();
        String[] names = {"Músicas", "Favoritos", "Recentes"};
        for (String name : names) {
            boolean selected = name.equals(selectedTab);
            TextView tab = text(name, 13, selected ? Color.WHITE : TEXT);
            tab.setGravity(Gravity.CENTER);
            tab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tab.setBackground(round(selected ? GREEN : CARD, 16));
            LinearLayout.LayoutParams params = weight(1, 44);
            params.leftMargin = dp(3);
            params.rightMargin = dp(3);
            tabs.addView(tab, params);
            tab.setOnClickListener(v -> {
                selectedTab = name;
                renderTabs(tabs);
                renderLibrary();
            });
        }
    }

    private void renderLibrary() {
        if (content == null) return;
        content.removeAllViews();

        String query = search == null
                ? ""
                : search.getText().toString().trim()
                .toLowerCase(Locale.getDefault());

        visible.clear();
        Set<String> favorites = favorites();
        List<String> recent = recentHistory();

        if (selectedTab.equals("Recentes")) {
            for (String uri : recent) {
                PlayerManager.Song song = findSong(uri);
                if (song != null && matches(song, query)) visible.add(song);
            }
        } else {
            for (PlayerManager.Song song : songs) {
                if (!matches(song, query)) continue;
                if (selectedTab.equals("Favoritos")
                        && !favorites.contains(song.uri.toString())) continue;
                visible.add(song);
            }
        }

        LinearLayout headingRow = row(BG);
        TextView title = text(selectedTab, 22, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        headingRow.addView(title, weight(1, 34));

        libraryStatus = text(
                visible.size() + (visible.size() == 1
                        ? " música" : " músicas"),
                12, MUTED);
        libraryStatus.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        headingRow.addView(libraryStatus, size(110, 34));
        content.addView(headingRow);

        if (!hasAudioPermission()) {
            addPermissionCard();
            return;
        }

        if (songs.isEmpty()) {
            addEmpty(
                    "A biblioteca está vazia",
                    "Não encontrámos músicas neste dispositivo.",
                    true);
            return;
        }

        if (visible.isEmpty()) {
            String heading = selectedTab.equals("Recentes")
                    ? "Ainda não há músicas recentes"
                    : "Nada encontrado";
            String message = selectedTab.equals("Recentes")
                    ? "As músicas que reproduzires aparecerão aqui."
                    : "Experimenta outro nome de música, artista ou álbum.";
            addEmpty(heading, message, false);
            return;
        }

        for (PlayerManager.Song song : visible) {
            addSongCard(song, favorites.contains(song.uri.toString()));
        }
    }

    private void addPermissionCard() {
        LinearLayout card = card();
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(22), dp(24), dp(22), dp(24));

        TextView icon = text("♫", 38, GREEN);
        icon.setGravity(Gravity.CENTER);
        card.addView(icon, size(-1, 50));

        TextView title = text("Dá acesso à tua música", 19, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title, size(-1, 30));

        TextView message = text(
                "O Auren precisa de permissão para mostrar as músicas "
                        + "guardadas no telemóvel.",
                13, MUTED);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, dp(4), 0, dp(14));
        card.addView(message, size(-1, 52));

        TextView button = action("Permitir acesso", Color.WHITE, 14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(round(GREEN, 16));
        card.addView(button, size(170, 48));
        button.setOnClickListener(v -> requestAudioPermission());

        content.addView(card, size(-1, 225));
    }

    private boolean matches(PlayerManager.Song song, String query) {
        if (query.isEmpty()) return true;
        return song.title.toLowerCase(Locale.getDefault()).contains(query)
                || song.artist.toLowerCase(Locale.getDefault()).contains(query)
                || song.album.toLowerCase(Locale.getDefault()).contains(query);
    }

    private void addSongCard(PlayerManager.Song song, boolean favorite) {
        LinearLayout card = row(CARD);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(9), dp(8), dp(9));
        card.setBackground(round(CARD, 18));
        card.setElevation(dp(1));

        LinearLayout.LayoutParams cp = size(-1, 76);
        cp.bottomMargin = dp(9);
        content.addView(card, cp);

        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setImageResource(android.R.drawable.ic_media_play);
        cover.setColorFilter(GREEN);
        cover.setBackground(round(Color.rgb(235, 243, 237), 14));
        cover.setTag(song.uri.toString());
        card.addView(cover, size(58, 58));

        LinearLayout info = column(CARD);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(12), 0, dp(4), 0);

        TextView name = text(song.title, 15, TEXT);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);

        TextView meta = text(song.artist + "  ·  " + song.album, 12, MUTED);
        meta.setMaxLines(1);
        meta.setEllipsize(TextUtils.TruncateAt.END);

        info.addView(name);
        info.addView(meta);
        card.addView(info, weight(1, 58));

        TextView heart = action(
                favorite ? "♥" : "♡", favorite ? GREEN : MUTED, 22);
        heart.setOnClickListener(v -> toggleFavorite(song));
        card.addView(heart, size(42, 52));

        TextView more = action("⋮", MUTED, 24);
        more.setOnClickListener(v -> showSongMenu(song));
        card.addView(more, size(34, 52));

        card.setOnClickListener(v -> playSong(song));
        loadArtwork(song, cover);
    }

    private LinearLayout buildMiniPlayer() {
        LinearLayout mini = row(CARD);
        mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setPadding(dp(12), dp(8), dp(10), dp(8));
        mini.setElevation(dp(8));
        mini.setBackground(round(CARD, 18));

        miniArtwork = new ImageView(this);
        miniArtwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniArtwork.setImageResource(android.R.drawable.ic_media_play);
        miniArtwork.setColorFilter(GREEN);
        miniArtwork.setBackground(round(GREEN_SOFT, 14));
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

        miniPlay = action("▶", GREEN, 22);
        mini.addView(miniPlay, size(50, 50));
        miniPlay.setOnClickListener(v -> PlayerManager.toggle());

        TextView open = action("⌃", MUTED, 19);
        mini.addView(open, size(34, 50));
        open.setOnClickListener(v -> openPlayer());
        mini.setOnClickListener(v -> openPlayer());
        return mini;
    }

    private void updateMiniPlayer() {
        if (miniContainer == null || miniTitle == null) return;
        PlayerManager.Song song = PlayerManager.getCurrentSong();
        if (song == null) {
            miniContainer.setVisibility(View.GONE);
            return;
        }
        miniContainer.setVisibility(View.VISIBLE);
        miniTitle.setText(song.title);
        miniArtist.setText(song.artist);
        miniPlay.setText(PlayerManager.isPlaying() ? "Ⅱ" : "▶");
        loadArtwork(song, miniArtwork);
    }

    private void playSong(PlayerManager.Song song) {
        if (song == null) return;
        PlayerManager.setQueue(songs.toArray(new PlayerManager.Song[0]));
        PlayerManager.play(this, song);
        saveRecent(song.uri.toString());
        updateMiniPlayer();
    }

    private void saveRecent(String uri) {
        List<String> history = recentHistory();
        history.remove(uri);
        history.add(0, uri);
        while (history.size() > 30) history.remove(history.size() - 1);
        StringBuilder value = new StringBuilder();
        for (String item : history) {
            if (value.length() > 0) value.append("|");
            value.append(item);
        }
        prefs.edit().putString("recent_history", value.toString()).apply();
    }

    private List<String> recentHistory() {
        List<String> result = new ArrayList<>();
        String raw = prefs.getString("recent_history", "");
        if (raw.isEmpty()) return result;
        for (String item : raw.split("\\|")) {
            if (!item.isEmpty() && !result.contains(item)) result.add(item);
        }
        return result;
    }

    private PlayerManager.Song findSong(String uri) {
        for (PlayerManager.Song song : songs) {
            if (song.uri.toString().equals(uri)) return song;
        }
        return null;
    }

    private void openPlayer() {
        if (PlayerManager.getCurrentSong() == null) {
            Toast.makeText(this, "Escolhe uma música primeiro.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(this, PlayerActivity.class));
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void openEffects() {
        startActivity(new Intent(this, EffectsActivity.class));
    }

    private void showMenu() {
        LinearLayout menu = column(CARD);
        menu.setPadding(dp(18), dp(8), dp(18), dp(12));

        TextView heading = text("AUREN", 22, TEXT);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        menu.addView(heading, size(-1, 38));

        TextView sub = text("O teu espaço de música", 12, MUTED);
        menu.addView(sub, size(-1, 26));

        addMenuItem(menu, "▶", "Player", "Abrir o leitor", this::openPlayer);
        addMenuItem(menu, "♫", "Efeitos de áudio",
                "Equalizador e graves", this::openEffects);
        addMenuItem(menu, "⚙", "Definições",
                "Aparência, biblioteca e atualizações", this::openSettings);
        addMenuItem(menu, "↻", "Atualizar biblioteca",
                "Procurar músicas novamente", this::loadSongs);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(menu)
                .setNegativeButton("Fechar", null)
                .create();
        dialog.show();
    }

    private void addMenuItem(
            LinearLayout parent,
            String icon,
            String title,
            String subtitle,
            final Runnable action) {
        LinearLayout item = row(CARD);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(0, dp(5), 0, dp(5));

        TextView iconView = action(icon, GREEN, 21);
        iconView.setBackground(round(GREEN_SOFT, 14));
        item.addView(iconView, size(48, 48));

        LinearLayout info = column(CARD);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(12), 0, 0, 0);
        TextView titleView = text(title, 15, TEXT);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView subtitleView = text(subtitle, 11, MUTED);
        info.addView(titleView);
        info.addView(subtitleView);
        item.addView(info, weight(1, 48));
        parent.addView(item, size(-1, 58));

        item.setOnClickListener(v -> action.run());
    }

    private void showSongMenu(PlayerManager.Song song) {
        String[] items = {
                "Reproduzir",
                "Adicionar/remover favorito",
                "Abrir player",
                "Efeitos de áudio"
        };
        new AlertDialog.Builder(this)
                .setTitle(song.title)
                .setItems(items, (d, which) -> {
                    if (which == 0) playSong(song);
                    else if (which == 1) toggleFavorite(song);
                    else if (which == 2) {
                        playSong(song);
                        openPlayer();
                    } else {
                        playSong(song);
                        openEffects();
                    }
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

    private void addEmpty(String heading, String message, boolean refresh) {
        LinearLayout box = card();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(24), dp(22), dp(24), dp(22));

        TextView icon = text("♫", 36, GREEN);
        icon.setGravity(Gravity.CENTER);
        box.addView(icon, size(-1, 48));

        TextView title = text(heading, 18, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        box.addView(title, size(-1, 30));

        TextView msg = text(message, 13, MUTED);
        msg.setGravity(Gravity.CENTER);
        box.addView(msg, size(-1, 44));

        if (refresh) {
            TextView button = action("Atualizar biblioteca", Color.WHITE, 13);
            button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            button.setBackground(round(GREEN, 15));
            box.addView(button, size(180, 46));
            button.setOnClickListener(v -> loadSongs());
        }

        content.addView(box, size(-1, refresh ? 205 : 180));
    }

    private LinearLayout card() {
        LinearLayout value = column(CARD);
        value.setBackground(round(CARD, 20));
        value.setElevation(dp(1));
        return value;
    }

    private void loadSongs() {
        if (!hasAudioPermission()) {
            renderLibrary();
            return;
        }

        songs.clear();
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.IS_MUSIC,
                MediaStore.Audio.Media.MIME_TYPE
        };

        try (Cursor cursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                MediaStore.Audio.Media.IS_MUSIC + " != 0",
                null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (cursor == null) {
                showLibraryError("Não foi possível ler a biblioteca.");
                return;
            }

            int id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);

            while (cursor.moveToNext()) {
                long songId = cursor.getLong(id);
                Uri uri = Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        String.valueOf(songId));
                long length = cursor.getLong(duration);
                if (length <= 0) continue;
                songs.add(new PlayerManager.Song(
                        safe(cursor.getString(title), "Sem título"),
                        safe(cursor.getString(artist), "Artista desconhecido"),
                        safe(cursor.getString(album), "Álbum desconhecido"),
                        length,
                        uri));
            }
        } catch (SecurityException e) {
            showLibraryError("O acesso às músicas foi recusado.");
            return;
        } catch (Exception e) {
            showLibraryError("Não foi possível carregar as músicas.");
            return;
        }

        PlayerManager.setQueue(songs.toArray(new PlayerManager.Song[0]));
        renderLibrary();
    }

    private void showLibraryError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        renderLibrary();
    }

    private boolean hasAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permission}, REQUEST_AUDIO);
        } else {
            loadSongs();
            requestNotificationPermission();
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_AUDIO) {
            if (hasAudioPermission()) {
                loadSongs();
                requestNotificationPermission();
            } else {
                renderLibrary();
            }
        }
    }

    private void loadArtwork(PlayerManager.Song song, ImageView view) {
        if (song == null || song.uri == null) return;
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
                if (!isFinishing()
                        && (view == miniArtwork || key.equals(view.getTag()))) {
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
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onPlayerChanged() {
        runOnUiThread(this::updateMiniPlayer);
    }

    @Override
    public void onPlayerError(String message) {
        runOnUiThread(() -> Toast.makeText(this, message,
                Toast.LENGTH_LONG).show());
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

    private TextView text(String value, float size, int color) {
        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(size);
        valueView.setTextColor(color);
        return valueView;
    }

    private TextView action(String value, int color, float size) {
        TextView valueView = text(value, size, color);
        valueView.setGravity(Gravity.CENTER);
        valueView.setClickable(true);
        return valueView;
    }

    private LinearLayout.LayoutParams size(int width, int height) {
        return new LinearLayout.LayoutParams(
                width < 0 ? width : dp(width),
                height < 0 ? height : dp(height));
    }

    private LinearLayout.LayoutParams weight(float weight, int height) {
        return new LinearLayout.LayoutParams(
                0, height < 0 ? height : dp(height), weight);
    }

    private android.graphics.drawable.GradientDrawable round(int color,
                                                               int radius) {
        android.graphics.drawable.GradientDrawable drawable =
                new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
