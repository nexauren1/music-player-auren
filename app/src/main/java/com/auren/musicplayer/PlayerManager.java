package com.auren.musicplayer;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;

public final class PlayerManager {
    public interface Listener {
        void onPlayerChanged();
    }

    private static MediaPlayer player;
    private static Song currentSong;
    private static Song[] queue = new Song[0];
    private static Listener listener;
    private static Context appContext;
    private static boolean shuffle;
    private static boolean repeat;
    private static boolean preparing;

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

    public static boolean isPreparing() {
        return preparing;
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
            return currentSong == null ? 0 : (int) currentSong.duration;
        }
    }

    public static void setQueue(Song[] songs) {
        queue = songs == null ? new Song[0] : songs.clone();
        notifyChanged();
    }

    public static void play(Context context, Song song) {
        if (context == null || song == null || song.uri == null) return;

        appContext = context.getApplicationContext();
        currentSong = song;
        preparing = true;
        releasePlayerOnly();
        startPlaybackService();
        notifyChanged();

        try {
            MediaPlayer newPlayer = new MediaPlayer();
            player = newPlayer;
            configureAudio(newPlayer);
            newPlayer.setDataSource(appContext, song.uri);
            newPlayer.setOnPreparedListener(mp -> {
                if (mp != player) return;
                preparing = false;
                mp.start();
                notifyChanged();
            });
            newPlayer.setOnCompletionListener(mp -> {
                preparing = false;
                handleCompletion();
            });
            newPlayer.setOnErrorListener((mp, what, extra) -> {
                if (mp == player) {
                    preparing = false;
                    releasePlayerOnly();
                    notifyChanged();
                }
                return true;
            });
            newPlayer.prepareAsync();
        } catch (Exception e) {
            preparing = false;
            releasePlayerOnly();
            notifyChanged();
        }
    }

    private static void configureAudio(MediaPlayer mediaPlayer) {
        if (Build.VERSION.SDK_INT >= 21) {
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
        } else {
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        }
    }

    private static void handleCompletion() {
        if (repeat && currentSong != null && appContext != null) {
            play(appContext, currentSong);
            return;
        }
        if (queue.length > 1 && appContext != null) {
            next(appContext, queue);
        } else {
            notifyChanged();
        }
    }

    public static void toggle() {
        if (player == null) {
            if (currentSong != null && appContext != null) {
                play(appContext, currentSong);
            }
            return;
        }
        try {
            if (player.isPlaying()) {
                player.pause();
            } else if (!preparing) {
                player.start();
            }
            notifyChanged();
        } catch (Exception ignored) {
            if (currentSong != null && appContext != null) {
                play(appContext, currentSong);
            }
        }
    }

    public static void seekTo(int position) {
        if (player == null || preparing) return;
        try {
            player.seekTo(Math.max(0, position));
        } catch (Exception ignored) {
        }
    }

    public static void next(Context context, Song[] songs) {
        Song[] source = songs == null ? queue : songs;
        if (source.length == 0) return;
        appContext = context.getApplicationContext();
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
        play(appContext, source[next]);
    }

    public static void previous(Context context, Song[] songs) {
        Song[] source = songs == null ? queue : songs;
        if (source.length == 0) return;
        appContext = context.getApplicationContext();
        queue = source.clone();
        if (getPosition() > 3000) {
            seekTo(0);
            return;
        }
        int index = indexOf(source, currentSong);
        int previous = index - 1;
        if (previous < 0) previous = source.length - 1;
        play(appContext, source[previous]);
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
        preparing = false;
        releasePlayerOnly();
    }

    private static void releasePlayerOnly() {
        MediaPlayer old = player;
        player = null;
        preparing = false;
        if (old != null) {
            try { old.setOnPreparedListener(null); } catch (Exception ignored) { }
            try { old.setOnCompletionListener(null); } catch (Exception ignored) { }
            try { old.setOnErrorListener(null); } catch (Exception ignored) { }
            try { old.stop(); } catch (Exception ignored) { }
            try { old.reset(); } catch (Exception ignored) { }
            try { old.release(); } catch (Exception ignored) { }
        }
    }

    private static void startPlaybackService() {
        if (appContext == null) return;
        Intent intent = new Intent(appContext, PlaybackService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                appContext.startForegroundService(intent);
            } else {
                appContext.startService(intent);
            }
        } catch (Exception ignored) {
        }
    }

    private static void notifyChanged() {
        if (listener != null) listener.onPlayerChanged();
        if (appContext != null) {
            Intent intent = new Intent(appContext, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_UPDATE);
            try {
                appContext.startService(intent);
            } catch (Exception ignored) {
            }
        }
    }

    private static int indexOf(Song[] songs, Song song) {
        if (song == null) return 0;
        for (int i = 0; i < songs.length; i++) {
            if (songs[i].uri.equals(song.uri)) return i;
        }
        return 0;
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
