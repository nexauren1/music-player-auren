package com.auren.musicplayer;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQUEST_AUDIO = 100;
    private final List<Song> songs = new ArrayList<>();
    private MediaPlayer player;
    private TextView songTitle;
    private Button playButton;
    private int currentIndex = -1;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        requestAudioPermission();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 48, 32, 32);
        root.setBackgroundColor(0xFFF7F8FA);

        TextView brand = text("AUREN", 14, 0xFF5B5CE2);
        root.addView(brand);

        TextView heading = text("Music Player", 30, 0xFF17181C);
        heading.setPadding(0, 10, 0, 4);
        root.addView(heading);

        TextView subtitle = text("Your music, simple and personal.", 15, 0xFF6B6E78);
        root.addView(subtitle);

        songTitle = text("No song selected", 20, 0xFF17181C);
        songTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, 0, 1);
        titleParams.setMargins(0, 30, 0, 20);
        root.addView(songTitle, titleParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);

        Button previous = button("⏮");
        playButton = button("▶");
        Button next = button("⏭");

        controls.addView(previous);
        controls.addView(playButton);
        controls.addView(next);
        root.addView(controls);

        previous.setOnClickListener(v -> playRelative(-1));
        next.setOnClickListener(v -> playRelative(1));
        playButton.setOnClickListener(v -> togglePlayback());

        setContentView(root);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(20);
        button.setAllCaps(false);
        return button;
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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_AUDIO && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            loadSongs();
        } else {
            Toast.makeText(this, "Audio permission is required to show your music.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void loadSongs() {
        songs.clear();
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (cursor == null) return;
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                String title = cursor.getString(titleColumn);
                Uri uri = Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        String.valueOf(id));
                songs.add(new Song(title, uri));
            }
        }
        if (!songs.isEmpty()) {
            songTitle.setText(songs.size() + " songs available");
        } else {
            songTitle.setText("No music found on this device");
        }
    }

    private void playRelative(int direction) {
        if (songs.isEmpty()) return;
        int nextIndex = currentIndex + direction;
        if (nextIndex < 0) nextIndex = songs.size() - 1;
        if (nextIndex >= songs.size()) nextIndex = 0;
        play(nextIndex);
    }

    private void togglePlayback() {
        if (player == null) {
            if (!songs.isEmpty()) play(0);
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

    private void play(int index) {
        releasePlayer();
        currentIndex = index;
        Song song = songs.get(index);
        player = MediaPlayer.create(this, song.uri);
        if (player == null) {
            Toast.makeText(this, "Unable to play this song.", Toast.LENGTH_SHORT).show();
            return;
        }
        songTitle.setText(song.title);
        player.setOnCompletionListener(mp -> playRelative(1));
        player.start();
        playButton.setText("Ⅱ");
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
        final Uri uri;
        Song(String title, Uri uri) {
            this.title = title;
            this.uri = uri;
        }
    }
}
