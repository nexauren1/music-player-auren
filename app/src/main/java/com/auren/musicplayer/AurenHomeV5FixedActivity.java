package com.auren.musicplayer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AurenHomeV5FixedActivity extends Activity
        implements PlayerManager.Listener {

    private static final int REQUEST_AUDIO = 700;
    private static final int GREEN = Color.rgb(24, 139, 58);
    private static final int GREEN_DARK = Color.rgb(10, 78, 34);
    private static final int GREEN_SOFT = Color.rgb(231, 247, 236);
    private static final int BG = Color.rgb(246, 249, 247);
    private static final int TEXT = Color.rgb(20, 27, 23);
    private static final int MUTED = Color.rgb(103, 114, 107);

    private final List<PlayerManager.Song> songs = new ArrayList<>();
    private final List<PlayerManager.Song> visible = new ArrayList<>();

    private LinearLayout content;
    private TextView nowTitle;
    private TextView nowArtist;
    private TextView playButton;
    private EditText search;
    private TextView count;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        configureBars();
        buildInterface();
        PlayerManager.setListener(this);
        if (hasAudioPermission()) {
            loadLibrary();
        } else {
            showPermission();
            requestAudioPermission();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNowPlaying();
        if (hasAudioPermission() && songs.isEmpty()) {
            loadLibrary();
        }
    }

    @Override
    protected void onDestroy() {
        PlayerManager.setListener(null);
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

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = column(BG);
        content.setPadding(dp(18), dp(16), dp(18), dp(20));
        scroll.addView(content);
        root.addView(scroll, weight(1, 0));

        root.addView(buildNowPlaying(), size(-1, 76));
        setContentView(root);
        render();
    }

    private LinearLayout buildHeader() {
        LinearLayout header = column(GREEN);
        header.setPadding(dp(20), dp(15), dp(20), dp(18));

        LinearLayout top = row(GREEN);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView settings = icon("☰", Color.WHITE, 24);
        settings.setOnClickListener(v -> openSettings());
        top.addView(settings, size(44, 44));

        LinearLayout brand = column(GREEN);
        TextView name = label("AUREN", 21, Color.WHITE);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView sub = label("MUSIC PLAYER · TEST BUILD", 8,
                Color.rgb(208, 239, 217));
        sub.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.addView(name);
        brand.addView(sub);
        top.addView(brand, weight(1, 44));

        TextView refresh = icon("↻", Color.WHITE, 25);
        refresh.setOnClickListener(v -> loadLibrary());
        top.addView(refresh, size(44, 44));
        header.addView(top);

        TextView welcome = label("A tua música,\nmais bonita.", 29,
                Color.WHITE);
        welcome.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams wp = size(-1, 76);
        wp.topMargin = dp(12);
        header.addView(welcome, wp);

        LinearLayout searchBox = row(Color.WHITE);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setPadding(dp(14), 0, dp(10), 0);
        searchBox.setBackground(round(Color.WHITE, 18));

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
        header.addView(searchBox, size(-1, 54));

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            public void onTextChanged(CharSequence s, int st, int b, int c) {
                render();
            }

            public void afterTextChanged(Editable e) {
            }
        });
        return header;
    }

    private LinearLayout buildNowPlaying() {
        LinearLayout bar = row(Color.WHITE);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(8), dp(10), dp(8));
        bar.setElevation(dp(8));

        TextView disc = icon("♫", GREEN, 26);
        disc.setBackground(round(GREEN_SOFT, 15));
        bar.addView(disc, size(50, 50));

        LinearLayout info = column(Color.WHITE);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(10), 0, dp(4), 0);
        nowTitle = label("Nada a tocar", 14, TEXT);
        nowTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nowTitle.setMaxLines(1);
        nowTitle.setEllipsize(TextUtils.TruncateAt.END);
        nowArtist = label("Escolhe uma música", 11, MUTED);
        info.addView(nowTitle);
        info.addView(nowArtist);
        bar.addView(info, weight(1, 50));

        playButton = icon("▶", GREEN, 21);
        playButton.setOnClickListener(v -> PlayerManager.toggle());
        bar.addView(playButton, size(50, 50));
        bar.setOnClickListener(v -> openPlayer());
        playButton.setOnClickListener(v -> PlayerManager.toggle());
        return bar;
    }

    private void render() {
        if (content == null) return;
        content.removeAllViews();
        visible.clear();

        String q = search == null ? "" : search.getText().toString()
                .trim().toLowerCase(Locale.getDefault());

        for (PlayerManager.Song song : songs) {
            if (matches(song, q)) visible.add(song);
        }

        LinearLayout heading = row(BG);
        TextView title = label("Músicas", 23, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.addView(title, weight(1, 42));
        count = label(visible.size() + " faixas", 12, MUTED);
        count.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        heading.addView(count, size(90, 42));
        content.addView(heading);

        if (!hasAudioPermission()) {
            showPermission();
            return;
        }

        if (songs.isEmpty()) {
            showEmpty("Nenhuma música encontrada",
                    "O Auren procura áudio através da biblioteca do Android.",
                    true);
            return;
        }

        if (visible.isEmpty()) {
            showEmpty("Nada encontrado",
                    "Experimenta pesquisar por outro nome.", false);
            return;
        }

        for (PlayerManager.Song song : visible) {
            addSong(song);
        }
    }

    private void addSong(PlayerManager.Song song) {
        LinearLayout card = row(Color.WHITE);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(9), dp(8), dp(9));
        card.setBackground(round(Color.WHITE, 20));
        card.setElevation(dp(2));

        LinearLayout.LayoutParams cp = size(-1, 78);
        cp.bottomMargin = dp(10);
        content.addView(card, cp);

        TextView icon = icon("♫", GREEN, 25);
        icon.setBackground(round(GREEN_SOFT, 16));
        card.addView(icon, size(60, 60));

        LinearLayout info = column(Color.WHITE);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(12), 0, dp(8), 0);

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
        card.addView(info, weight(1, 60));

        TextView play = icon("▶", GREEN, 17);
        play.setBackground(round(GREEN_SOFT, 15));
        card.addView(play, size(44, 44));

        View.OnClickListener listener = v -> playSong(song);
        card.setOnClickListener(listener);
        play.setOnClickListener(listener);
    }

    private boolean matches(PlayerManager.Song song, String q) {
        if (q.isEmpty()) return true;
        return song.title.toLowerCase(Locale.getDefault()).contains(q)
                || song.artist.toLowerCase(Locale.getDefault()).contains(q)
                || song.album.toLowerCase(Locale.getDefault()).contains(q);
    }

    private void loadLibrary() {
        if (!hasAudioPermission()) {
            requestAudioPermission();
            return;
        }

        content.removeAllViews();
        TextView loading = label("A procurar a tua música…", 15, MUTED);
        loading.setGravity(Gravity.CENTER);
        content.addView(loading, size(-1, 130));

        new Thread(() -> {
            List<PlayerManager.Song> found = queryAudio();
            runOnUiThread(() -> {
                songs.clear();
                songs.addAll(found);
                PlayerManager.setQueue(
                        songs.toArray(new PlayerManager.Song[0]));
                render();
            });
        }).start();
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
                MediaStore.Audio.Media.DURATION
        };

        String selection = MediaStore.Audio.Media.DURATION + " > 0";
        String sort = MediaStore.Audio.Media.TITLE
                + " COLLATE NOCASE ASC";

        try (Cursor cursor = getContentResolver().query(
                audioUri, projection, selection, null, sort)) {
            readAudioCursor(cursor, audioUri, result);
        } catch (Exception ignored) {
        }

        if (result.isEmpty()) {
            queryFilesFallback(result);
        }
        return result;
    }

    private void readAudioCursor(
            Cursor cursor,
            Uri baseUri,
            List<PlayerManager.Song> result) {
        if (cursor == null) return;
        int id = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
        int title = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
        int artist = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
        int album = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
        int duration = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
        if (id < 0 || title < 0 || duration < 0) return;

        while (cursor.moveToNext()) {
            long mediaId = cursor.getLong(id);
            Uri uri = Uri.withAppendedPath(
                    baseUri, String.valueOf(mediaId));
            String songTitle = safe(cursor.getString(title), "Sem título");
            String songArtist = artist < 0
                    ? "Artista desconhecido"
                    : safe(cursor.getString(artist), "Artista desconhecido");
            String songAlbum = album < 0
                    ? "Álbum desconhecido"
                    : safe(cursor.getString(album), "Álbum desconhecido");
            long songDuration = cursor.getLong(duration);
            if (songDuration > 0) {
                result.add(new PlayerManager.Song(
                        songTitle,
                        songArtist,
                        songAlbum,
                        songDuration,
                        uri));
            }
        }
    }

    private void queryFilesFallback(List<PlayerManager.Song> result) {
        Uri filesUri = MediaStore.Files.getContentUri("external");
        String[] projection = {
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.TITLE,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.SIZE
        };
        String selection = MediaStore.Files.FileColumns.MEDIA_TYPE
                + " = ? AND "
                + MediaStore.Files.FileColumns.SIZE + " > 0";
        String[] args = {
                String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE)
        };

        try (Cursor cursor = getContentResolver().query(
                filesUri, projection, selection,
                new String[]{"2"},
                MediaStore.Files.FileColumns.TITLE
                        + " COLLATE NOCASE ASC")) {
            if (cursor == null) return;
            int id = cursor.getColumnIndex(
                    MediaStore.Files.FileColumns._ID);
            int title = cursor.getColumnIndex(
                    MediaStore.Files.FileColumns.TITLE);
            if (id < 0 || title < 0) return;
            while (cursor.moveToNext()) {
                long mediaId = cursor.getLong(id);
                String name = safe(cursor.getString(title), "Sem título");
                Uri uri = Uri.withAppendedPath(
                        filesUri, String.valueOf(mediaId));
                result.add(new PlayerManager.Song(
                        name,
                        "Artista desconhecido",
                        "Álbum desconhecido",
                        0,
                        uri));
            }
        } catch (Exception ignored) {
        }
    }

    private void playSong(PlayerManager.Song song) {
        PlayerManager.setQueue(
                songs.toArray(new PlayerManager.Song[0]));
        PlayerManager.play(this, song);
        updateNowPlaying();
    }

    private void updateNowPlaying() {
        if (nowTitle == null) return;
        PlayerManager.Song song = PlayerManager.getCurrentSong();
        if (song == null) {
            nowTitle.setText("Nada a tocar");
            nowArtist.setText("Escolhe uma música");
            playButton.setText("▶");
            return;
        }
        nowTitle.setText(song.title);
        nowArtist.setText(song.artist);
        playButton.setText(PlayerManager.isPlaying() ? "Ⅱ" : "▶");
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

    private void showPermission() {
        if (content == null) return;
        LinearLayout card = column(Color.WHITE);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(22), dp(20), dp(22), dp(20));
        card.setBackground(round(Color.WHITE, 22));

        TextView icon = icon("♫", GREEN, 36);
        card.addView(icon, size(-1, 50));
        TextView title = label("Permitir acesso à música", 19, TEXT);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(title, size(-1, 34));
        TextView message = label(
                "O Auren precisa de acesso ao áudio para mostrar as tuas músicas.",
                13, MUTED);
        message.setGravity(Gravity.CENTER);
        card.addView(message, size(-1, 55));

        TextView button = label("Permitir acesso", 13, Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(round(GREEN, 16));
        button.setOnClickListener(v -> requestAudioPermission());
        card.addView(button, size(190, 46));
        content.addView(card, size(-1, 235));
    }

    private void showEmpty(String titleText, String message,
            boolean refresh) {
        LinearLayout box = column(Color.WHITE);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(22), dp(22), dp(22), dp(22));
        box.setBackground(round(Color.WHITE, 22));

        TextView icon = icon("♫", GREEN, 34);
        box.addView(icon, size(-1, 48));
        TextView title = label(titleText, 18, TEXT);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        box.addView(title, size(-1, 34));
        TextView msg = label(message, 13, MUTED);
        msg.setGravity(Gravity.CENTER);
        box.addView(msg, size(-1, 58));

        if (refresh) {
            TextView button = label("Atualizar biblioteca", 13,
                    Color.WHITE);
            button.setGravity(Gravity.CENTER);
            button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            button.setBackground(round(GREEN, 16));
            button.setOnClickListener(v -> loadLibrary());
            box.addView(button, size(190, 46));
        }
        content.addView(box, size(-1, refresh ? 230 : 190));
    }

    private void requestAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(permission)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permission}, REQUEST_AUDIO);
        } else {
            loadLibrary();
        }
    }

    private boolean hasAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(
                requestCode, permissions, results);
        if (requestCode == REQUEST_AUDIO) {
            if (hasAudioPermission()) {
                loadLibrary();
            } else {
                showPermission();
            }
        }
    }

    @Override
    public void onPlayerChanged() {
        runOnUiThread(this::updateNowPlaying);
    }

    @Override
    public void onPlayerError(String message) {
        runOnUiThread(() -> Toast.makeText(
                this, message, Toast.LENGTH_LONG).show());
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

    private android.graphics.drawable.GradientDrawable round(
            int color, int radius) {
        android.graphics.drawable.GradientDrawable drawable =
                new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value
                * getResources().getDisplayMetrics().density + .5f);
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty()
                ? fallback : value;
    }
}
