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
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
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
    private TextView nowTitle, nowArtist, positionText, durationText;
    private ImageView nowArt;
    private ImageButton miniPlay, mainPlay;
    private SeekBar seekBar;
    private ObjectAnimator artAnimator;
    private Track currentTrack;
    private boolean favorite;
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
        mini.setOnClickListener(v -> showNowPlaying());
        return mini;
    }

    private void showNowPlaying() {
        if (currentTrack == null) {
            Toast.makeText(this, "Choose a song first", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout root = column();
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(18), dp(22), dp(22));
        root.setBackgroundColor(getColor(R.color.surface));

        LinearLayout top = row();
        ImageButton close = iconButton(android.R.drawable.ic_menu_close_clear_cancel, "Close");
        close.setOnClickListener(v -> setContentView(buildMainForReturn()));
        top.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView label = text("NOW PLAYING", 12, R.color.auren_primary);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(label, new LinearLayout.LayoutParams(0, dp(48), 1));
        ImageButton more = iconButton(android.R.drawable.ic_menu_more, "More");
        top.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(48)));

        ImageView hero = artwork(1);
        hero.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.setImageURI(currentTrack.albumArtUri());
        if (hero.getDrawable() == null) hero.setImageResource(android.R.drawable.ic_media_play);
        hero.setPadding(0, 0, 0, 0);
        hero.setElevation(dp(12));
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, 0, 0.62f);
        heroParams.setMargins(0, dp(22), 0, dp(24));
        root.addView(hero, heroParams);

        LinearLayout songHead = row();
        LinearLayout names = column();
        TextView t = text(currentTrack.title == null ? "Untitled" : currentTrack.title, 23, R.color.text_primary);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView a = text(currentTrack.artist == null ? "Unknown artist" : currentTrack.artist, 14, R.color.text_secondary);
        names.addView(t);
        names.addView(a, margins(0, 3, 0, 0));
        songHead.addView(names, new LinearLayout.LayoutParams(0, dp(60), 1));
        ImageButton fav = iconButton(favorite ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off, "Favorite");
        fav.setOnClickListener(v -> { favorite = !favorite; fav.setImageResource(favorite ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off); });
        songHead.addView(fav, new LinearLayout.LayoutParams(dp(50), dp(60)));
        root.addView(songHead, new LinearLayout.LayoutParams(-1, dp(60)));

        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setProgress(0);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean fromUser) { if (fromUser && player.getDuration() > 0) player.seekTo(player.getDuration() * p / 1000); }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        root.addView(seekBar, margins(0, 14, 0, 0));
        LinearLayout times = row();
        positionText = text("0:00", 11, R.color.text_secondary);
        durationText = text(formatTime(player.getDuration()), 11, R.color.text_secondary);
        times.addView(positionText, new LinearLayout.LayoutParams(0, dp(22), 1));
        durationText.setGravity(Gravity.RIGHT);
        times.addView(durationText, new LinearLayout.LayoutParams(0, dp(22), 1));
        root.addView(times);

        LinearLayout controls = row();
        controls.setGravity(Gravity.CENTER);
        ImageButton previous = iconButton(android.R.drawable.ic_media_previous, "Previous");
        previous.setOnClickListener(v -> previousTrack());
        controls.addView(previous, new LinearLayout.LayoutParams(dp(62), dp(62)));
        mainPlay = iconButton(player.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, "Play");
        mainPlay.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.auren_primary)));
        DrawableCompat.setTint(mainPlay.getDrawable(), Color.WHITE);
        mainPlay.setPadding(dp(18), dp(18), dp(18), dp(18));
        mainPlay.setOnClickListener(v -> togglePlayback());
        controls.addView(mainPlay, new LinearLayout.LayoutParams(dp(72), dp(72)));
        ImageButton next = iconButton(android.R.drawable.ic_media_next, "Next");
        next.setOnClickListener(v -> nextTrack());
        controls.addView(next, new LinearLayout.LayoutParams(dp(62), dp(62)));
        root.addView(controls, margins(0, 8, 0, 0));

        TextView hint = text("Auren • Music that moves with you", 11, R.color.text_secondary);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, margins(0, 4, 0, 0));
        setContentView(root);
        startHeroAnimation(hero);
    }

    private View buildMainForReturn() {
        buildUi();
        return findViewById(android.R.id.content).getRootView();
    }

    private void previousTrack() {
        if (currentTrack == null || tracks.isEmpty()) return;
        int i = tracks.indexOf(currentTrack);
        play(tracks.get(i <= 0 ? tracks.size() - 1 : i - 1));
        showNowPlaying();
    }

    private void nextTrack() {
        if (currentTrack == null || tracks.isEmpty()) return;
        int i = tracks.indexOf(currentTrack);
        play(tracks.get(i >= tracks.size() - 1 ? 0 : i + 1));
        showNowPlaying();
    }

    private void startHeroAnimation(View view) {
        view.setScaleX(0.98f); view.setScaleY(0.98f); view.animate().scaleX(1f).scaleY(1f).setDuration(420).start();
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "0:00";
        long sec = ms / 1000;
        return (sec / 60) + ":" + String.format(java.util.Locale.US, "%02d", sec % 60);
    }

    private void requestMusicPermission() {
        String permission = android.os.Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) loadMusic();
        else ActivityCompat.requestPermissions(this, new String[]{permission}, MUSIC_PERMISSION);
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(requestCode, p, r);
        if (requestCode == MUSIC_PERMISSION && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) loadMusic();
    }

    private void loadMusic() {
        tracks.clear();
        String[] projection = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM_ID};
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        try (Cursor c = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (c != null) while (c.moveToNext()) tracks.add(new Track(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)));
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void play(Track t) {
        currentTrack = t;
        Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, t.id);
        player.setMediaItem(MediaItem.fromUri(uri)); player.prepare(); player.play();
        if (nowTitle != null) nowTitle.setText(t.title == null ? "Untitled" : t.title);
        if (nowArtist != null) nowArtist.setText(t.artist == null ? "Unknown artist" : t.artist);
        if (nowArt != null) { nowArt.setImageURI(t.albumArtUri()); if (nowArt.getDrawable() == null) nowArt.setImageResource(android.R.drawable.ic_media_play); }
        if (miniPlay != null) miniPlay.setImageResource(android.R.drawable.ic_media_pause);
        startArtAnimation();
    }

    private void togglePlayback() {
        if (player == null) return;
        if (player.isPlaying()) { player.pause(); if (miniPlay != null) miniPlay.setImageResource(android.R.drawable.ic_media_play); if (mainPlay != null) mainPlay.setImageResource(android.R.drawable.ic_media_play); stopArtAnimation(); }
        else if (player.getMediaItemCount() > 0) { player.play(); if (miniPlay != null) miniPlay.setImageResource(android.R.drawable.ic_media_pause); if (mainPlay != null) mainPlay.setImageResource(android.R.drawable.ic_media_pause); startArtAnimation(); }
    }

    private void startArtAnimation() {
        if (artAnimator != null && artAnimator.isRunning()) return;
        if (nowArt == null) return;
        artAnimator = ObjectAnimator.ofFloat(nowArt, View.ROTATION, 0f, 360f); artAnimator.setDuration(12000); artAnimator.setRepeatCount(ObjectAnimator.INFINITE); artAnimator.setInterpolator(new LinearInterpolator()); artAnimator.start();
    }

    private void stopArtAnimation() { if (artAnimator != null) artAnimator.pause(); }

    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private LinearLayout rounded(int color, int radius) { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(radius)); v.setBackground(bg); return v; }
    private ImageView artwork(int size) { ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(); bg.setColor(getColor(R.color.accent_soft)); bg.setCornerRadius(dp(15)); image.setBackground(bg); image.setImageResource(android.R.drawable.ic_media_play); image.setPadding(dp(12), dp(12), dp(12), dp(12)); return image; }
    private ImageButton iconButton(int icon, String description) { ImageButton b = new ImageButton(this); b.setImageResource(icon); b.setContentDescription(description); b.setBackgroundColor(Color.TRANSPARENT); b.setPadding(dp(10), dp(10), dp(10), dp(10)); DrawableCompat.setTint(b.getDrawable(), getColor(R.color.text_primary)); return b; }
    private TextView text(String value, float size, int color) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(getColor(color)); return v; }
    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(dp(l), dp(t), dp(r), dp(b)); return p; }
    private LinearLayout.LayoutParams wrapParams() { return new LinearLayout.LayoutParams(-2, -2); }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + 0.5f); }
    @Override protected void onDestroy() { if (artAnimator != null) artAnimator.cancel(); if (player != null) player.release(); super.onDestroy(); }

    private class TrackAdapter extends RecyclerView.Adapter<TrackHolder> {
        @NonNull @Override public TrackHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LinearLayout card = rounded(Color.WHITE, 18); card.setGravity(Gravity.CENTER_VERTICAL); card.setPadding(dp(8), dp(8), dp(10), dp(8)); card.setElevation(dp(1));
            ImageView art = artwork(56); card.addView(art, new LinearLayout.LayoutParams(dp(56), dp(56)));
            LinearLayout info = column(); info.setGravity(Gravity.CENTER_VERTICAL); TextView title = text("", 15, R.color.text_primary); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); TextView artist = text("", 12, R.color.text_secondary); info.addView(title); info.addView(artist, margins(0, 3, 0, 0)); card.addView(info, new LinearLayout.LayoutParams(0, dp(56), 1));
            TextView more = text("⋯", 25, R.color.text_secondary); more.setGravity(Gravity.CENTER); card.addView(more, new LinearLayout.LayoutParams(dp(32), dp(56))); return new TrackHolder(card, title, artist, art);
        }
        @Override public void onBindViewHolder(@NonNull TrackHolder h, int pos) { Track t = tracks.get(pos); h.title.setText(t.title == null || t.title.isEmpty() ? "Untitled" : t.title); h.artist.setText(t.artist == null || t.artist.isEmpty() ? "Unknown artist" : t.artist); h.art.setImageURI(t.albumArtUri()); if (h.art.getDrawable() == null) h.art.setImageResource(android.R.drawable.ic_media_play); h.itemView.setOnClickListener(v -> { play(t); showNowPlaying(); }); }
        @Override public int getItemCount() { return tracks.size(); }
    }
    private static class TrackHolder extends RecyclerView.ViewHolder { TextView title, artist; ImageView art; TrackHolder(View v, TextView t, TextView a, ImageView i) { super(v); title=t; artist=a; art=i; } }
    private static class Track { long id, albumId; String title, artist; Track(long i, String t, String a, long album) { id=i; title=t; artist=a; albumId=album; } Uri albumArtUri() { return ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId); } }
}
