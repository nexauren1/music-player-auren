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
    private static Song[] queue = new Song[0];
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

    public static Song[] getQueue() {
        return queue.clone();
    }

    public static boolean isPlaying() {
        try {
            return player != null && player.isPlaying();
        } catch (Exception e) {
            return false;
        }
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

    public static void setQueue(Song[] songs) {
        queue = songs == null ? new Song[0] : songs.clone();
        notifyChanged();
    }

    public static void play(Context context, Song song) {
        if (song == null || song.uri == null) return;
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
            player.setOnCompletionListener(mp -> handleCompletion(context));
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

    private static void handleCompletion(Context context) {
        if (repeat && player != null) {
            try {
                player.seekTo(0);
                player.start();
                notifyChanged();
                return;
            } catch (Exception ignored) {
            }
        }
        if (queue.length > 1) {
            next(context, queue);
        } else {
            notifyChanged();
        }
    }

    public static void toggle() {
        if (player == null) return;
        try {
            if (player.isPlaying()) player.pause();
            else player.start();
            notifyChanged();
        } catch (Exception ignored) {
        }
    }

    public static void seekTo(int position) {
        if (player == null) return;
        try {
            player.seekTo(position);
        } catch (Exception ignored) {
        }
    }

    public static void next(Context context, Song[] songs) {
        Song[] source = songs == null ? queue : songs;
        if (source.length == 0) return;
        queue = source.clone();
        int index = indexOf(source, currentSong);
        int next;
        if (shuffle && source.length > 1) {
            do {
                next = (int) (Math.random() * source.length);
            } while (next == index);
        } else {
            next = (index + 1) % source.length;
        }
        play(context, source[next]);
    }

    public static void previous(Context context, Song[] songs) {
        Song[] source = songs == null ? queue : songs;
        if (source.length == 0) return;
        queue = source.clone();
        if (getPosition() > 3000) {
            seekTo(0);
            return;
        }
        int index = indexOf(source, currentSong);
        int previous = index - 1;
        if (previous < 0) previous = source.length - 1;
        play(context, source[previous]);
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
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    private static int indexOf(Song[] songs, Song song) {
        if (song == null) return 0;
        for (int i = 0; i < songs.length; i++) {
            if (songs[i].uri.equals(song.uri)) return i;
        }
        return 0;
    }

    private static void notifyChanged() {
        if (listener != null) listener.onPlayerChanged();
    }

    public static final class Song {
        public final String title;
        public final String artist;
        public final String album;
        public final long duration;
        public final Uri uri;

        public Song(String title, String artist, String album,
                    long duration, Uri uri) {
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.duration = duration;
            this.uri = uri;
        }
    }
}
