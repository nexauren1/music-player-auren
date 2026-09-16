package com.auren.musicplayer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQUEST_AUDIO = 100;
    private static final int GREEN = Color.rgb(32, 150, 42);
    private static final int TEXT = Color.rgb(35, 36, 40);
    private static final int MUTED = Color.rgb(105, 108, 116);

    private final List<Song> songs = new ArrayList<>();
    private final List<Song> visibleSongs = new ArrayList<>();
    private MediaPlayer player;
    private ListView songList;
    private TextView nowTitle;
    private TextView nowArtist;
    private TextView countText;
    private Button playButton;
    private int currentIndex = -1;
    private boolean darkMode;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        prefs = getSharedPreferences("auren", MODE_PRIVATE);
        darkMode = prefs.getBoolean("dark", false);
        buildUi();
        requestAudioPermission();
    }

    private int bg() {
        return darkMode ? Color.rgb(20, 22, 24) : Color.rgb(247, 248, 250);
    }

    private int panel() {
        return darkMode ? Color.rgb(31, 34, 37) : Color.WHITE;
    }

    private int mainText() {
        return darkMode ? Color.WHITE : TEXT;
    }

    private int mutedText() {
        return darkMode ? Color.rgb(185, 188, 193) : MUTED;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg());

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), dp(4), dp(4), dp(2));
        toolbar.setBackgroundColor(GREEN);

        Button menu = iconButton("☰");
        TextView brand = text("AUREN", 20, Color.WHITE);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        toolbar.addView(menu, new LinearLayout.LayoutParams(dp(52), dp(52)));
        toolbar.addView(brand, new LinearLayout.LayoutParams(0, dp(52), 1));

        Button search = iconButton("⌕");
        Button equalizer = iconButton("☷");
        Button more = iconButton("⋮");
        toolbar.addView(search, new LinearLayout.LayoutParams(dp(48), dp(52)));
        toolbar.addView(equalizer, new LinearLayout.LayoutParams(dp(48), dp(52)));
        toolbar.addView(more, new LinearLayout.LayoutParams(dp(44), dp(52)));
        root.addView(toolbar);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setBackgroundColor(GREEN);
        String[] names = {"ÁLBUM", "MÚSICAS", "ARTISTAS", "PASTA", "LISTA"};
        for (String name : names) {
            TextView tab = text(name, 13, Color.WHITE);
            tab.setGravity(Gravity.CENTER);
            tab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tabs.addView(tab, new LinearLayout.LayoutParams(0, dp(46), 1));
            tab.setOnClickListener(v -> Toast.makeText(
                    this, name + " selecionado", Toast.LENGTH_SHORT).show());
        }
        root.addView(tabs);

        countText = text("A carregar músicas...", 13, mutedText());
        countText.setPadding(dp(16), dp(10), dp(16), dp(6));
        root.addView(countText);

        songList = new ListView(this);
        songList.setDivider(null);
        songList.setBackgroundColor(bg());
        songList.setOnItemClickListener((p, v, position, id) ->
                play(visibleSongs.get(position)));
        root.addView(songList, new LinearLayout.LayoutParams(-1, 0, 1));

        root.addView(buildMiniPlayer());

        menu.setOnClickListener(v -> showSideMenu());
        search.setOnClickListener(v -> showSearch());
        equalizer.setOnClickListener(v -> openEqualizer());
        more.setOnClickListener(v -> showMainMenu());

        setContentView(root);
    }

    private LinearLayout buildMiniPlayer() {
        LinearLayout mini = new LinearLayout(this);
        mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setPadding(dp(10), dp(5), dp(8), dp(5));
        mini.setBackgroundColor(panel());

        ImageView cover = new ImageView(this);
        cover.setImageResource(android.R.drawable.ic_media_play);
        cover.setColorFilter(GREEN);
        mini.addView(cover, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(10), 0, dp(4), 0);
        nowTitle = text("Nenhuma música", 15, mainText());
        nowTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nowTitle.setMaxLines(1);
        nowArtist = text("Escolha uma música", 12, mutedText());
        nowArtist.setMaxLines(1);
        info.addView(nowTitle);
        info.addView(nowArtist);
        mini.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

        playButton = iconButton("▶");
        mini.addView(playButton, new LinearLayout.LayoutParams(dp(58), dp(58)));
        playButton.setOnClickListener(v -> togglePlayback());
        return mini;
    }

    private void requestAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(permission)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permission}, REQUEST_AUDIO);
        } else {
            loadSongs();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_AUDIO && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            loadSongs();
        } else {
            countText.setText("Permissão de música necessária");
        }
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
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
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
                Uri uri = Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        String.valueOf(songId));
                songs.add(new Song(
                        safe(cursor.getString(title), "Sem título"),
                        safe(cursor.getString(artist), "Artista desconhecido"),
                        safe(cursor.getString(album), "Álbum desconhecido"),
                        cursor.getLong(duration),
                        uri));
            }
        }
        refreshList(songs);
    }

    private void refreshList(List<Song> source) {
        visibleSongs.clear();
        visibleSongs.addAll(source);
        songList.setAdapter(new SongAdapter());
        countText.setText(visibleSongs.size() + " músicas");
    }

    private void filterSongs(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        List<Song> result = new ArrayList<>();
        for (Song song : songs) {
            if (q.isEmpty()
                    || song.title.toLowerCase(Locale.ROOT).contains(q)
                    || song.artist.toLowerCase(Locale.ROOT).contains(q)
                    || song.album.toLowerCase(Locale.ROOT).contains(q)) {
                result.add(song);
            }
        }
        refreshList(result);
    }

    private class SongAdapter extends ArrayAdapter<Song> {
        SongAdapter() {
            super(MainActivity.this, android.R.layout.simple_list_item_1, visibleSongs);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Song song = visibleSongs.get(position);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(8), dp(6), dp(8));

            ImageView image = new ImageView(MainActivity.this);
            image.setImageResource(android.R.drawable.ic_media_play);
            image.setColorFilter(GREEN);
            row.addView(image, new LinearLayout.LayoutParams(dp(52), dp(52)));

            LinearLayout info = new LinearLayout(MainActivity.this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(12), 0, dp(4), 0);
            TextView name = text(song.title, 16, mainText());
            name.setMaxLines(2);
            TextView meta = text(song.artist + " · " + song.album, 12, mutedText());
            meta.setMaxLines(1);
            info.addView(name);
            info.addView(meta);
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

            TextView duration = text(formatDuration(song.duration), 12, mutedText());
            row.addView(duration, new LinearLayout.LayoutParams(dp(45), -2));

            Button itemMenu = iconButton("⋮");
            row.addView(itemMenu, new LinearLayout.LayoutParams(dp(42), dp(52)));
            itemMenu.setOnClickListener(v -> showSongMenu(song));
            return row;
        }
    }

    private void play(Song song) {
        releasePlayer();
        currentIndex = songs.indexOf(song);
        try {
            player = MediaPlayer.create(this, song.uri);
            if (player == null) throw new IllegalStateException();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setOnCompletionListener(mp -> playRelative(1));
            player.start();
            nowTitle.setText(song.title);
            nowArtist.setText(song.artist);
            playButton.setText("Ⅱ");
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível reproduzir esta música.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void playRelative(int direction) {
        if (songs.isEmpty()) return;
        int next = currentIndex + direction;
        if (next < 0) next = songs.size() - 1;
        if (next >= songs.size()) next = 0;
        play(songs.get(next));
    }

    private void togglePlayback() {
        if (player == null) {
            if (!songs.isEmpty()) play(songs.get(0));
            return;
        }
        if (player.isPlaying()) {
            player.pause();
            playButton.setText("▶");
        } else {
            player.start();
            playButton.setText("Ⅱ");
        }
    }

    private void showSearch() {
        EditText input = new EditText(this);
        input.setHint("Música, artista ou álbum");
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("Pesquisar")
                .setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Pesquisar", (d, w) ->
                        filterSongs(input.getText().toString()))
                .show();
    }

    private void showSongMenu(Song song) {
        String[] items = {
                "REPRODUZIR",
                "REPRODUZIR A SEGUIR",
                "ADICIONAR À FILA",
                "ADICIONAR AOS FAVORITOS",
                "DETALHES",
                "ENVIAR",
                "DEFINIR COMO TOQUE"
        };
        new AlertDialog.Builder(this)
                .setItems(items, (d, which) -> handleSongAction(which, song))
                .show();
    }

    private void handleSongAction(int action, Song song) {
        if (action == 0) {
            play(song);
            return;
        }
        if (action == 3) {
            Set<String> favorites = new HashSet<>(prefs.getStringSet(
                    "favorites", new HashSet<>()));
            String key = song.uri.toString();
            if (favorites.contains(key)) favorites.remove(key);
            else favorites.add(key);
            prefs.edit().putStringSet("favorites", favorites).apply();
            Toast.makeText(this, "Favoritos atualizados", Toast.LENGTH_SHORT).show();
            return;
        }
        if (action == 4) {
            new AlertDialog.Builder(this)
                    .setTitle(song.title)
                    .setMessage("Artista: " + song.artist
                            + "\nÁlbum: " + song.album
                            + "\nDuração: " + formatDuration(song.duration)
                            + "\n\n" + song.uri)
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        if (action == 5) {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("audio/*");
            share.putExtra(Intent.EXTRA_STREAM, song.uri);
            startActivity(Intent.createChooser(share, "Enviar música"));
            return;
        }
        Toast.makeText(this, "Função será adicionada em breve.",
                Toast.LENGTH_SHORT).show();
    }

    private void showSideMenu() {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(22), dp(26), dp(18), dp(16));
        menu.setBackgroundColor(panel());

        TextView brand = text("AUREN", 24, GREEN);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        menu.addView(brand);
        menu.addView(text("Music Player", 14, mutedText()));

        String[] items = {
                "♛  REMOVER ANÚNCIOS",
                "☷  BIBLIOTECA",
                "☷  EQUALIZADOR",
                "▣  MODO DE CONDUÇÃO",
                "◷  TEMPORIZADOR DE SONO",
                "✂  CORTADOR DE MP3",
                "◉  TEMA",
                "▣  ENCONTRAR DUPLICADOS",
                "♡  ADICIONAR AOS FAVORITOS",
                "⚙  CONFIGURAÇÕES"
        };
        for (String item : items) {
            TextView row = text(item, 16, mainText());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(17), 0, dp(17));
            menu.addView(row);
            row.setOnClickListener(v -> handleDrawerItem(item));
        }

        PopupWindow popup = new PopupWindow(menu, dp(320), -1, true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(panel()));
        popup.setElevation(dp(10));
        popup.showAtLocation(songList, Gravity.LEFT | Gravity.TOP, 0, 0);
    }

    private void handleDrawerItem(String item) {
        if (item.contains("TEMA")) {
            darkMode = !darkMode;
            prefs.edit().putBoolean("dark", darkMode).apply();
            buildUi();
            loadSongs();
        } else if (item.contains("EQUALIZADOR")) {
            openEqualizer();
        } else if (item.contains("TEMPORIZADOR")) {
            showSleepTimer();
        } else {
            Toast.makeText(this, item.replace("♛", "").trim(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showMainMenu() {
        String[] items = {
                "Desbloquear Premium",
                "Reproduzir tudo",
                "Adicionar widget",
                "Ordenar",
                "Configurações"
        };
        new AlertDialog.Builder(this)
                .setItems(items, (d, which) -> {
                    if (which == 1 && !songs.isEmpty()) play(songs.get(0));
                    else if (which == 3) showSortMenu();
                }).show();
    }

    private void showSortMenu() {
        String[] options = {"Título A–Z", "Artista A–Z", "Álbum A–Z"};
        new AlertDialog.Builder(this)
                .setTitle("Ordenar")
                .setItems(options, (d, which) -> {
                    Toast.makeText(this, options[which], Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void openEqualizer() {
        try {
            Intent intent = new Intent(
                    "android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL");
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Equalizador não disponível neste dispositivo.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showSleepTimer() {
        String[] options = {"Desligado", "15 minutos", "30 minutos", "60 minutos"};
        new AlertDialog.Builder(this)
                .setTitle("Temporizador de sono")
                .setItems(options, (d, which) ->
                        Toast.makeText(this, options[which], Toast.LENGTH_SHORT).show())
                .show();
    }

    private Button iconButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(25);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() || "<unknown>".equals(value)
                ? fallback : value;
    }

    private String formatDuration(long millis) {
        long total = millis / 1000;
        return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }

    private static class Song {
        final String title;
        final String artist;
        final String album;
        final long duration;
        final Uri uri;

        Song(String title, String artist, String album, long duration, Uri uri) {
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.duration = duration;
            this.uri = uri;
        }
    }
}
