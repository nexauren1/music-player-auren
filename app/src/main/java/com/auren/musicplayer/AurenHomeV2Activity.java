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

public class AurenHomeV2Activity extends Activity implements PlayerManager.Listener {
    private static final int REQUEST_AUDIO = 100;
    private static final int REQUEST_NOTIFICATIONS = 101;
    private static final int GREEN = Color.rgb(32, 150, 42);
    private static final int TEXT = Color.rgb(35, 36, 40);
    private static final int MUTED = Color.rgb(105, 108, 116);

    private final List<PlayerManager.Song> songs = new ArrayList<>();
    private final List<PlayerManager.Song> visible = new ArrayList<>();
    private SharedPreferences prefs;
    private ListView list;
    private TextView count;
    private TextView nowTitle;
    private TextView nowArtist;
    private Button miniPlay;
    private boolean dark;
    private PopupWindow drawer;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("auren", MODE_PRIVATE);
        dark = prefs.getBoolean("dark", false);
        buildUi();
        PlayerManager.setListener(this);
        requestAudioPermission();
        requestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs != null && list != null) updateMiniPlayer();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg());

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(6), dp(3), dp(4), dp(2));
        bar.setBackgroundColor(GREEN);

        Button menu = icon("☰", Color.WHITE, 28);
        bar.addView(menu, new LinearLayout.LayoutParams(dp(54), dp(54)));
        TextView brand = text("AUREN", 21, Color.WHITE);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(brand, new LinearLayout.LayoutParams(0, dp(54), 1));

        Button search = icon("⌕", Color.WHITE, 30);
        Button more = icon("⋮", Color.WHITE, 28);
        bar.addView(search, new LinearLayout.LayoutParams(dp(52), dp(54)));
        bar.addView(more, new LinearLayout.LayoutParams(dp(46), dp(54)));
        root.addView(bar);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setBackgroundColor(GREEN);
        String[] names = {"MÚSICAS", "ÁLBUNS", "ARTISTAS", "FAVORITOS"};
        for (String name : names) {
            TextView tab = text(name, 12, Color.WHITE);
            tab.setGravity(Gravity.CENTER);
            tab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tab.setClickable(true);
            tabs.addView(tab, new LinearLayout.LayoutParams(0, dp(46), 1));
            tab.setOnClickListener(v -> selectTab(name));
        }
        root.addView(tabs);

        count = text("A carregar músicas...", 14, muted());
        count.setPadding(dp(16), dp(12), dp(16), dp(7));
        root.addView(count);

        list = new ListView(this);
        list.setDivider(null);
        list.setBackgroundColor(bg());
        list.setItemsCanFocus(false);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < visible.size()) {
                playSong(visible.get(position));
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(buildMiniPlayer());

        menu.setOnClickListener(v -> showDrawer());
        search.setOnClickListener(v -> showSearch());
        more.setOnClickListener(v -> showMainMenu());
        setContentView(root);
    }

    private LinearLayout buildMiniPlayer() {
        LinearLayout mini = new LinearLayout(this);
        mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setPadding(dp(10), dp(5), dp(6), dp(5));
        mini.setBackgroundColor(panel());
        mini.setClickable(true);
        mini.setFocusable(false);
        mini.setElevation(dp(3));
        mini.setOnClickListener(v -> openCurrentPlayer());

        ImageView image = new ImageView(this);
        image.setImageResource(android.R.drawable.ic_media_play);
        image.setColorFilter(GREEN);
        image.setClickable(false);
        image.setFocusable(false);
        mini.addView(image, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(10), 0, dp(4), 0);
        info.setClickable(false);
        info.setFocusable(false);

        nowTitle = text("Nenhuma música", 15, mainText());
        nowTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nowTitle.setMaxLines(1);
        nowTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nowTitle.setClickable(false);
        nowTitle.setFocusable(false);

        nowArtist = text("Escolha uma música", 12, muted());
        nowArtist.setMaxLines(1);
        nowArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nowArtist.setClickable(false);
        nowArtist.setFocusable(false);

        info.addView(nowTitle);
        info.addView(nowArtist);
        mini.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

        TextView expand = text("⌃", 20, muted());
        expand.setGravity(Gravity.CENTER);
        expand.setClickable(false);
        expand.setFocusable(false);
        mini.addView(expand, new LinearLayout.LayoutParams(dp(28), dp(54)));

        miniPlay = icon("▶", GREEN, 24);
        miniPlay.setFocusable(false);
        miniPlay.setFocusableInTouchMode(false);
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

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_AUDIO) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                loadSongs();
            } else {
                count.setText("Permissão para acessar músicas é necessária");
            }
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
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                MediaStore.Audio.Media.IS_MUSIC + " != 0",
                null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (cursor == null) {
                count.setText("Não foi possível ler a biblioteca");
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
        } catch (Exception e) {
            count.setText("Erro ao carregar a biblioteca");
            return;
        }
        PlayerManager.setQueue(songs.toArray(new PlayerManager.Song[0]));
        refresh(songs, songs.size() + " músicas");
    }

    private void refresh(List<PlayerManager.Song> source, String label) {
        visible.clear();
        visible.addAll(source);
        list.setAdapter(new SongAdapter());
        count.setText(label);
    }

    private class SongAdapter extends ArrayAdapter<PlayerManager.Song> {
        SongAdapter() {
            super(AurenHomeV2Activity.this, android.R.layout.simple_list_item_1, visible);
        }

        @Override
        public View getView(int position, View old, ViewGroup parent) {
            PlayerManager.Song song = visible.get(position);
            LinearLayout row = new LinearLayout(AurenHomeV2Activity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(8), dp(6), dp(8));
            row.setClickable(true);
            row.setFocusable(false);
            row.setOnClickListener(v -> playSong(song));

            ImageView image = new ImageView(AurenHomeV2Activity.this);
            image.setImageResource(android.R.drawable.ic_media_play);
            image.setColorFilter(GREEN);
            image.setClickable(false);
            image.setFocusable(false);
            row.addView(image, new LinearLayout.LayoutParams(dp(54), dp(54)));

            LinearLayout info = new LinearLayout(AurenHomeV2Activity.this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(12), 0, dp(4), 0);
            info.setClickable(false);
            info.setFocusable(false);

            TextView title = text(song.title, 16, mainText());
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setClickable(false);
            title.setFocusable(false);

            TextView meta = text(song.artist + " · " + song.album, 12, muted());
            meta.setMaxLines(1);
            meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
            meta.setClickable(false);
            meta.setFocusable(false);

            info.addView(title);
            info.addView(meta);
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

            TextView duration = text(format(song.duration), 12, muted());
            duration.setGravity(Gravity.CENTER);
            duration.setClickable(false);
            duration.setFocusable(false);
            row.addView(duration, new LinearLayout.LayoutParams(dp(45), -2));

            Button actions = icon("⋮", mainText(), 25);
            actions.setFocusable(false);
            actions.setFocusableInTouchMode(false);
            actions.setOnClickListener(v -> songMenu(song));
            row.addView(actions, new LinearLayout.LayoutParams(dp(44), dp(54)));
            return row;
        }
    }

    private void playSong(PlayerManager.Song song) {
        if (song == null) return;
        PlayerManager.setQueue(songs.toArray(new PlayerManager.Song[0]));
        PlayerManager.play(this, song);
        updateMiniPlayer();
        Toast.makeText(this, "A reproduzir: " + song.title, Toast.LENGTH_SHORT).show();
    }

    private void openCurrentPlayer() {
        PlayerManager.Song song = PlayerManager.getCurrentSong();
        if (song == null) return;
        openPlayer(song);
    }

    private void openPlayer(PlayerManager.Song song) {
        if (song == null) return;
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("title", song.title);
        intent.putExtra("artist", song.artist);
        intent.putExtra("album", song.album);
        intent.putExtra("duration", song.duration);
        intent.putExtra("uri", song.uri);
        startActivity(intent);
    }

    private void showSearch() {
        EditText input = new EditText(this);
        input.setHint("Música, artista ou álbum");
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("Pesquisar música")
                .setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Pesquisar", (d, w) -> filter(input.getText().toString()))
                .show();
    }

    private void filter(String value) {
        String q = value.toLowerCase(Locale.ROOT).trim();
        List<PlayerManager.Song> result = new ArrayList<>();
        for (PlayerManager.Song song : songs) {
            if (q.isEmpty()
                    || song.title.toLowerCase(Locale.ROOT).contains(q)
                    || song.artist.toLowerCase(Locale.ROOT).contains(q)
                    || song.album.toLowerCase(Locale.ROOT).contains(q)) {
                result.add(song);
            }
        }
        refresh(result, result.size() + " resultados");
    }

    private void selectTab(String tab) {
        if (tab.equals("MÚSICAS")) {
            refresh(songs, songs.size() + " músicas");
        } else if (tab.equals("FAVORITOS")) {
            showFavorites();
        } else if (tab.equals("ARTISTAS")) {
            showUniqueArtists();
        } else {
            showUniqueAlbums();
        }
    }

    private void showFavorites() {
        Set<String> fav = prefs.getStringSet("favorites", new HashSet<>());
        List<PlayerManager.Song> result = new ArrayList<>();
        for (PlayerManager.Song song : songs) {
            if (fav.contains(song.uri.toString())) result.add(song);
        }
        refresh(result, result.size() + " favoritos");
    }

    private void showUniqueArtists() {
        List<PlayerManager.Song> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PlayerManager.Song song : songs) {
            if (seen.add(song.artist)) result.add(song);
        }
        refresh(result, result.size() + " artistas");
    }

    private void showUniqueAlbums() {
        List<PlayerManager.Song> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PlayerManager.Song song : songs) {
            if (seen.add(song.album)) result.add(song);
        }
        refresh(result, result.size() + " álbuns");
    }

    private void songMenu(PlayerManager.Song song) {
        String[] items = {
                "REPRODUZIR",
                "REPRODUZIR A SEGUIR",
                "ADICIONAR AOS FAVORITOS",
                "DETALHES",
                "ENVIAR"
        };
        new AlertDialog.Builder(this).setItems(items, (d, which) -> {
            if (which == 0) playSong(song);
            else if (which == 1) {
                PlayerManager.play(this, song);
                Toast.makeText(this, "A reproduzir a seguir: " + song.title, Toast.LENGTH_SHORT).show();
            } else if (which == 2) toggleFavorite(song);
            else if (which == 3) details(song);
            else share(song);
        }).show();
    }

    private void toggleFavorite(PlayerManager.Song song) {
        Set<String> favorites = new HashSet<>(prefs.getStringSet("favorites", new HashSet<>()));
        if (!favorites.add(song.uri.toString())) favorites.remove(song.uri.toString());
        prefs.edit().putStringSet("favorites", favorites).apply();
        Toast.makeText(this, "Favoritos atualizados", Toast.LENGTH_SHORT).show();
    }

    private void details(PlayerManager.Song song) {
        new AlertDialog.Builder(this)
                .setTitle(song.title)
                .setMessage("Artista: " + song.artist
                        + "\nÁlbum: " + song.album
                        + "\nDuração: " + format(song.duration)
                        + "\n\nOrigem: " + song.uri)
                .setPositiveButton("OK", null)
                .show();
    }

    private void share(PlayerManager.Song song) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_STREAM, song.uri);
        startActivity(Intent.createChooser(intent, "Enviar música"));
    }

    private void showMainMenu() {
        String[] items = {"Reproduzir tudo", "Aleatório", "Tema", "Configurações"};
        new AlertDialog.Builder(this).setItems(items, (d, which) -> {
            if (which == 0 && !songs.isEmpty()) playSong(songs.get(0));
            else if (which == 1) {
                PlayerManager.setShuffle(!PlayerManager.isShuffle());
                Toast.makeText(this,
                        PlayerManager.isShuffle() ? "Aleatório ligado" : "Aleatório desligado",
                        Toast.LENGTH_SHORT).show();
            } else if (which == 2) toggleTheme();
            else openSettings();
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
                "♛  REMOVER ANÚNCIOS", "☷  BIBLIOTECA", "☷  EQUALIZADOR",
                "▣  MODO DE CONDUÇÃO", "◷  TEMPORIZADOR DE SONO",
                "✂  CORTADOR DE MP3", "◉  TEMA", "▣  ENCONTRAR DUPLICADOS",
                "♡  FAVORITOS", "⚙  CONFIGURAÇÕES"
        };
        for (String item : items) {
            TextView row = text(item, 16, mainText());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(16), 0, dp(16));
            menu.addView(row);
            row.setOnClickListener(v -> drawerAction(item));
        }

        drawer = new PopupWindow(menu, dp(320), -1, true);
        drawer.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(panel()));
        drawer.setElevation(dp(10));
        drawer.setOutsideTouchable(true);
        drawer.showAtLocation(list, Gravity.LEFT | Gravity.TOP, 0, 0);
    }

    private void drawerAction(String item) {
        if (drawer != null) drawer.dismiss();
        if (item.contains("CONFIGURAÇÕES")) openSettings();
        else if (item.contains("TEMA")) toggleTheme();
        else if (item.contains("EQUALIZADOR")) openFeature("Equalizador", "Controlos de áudio do dispositivo.");
        else if (item.contains("MODO DE CONDUÇÃO")) openFeature("Modo de condução", "Interface simplificada para utilizar o player durante a condução.");
        else if (item.contains("TEMPORIZADOR")) openFeature("Temporizador de sono", "Escolha quando a reprodução deve parar.");
        else if (item.contains("CORTADOR")) openFeature("Cortador de MP3", "Ferramenta para selecionar uma parte de um áudio.");
        else if (item.contains("DUPLICADOS")) openFeature("Encontrar duplicados", "Analise títulos e ficheiros repetidos na biblioteca.");
        else if (item.contains("FAVORITOS")) showFavorites();
        else if (item.contains("BIBLIOTECA")) refresh(songs, songs.size() + " músicas");
        else Toast.makeText(this, "Premium ficará disponível numa próxima versão.", Toast.LENGTH_SHORT).show();
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void openFeature(String title, String description) {
        Intent intent = new Intent(this, FeatureActivity.class);
        intent.putExtra("title", title);
        intent.putExtra("description", description);
        startActivity(intent);
    }

    private void toggleTheme() {
        dark = !dark;
        prefs.edit().putBoolean("dark", dark).apply();
        buildUi();
        PlayerManager.setListener(this);
        if (checkSelfPermission(Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            loadSongs();
        }
    }

    private void updateMiniPlayer() {
        PlayerManager.Song song = PlayerManager.getCurrentSong();
        if (song == null) {
            nowTitle.setText("Nenhuma música");
            nowArtist.setText("Escolha uma música");
        } else {
            nowTitle.setText(song.title);
            nowArtist.setText(song.artist);
        }
        miniPlay.setText(PlayerManager.isPlaying() ? "Ⅱ" : "▶");
    }

    @Override
    public void onPlayerChanged() {
        runOnUiThread(this::updateMiniPlayer);
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
        if (value == null || value.trim().isEmpty() || "<unknown>".equalsIgnoreCase(value)) {
            return fallback;
        }
        return value;
    }

    private String format(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        if (drawer != null) drawer.dismiss();
        PlayerManager.setListener(null);
        super.onDestroy();
    }
}