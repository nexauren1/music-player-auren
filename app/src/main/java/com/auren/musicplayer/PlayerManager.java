package com.auren.musicplayer;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;

public final class PlayerManager {
    public interface Listener {
        void onPlayerChanged();
    }

    private static MediaPlayer player;
    private static Song currentSong;
    private static Listener listener;
    private static boolean shuffle;
    private static boolean repeat;

    private PlayerManager() {
    }

    public static void setListener(Listener value) {
        listener = value;
        notifyChanged();
    }

    public static Song getCurrentSong() {
        return currentSong;
    }

    public static boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public static boolean isShuffle() {
        return shuffle;
    }

    public static boolean isRepeat() {
        return repeat;
    }

    public static int getPosition() {
        try {
            return player == null ? 0 : player.getCurrentPosition();
        } catch (Exception e) {
            return 0;
        }
    }

    public static int getDuration() {
        try {
            return player == null ? 0 : player.getDuration();
        } catch (Exception e) {
            return 0;
        }
    }

    public static void play(Context context, Song song) {
        release();
        currentSong = song;
        try {
            player = new MediaPlayer();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setDataSource(context.getApplicationContext(), song.uri);
            player.setOnPreparedListener(mp -> {
                mp.start();
                notifyChanged();
            });
            player.setOnCompletionListener(mp -> {
                if (repeat) {
                    mp.seekTo(0);
                    mp.start();
                    notifyChanged();
                } else {
                    notifyChanged();
                }
            });
            player.setOnErrorListener((mp, what, extra) -> {
                release();
                notifyChanged();
                return true;
            });
            player.prepareAsync();
            notifyChanged();
        } catch (Exception e) {
            release();
            notifyChanged();
        }
    }

    public static void toggle() {
        if (player == null) {
            return;
        }
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.start();
        }
        notifyChanged();
    }

    public static void seekTo(int position) {
        if (player == null) {
            return;
        }
        try {
            player.seekTo(position);
        } catch (Exception ignored) {
        }
    }

    public static void next(Context context, Song[] queue) {
        if (queue == null || queue.length == 0) {
            return;
        }
        int index = indexOf(queue, currentSong);
        if (shuffle && queue.length > 1) {
            int next;
            do {
                next = (int) (Math.random() * queue.length);
            } while (next == index);
            play(context, queue[next]);
            return;
        }
        play(context, queue[(index + 1) % queue.length]);
    }

    public static void previous(Context context, Song[] queue) {
        if (queue == null || queue.length == 0) {
            return;
        }
        if (getPosition() > 3000) {
            seekTo(0);
            return;
        }
        int index = indexOf(queue, currentSong);
        int previous = index - 1;
        if (previous < 0) {
            previous = queue.length - 1;
        }
        play(context, queue[previous]);
    }

    public static void setShuffle(boolean value) {
        shuffle = value;
        notifyChanged();
    }

    public static void setRepeat(boolean value) {
        repeat = value;
        notifyChanged();
    }

    public static void release() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
    }

    private static int indexOf(Song[] queue, Song song) {
        if (song == null) {
            return 0;
        }
        for (int i = 0; i < queue.length; i++) {
            if (queue[i].uri.equals(song.uri)) {
                return i;
            }
        }
        return 0;
    }

    private static void notifyChanged() {
        if (listener != null) {
            listener.onPlayerChanged();
        }
    }

    public static final class Song {
        public final String title;
        public final String artist;
        public final String album;
        public final long duration;
        public final Uri uri;

        public Song(
                String title,
                String artist,
                String album,
                long duration,
                Uri uri) {
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.duration = duration;
            this.uri = uri;
        }
    }
}
