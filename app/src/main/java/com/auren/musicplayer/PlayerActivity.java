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
    private static final int GREEN = Color.rgb(25, 139, 57);
    private static final int GREEN_DARK = Color.rgb(14, 101, 39);
    private static final int BG = Color.rgb(245, 248, 246);
    private static final int CARD = Color.WHITE;
    private static final int TEXT = Color.rgb(22, 28, 24);
    private static final int MUTED = Color.rgb(103, 113, 107);

    private ImageView cover;
    private TextView title;
    private TextView artist;
    private TextView elapsed;
    private TextView total;
    private TextView status;
    private TextView quality;
    private SeekBar seekBar;
    private Button play;
    private Button shuffle;
    private Button repeat;
    private LinearLayout root;
    private final Handler handler = new Handler();

    private final Runnable updateTask = new Runnable() {
        @Override public void run() {
            updateProgress();
            updateButtons();
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
        root = column(BG);

        LinearLayout top = row(BG);
        top.setPadding(dp(8), dp(8), dp(8), dp(8));

        Button back = iconButton("‹", 31, TEXT);
        top.addView(back, size(52, 52));
        back.setOnClickListener(v -> finish());

        LinearLayout headingBox = column(BG);
        TextView heading = text("AUREN", 18, TEXT);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView sub = text("FULL PLAYER", 10, MUTED);
        sub.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        headingBox.addView(heading, size(-1, 27));
        headingBox.addView(sub, size(-1, 18));
        top.addView(headingBox, weight(1, 52));

        Button queue = iconButton("☷", 23, TEXT);
        top.addView(queue, size(52, 52));
        queue.setOnClickListener(v -> showQueue());
        root.addView(top);

        LinearLayout scrollBody = column(BG);
        scrollBody.setGravity(Gravity.CENTER_HORIZONTAL);
        scrollBody.setPadding(dp(20), dp(4), dp(20), dp(20));

        status = text("A tocar agora", 11, GREEN);
        status.setGravity(Gravity.CENTER);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        scrollBody.addView(status, size(-1, 30));

        LinearLayout artCard = column(CARD);
        artCard.setGravity(Gravity.CENTER);
        artCard.setPadding(dp(7), dp(7), dp(7), dp(7));
        artCard.setBackground(round(CARD, 24));
        artCard.setElevation(dp(7));
        cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(round(Color.rgb(229, 237, 232), 20));
        artCard.addView(cover, size(300, 300));
        LinearLayout.LayoutParams artParams = size(314, 314);
        artParams.topMargin = dp(6);
        scrollBody.addView(artCard, artParams);

        LinearLayout meta = column(BG);
        meta.setGravity(Gravity.CENTER_HORIZONTAL);
        title = text("Nenhuma música", 24, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        LinearLayout.LayoutParams titleParams = size(-1, -2);
        titleParams.topMargin = dp(22);
        meta.addView(title, titleParams);

        artist = text("Escolhe uma música", 14, MUTED);
        artist.setGravity(Gravity.CENTER);
        artist.setMaxLines(2);
        meta.addView(artist, size(-1, 34));

        quality = text("LOCAL LIBRARY  •  AUREN AUDIO", 9, MUTED);
        quality.setGravity(Gravity.CENTER);
        quality.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        meta.addView(quality, size(-1, 22));
        scrollBody.addView(meta, size(-1, -2));

        seekBar = new SeekBar(this);
        seekBar.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams seekParams = size(-1, 40);
        seekParams.topMargin = dp(12);
        scrollBody.addView(seekBar, seekParams);
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
        scrollBody.addView(times);

        LinearLayout controls = row(BG);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(8), 0, dp(6));

        shuffle = iconButton("🔀", 19, GREEN);
        controls.addView(shuffle, size(52, 58));
        shuffle.setOnClickListener(v -> {
            PlayerManager.setShuffle(!PlayerManager.isShuffle());
            updateButtons();
        });

        Button previous = iconButton("|◀", 20, TEXT);
        controls.addView(previous, size(56, 58));
        previous.setOnClickListener(v -> PlayerManager.previous(this, PlayerManager.getQueue()));

        play = iconButton("▶", 29, Color.WHITE);
        play.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        play.setBackground(round(GREEN, 36));
        controls.addView(play, size(78, 66));
        play.setOnClickListener(v -> PlayerManager.toggle());

        Button next = iconButton("▶|", 20, TEXT);
        controls.addView(next, size(56, 58));
        next.setOnClickListener(v -> PlayerManager.next(this, PlayerManager.getQueue()));

        repeat = iconButton("↻", 22, GREEN);
        controls.addView(repeat, size(52, 58));
        repeat.setOnClickListener(v -> {
            PlayerManager.setRepeat(!PlayerManager.isRepeat());
            updateButtons();
        });
        scrollBody.addView(controls);

        LinearLayout quick = row(BG);
        quick.setGravity(Gravity.CENTER);
        Button queueButton = actionButton("☷  FILA", GREEN);
        Button effectsButton = actionButton("♫  EFEITOS", GREEN);
        quick.addView(queueButton, weight(1, 48));
        LinearLayout.LayoutParams ep = weight(1, 48);
        ep.leftMargin = dp(8);
        quick.addView(effectsButton, ep);
        scrollBody.addView(quick);

        queueButton.setOnClickListener(v -> showQueue());
        effectsButton.setOnClickListener(v -> openEffects());

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.addView(scrollBody);
        root.addView(scroll, weight(1, 0));
        setContentView(root);
        updateButtons();
    }

    private void refreshTrack() {
        PlayerManager.Song current = PlayerManager.getCurrentSong();
        if (current == null) {
            status.setText("Escolhe uma música");
            title.setText("Nenhuma música");
            artist.setText("A biblioteca está pronta para começar");
            return;
        }
        title.setText(current.title);
        String safeArtist = current.artist == null || current.artist.trim().isEmpty()
                ? "Artista desconhecido" : current.artist;
        String safeAlbum = current.album == null || current.album.trim().isEmpty()
                ? "Álbum desconhecido" : current.album;
        artist.setText(safeArtist + "  •  " + safeAlbum);
        quality.setText("LOCAL LIBRARY  •  " + format(current.duration));
        status.setText(PlayerManager.isPreparing()
                ? "A preparar…"
                : PlayerManager.isPlaying() ? "A tocar agora" : "Em pausa");
        loadArtwork(current);
        updateProgress();
    }

    private void openEffects() {
        try {
            startActivity(new android.content.Intent(this, EffectsActivity.class));
        } catch (Exception ignored) {
            status.setText("Efeitos indisponíveis");
        }
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
                .setMessage(message.length() == 0
                        ? "A fila está vazia. Escolhe uma música na biblioteca."
                        : message.toString())
                .setPositiveButton("Fechar", null)
                .show();
    }

    private void updateProgress() {
        if (seekBar == null) return;
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
        boolean playing = PlayerManager.isPlaying();
        play.setText(playing ? "Ⅱ" : "▶");
        status.setText(PlayerManager.isPreparing()
                ? "A preparar…" : playing ? "A tocar agora" : "Em pausa");
        shuffle.setAlpha(PlayerManager.isShuffle() ? 1f : 0.38f);
        repeat.setAlpha(PlayerManager.isRepeat() ? 1f : 0.38f);
        play.animate().scaleX(playing ? 1.04f : 1f).scaleY(playing ? 1.04f : 1f)
                .setDuration(180).start();
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
        new Thread(() -> {
            Bitmap bitmap = null;
            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(this, song.uri);
                byte[] data = retriever.getEmbeddedPicture();
                retriever.release();
                if (data != null) bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            } catch (Exception ignored) {
            }
            Bitmap result = bitmap;
            runOnUiThread(() -> {
                if (result != null) {
                    cover.setColorFilter(null);
                    cover.setImageBitmap(result);
                }
            });
        }).start();
    }

    private Button actionButton(String value, int color) {
        Button b = iconButton(value, 13, color);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round(CARD, 18));
        return b;
    }

    private Button iconButton(String value, int textSize, int color) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(textSize);
        b.setTextColor(color);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(0, 0, 0, 0);
        return b;
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

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout.LayoutParams size(int width, int height) {
        return new LinearLayout.LayoutParams(
                width < 0 ? width : dp(width),
                height < 0 ? height : dp(height));
    }

    private LinearLayout.LayoutParams weight(float value, int height) {
        return new LinearLayout.LayoutParams(
                0, height < 0 ? height : dp(height), value);
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radius) {
        android.graphics.drawable.GradientDrawable drawable =
                new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private String format(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format(Locale.getDefault(), "%d:%02d",
                seconds / 60, seconds % 60);
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
