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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HomeActivity extends Activity implements PlayerManager.Listener {
    private static final int REQUEST_AUDIO = 100;
    private static final int GREEN = Color.rgb(32, 150, 42);
    private static final int TEXT = Color.rgb(35, 36, 40);
    private static final int MUTED = Color.rgb(105, 108, 116);

    private final List<PlayerManager.Song> songs = new ArrayList<>();
    private final List<PlayerManager.Song> visible = new ArrayList<>();
    private ListView list;
    private TextView count;
    private TextView nowTitle;
    private TextView nowArtist;
    private Button miniPlay;
    private SharedPreferences prefs;
    private boolean dark;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("auren", MODE_PRIVATE);
        dark = prefs.getBoolean("dark", false);
        buildUi();
        PlayerManager.setListener(this);
        requestAudioPermission();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg());

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(4), dp(4), dp(2));
        bar.setBackgroundColor(GREEN);

        Button menu = icon("☰", Color.WHITE, 27);
        bar.addView(menu, new LinearLayout.LayoutParams(dp(52), dp(54)));
        TextView brand = text("AUREN", 20, Color.WHITE);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(brand, new LinearLayout.LayoutParams(0, dp(54), 1));

        Button search = icon("⌕", Color.WHITE, 29);
        Button more = icon("⋮", Color.WHITE, 27);
        bar.addView(search, new LinearLayout.LayoutParams(dp(50), dp(54)));
        bar.addView(more, new LinearLayout.LayoutParams(dp(46), dp(54)));
        root.addView(bar);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setBackgroundColor(GREEN);
        String[] names = {"ÁLBUM", "MÚSICAS", "ARTISTAS", "PASTA", "LISTA"};
        for (String name : names) {
            TextView tab = text(name, 12, Color.WHITE);
            tab.setGravity(Gravity.CENTER);
            tab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tabs.addView(tab, new LinearLayout.LayoutParams(0, dp(46), 1));
            tab.setOnClickListener(v -> showSection(name));
        }
        root.addView(tabs);

        count = text("A carregar músicas...", 13, muted());
        count.setPadding(dp(16), dp(10), dp(16), dp(6));
        root.addView(count);

        list = new ListView(this);
        list.setDivider(null);
        list.setBackgroundColor(bg());
        list.setOnItemClickListener((p, v, position, id) -> openSong(visible.get(position)));
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        root.addView(buildMiniPlayer());

        menu.setOnClickListener(v -> showDrawer());
        search.setOnClickListener(v -> search());
        more.setOnClickListener(v -> mainMenu());
        setContentView(root);
    }

    private LinearLayout buildMiniPlayer() {
        LinearLayout mini = new LinearLayout(this);
        mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setPadding(dp(10), dp(5), dp(8), dp(5));
        mini.setBackgroundColor(panel());
        mini.setOnClickListener(v -> openCurrentPlayer());

        ImageView image = new ImageView(this);
        image.setImageResource(android.R.drawable.ic_media_play);
        image.setColorFilter(GREEN);
        mini.addView(image, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(10), 0, dp(4), 0);
        nowTitle = text("Nenhuma música", 15, mainText());
        nowTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nowTitle.setMaxLines(1);
        nowArtist = text("Escolha uma música", 12, muted());
        nowArtist.setMaxLines(1);
        info.addView(nowTitle);
        info.addView(nowArtist);
        mini.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

        miniPlay = icon("▶", GREEN, 23);
        mini.addView(miniPlay, new LinearLayout.LayoutParams(dp(58), dp(58)));
        miniPlay.setOnClickListener(v -> PlayerManager.toggle());
        return mini;
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
            count.setText("Permissão de música necessária");
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
            if (cursor == null) {
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
                songs.add(new PlayerManager.Song(
                        safe(cursor.getString(title), "Sem título"),
                        safe(cursor.getString(artist), "Artista desconhecido"),
                        safe(cursor.getString(album), "Álbum desconhecido"),
                        cursor.getLong(duration),
                        uri));
            }
        }
        refresh(songs);
    }

    private void refresh(List<PlayerManager.Song> source) {
        visible.clear();
        visible.addAll(source);
        list.setAdapter(new SongAdapter());
        count.setText(visible.size() + " músicas");
    }

    private void filter(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        List<PlayerManager.Song> result = new ArrayList<>();
        for (PlayerManager.Song song : songs) {
            if (q.isEmpty()
                    || song.title.toLowerCase(Locale.ROOT).contains(q)
                    || song.artist.toLowerCase(Locale.ROOT).contains(q)
                    || song.album.toLowerCase(Locale.ROOT).contains(q)) {
                result.add(song);
            }
        }
        refresh(result);
    }

    private class SongAdapter extends ArrayAdapter<PlayerManager.Song> {
        SongAdapter() {
            super(HomeActivity.this, android.R.layout.simple_list_item_1, visible);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            PlayerManager.Song song = visible.get(position);
            LinearLayout row = new LinearLayout(HomeActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(8), dp(6), dp(8));

            ImageView image = new ImageView(HomeActivity.this);
            image.setImageResource(android.R.drawable.ic_media_play);
            image.setColorFilter(GREEN);
            row.addView(image, new LinearLayout.LayoutParams(dp(54), dp(54)));

            LinearLayout info = new LinearLayout(HomeActivity.this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(12), 0, dp(4), 0);
            TextView title = text(song.title, 16, mainText());
            title.setMaxLines(2);
            TextView meta = text(song.artist + " · " + song.album, 12, muted());
            meta.setMaxLines(1);
            info.addView(title);
            info.addView(meta);
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

            TextView duration = text(format(song.duration), 12, muted());
            row.addView(duration, new LinearLayout.LayoutParams(dp(45), -2));
            Button actions = icon("⋮", mainText(), 25);
            row.addView(actions, new LinearLayout.LayoutParams(dp(44), dp(54)));
            actions.setOnClickListener(v -> songMenu(song));
            return row;
        }
    }

    private void openSong(PlayerManager.Song song) {
        PlayerManager.play(this, song);
        openCurrentPlayer();
    }

    private void openCurrentPlayer() {
        PlayerManager.Song song = PlayerManager.getCurrentSong();
        if (song == null) {
            if (songs.isEmpty()) return;
            song = songs.get(0);
            PlayerManager.play(this, song);
        }
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("title", song.title);
        intent.putExtra("artist", song.artist);
        intent.putExtra("album", song.album);
        intent.putExtra("duration", song.duration);
        intent.putExtra("uri", song.uri);
        startActivity(intent);
    }

    private void search() {
        EditText input = new EditText(this);
        input.setHint("Música, artista ou álbum");
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("Pesquisar")
                .setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Pesquisar", (d, w) -> filter(input.getText().toString()))
                .show();
    }

    private void songMenu(PlayerManager.Song song) {
        String[] items = {
                "REPRODUZIR",
                "REPRODUZIR A SEGUIR",
                "ADICIONAR À FILA",
                "ADICIONAR AOS FAVORITOS",
                "DETALHES",
                "ENVIAR"
        };
        new AlertDialog.Builder(this)
                .setItems(items, (d, which) -> {
                    if (which == 0) openSong(song);
                    else if (which == 3) toggleFavorite(song);
                    else if (which == 4) details(song);
                    else if (which == 5) share(song);
                    else Toast.makeText(this, "Função preparada para a próxima evolução.",
                            Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void toggleFavorite(PlayerManager.Song song) {
        Set<String> set = new HashSet<>(prefs.getStringSet("favorites", new HashSet<>()));
        String key = song.uri.toString();
        if (set.contains(key)) set.remove(key);
        else set.add(key);
        prefs.edit().putStringSet("favorites", set).apply();
        Toast.makeText(this, "Favoritos atualizados", Toast.LENGTH_SHORT).show();
    }

    private void details(PlayerManager.Song song) {
        new AlertDialog.Builder(this)
                .setTitle(song.title)
                .setMessage("Artista: " + song.artist
                        + "\nÁlbum: " + song.album
                        + "\nDuração: " + format(song.duration)
                        + "\n\n" + song.uri)
                .setPositiveButton("OK", null)
                .show();
    }

    private void share(PlayerManager.Song song) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_STREAM, song.uri);
        startActivity(Intent.createChooser(intent, "Enviar música"));
    }

    private void showSection(String section) {
        if (section.equals("MÚSICAS")) {
            refresh(songs);
        } else if (section.equals("ARTISTAS")) {
            showArtists();
        } else if (section.equals("ÁLBUM")) {
            showAlbums();
        } else {
            Toast.makeText(this, section + " será evoluído na próxima etapa.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showArtists() {
        List<PlayerManager.Song> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PlayerManager.Song song : songs) {
            if (seen.add(song.artist)) result.add(song);
        }
        refresh(result);
        count.setText(seen.size() + " artistas");
    }

    private void showAlbums() {
        List<PlayerManager.Song> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PlayerManager.Song song : songs) {
            if (seen.add(song.album)) result.add(song);
        }
        refresh(result);
        count.setText(seen.size() + " álbuns");
    }

    private void mainMenu() {
        String[] items = {"Reproduzir tudo", "Aleatório", "Tema", "Configurações"};
        new AlertDialog.Builder(this)
                .setItems(items, (d, which) -> {
                    if (which == 0 && !songs.isEmpty()) openSong(songs.get(0));
                    else if (which == 1) {
                        PlayerManager.setShuffle(!PlayerManager.isShuffle());
                        Toast.makeText(this, "Aleatório: "
                                + (PlayerManager.isShuffle() ? "ligado" : "desligado"),
                                Toast.LENGTH_SHORT).show();
                    } else if (which == 2) toggleTheme();
                }).show();
    }

    private void showDrawer() {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(22), dp(28), dp(18), dp(16));
        menu.setBackgroundColor(panel());
        TextView title = text("AUREN", 25, GREEN);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        menu.addView(title);
        menu.addView(text("Music Player", 14, muted()));

        String[] items = {
                "♛  REMOVER ANÚNCIOS",
                "☷  BIBLIOTECA",
                "☷  EQUALIZADOR",
                "▣  MODO DE CONDUÇÃO",
                "◷  TEMPORIZADOR DE SONO",
                "✂  CORTADOR DE MP3",
                "◉  TEMA",
                "▣  ENCONTRAR DUPLICADOS",
                "♡  FAVORITOS",
                "⚙  CONFIGURAÇÕES"
        };
        for (String item : items) {
            TextView row = text(item, 16, mainText());
            row.setPadding(0, dp(16), 0, dp(16));
            menu.addView(row);
            row.setOnClickListener(v -> drawerAction(item));
        }
        PopupWindow popup = new PopupWindow(menu, dp(320), -1, true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(panel()));
        popup.setElevation(dp(10));
        popup.showAtLocation(list, Gravity.LEFT | Gravity.TOP, 0, 0);
    }

    private void drawerAction(String item) {
        if (item.contains("TEMA")) toggleTheme();
        else if (item.contains("EQUALIZADOR")) {
            try {
                startActivity(new Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL"));
            } catch (Exception e) {
                Toast.makeText(this, "Equalizador não disponível.", Toast.LENGTH_SHORT).show();
            }
        } else if (item.contains("TEMPORIZADOR")) {
            new AlertDialog.Builder(this)
                    .setTitle("Temporizador de sono")
                    .setItems(new String[]{"Desligado", "15 minutos", "30 minutos", "60 minutos"},
                            (d, w) -> Toast.makeText(this, "Temporizador selecionado",
                                    Toast.LENGTH_SHORT).show())
                    .show();
        } else if (item.contains("FAVORITOS")) showFavorites();
        else Toast.makeText(this, item.replace("♛", "").trim(), Toast.LENGTH_SHORT).show();
    }

    private void showFavorites() {
        Set<String> fav = prefs.getStringSet("favorites", new HashSet<>());
        List<PlayerManager.Song> result = new ArrayList<>();
        for (PlayerManager.Song song : songs) {
            if (fav.contains(song.uri.toString())) result.add(song);
        }
        refresh(result);
        count.setText(result.size() + " favoritos");
    }

    private void toggleTheme() {
        dark = !dark;
        prefs.edit().putBoolean("dark", dark).apply();
        buildUi();
        loadSongs();
    }

    @Override
    public void onPlayerChanged() {
        runOnUiThread(() -> {
            PlayerManager.Song song = PlayerManager.getCurrentSong();
            if (song != null) {
                nowTitle.setText(song.title);
                nowArtist.setText(song.artist);
            }
            miniPlay.setText(PlayerManager.isPlaying() ? "Ⅱ" : "▶");
        });
    }

    private int bg() {
        return dark ? Color.rgb(20, 22, 24) : Color.rgb(247, 248, 250);
    }

    private int panel() {
        return dark ? Color.rgb(31, 34, 37) : Color.WHITE;
    }

    private int mainText() {
        return dark ? Color.WHITE : TEXT;
    }

    private int muted() {
        return dark ? Color.rgb(185, 188, 193) : MUTED;
    }

    private Button icon(String value, int color, int size) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(color);
        b.setTextSize(size);
        b.setAllCaps(false);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() || "<unknown>".equals(value)
                ? fallback : value;
    }

    private String format(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
