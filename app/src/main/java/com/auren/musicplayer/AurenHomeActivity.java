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

public class AurenHomeActivity extends Activity implements PlayerManager.Listener {
    private static final int REQUEST_AUDIO = 100;
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
        list.setOnItemClickListener((p, v, position, id) -> openSong(visible.get(position)));
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

        miniPlay = icon("▶", GREEN, 24);
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
        } else loadSongs();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_AUDIO && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) loadSongs();
        else count.setText("Permissão de música necessária");
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
                Uri uri = Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        String.valueOf(songId));
                songs.add(new PlayerManager.Song(
                        safe(cursor.getString(title), "Sem título"),
                        safe(cursor.getString(artist), "Artista desconhecido"),
                        safe(cursor.getString(album), "Álbum desconhecido"),
                        cursor.getLong(duration), uri));
            }
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
            super(AurenHomeActivity.this, android.R.layout.simple_list_item_1, visible);
        }

        @Override
        public View getView(int position, View old, ViewGroup parent) {
            PlayerManager.Song song = visible.get(position);
            LinearLayout row = new LinearLayout(AurenHomeActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(8), dp(6), dp(8));

            ImageView image = new ImageView(AurenHomeActivity.this);
            image.setImageResource(android.R.drawable.ic_media_play);
            image.setColorFilter(GREEN);
            row.addView(image, new LinearLayout.LayoutParams(dp(54), dp(54)));

            LinearLayout info = new LinearLayout(AurenHomeActivity.this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(12), 0, dp(4), 0);
            TextView title = text(song.title, 16, mainText());
            title.setMaxLines(2);
            TextView meta = text(song.artist + " · " + song.album, 12, muted());
            meta.setMaxLines(1);
            info.addView(title);
            info.addView(meta);
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

            row.addView(text(format(song.duration), 12, muted()),
                    new LinearLayout.LayoutParams(dp(45), -2));
            Button actions = icon("⋮", mainText(), 25);
            row.addView(actions, new LinearLayout.LayoutParams(dp(44), dp(54)));
            actions.setOnClickListener(v -> songMenu(song));
            return row;
        }
    }

    private void openSong(PlayerManager.Song song) {
        PlayerManager.play(this, song);
        openPlayer(song);
    }

    private void openCurrentPlayer() {
        PlayerManager.Song song = PlayerManager.getCurrentSong();
        if (song == null && !songs.isEmpty()) song = songs.get(0);
        if (song == null) return;
        if (!PlayerManager.isPlaying() && PlayerManager.getPosition() == 0) {
            PlayerManager.play(this, song);
        }
        openPlayer(song);
    }

    private void openPlayer(PlayerManager.Song song) {
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
        new AlertDialog.Builder(this).setTitle("Pesquisar").setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Pesquisar", (d, w) -> filter(input.getText().toString()))
                .show();
    }

    private void filter(String value) {
        String q = value.toLowerCase(Locale.ROOT).trim();
        List<PlayerManager.Song> result = new ArrayList<>();
        for (PlayerManager.Song song : songs) {
            if (q.isEmpty() || song.title.toLowerCase(Locale.ROOT).contains(q)
                    || song.artist.toLowerCase(Locale.ROOT).contains(q)
                    || song.album.toLowerCase(Locale.ROOT).contains(q)) result.add(song);
        }
        refresh(result, result.size() + " resultados");
    }

    private void selectTab(String tab) {
        if (tab.equals("MÚSICAS")) refresh(songs, songs.size() + " músicas");
        else if (tab.equals("FAVORITOS")) showFavorites();
        else if (tab.equals("ARTISTAS")) showUniqueArtists();
        else showUniqueAlbums();
    }

    private void showFavorites() {
        Set<String> fav = prefs.getStringSet("favorites", new HashSet<>());
        List<PlayerManager.Song> result = new ArrayList<>();
        for (PlayerManager.Song s : songs) if (fav.contains(s.uri.toString())) result.add(s);
        refresh(result, result.size() + " favoritos");
    }

    private void showUniqueArtists() {
        List<PlayerManager.Song> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PlayerManager.Song s : songs) if (seen.add(s.artist)) result.add(s);
        refresh(result, seen.size() + " artistas");
    }

    private void showUniqueAlbums() {
        List<PlayerManager.Song> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PlayerManager.Song s : songs) if (seen.add(s.album)) result.add(s);
        refresh(result, seen.size() + " álbuns");
    }

    private void songMenu(PlayerManager.Song song) {
        String[] items = {"REPRODUZIR", "ADICIONAR AOS FAVORITOS", "DETALHES", "ENVIAR"};
        new AlertDialog.Builder(this).setItems(items, (d, w) -> {
            if (w == 0) openSong(song);
            else if (w == 1) toggleFavorite(song);
            else if (w == 2) details(song);
            else share(song);
        }).show();
    }

    private void toggleFavorite(PlayerManager.Song song) {
        Set<String> fav = new HashSet<>(prefs.getStringSet("favorites", new HashSet<>()));
        if (!fav.add(song.uri.toString())) fav.remove(song.uri.toString());
        prefs.edit().putStringSet("favorites", fav).apply();
        Toast.makeText(this, "Favoritos atualizados", Toast.LENGTH_SHORT).show();
    }

    private void details(PlayerManager.Song song) {
        new AlertDialog.Builder(this).setTitle(song.title)
                .setMessage("Artista: " + song.artist + "\nÁlbum: " + song.album
                        + "\nDuração: " + format(song.duration) + "\n\n" + song.uri)
                .setPositiveButton("OK", null).show();
    }

    private void share(PlayerManager.Song song) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("audio/*");
        i.putExtra(Intent.EXTRA_STREAM, song.uri);
        startActivity(Intent.createChooser(i, "Enviar música"));
    }

    private void showMainMenu() {
        String[] items = {"Reproduzir tudo", "Aleatório", "Tema", "Configurações"};
        new AlertDialog.Builder(this).setItems(items, (d, w) -> {
            if (w == 0 && !songs.isEmpty()) openSong(songs.get(0));
            else if (w == 1) PlayerManager.setShuffle(!PlayerManager.isShuffle());
            else if (w == 2) toggleTheme();
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
        if (item.contains("CONFIGURAÇÕES")) openSettings();
        else if (item.contains("TEMA")) toggleTheme();
        else if (item.contains("EQUALIZADOR")) {
            try { startActivity(new Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL")); }
            catch (Exception e) { openFeature("Equalizador", "Controlos de áudio do dispositivo."); }
        } else if (item.contains("FAVORITOS")) showFavorites();
        else if (item.contains("TEMPORIZADOR")) showTimer();
        else if (item.contains("BIBLIOTECA")) refresh(songs, songs.size() + " músicas");
        else if (item.contains("MODO DE CONDUÇÃO")) openFeature("Modo de condução", "Interface simplificada para utilização durante a condução.");
        else if (item.contains("CORTADOR")) openFeature("Cortador de MP3", "Área preparada para cortar uma faixa de áudio.");
        else if (item.contains("DUPLICADOS")) openFeature("Encontrar duplicados", "Área preparada para comparar músicas da biblioteca.");
        else openFeature(item.replace("♛", "").trim(), "Esta função será adicionada ao Auren.");
    }

    private void showTimer() {
        new AlertDialog.Builder(this).setTitle("Temporizador de sono")
                .setItems(new String[]{"Desligado", "15 minutos", "30 minutos", "60 minutos"},
                        (d, w) -> Toast.makeText(this, "Temporizador selecionado", Toast.LENGTH_SHORT).show())
                .show();
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void openFeature(String title, String description) {
        Intent i = new Intent(this, FeatureActivity.class);
        i.putExtra("title", title);
        i.putExtra("description", description);
        startActivity(i);
    }

    private void toggleTheme() {
        dark = !dark;
        prefs.edit().putBoolean("dark", dark).apply();
        buildUi();
        PlayerManager.setListener(this);
        loadSongs();
    }

    @Override
    public void onPlayerChanged() {
        runOnUiThread(() -> {
            PlayerManager.Song s = PlayerManager.getCurrentSong();
            if (s != null) {
                nowTitle.setText(s.title);
                nowArtist.setText(s.artist);
            }
            miniPlay.setText(PlayerManager.isPlaying() ? "Ⅱ" : "▶");
        });
    }

    private int bg() { return dark ? Color.rgb(20, 22, 24) : Color.rgb(247, 248, 250); }
    private int panel() { return dark ? Color.rgb(31, 34, 37) : Color.WHITE; }
    private int mainText() { return dark ? Color.WHITE : TEXT; }
    private int muted() { return dark ? Color.rgb(185, 188, 193) : MUTED; }

    private Button icon(String value, int color, int size) {
        Button b = new Button(this);
        b.setText(value); b.setTextColor(color); b.setTextSize(size);
        b.setAllCaps(false); b.setBackgroundColor(Color.TRANSPARENT);
        b.setPadding(0, 0, 0, 0); return b;
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color); return t;
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() || "<unknown>".equals(value) ? fallback : value;
    }

    private String format(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
