package com.auren.musicplayer;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends ComponentActivity {
    private static final int MUSIC_PERMISSION = 41;
    private final List<Track> tracks = new ArrayList<>();
    private ExoPlayer player;
    private TextView nowTitle;
    private TextView nowArtist;
    private ImageView nowArt;
    private ImageButton miniPlay;
    private ObjectAnimator artAnimator;
    private TrackAdapter adapter;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        player = new ExoPlayer.Builder(this).build();
        requestMusicPermission();
    }

    private void buildUi() {
        LinearLayout root = column();
        root.setPadding(dp(20), dp(16), dp(20), 0);
        root.setBackgroundColor(getColor(R.color.surface));

        LinearLayout header = row();
        TextView brand = text("AUREN", 25, R.color.text_primary);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView beta = text(" MUSIC", 11, R.color.auren_primary);
        beta.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout logo = row();
        logo.addView(brand);
        logo.addView(beta, margins(2, 7, 0, 0));
        header.addView(logo, new LinearLayout.LayoutParams(0, dp(52), 1));

        ImageButton settings = iconButton(android.R.drawable.ic_menu_preferences, "Settings");
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        header.addView(settings, new LinearLayout.LayoutParams(dp(46), dp(46)));
        root.addView(header);

        TextView eyebrow = text("YOUR LIBRARY", 11, R.color.auren_primary);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(eyebrow, margins(2, 18, 0, 5));

        TextView title = text("Your music", 31, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);
        TextView subtitle = text("Everything you love, in one place.", 14, R.color.text_secondary);
        root.addView(subtitle, margins(0, 2, 0, 16));

        LinearLayout pill = rounded(0xFFEEECFF, 18);
        TextView count = text("♪  " + tracks.size() + " songs", 13, R.color.auren_primary);
        count.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        pill.addView(count, margins(14, 7, 14, 7));
        root.addView(pill, wrapParams());

        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setClipToPadding(false);
        list.setPadding(0, dp(8), 0, dp(150));
        adapter = new TrackAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        root.addView(buildMiniPlayer(), margins(0, 6, 0, 12));
        setContentView(root);
    }

    private LinearLayout buildMiniPlayer() {
        LinearLayout mini = rounded(0xFFFFFFFF, 20);
        mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setPadding(dp(8), dp(8), dp(8), dp(8));
        mini.setElevation(dp(6));

        nowArt = artwork(52);
        mini.addView(nowArt, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout info = column();
        info.setGravity(Gravity.CENTER_VERTICAL);
        nowTitle = text("Nothing playing", 14, R.color.text_primary);
        nowTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nowArtist = text("Choose a song to start", 12, R.color.text_secondary);
        info.addView(nowTitle);
        info.addView(nowArtist, margins(0, 2, 0, 0));
        mini.addView(info, new LinearLayout.LayoutParams(0, dp(58), 1));

        miniPlay = iconButton(android.R.drawable.ic_media_play, "Play");
        miniPlay.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.auren_primary)));
        DrawableCompat.setTint(miniPlay.getDrawable(), Color.WHITE);
        miniPlay.setOnClickListener(v -> togglePlayback());
        mini.addView(miniPlay, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return mini;
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
        String[] projection = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM_ID};
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        try (Cursor c = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (c != null) while (c.moveToNext()) {
                tracks.add(new Track(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)));
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void play(Track t) {
        Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, t.id);
        player.setMediaItem(MediaItem.fromUri(uri));
        player.prepare();
        player.play();
        nowTitle.setText(t.title);
        nowArtist.setText(t.artist == null || t.artist.isEmpty() ? "Unknown artist" : t.artist);
        nowArt.setImageURI(t.albumArtUri());
        if (nowArt.getDrawable() == null) nowArt.setImageResource(android.R.drawable.ic_media_play);
        startArtAnimation();
        miniPlay.setImageResource(android.R.drawable.ic_media_pause);
    }

    private void togglePlayback() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
            miniPlay.setImageResource(android.R.drawable.ic_media_play);
            stopArtAnimation();
        } else if (player.getMediaItemCount() > 0) {
            player.play();
            miniPlay.setImageResource(android.R.drawable.ic_media_pause);
            startArtAnimation();
        }
    }

    private void startArtAnimation() {
        if (artAnimator != null) artAnimator.cancel();
        artAnimator = ObjectAnimator.ofFloat(nowArt, View.ROTATION, 0f, 360f);
        artAnimator.setDuration(12000);
        artAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        artAnimator.setInterpolator(new LinearInterpolator());
        artAnimator.start();
    }

    private void stopArtAnimation() {
        if (artAnimator != null) artAnimator.pause();
    }

    private LinearLayout column() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private LinearLayout row() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private LinearLayout rounded(int color, int radius) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        v.setBackground(bg);
        return v;
    }

    private ImageView artwork(int size) {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(getColor(R.color.accent_soft));
        bg.setCornerRadius(dp(15));
        image.setBackground(bg);
        image.setImageResource(android.R.drawable.ic_media_play);
        image.setPadding(dp(12), dp(12), dp(12), dp(12));
        return image;
    }

    private ImageButton iconButton(int icon, String description) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setContentDescription(description);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setPadding(dp(10), dp(10), dp(10), dp(10));
        DrawableCompat.setTint(b.getDrawable(), getColor(R.color.text_primary));
        return b;
    }

    private TextView text(String value, float size, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(getColor(color));
        return v;
    }

    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private LinearLayout.LayoutParams wrapParams() {
        return new LinearLayout.LayoutParams(-2, -2);
    }

    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override protected void onDestroy() {
        if (artAnimator != null) artAnimator.cancel();
        if (player != null) player.release();
        super.onDestroy();
    }

    private class TrackAdapter extends RecyclerView.Adapter<TrackHolder> {
        @NonNull @Override public TrackHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LinearLayout card = rounded(Color.WHITE, 18);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(8), dp(8), dp(10), dp(8));
            card.setElevation(dp(1));
            ImageView art = artwork(56);
            card.addView(art, new LinearLayout.LayoutParams(dp(56), dp(56)));

            LinearLayout info = column();
            info.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = text("", 15, R.color.text_primary);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            TextView artist = text("", 12, R.color.text_secondary);
            info.addView(title);
            info.addView(artist, margins(0, 3, 0, 0));
            card.addView(info, new LinearLayout.LayoutParams(0, dp(56), 1));

            TextView more = text("⋯", 25, R.color.text_secondary);
            more.setGravity(Gravity.CENTER);
            card.addView(more, new LinearLayout.LayoutParams(dp(32), dp(56)));
            return new TrackHolder(card, title, artist, art);
        }

        @Override public void onBindViewHolder(@NonNull TrackHolder h, int pos) {
            Track t = tracks.get(pos);
            h.title.setText(t.title == null || t.title.isEmpty() ? "Untitled" : t.title);
            h.artist.setText(t.artist == null || t.artist.isEmpty() ? "Unknown artist" : t.artist);
            h.art.setImageURI(t.albumArtUri());
            if (h.art.getDrawable() == null) h.art.setImageResource(android.R.drawable.ic_media_play);
            h.itemView.setOnClickListener(v -> play(t));
        }

        @Override public int getItemCount() { return tracks.size(); }
    }

    private static class TrackHolder extends RecyclerView.ViewHolder {
        TextView title, artist;
        ImageView art;
        TrackHolder(View v, TextView t, TextView a, ImageView i) {
            super(v); title = t; artist = a; art = i;
        }
    }

    private static class Track {
        long id, albumId;
        String title, artist;
        Track(long i, String t, String a, long album) {
            id = i; title = t; artist = a; albumId = album;
        }
        Uri albumArtUri() {
            return ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId);
        }
    }
}
