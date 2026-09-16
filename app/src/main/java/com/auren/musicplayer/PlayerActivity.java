package com.auren.musicplayer;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class PlayerActivity extends Activity implements PlayerManager.Listener {
    private static final int GREEN = Color.rgb(32, 150, 42);
    private static final int TEXT = Color.rgb(35, 36, 40);

    private ImageView cover;
    private TextView title;
    private TextView artist;
    private TextView elapsed;
    private TextView total;
    private SeekBar seekBar;
    private Button play;
    private Button shuffle;
    private Button repeat;
    private SongData song;
    private SongData[] queue;
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        song = readSong();
        queue = readQueue();
        buildUi();
        PlayerManager.setListener(this);
        handler.post(updateTask);
    }

    private final Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 248, 250));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(6), dp(8), dp(6));
        top.setBackgroundColor(GREEN);

        Button back = button("‹", 32, Color.WHITE);
        top.addView(back, new LinearLayout.LayoutParams(dp(54), dp(54)));
        back.setOnClickListener(v -> finish());

        TextView heading = text("AUREN PLAYER", 19, Color.WHITE);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(heading, new LinearLayout.LayoutParams(0, dp(54), 1));
        root.addView(top);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(24), dp(28), dp(24), dp(18));

        cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setImageResource(android.R.drawable.ic_media_play);
        cover.setColorFilter(GREEN);
        body.addView(cover, new LinearLayout.LayoutParams(dp(280), dp(280)));

        title = text(song.title, 22, TEXT);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(24);
        body.addView(title, titleParams);

        artist = text(song.artist + " · " + song.album, 14,
                Color.rgb(105, 108, 116));
        artist.setGravity(Gravity.CENTER);
        artist.setMaxLines(2);
        body.addView(artist);

        seekBar = new SeekBar(this);
        seekBar.setMax(Math.max(1, song.duration > 0
                ? (int) song.duration : 1));
        LinearLayout.LayoutParams seekParams =
                new LinearLayout.LayoutParams(-1, dp(42));
        seekParams.topMargin = dp(22);
        body.addView(seekBar, seekParams);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser) {
                    elapsed.setText(format(value));
                }
            }

            public void onStartTrackingTouch(SeekBar bar) {
            }

            public void onStopTrackingTouch(SeekBar bar) {
                PlayerManager.seekTo(bar.getProgress());
            }
        });

        LinearLayout times = new LinearLayout(this);
        elapsed = text("0:00", 12, Color.GRAY);
        total = text(format(song.duration), 12, Color.GRAY);
        times.addView(elapsed, new LinearLayout.LayoutParams(0, -2, 1));
        total.setGravity(Gravity.RIGHT);
        times.addView(total, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(times);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(18), 0, 0);

        shuffle = button("🔀", 22, GREEN);
        controls.addView(shuffle, new LinearLayout.LayoutParams(dp(58), dp(58)));
        shuffle.setOnClickListener(v -> {
            PlayerManager.setShuffle(!PlayerManager.isShuffle());
            updateButtons();
        });

        Button previous = button("|◀", 22, TEXT);
        controls.addView(previous, new LinearLayout.LayoutParams(dp(68), dp(68)));
        previous.setOnClickListener(v -> previousSong());

        play = button("▶", 30, Color.WHITE);
        play.setBackgroundColor(GREEN);
        controls.addView(play, new LinearLayout.LayoutParams(dp(78), dp(78)));
        play.setOnClickListener(v -> PlayerManager.toggle());

        Button next = button("▶|", 22, TEXT);
        controls.addView(next, new LinearLayout.LayoutParams(dp(68), dp(68)));
        next.setOnClickListener(v -> nextSong());

        repeat = button("↻", 22, GREEN);
        controls.addView(repeat, new LinearLayout.LayoutParams(dp(58), dp(58)));
        repeat.setOnClickListener(v -> {
            PlayerManager.setRepeat(!PlayerManager.isRepeat());
            updateButtons();
        });
        body.addView(controls);

        Button queueButton = button("☷  FILA DE REPRODUÇÃO", 15, GREEN);
        queueButton.setBackgroundColor(Color.TRANSPARENT);
        body.addView(queueButton, new LinearLayout.LayoutParams(-1, dp(52)));
        queueButton.setOnClickListener(v -> showQueue());

        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        loadArtwork();
        updateButtons();
    }

    private void nextSong() {
        PlayerManager.Song[] items = managerQueue();
        PlayerManager.next(this, items);
    }

    private void previousSong() {
        PlayerManager.previous(this, managerQueue());
    }

    private PlayerManager.Song[] managerQueue() {
        PlayerManager.Song[] result = new PlayerManager.Song[queue.length];
        for (int i = 0; i < queue.length; i++) {
            result[i] = new PlayerManager.Song(
                    queue[i].title, queue[i].artist, queue[i].album,
                    queue[i].duration, queue[i].uri);
        }
        return result;
    }

    private void showQueue() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < queue.length; i++) {
            text.append(i + 1).append(". ")
                    .append(queue[i].title).append("\n");
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Fila de reprodução")
                .setMessage(text.length() == 0 ? "A fila está vazia." : text.toString())
                .setPositiveButton("Fechar", null)
                .show();
    }

    private void updateProgress() {
        int duration = PlayerManager.getDuration();
        int position = PlayerManager.getPosition();
        if (duration > 0) {
            seekBar.setMax(duration);
            seekBar.setProgress(Math.min(position, duration));
            total.setText(format(duration));
        }
        elapsed.setText(format(position));
    }

    private void updateButtons() {
        play.setText(PlayerManager.isPlaying() ? "Ⅱ" : "▶");
        shuffle.setAlpha(PlayerManager.isShuffle() ? 1f : 0.45f);
        repeat.setAlpha(PlayerManager.isRepeat() ? 1f : 0.45f);
    }

    @Override
    public void onPlayerChanged() {
        runOnUiThread(() -> {
            PlayerManager.Song current = PlayerManager.getCurrentSong();
            if (current != null) {
                title.setText(current.title);
                artist.setText(current.artist + " · " + current.album);
            }
            updateButtons();
            updateProgress();
        });
    }

    private void loadArtwork() {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(this, song.uri);
            byte[] data = retriever.getEmbeddedPicture();
            retriever.release();
            if (data != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                if (bitmap != null) {
                    cover.setColorFilter(null);
                    cover.setImageBitmap(bitmap);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private SongData readSong() {
        String title = getIntent().getStringExtra("title");
        String artist = getIntent().getStringExtra("artist");
        String album = getIntent().getStringExtra("album");
        long duration = getIntent().getLongExtra("duration", 0);
        android.net.Uri uri = getIntent().getParcelableExtra("uri");
        return new SongData(title == null ? "Nenhuma música" : title,
                artist == null ? "Artista desconhecido" : artist,
                album == null ? "Álbum desconhecido" : album,
                duration, uri);
    }

    private SongData[] readQueue() {
        return new SongData[]{song};
    }

    private Button button(String value, int size, int color) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(size);
        b.setTextColor(color);
        b.setAllCaps(false);
        return b;
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private String format(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format(java.util.Locale.getDefault(),
                "%d:%02d", seconds / 60, seconds % 60);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(updateTask);
        PlayerManager.setListener(null);
        super.onDestroy();
    }

    private static final class SongData {
        final String title;
        final String artist;
        final String album;
        final long duration;
        final android.net.Uri uri;

        SongData(String title, String artist, String album,
                 long duration, android.net.Uri uri) {
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.duration = duration;
            this.uri = uri;
        }
    }
}
