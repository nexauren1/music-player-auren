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
import android.text.TextUtils;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AurenHomeV3Activity extends Activity
        implements PlayerManager.Listener {

    private static final int REQUEST_AUDIO = 100;
    private static final int REQUEST_NOTIFICATIONS = 101;
    private static final int GREEN = Color.rgb(32, 150, 42);
    private static final int TEXT = Color.rgb(35, 36, 40);
    private static final int MUTED = Color.rgb(105, 108, 116);

    private final List<PlayerManager.Song> songs = new ArrayList<>();
    private final List<PlayerManager.Song> visible = new ArrayList<>();
    private final Map<String, Bitmap> artworkCache = new HashMap<>();
    private final ExecutorService artworkExecutor =
            Executors.newFixedThreadPool(3);

    private SharedPreferences prefs;
    private ListView list;
    private TextView count;
    private TextView nowTitle;
    private TextView nowArtist;
    private ImageView miniArtwork;
    private Button miniPlay;
    private boolean dark;
    private PopupWindow drawer;
    private PopupWindow miniMenu;

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
    protected void onDestroy() {
        PlayerManager.setListener(null);
        if (miniMenu != null && miniMenu.isShowing()) {
            miniMenu.dismiss();
        }
        artworkExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateMiniPlayer();
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
        bar.addView(menu,
                new LinearLayout.LayoutParams(dp(54), dp(54)));

        TextView brand = text("AUREN", 21, Color.WHITE);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(brand,
                new LinearLayout.LayoutParams(0, dp(54), 1));

        Button search = icon("⌕", Color.WHITE, 30);
        Button more = icon("⋮", Color.WHITE, 28);
        bar.addView(search,
                new LinearLayout.LayoutParams(dp(52), dp(54)));
        bar.addView(more,
                new LinearLayout.LayoutParams(dp(46), dp(54)));
        root.addView(bar);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setBackgroundColor(GREEN);
        String[] names = {
                "MÚSICAS", "ÁLBUNS", "ARTISTAS", "FAVORITOS"
        };
        for (String name : names) {
            TextView tab = text(name, 12, Color.WHITE);
            tab.setGravity(Gravity.CENTER);
            tab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tab.setClickable(true);
            tabs.addView(tab,
                    new LinearLayout.LayoutParams(0, dp(46), 1));
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
        list.setPadding(0, dp(2), 0, dp(8));
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < visible.size()) {
                playSong(visible.get(position));
            }
        });
        root.addView(list,
                new LinearLayout.LayoutParams(-1, 0, 1));
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

        miniArtwork = new ImageView(this);
        miniArtwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniArtwork.setImageResource(
                android.R.drawable.ic_media_play);
        miniArtwork.setColorFilter(GREEN);
        miniArtwork.setClickable(false);
        miniArtwork.setFocusable(false);
        mini.addView(miniArtwork,
                new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(10), 0, dp(4), 0);
        info.setClickable(false);
        info.setFocusable(false);

        nowTitle = text("Nenhuma música", 15, mainText());
        nowTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nowTitle.setMaxLines(1);
        nowTitle.setEllipsize(TextUtils.TruncateAt.END);
        nowTitle.setClickable(false);
        nowTitle.setFocusable(false);

        nowArtist = text("Escolha uma música", 12, muted());
        nowArtist.setMaxLines(1);
        nowArtist.setEllipsize(TextUtils.TruncateAt.END);
        nowArtist.setClickable(false);
        nowArtist.setFocusable(false);

        info.addView(nowTitle);
        info.addView(nowArtist);
        mini.addView(info,
                new LinearLayout.LayoutParams(0, -2, 1));

        TextView expand = text("▼", 16, muted());
        expand.setGravity(Gravity.CENTER);
        expand.setClickable(true);
        expand.setFocusable(true);
        expand.setOnClickListener(v -> showMiniMenu());
        mini.addView(expand,
                new LinearLayout.LayoutParams(dp(38), dp(54)));

        miniPlay = icon("▶", GREEN, 24);
        miniPlay.setFocusable(false);
        miniPlay.setFocusableInTouchMode(false);
        mini.addView(miniPlay,
                new LinearLayout.LayoutParams(dp(58), dp(58)));
        miniPlay.setOnClickListener(v -> PlayerManager.toggle());
        return mini;
    }

    private void showMiniMenu() {
        if (miniMenu != null && miniMenu.isShowing()) {
            miniMenu.dismiss();
            return;
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(12), dp(18), dp(12));
        box.setBackgroundColor(panel());

        miniMenu = new PopupWindow(
                box, dp(320), dp(64), true);
        miniMenu.setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(
                        panel()));
        miniMenu.setElevation(dp(8));
        miniMenu.setOutsideTouchable(true);
        miniMenu.showAsDropDown(
                miniArtwork,
                dp(8),
                -dp(112));
    }

    private void requestAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(permission)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{permission}, REQUEST_AUDIO);
        } else {
            loadSongs();
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] results) {
        super.onRequestPermissionsResult(
                requestCode, permissions, results);
        if (requestCode == REQUEST_AUDIO) {
            if (results.length > 0
                    && results[0] == PackageManager.PERMISSION_GRANTED) {
                loadSongs();
            } else if (count != null) {
                count.setText(
                        "Permissão para acessar músicas é necessária");
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
                MediaStore.Audio.Media.TITLE
                        + " COLLATE NOCASE ASC")) {
            if (cursor == null) {
                count.setText("Não foi possível ler a biblioteca");
                return;
            }

            int id = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media._ID);
            int title = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.TITLE);
            int artist = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.ARTIST);
            int album = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.ALBUM);
            int duration = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.DURATION);

            while (cursor.moveToNext()) {
                long songId = cursor.getLong(id);
                Uri uri = Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        String.valueOf(songId));

                songs.add(new PlayerManager.Song(
                        safe(cursor.getString(title), "Sem título"),
                        safe(cursor.getString(artist),
                                "Artista desconhecido"),
                        safe(cursor.getString(album),
                                "Álbum desconhecido"),
                        cursor.getLong(duration),
                        uri));
            }
        } catch (Exception e) {
            count.setText("Erro ao carregar a biblioteca");
            return;
        }

        PlayerManager.setQueue(
                songs.toArray(new PlayerManager.Song[0]));
        refresh(songs, songs.size() + " músicas");
    }

    private void refresh(
            List<PlayerManager.Song> source,
            String label) {
        visible.clear();
        visible.addAll(source);
        list.setAdapter(new SongAdapter());
        count.setText(label);
    }

    private class SongAdapter
            extends ArrayAdapter<PlayerManager.Song> {
        SongAdapter() {
            super(AurenHomeV3Activity.this,
                    android.R.layout.simple_list_item_1,
                    visible);
        }

        @Override
        public View getView(
                int position,
                View old,
                ViewGroup parent) {
            PlayerManager.Song song = visible.get(position);

            LinearLayout row = new LinearLayout(
                    AurenHomeV3Activity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(7), dp(6), dp(7));
            row.setMinimumHeight(dp(70));
            row.setClickable(true);
            row.setFocusable(false);
            row.setBackgroundColor(bg());
            row.setOnClickListener(v -> playSong(song));

            ImageView image = new ImageView(
                    AurenHomeV3Activity.this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageResource(
                    android.R.drawable.ic_media_play);
            image.setColorFilter(GREEN);
            image.setTag(song.uri.toString());
            image.setBackground(round(
                    dark
                            ? Color.rgb(50, 52, 58)
                            : Color.rgb(238, 240, 244),
                    10));
            image.setClickable(false);
            image.setFocusable(false);
            row.addView(image,
                    new LinearLayout.LayoutParams(
                            dp(58), dp(58)));

            LinearLayout info = new LinearLayout(
                    AurenHomeV3Activity.this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setGravity(Gravity.CENTER_VERTICAL);
            info.setPadding(dp(12), 0, dp(4), 0);
            info.setClickable(false);
            info.setFocusable(false);

            TextView title = text(
                    song.title, 16, mainText());
            title.setTypeface(
                    Typeface.DEFAULT, Typeface.BOLD);
            title.setMaxLines(1);
            title.setEllipsize(
                    TextUtils.TruncateAt.END);
            title.setClickable(false);
            title.setFocusable(false);

            String metaText = song.artist;
            if (!song.album.isEmpty()) {
                metaText += " · " + song.album;
            }
            TextView meta = text(
                    metaText, 12, muted());
            meta.setMaxLines(1);
            meta.setEllipsize(
                    TextUtils.TruncateAt.END);
            meta.setClickable(false);
            meta.setFocusable(false);

            info.addView(title);
            info.addView(meta);
            row.addView(info,
                    new LinearLayout.LayoutParams(0, -2, 1));

            TextView duration = text(
                    format(song.duration), 12, muted());
            duration.setGravity(Gravity.CENTER);
            duration.setClickable(false);
            duration.setFocusable(false);
            row.addView(duration,
                    new LinearLayout.LayoutParams(dp(45), -2));

            Button actions = icon("⋮", mainText(), 25);
            actions.setFocusable(false);
            actions.setFocusableInTouchMode(false);
            actions.setOnClickListener(v -> songMenu(song));
            row.addView(actions,
                    new LinearLayout.LayoutParams(
                            dp(44), dp(54)));

            loadArtwork(song, image);
            return row;
        }
    }

    private void loadArtwork(
            PlayerManager.Song song,
            ImageView view) {
        final String key = song.uri.toString();
        Bitmap cached = artworkCache.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            view.clearColorFilter();
            return;
        }

        artworkExecutor.execute(() -> {
            Bitmap bitmap = extractArtwork(song.uri);
            if (bitmap == null) {
                return;
            }
            artworkCache.put(key, bitmap);
            runOnUiThread(() -> {
                if (key.equals(view.getTag())) {
                    view.setImageBitmap(bitmap);
                    view.clearColorFilter();
                }
            });
        });
    }

    private Bitmap extractArtwork(Uri uri) {
        MediaMetadataRetriever retriever =
                new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            byte[] data = retriever.getEmbeddedPicture();
            if (data == null || data.length == 0) {
                return null;
            }
            Bitmap bitmap = BitmapFactory.decodeByteArray(
                    data, 0, data.length);
            if (bitmap == null) {
                return null;
            }
            return Bitmap.createScaledBitmap(
                    bitmap, 300, 300, true);
        } catch (Exception ignored) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void playSong(PlayerManager.Song song) {
        if (song == null) {
            return;
        }
        PlayerManager.setQueue(
                songs.toArray(new PlayerManager.Song[0]));
        PlayerManager.play(this, song);
        updateMiniPlayer();
    }

    private void openCurrentPlayer() {
        PlayerManager.Song song =
                PlayerManager.getCurrentSong();
        if (song != null) {
            openPlayer(song);
        }
    }

    private void openPlayer(PlayerManager.Song song) {
        Intent intent = new Intent(
                this, PlayerActivity.class);
        intent.putExtra("title", song.title);
        intent.putExtra("artist", song.artist);
        intent.putExtra("album", song.album);
        intent.putExtra("duration", song.duration);
        intent.putExtra("uri", song.uri);
        startActivity(intent);
    }

    private void updateMiniPlayer() {
        if (nowTitle == null) {
            return;
        }

        PlayerManager.Song song =
                PlayerManager.getCurrentSong();
        if (song == null) {
            nowTitle.setText("Nenhuma música");
            nowArtist.setText("Escolha uma música");
            miniPlay.setText("▶");
            miniArtwork.setImageResource(
                    android.R.drawable.ic_media_play);
            miniArtwork.setColorFilter(GREEN);
            return;
        }

        nowTitle.setText(song.title);
        nowArtist.setText(song.artist);
        miniPlay.setText(
                PlayerManager.isPlaying() ? "Ⅱ" : "▶");
        loadMiniArtwork(song);
    }

    private void loadMiniArtwork(
            PlayerManager.Song song) {
        String key = song.uri.toString();
        Bitmap cached = artworkCache.get(key);
        if (cached != null) {
            miniArtwork.setImageBitmap(cached);
            miniArtwork.clearColorFilter();
            return;
        }

        artworkExecutor.execute(() -> {
            Bitmap bitmap = extractArtwork(song.uri);
            if (bitmap == null) {
                return;
            }
            artworkCache.put(key, bitmap);
            runOnUiThread(() -> {
                PlayerManager.Song current =
                        PlayerManager.getCurrentSong();
                if (current != null
                        && key.equals(current.uri.toString())) {
                    miniArtwork.setImageBitmap(bitmap);
                    miniArtwork.clearColorFilter();
                }
            });
        });
    }

    @Override
    public void onPlayerChanged() {
        runOnUiThread(this::updateMiniPlayer);
    }

    @Override
    public void onPlayerError(String message) {
        runOnUiThread(() -> Toast.makeText(
                this, message, Toast.LENGTH_LONG).show());
    }

    private void showSearch() {
        EditText input = new EditText(this);
        input.setHint("Música, artista ou álbum");
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("Pesquisar música")
                .setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(
                        "Pesquisar",
                        (d, w) -> filter(
                                input.getText().toString()))
                .show();
    }

    private void filter(String value) {
        String q = value.toLowerCase(Locale.ROOT).trim();
        List<PlayerManager.Song> result =
                new ArrayList<>();
        for (PlayerManager.Song song : songs) {
            if (q.isEmpty()
                    || song.title.toLowerCase(Locale.ROOT)
                    .contains(q)
                    || song.artist.toLowerCase(Locale.ROOT)
                    .contains(q)
                    || song.album.toLowerCase(Locale.ROOT)
                    .contains(q)) {
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
        Set<String> fav = prefs.getStringSet(
                "favorites", new HashSet<>());
        List<PlayerManager.Song> result =
                new ArrayList<>();
        for (PlayerManager.Song song : songs) {
            if (fav.contains(song.uri.toString())) {
                result.add(song);
            }
        }
        refresh(result, result.size() + " favoritos");
    }

    private void showUniqueArtists() {
        List<PlayerManager.Song> result =
                new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PlayerManager.Song song : songs) {
            if (seen.add(song.artist)) {
                result.add(song);
            }
        }
        refresh(result, result.size() + " artistas");
    }

    private void showUniqueAlbums() {
        List<PlayerManager.Song> result =
                new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PlayerManager.Song song : songs) {
            if (seen.add(song.album)) {
                result.add(song);
            }
        }
        refresh(result, result.size() + " álbuns");
    }

    private void showDrawer() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        box.setBackgroundColor(panel());

        TextView title = text("AUREN", 24, mainText());
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        box.addView(title);

        addDrawerButton(box, "Biblioteca", this::closeDrawer);
        addDrawerButton(box, "Definições", () -> {
            closeDrawer();
            startActivity(new Intent(
                    this, SettingsActivity.class));
        });
        addDrawerButton(box, "Recarregar músicas", () -> {
            closeDrawer();
            loadSongs();
        });

        drawer = new PopupWindow(
                box, dp(300), -1, true);
        drawer.setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(
                        panel()));
        drawer.setElevation(dp(10));
        drawer.showAtLocation(
                list, Gravity.START, 0, 0);
    }

    private void addDrawerButton(
            LinearLayout box,
            String label,
            Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(mainText());
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        box.addView(button,
                new LinearLayout.LayoutParams(
                        -1, dp(54)));
    }

    private void closeDrawer() {
        if (drawer != null && drawer.isShowing()) {
            drawer.dismiss();
        }
    }

    private void showMainMenu() {
        String[] items = {
                "Atualizar biblioteca", "Definições"
        };
        new AlertDialog.Builder(this)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        loadSongs();
                    } else {
                        startActivity(new Intent(
                                this, SettingsActivity.class));
                    }
                }).show();
    }

    private void songMenu(PlayerManager.Song song) {
        String[] items = {
                "REPRODUZIR",
                "REPRODUZIR A SEGUIR",
                "ADICIONAR AOS FAVORITOS",
                "DETALHES",
                "ENVIAR"
        };
        new AlertDialog.Builder(this)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        playSong(song);
                    } else if (which == 1) {
                        PlayerManager.play(this, song);
                        Toast.makeText(
                                this,
                                "A reproduzir a seguir: "
                                        + song.title,
                                Toast.LENGTH_SHORT).show();
                    } else if (which == 2) {
                        toggleFavorite(song);
                    } else if (which == 3) {
                        details(song);
                    } else {
                        share(song);
                    }
                }).show();
    }

    private void toggleFavorite(PlayerManager.Song song) {
        Set<String> favorites = new HashSet<>(
                prefs.getStringSet(
                        "favorites", new HashSet<>()));
        if (!favorites.add(song.uri.toString())) {
            favorites.remove(song.uri.toString());
        }
        prefs.edit()
                .putStringSet("favorites", favorites)
                .apply();
        Toast.makeText(
                this,
                "Favoritos atualizados",
                Toast.LENGTH_SHORT).show();
    }

    private void details(PlayerManager.Song song) {
        String message = "Título: " + song.title
                + "\nArtista: " + song.artist
                + "\nÁlbum: " + song.album
                + "\nDuração: " + format(song.duration);
        new AlertDialog.Builder(this)
                .setTitle("Detalhes")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void share(PlayerManager.Song song) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(
                Intent.EXTRA_TEXT,
                song.title + " — " + song.artist);
        startActivity(Intent.createChooser(
                intent, "Enviar música"));
    }

    private Button icon(String value, int color, int size) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(color);
        button.setTextSize(size);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setAllCaps(false);
        return button;
    }

    private TextView text(
            String value,
            float size,
            int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private android.graphics.drawable.GradientDrawable round(
            int color,
            int radius) {
        android.graphics.drawable.GradientDrawable drawable =
                new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources()
                .getDisplayMetrics().density);
    }

    private int bg() {
        return dark
                ? Color.rgb(22, 24, 28)
                : Color.rgb(247, 248, 250);
    }

    private int panel() {
        return dark
                ? Color.rgb(34, 36, 42)
                : Color.WHITE;
    }

    private int mainText() {
        return dark ? Color.WHITE : TEXT;
    }

    private int muted() {
        return dark
                ? Color.rgb(175, 178, 185)
                : MUTED;
    }

    private String format(long milliseconds) {
        long totalSeconds = Math.max(0, milliseconds / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(
                Locale.ROOT,
                "%d:%02d",
                minutes,
                seconds);
    }

    private String safe(String value, String fallback) {
        if (value == null
                || value.trim().isEmpty()
                || "<unknown>".equalsIgnoreCase(value)) {
            return fallback;
        }
        return value.trim();
    }
}
