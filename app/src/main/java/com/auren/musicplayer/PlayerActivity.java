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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

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
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        PlayerManager.setListener(this);
        refreshTrack();
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
        body.addView(cover, new LinearLayout.LayoutParams(dp(280), dp(280)));

        title = text("Nenhuma música", 22, TEXT);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(24);
        body.addView(title, titleParams);

        artist = text("Escolha uma música", 14, Color.rgb(105, 108, 116));
        artist.setGravity(Gravity.CENTER);
        artist.setMaxLines(2);
        body.addView(artist);

        seekBar = new SeekBar(this);
        LinearLayout.LayoutParams seekParams =
                new LinearLayout.LayoutParams(-1, dp(42));
        seekParams.topMargin = dp(22);
        body.addView(seekBar, seekParams);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser) elapsed.setText(format(value));
            }

            public void onStartTrackingTouch(SeekBar bar) {
            }

            public void onStopTrackingTouch(SeekBar bar) {
                PlayerManager.seekTo(bar.getProgress());
            }
        });

        LinearLayout times = new LinearLayout(this);
        elapsed = text("0:00", 12, Color.GRAY);
        total = text("0:00", 12, Color.GRAY);
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
        previous.setOnClickListener(v ->
                PlayerManager.previous(this, PlayerManager.getQueue()));

        play = button("▶", 30, Color.WHITE);
        play.setBackgroundColor(GREEN);
        controls.addView(play, new LinearLayout.LayoutParams(dp(78), dp(78)));
        play.setOnClickListener(v -> PlayerManager.toggle());

        Button next = button("▶|", 22, TEXT);
        controls.addView(next, new LinearLayout.LayoutParams(dp(68), dp(68)));
        next.setOnClickListener(v ->
                PlayerManager.next(this, PlayerManager.getQueue()));

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
        updateButtons();
    }

    private void refreshTrack() {
        PlayerManager.Song current = PlayerManager.getCurrentSong();
        if (current == null) return;
        title.setText(current.title);
        artist.setText(current.artist + " · " + current.album);
        loadArtwork(current);
        updateProgress();
    }

    private void showQueue() {
        PlayerManager.Song[] queue = PlayerManager.getQueue();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < queue.length; i++) {
            boolean current = queue[i].uri.equals(
                    PlayerManager.getCurrentSong() == null
                            ? null : PlayerManager.getCurrentSong().uri);
            text.append(current ? "▶ " : "   ")
                    .append(i + 1).append(". ")
                    .append(queue[i].title).append("\n");
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Fila de reprodução")
                .setMessage(text.length() == 0
                        ? "A fila está vazia." : text.toString())
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
        if (play == null) return;
        play.setText(PlayerManager.isPlaying() ? "Ⅱ" : "▶");
        shuffle.setAlpha(PlayerManager.isShuffle() ? 1f : 0.45f);
        repeat.setAlpha(PlayerManager.isRepeat() ? 1f : 0.45f);
    }

    @Override
    public void onPlayerChanged() {
        runOnUiThread(() -> {
            refreshTrack();
            updateButtons();
        });
    }

    private void loadArtwork(PlayerManager.Song song) {
        cover.setImageResource(android.R.drawable.ic_media_play);
        cover.setColorFilter(GREEN);
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
}
