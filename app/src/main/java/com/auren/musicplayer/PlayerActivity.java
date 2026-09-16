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

import java.util.Locale;

public class PlayerActivity extends Activity implements PlayerManager.Listener {
    private static final int GREEN = Color.rgb(26, 142, 55);
    private static final int GREEN_DARK = Color.rgb(18, 104, 40);
    private static final int BG = Color.rgb(246, 248, 247);
    private static final int CARD = Color.WHITE;
    private static final int TEXT = Color.rgb(24, 28, 26);
    private static final int MUTED = Color.rgb(103, 111, 106);

    private ImageView cover;
    private TextView title;
    private TextView artist;
    private TextView elapsed;
    private TextView total;
    private TextView status;
    private SeekBar seekBar;
    private Button play;
    private Button shuffle;
    private Button repeat;
    private final Handler handler = new Handler();

    private final Runnable updateTask = new Runnable() {
        @Override public void run() {
            updateProgress();
            handler.postDelayed(this, 400);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        PlayerManager.setListener(this);
        refreshTrack();
        handler.post(updateTask);
    }

    private void buildUi() {
        LinearLayout root = column(BG);

        LinearLayout top = row(GREEN);
        top.setPadding(dp(8), dp(7), dp(8), dp(7));
        Button back = button("‹", 32, Color.WHITE);
        top.addView(back, size(54, 52));
        back.setOnClickListener(v -> finish());

        TextView heading = text("AUREN PLAYER", 18, Color.WHITE);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(heading, weight(1, 52));

        Button queue = button("☷", 24, Color.WHITE);
        top.addView(queue, size(52, 52));
        queue.setOnClickListener(v -> showQueue());
        root.addView(top);

        LinearLayout body = column(BG);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(22), dp(22), dp(22), dp(14));

        status = text("A tocar agora", 12, GREEN);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        body.addView(status, size(-1, 28));

        LinearLayout artFrame = column(CARD);
        artFrame.setGravity(Gravity.CENTER);
        artFrame.setPadding(dp(8), dp(8), dp(8), dp(8));
        artFrame.setElevation(dp(4));
        cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(round(Color.rgb(232, 239, 234), 22));
        artFrame.addView(cover, size(286, 286));
        LinearLayout.LayoutParams artParams = size(302, 302);
        artParams.topMargin = dp(8);
        body.addView(artFrame, artParams);

        title = text("Nenhuma música", 23, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        LinearLayout.LayoutParams titleParams = size(-1, -2);
        titleParams.topMargin = dp(22);
        body.addView(title, titleParams);

        artist = text("Escolhe uma música", 14, MUTED);
        artist.setGravity(Gravity.CENTER);
        artist.setMaxLines(2);
        body.addView(artist, size(-1, 34));

        seekBar = new SeekBar(this);
        seekBar.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams seekParams = size(-1, 38);
        seekParams.topMargin = dp(14);
        body.addView(seekBar, seekParams);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser) elapsed.setText(format(value));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {
                PlayerManager.seekTo(bar.getProgress());
            }
        });

        LinearLayout times = row(BG);
        elapsed = text("0:00", 12, MUTED);
        total = text("0:00", 12, MUTED);
        times.addView(elapsed, weight(1, 24));
        total.setGravity(Gravity.RIGHT);
        times.addView(total, weight(1, 24));
        body.addView(times);

        LinearLayout controls = row(BG);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(12), 0, dp(4));

        shuffle = button("🔀", 20, GREEN);
        controls.addView(shuffle, size(52, 58));
        shuffle.setOnClickListener(v -> {
            PlayerManager.setShuffle(!PlayerManager.isShuffle());
            updateButtons();
        });

        Button previous = button("|◀", 21, TEXT);
        controls.addView(previous, size(58, 58));
        previous.setOnClickListener(v -> PlayerManager.previous(this, PlayerManager.getQueue()));

        play = button("▶", 29, Color.WHITE);
        play.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        play.setBackground(round(GREEN, 32));
        controls.addView(play, size(76, 64));
        play.setOnClickListener(v -> PlayerManager.toggle());

        Button next = button("▶|", 21, TEXT);
        controls.addView(next, size(58, 58));
        next.setOnClickListener(v -> PlayerManager.next(this, PlayerManager.getQueue()));

        repeat = button("↻", 22, GREEN);
        controls.addView(repeat, size(52, 58));
        repeat.setOnClickListener(v -> {
            PlayerManager.setRepeat(!PlayerManager.isRepeat());
            updateButtons();
        });
        body.addView(controls);

        Button queueButton = button("☷   FILA DE REPRODUÇÃO", 14, GREEN);
        queueButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        queueButton.setBackground(round(CARD, 18));
        LinearLayout.LayoutParams qp = size(-1, 52);
        qp.topMargin = dp(6);
        body.addView(queueButton, qp);
        queueButton.setOnClickListener(v -> showQueue());

        root.addView(body, weight(1, 0));
        setContentView(root);
        updateButtons();
    }

    private void refreshTrack() {
        PlayerManager.Song current = PlayerManager.getCurrentSong();
        if (current == null) {
            status.setText("Escolhe uma música");
            return;
        }
        title.setText(current.title);
        artist.setText(current.artist + " • " + current.album);
        status.setText(PlayerManager.isPlaying() ? "A tocar agora" : "Em pausa");
        loadArtwork(current);
        updateProgress();
    }

    private void showQueue() {
        PlayerManager.Song[] queue = PlayerManager.getQueue();
        StringBuilder message = new StringBuilder();
        PlayerManager.Song current = PlayerManager.getCurrentSong();
        for (int i = 0; i < queue.length; i++) {
            boolean selected = current != null && queue[i].uri.equals(current.uri);
            message.append(selected ? "▶  " : "    ")
                    .append(i + 1).append(". ")
                    .append(queue[i].title).append("\n");
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Fila de reprodução")
                .setMessage(message.length() == 0 ? "A fila está vazia." : message.toString())
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
        status.setText(PlayerManager.isPlaying() ? "A tocar agora" : "Em pausa");
        shuffle.setAlpha(PlayerManager.isShuffle() ? 1f : 0.42f);
        repeat.setAlpha(PlayerManager.isRepeat() ? 1f : 0.42f);
    }

    @Override public void onPlayerChanged() {
        runOnUiThread(() -> {
            refreshTrack();
            updateButtons();
        });
    }

    @Override public void onPlayerError(String message) {
        runOnUiThread(() -> status.setText("Não foi possível reproduzir"));
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

    private LinearLayout row(int color) {
        LinearLayout value = new LinearLayout(this);
        value.setOrientation(LinearLayout.HORIZONTAL);
        value.setBackgroundColor(color);
        return value;
    }

    private LinearLayout column(int color) {
        LinearLayout value = new LinearLayout(this);
        value.setOrientation(LinearLayout.VERTICAL);
        value.setBackgroundColor(color);
        return value;
    }

    private Button button(String value, int size, int color) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(size);
        b.setTextColor(color);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        return b;
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout.LayoutParams size(int width, int height) {
        return new LinearLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height));
    }

    private LinearLayout.LayoutParams weight(float value, int height) {
        return new LinearLayout.LayoutParams(0, height < 0 ? height : dp(height), value);
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radius) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private String format(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(updateTask);
        PlayerManager.setListener(null);
        super.onDestroy();
    }
}
