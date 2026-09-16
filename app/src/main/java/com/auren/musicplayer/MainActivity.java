package com.auren.musicplayer;

import android.Manifest;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends ComponentActivity {
    private static final int MUSIC_PERMISSION = 41;
    private final List<Track> tracks = new ArrayList<>();
    private ExoPlayer player;
    private TextView nowPlaying;
    private TrackAdapter adapter;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        player = new ExoPlayer.Builder(this).build();
        requestMusicPermission();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), 0);
        root.setBackgroundColor(getColor(R.color.surface));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("AUREN", 24, R.color.text_primary);
        brand.setTypeface(null, 1);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(50), 1));
        ImageButton settings = new ImageButton(this);
        settings.setImageResource(android.R.drawable.ic_menu_preferences);
        settings.setBackgroundColor(0x00000000);
        settings.setContentDescription("Settings");
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        header.addView(settings, new LinearLayout.LayoutParams(dp(50), dp(50)));
        root.addView(header);

        TextView title = text("Your music", 30, R.color.text_primary);
        title.setTypeface(null, 1);
        root.addView(title);
        TextView subtitle = text("Everything you love, in one place.", 14, R.color.text_secondary);
        root.addView(subtitle, marginParams(0, 2, 0, 14));

        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TrackAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout mini = new LinearLayout(this);
        mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setPadding(dp(14), dp(8), dp(8), dp(8));
        mini.setBackgroundColor(getColor(R.color.card));
        nowPlaying = text("Choose a song to start", 14, R.color.text_primary);
        nowPlaying.setTypeface(null, 1);
        mini.addView(nowPlaying, new LinearLayout.LayoutParams(0, dp(58), 1));
        ImageButton play = new ImageButton(this);
        play.setImageResource(android.R.drawable.ic_media_play);
        play.setBackgroundColor(0x00000000);
        play.setOnClickListener(v -> { if (player.isPlaying()) player.pause(); else player.play(); });
        mini.addView(play, new LinearLayout.LayoutParams(dp(52), dp(58)));
        root.addView(mini, marginParams(0, 10, 0, 12));
        setContentView(root);
    }

    private void requestMusicPermission() {
        String permission = android.os.Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) loadMusic();
        else ActivityCompat.requestPermissions(this, new String[]{permission}, MUSIC_PERMISSION);
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(requestCode, p, r);
        if (requestCode == MUSIC_PERMISSION && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) loadMusic();
    }

    private void loadMusic() {
        tracks.clear();
        String[] projection = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST};
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        try (Cursor c = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (c != null) while (c.moveToNext()) {
                long id = c.getLong(0);
                tracks.add(new Track(id, c.getString(1), c.getString(2)));
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void play(Track t) {
        Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, t.id);
        player.setMediaItem(MediaItem.fromUri(uri));
        player.prepare();
        player.play();
        nowPlaying.setText(t.title);
    }

    private TextView text(String value, float size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(getColor(color)); return v;
    }
    private LinearLayout.LayoutParams marginParams(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(dp(l), dp(t), dp(r), dp(b)); return p;
    }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onDestroy() { if (player != null) player.release(); super.onDestroy(); }

    private class TrackAdapter extends RecyclerView.Adapter<TrackHolder> {
        @NonNull @Override public TrackHolder onCreateViewHolder(@NonNull android.view.ViewGroup p, int type) {
            LinearLayout row = new LinearLayout(MainActivity.this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(4), dp(8), dp(4), dp(8));
            TextView title = text("", 16, R.color.text_primary); row.addView(title, new LinearLayout.LayoutParams(0, dp(54), 1));
            TextView artist = text("", 13, R.color.text_secondary); row.addView(artist, new LinearLayout.LayoutParams(dp(130), dp(54)));
            return new TrackHolder(row, title, artist);
        }
        @Override public void onBindViewHolder(@NonNull TrackHolder h, int pos) { Track t = tracks.get(pos); h.title.setText(t.title); h.artist.setText(t.artist == null ? "Unknown artist" : t.artist); h.itemView.setOnClickListener(v -> play(t)); }
        @Override public int getItemCount() { return tracks.size(); }
    }
    private static class TrackHolder extends RecyclerView.ViewHolder { TextView title, artist; TrackHolder(View v, TextView t, TextView a) { super(v); title=t; artist=a; } }
    private static class Track { long id; String title, artist; Track(long i, String t, String a) { id=i; title=t; artist=a; } }
}
