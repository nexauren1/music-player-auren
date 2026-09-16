package com.auren.musicplayer;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.view.animation.LinearInterpolator;
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

public class AurenHomeV5Activity extends Activity
        implements PlayerManager.Listener {

    private static final int REQUEST_AUDIO = 500;
    private static final int GREEN = Color.rgb(24, 139, 58);
    private static final int GREEN_DARK = Color.rgb(10, 78, 34);
    private static final int GREEN_SOFT = Color.rgb(231, 247, 236);
    private static final int BG = Color.rgb(246, 249, 247);
    private static final int TEXT = Color.rgb(20, 27, 23);
    private static final int MUTED = Color.rgb(103, 114, 107);

    private final List<PlayerManager.Song> songs = new ArrayList<>();
    private final List<PlayerManager.Song> visible = new ArrayList<>();
    private final Map<String, Bitmap> artwork = new HashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    private LinearLayout content;
    private LinearLayout miniPlayer;
    private ImageView miniArtwork;
    private TextView miniTitle;
    private TextView miniArtist;
    private TextView miniPlay;
    private EditText search;
    private ObjectAnimator artworkRotation;
    private String section = "Músicas";
    private android.content.SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("auren", MODE_PRIVATE);
        configureBars();
        buildInterface();
        PlayerManager.setListener(this);
        loadLibrary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateMiniPlayer();
        if (hasAudioPermission()) loadLibrary();
    }

    @Override
    protected void onDestroy() {
        PlayerManager.setListener(null);
        stopArtworkAnimation();
        executor.shutdownNow();
        super.onDestroy();
    }

    private void configureBars() {
        Window window = getWindow();
        window.setStatusBarColor(GREEN_DARK);
        window.setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 26) {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    private void buildInterface() {
        LinearLayout root = column(BG);
        root.addView(buildHeader());
        root.addView(buildNavigation(), size(-1, 58));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = column(BG);
        content.setPadding(dp(18), dp(8), dp(18), dp(24));
        scroll.addView(content);
        root.addView(scroll, weight(1, 0));

        miniPlayer = buildMiniPlayer();
        root.addView(miniPlayer, size(-1, 72));
        setContentView(root);
        render();
    }

    private LinearLayout buildHeader() {
        LinearLayout header = column(GREEN);
        header.setPadding(dp(20), dp(16), dp(20), dp(18));

        LinearLayout top = row(GREEN);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView menu = icon("☰", Color.WHITE, 24);
        top.addView(menu, size(44, 44));
        menu.setOnClickListener(v -> openSettings());

        LinearLayout brand = column(GREEN);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = label("AUREN", 21, Color.WHITE);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView sub = label("PROFESSIONAL MUSIC PLAYER", 8,
                Color.rgb(208, 239, 217));
        sub.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.addView(name);
        brand.addView(sub);
        top.addView(brand, weight(1, 44));

        TextView refresh = icon("↻", Color.WHITE, 25);
        top.addView(refresh, size(44, 44));
        refresh.setOnClickListener(v -> loadLibrary());
        header.addView(top);

        TextView welcome = label("A tua música,\nmais bonita.", 29,
                Color.WHITE);
        welcome.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams wp = size(-1, 76);
        wp.topMargin = dp(13);
        header.addView(welcome, wp);

        LinearLayout searchBox = row(Color.WHITE);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setPadding(dp(14), 0, dp(10), 0);
        searchBox.setBackground(round(Color.WHITE, 18));
        header.addView(searchBox, size(-1, 54));

        TextView magnify = icon("⌕", GREEN, 25);
        searchBox.addView(magnify, size(32, 50));
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Pesquisar músicas, artistas ou álbuns");
        search.setTextSize(14);
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setBackgroundColor(Color.TRANSPARENT);
        searchBox.addView(search, weight(1, 50));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                render();
            }
            public void afterTextChanged(Editable e) {}
        });
        return header;
    }

    private LinearLayout buildNavigation() {
        LinearLayout nav = row(Color.WHITE);
        nav.setPadding(dp(12), dp(7), dp(12), dp(7));
        rebuildNavigation(nav);
        return nav;
    }

    private void rebuildNavigation(LinearLayout nav) {
        nav.removeAllViews();
        String[] tabs = {"Músicas", "Favoritos", "Recentes"};
        for (String tabName : tabs) {
            TextView tab = label(tabName, 12,
                    tabName.equals(section) ? Color.WHITE : TEXT);
            tab.setGravity(Gravity.CENTER);
            tab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tab.setBackground(round(
                    tabName.equals(section) ? GREEN : Color.WHITE, 16));
            LinearLayout.LayoutParams p = weight(1, 44);
            p.leftMargin = dp(3);
            p.rightMargin = dp(3);
            nav.addView(tab, p);
            tab.setOnClickListener(v -> {
                section = tabName;
                rebuildNavigation(nav);
                render();
            });
        }
    }

    private void render() {
        if (content == null) return;
        content.removeAllViews();
        visible.clear();

        String q = search == null ? "" : search.getText().toString()
                .trim().toLowerCase(Locale.getDefault());
        Set<String> favorites = favorites();

        if (section.equals("Recentes")) {
            for (String uri : recent()) {
                PlayerManager.Song song = findSong(uri);
                if (song != null && matches(song, q)) visible.add(song);
            }
        } else {
            for (PlayerManager.Song song : songs) {
                if (!matches(song, q)) continue;
                if (section.equals("Favoritos")
                        && !favorites.contains(song.uri.toString())) continue;
                visible.add(song);
            }
        }

        LinearLayout heading = row(BG);
        TextView title = label(section, 23, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.addView(title, weight(1, 40));
        TextView count = label(visible.size() + " faixas", 12, MUTED);
        count.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        heading.addView(count, size(90, 40));
        content.addView(heading);

        if (!hasAudioPermission()) {
            showPermissionCard();
            return;
        }
        if (songs.isEmpty()) {
            showEmpty("Nenhuma música encontrada",
                    "O Auren não encontrou áudio no armazenamento.", true);
            return;
        }
        if (visible.isEmpty()) {
            showEmpty("Nada encontrado",
                    "Experimenta outra pesquisa ou outra secção.", false);
            return;
        }
        for (PlayerManager.Song song : visible) {
            addSong(song, favorites.contains(song.uri.toString()));
        }
    }

    private void addSong(PlayerManager.Song song, boolean favorite) {
        LinearLayout card = row(Color.WHITE);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(9), dp(8), dp(9));
        card.setBackground(round(Color.WHITE, 20));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams cp = size(-1, 80);
        cp.bottomMargin = dp(10);
        content.addView(card, cp);

        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(round(GREEN_SOFT, 16));
        cover.setImageResource(android.R.drawable.ic_media_play);
        cover.setColorFilter(GREEN);
        cover.setTag(song.uri.toString());
        card.addView(cover, size(62, 62));

        LinearLayout info = column(Color.WHITE);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(12), 0, dp(4), 0);
        TextView title = label(song.title, 15, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView meta = label(song.artist + "  ·  " + song.album,
                11, MUTED);
        meta.setMaxLines(1);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(title);
        info.addView(meta);
        card.addView(info, weight(1, 62));

        TextView heart = icon(favorite ? "♥" : "♡",
                favorite ? GREEN : MUTED, 21);
        card.addView(heart, size(42, 54));
        heart.setOnClickListener(v -> toggleFavorite(song));

        TextView play = icon("▶", GREEN, 17);
        play.setBackground(round(GREEN_SOFT, 16));
        card.addView(play, size(44, 44));
        play.setOnClickListener(v -> play(song));
        card.setOnClickListener(v -> play(song));
        loadArtwork(song, cover);
    }

    private LinearLayout buildMiniPlayer() {
        LinearLayout mini = row(Color.WHITE);
        mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setPadding(dp(12), dp(8), dp(10), dp(8));
        mini.setElevation(dp(10));
        mini.setBackground(round(Color.WHITE, 20));

        miniArtwork = new ImageView(this);
        miniArtwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniArtwork.setBackground(round(GREEN_SOFT, 14));
        miniArtwork.setImageResource(android.R.drawable.ic_media_play);
        miniArtwork.setColorFilter(GREEN);
        mini.addView(miniArtwork, size(50, 50));

        LinearLayout info = column(Color.WHITE);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(10), 0, dp(4), 0);
        miniTitle = label("Nada a tocar", 14, TEXT);
        miniTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        miniTitle.setMaxLines(1);
        miniTitle.setEllipsize(TextUtils.TruncateAt.END);
        miniArtist = label("Escolhe uma música", 11, MUTED);
        info.addView(miniTitle);
        info.addView(miniArtist);
        mini.addView(info, weight(1, 50));

        miniPlay = icon("▶", GREEN, 21);
        mini.addView(miniPlay, size(48, 50));
        miniPlay.setOnClickListener(v -> PlayerManager.toggle());
        mini.setOnClickListener(v -> openPlayer());
        miniArtwork.setOnClickListener(v -> openPlayer());
        return mini;
    }

    private void updateMiniPlayer() {
        if (miniPlayer == null) return;
        PlayerManager.Song song = PlayerManager.getCurrentSong();
        if (song == null) {
            miniPlayer.setVisibility(View.GONE);
            stopArtworkAnimation();
            return;
        }
        miniPlayer.setVisibility(View.VISIBLE);
        miniTitle.setText(song.title);
        miniArtist.setText(song.artist);
        miniPlay.setText(PlayerManager.isPlaying() ? "Ⅱ" : "▶");
        loadArtwork(song, miniArtwork);
        if (PlayerManager.isPlaying()) startArtworkAnimation();
        else stopArtworkAnimation();
    }

    private void startArtworkAnimation() {
        if (artworkRotation != null) return;
        artworkRotation = ObjectAnimator.ofFloat(miniArtwork,
                View.ROTATION, 0f, 360f);
        artworkRotation.setDuration(12000);
        artworkRotation.setRepeatCount(ObjectAnimator.INFINITE);
        artworkRotation.setInterpolator(new LinearInterpolator());
        artworkRotation.start();
    }

    private void stopArtworkAnimation() {
        if (artworkRotation == null) return;
        artworkRotation.cancel();
        artworkRotation = null;
        if (miniArtwork != null) miniArtwork.setRotation(0f);
    }

    private void play(PlayerManager.Song song) {
        PlayerManager.setQueue(songs.toArray(new PlayerManager.Song[0]));
        PlayerManager.play(this, song);
        saveRecent(song.uri.toString());
        updateMiniPlayer();
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

    private boolean matches(PlayerManager.Song song, String q) {
        if (q.isEmpty()) return true;
        return song.title.toLowerCase(Locale.getDefault()).contains(q)
                || song.artist.toLowerCase(Locale.getDefault()).contains(q)
                || song.album.toLowerCase(Locale.getDefault()).contains(q);
    }

    private void loadLibrary() {
        if (!hasAudioPermission()) {
            showPermissionState();
            requestAudioPermission();
            return;
        }

        content.removeAllViews();
        TextView loading = label("A procurar a tua música…", 15, MUTED);
        loading.setGravity(Gravity.CENTER);
        content.addView(loading, size(-1, 120));

        executor.execute(() -> {
            List<PlayerManager.Song> found = queryAudio();
            runOnUiThread(() -> {
                if (isFinishing()) return;
                songs.clear();
                songs.addAll(found);
                PlayerManager.setQueue(songs.toArray(
                        new PlayerManager.Song[0]));
                render();
            });
        });
    }

    private List<PlayerManager.Song> queryAudio() {
        List<PlayerManager.Song> result = new ArrayList<>();
        Uri audioUri = Build.VERSION.SDK_INT >= 29
                ? MediaStore.Audio.Media.getContentUri(
                        MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.MIME_TYPE
        };

        try (Cursor cursor = getContentResolver().query(
                audioUri, projection, null, null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (cursor != null) {
                readAudioCursor(cursor, audioUri, result);
            }
        } catch (Exception ignored) {
        }

        if (result.isEmpty()) {
            queryFilesFallback(result);
        }
        return result;
    }

    private void readAudioCursor(Cursor cursor, Uri baseUri,
            List<PlayerManager.Song> result) {
        int id = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
        int title = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
        int artist = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
        int album = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
        int duration = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
        int display = cursor.getColumnIndex(
                MediaStore.Audio.Media.DISPLAY_NAME);

        if (id < 0) return;
        while (cursor.moveToNext()) {
            long mediaId = cursor.getLong(id);
            Uri uri = ContentUris.withAppendedId(baseUri, mediaId);
            String name = display >= 0 ? cursor.getString(display) : null;
            String songTitle = title >= 0 ? cursor.getString(title) : null;
            String songArtist = artist >= 0 ? cursor.getString(artist) : null;
            String songAlbum = album >= 0 ? cursor.getString(album) : null;
            long songDuration = duration >= 0 ? cursor.getLong(duration) : 0;

            result.add(new PlayerManager.Song(
                    safe(songTitle, safe(name, "Sem título")),
                    safe(songArtist, "Artista desconhecido"),
                    safe(songAlbum, "Álbum desconhecido"),
                    songDuration, uri));
        }
    }

    private void queryFilesFallback(List<PlayerManager.Song> result) {
        Uri filesUri = MediaStore.Files.getContentUri("external");
        String[] projection = {
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.DURATION
        };
        String selection = MediaStore.Files.FileColumns.MEDIA_TYPE
                + " = ?";
        String[] args = {
                String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO)
        };

        try (Cursor cursor = getContentResolver().query(
                filesUri, projection, selection, args,
                MediaStore.Files.FileColumns.DISPLAY_NAME
                        + " COLLATE NOCASE ASC")) {
            if (cursor == null) return;
            int id = cursor.getColumnIndex(
                    MediaStore.Files.FileColumns._ID);
            int name = cursor.getColumnIndex(
                    MediaStore.Files.FileColumns.DISPLAY_NAME);
            int duration = cursor.getColumnIndex(
                    MediaStore.Files.FileColumns.DURATION);
            if (id < 0) return;

            while (cursor.moveToNext()) {
                long mediaId = cursor.getLong(id);
                String fileName = name >= 0
                        ? cursor.getString(name) : "Sem título";
                long length = duration >= 0 ? cursor.getLong(duration) : 0;
                Uri uri = ContentUris.withAppendedId(filesUri, mediaId);
                result.add(new PlayerManager.Song(
                        removeExtension(safe(fileName, "Sem título")),
                        "Artista desconhecido",
                        "Álbum desconhecido",
                        length, uri));
            }
        } catch (Exception ignored) {
        }
    }

    private String removeExtension(String value) {
        int dot = value.lastIndexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }

    private void showPermissionState() {
        content.removeAllViews();
        showPermissionCard();
    }

    private void showPermissionCard() {
        LinearLayout card = column(Color.WHITE);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(22), dp(20), dp(22), dp(20));
        card.setBackground(round(Color.WHITE, 22));

        TextView music = icon("♫", GREEN, 38);
        card.addView(music, size(-1, 50));
        TextView title = label("A tua música está aqui", 19, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title, size(-1, 32));

        TextView message = label(
                "Permite acesso ao áudio para o Auren ler a biblioteca.",
                13, MUTED);
        message.setGravity(Gravity.CENTER);
        card.addView(message, size(-1, 48));

        TextView button = label("Permitir acesso", 13, Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(round(GREEN, 16));
        card.addView(button, size(180, 46));
        button.setOnClickListener(v -> requestAudioPermission());
        content.addView(card, size(-1, 215));
    }

    private void showEmpty(String title, String message, boolean refresh) {
        LinearLayout box = column(Color.WHITE);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(22), dp(22), dp(22), dp(22));
        box.setBackground(round(Color.WHITE, 22));

        TextView music = icon("♫", GREEN, 34);
        box.addView(music, size(-1, 48));
        TextView heading = label(title, 18, TEXT);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        box.addView(heading, size(-1, 32));
        TextView msg = label(message, 13, MUTED);
        msg.setGravity(Gravity.CENTER);
        box.addView(msg, size(-1, 48));

        if (refresh) {
            TextView button = label("Atualizar biblioteca", 13, Color.WHITE);
            button.setGravity(Gravity.CENTER);
            button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            button.setBackground(round(GREEN, 15));
            box.addView(button, size(190, 46));
            button.setOnClickListener(v -> loadLibrary());
        }
        content.addView(box, size(-1, refresh ? 215 : 190));
    }

    private void requestAudioPermission() {
        if (Build.VERSION.SDK_INT < 23) {
            loadLibrary();
            return;
        }
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(permission)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permission}, REQUEST_AUDIO);
        } else {
            loadLibrary();
        }
    }

    private boolean hasAudioPermission() {
        if (Build.VERSION.SDK_INT < 23) return true;
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return checkSelfPermission(permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_AUDIO) return;
        if (hasAudioPermission()) {
            loadLibrary();
        } else {
            showPermissionState();
        }
    }

    private void loadArtwork(PlayerManager.Song song, ImageView view) {
        if (song == null || song.uri == null) return;
        String key = song.uri.toString();
        Bitmap cached = artwork.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            view.clearColorFilter();
            return;
        }
        executor.execute(() -> {
            Bitmap bitmap = extractArtwork(song.uri);
            if (bitmap == null) return;
            artwork.put(key, bitmap);
            runOnUiThread(() -> {
                if (!isFinishing()
                        && (view == miniArtwork
                        || key.equals(view.getTag()))) {
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
            Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(
                    data, 0, data.length);
            return bitmap == null ? null
                    : Bitmap.createScaledBitmap(bitmap, 300, 300, true);
        } catch (Exception ignored) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void toggleFavorite(PlayerManager.Song song) {
        Set<String> set = favorites();
        String key = song.uri.toString();
        if (!set.add(key)) set.remove(key);
        prefs.edit().putStringSet("favorites", set).apply();
        render();
    }

    private Set<String> favorites() {
        return new HashSet<>(prefs.getStringSet(
                "favorites", new HashSet<>()));
    }

    private void saveRecent(String uri) {
        List<String> list = recent();
        list.remove(uri);
        list.add(0, uri);
        while (list.size() > 30) list.remove(list.size() - 1);
        prefs.edit().putString("recent_history", join(list)).apply();
    }

    private List<String> recent() {
        List<String> result = new ArrayList<>();
        String raw = prefs.getString("recent_history", "");
        if (raw.isEmpty()) return result;
        for (String value : raw.split("\\|")) {
            if (!value.isEmpty() && !result.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append('|');
            out.append(value);
        }
        return out.toString();
    }

    private PlayerManager.Song findSong(String uri) {
        for (PlayerManager.Song song : songs) {
            if (song.uri.toString().equals(uri)) return song;
        }
        return null;
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
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setBackgroundColor(color);
        return view;
    }

    private LinearLayout column(int color) {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackgroundColor(color);
        return view;
    }

    private TextView label(String text, float size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private TextView icon(String text, int color, float size) {
        TextView view = label(text, size, color);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        return view;
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

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty()
                ? fallback : value;
    }
}
