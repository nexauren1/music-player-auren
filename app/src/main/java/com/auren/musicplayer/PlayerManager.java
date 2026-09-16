package com.auren.musicplayer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

public final class PlayerManager {
    public interface Listener {
        void onPlayerChanged();

        default void onPlayerError(String message) {
        }
    }

    private static ExoPlayer player;
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
        return player != null && player.isPlaying();
    }

    public static int getPosition() {
        if (player == null) return 0;
        return (int) Math.max(0, player.getCurrentPosition());
    }

    public static int getDuration() {
        if (player == null) {
            return currentSong == null ? 0 : (int) currentSong.duration;
        }
        long duration = player.getDuration();
        if (duration == C.TIME_UNSET || duration < 0) {
            return currentSong == null ? 0 : (int) currentSong.duration;
        }
        return (int) duration;
    }

    public static Bitmap getCurrentArtwork() {
        if (currentSong == null || currentSong.uri == null) return null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(appContext, currentSong.uri);
            byte[] data = retriever.getEmbeddedPicture();
            if (data != null) return BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Exception ignored) {
        } finally {
            try { retriever.release(); } catch (Exception ignored) { }
        }
        return null;
    }

    public static void setQueue(Song[] songs) {
        queue = songs == null ? new Song[0] : songs.clone();
        notifyChanged();
    }

    public static void play(Context context, Song song) {
        if (context == null || song == null || song.uri == null) {
            notifyError("Esta música não está disponível.");
            return;
        }

        appContext = context.getApplicationContext();
        currentSong = song;
        preparing = true;
        ensurePlayer();
        startPlaybackService();
        notifyChanged();

        try {
            MediaMetadata metadata = new MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .build();
            MediaItem item = new MediaItem.Builder()
                    .setUri(song.uri)
                    .setMediaMetadata(metadata)
                    .build();
            player.setMediaItem(item);
            player.prepare();
            player.play();
        } catch (Exception e) {
            failPlayback("Não foi possível iniciar esta música.");
        }
    }

    private static void ensurePlayer() {
        if (player != null) return;

        player = new ExoPlayer.Builder(appContext).build();
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
        player.setShuffleModeEnabled(shuffle);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                preparing = state == Player.STATE_BUFFERING
                        || state == Player.STATE_IDLE;
                if (state == Player.STATE_READY) {
                    preparing = false;
                    notifyChanged();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) preparing = false;
                notifyChanged();
            }

            @Override
            public void onMediaItemTransition(MediaItem item, int reason) {
                updateCurrentSongFromIndex();
                notifyChanged();
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                preparing = false;
                notifyError("Não foi possível reproduzir esta música. "
                        + "Erro " + error.errorCode + ".");
                notifyChanged();
            }
        });
    }

    private static void updateCurrentSongFromIndex() {
        if (player == null || queue.length == 0) return;
        int index = player.getCurrentMediaItemIndex();
        if (index >= 0 && index < queue.length) currentSong = queue[index];
    }

    public static void toggle() {
        if (player == null) {
            if (currentSong != null && appContext != null) play(appContext, currentSong);
            return;
        }
        if (player.isPlaying()) player.pause();
        else if (player.getPlaybackState() == Player.STATE_IDLE) {
            player.prepare();
            player.play();
        } else player.play();
        notifyChanged();
    }

    public static void seekTo(int position) {
        if (player != null) player.seekTo(Math.max(0, position));
    }

    public static void next(Context context, Song[] songs) {
        if (context == null) return;
        Song[] source = songs == null ? queue : songs;
        if (source.length == 0) return;
        appContext = context.getApplicationContext();
        queue = source.clone();
        if (player != null && player.getMediaItemCount() == source.length) {
            player.seekToNextMediaItem();
            player.play();
            return;
        }
        int index = indexOf(source, currentSong);
        int next;
        if (shuffle && source.length > 1) {
            do next = (int) (Math.random() * source.length);
            while (next == index);
        } else next = (index + 1) % source.length;
        play(appContext, source[next]);
    }

    public static void previous(Context context, Song[] songs) {
        if (context == null) return;
        Song[] source = songs == null ? queue : songs;
        if (source.length == 0) return;
        appContext = context.getApplicationContext();
        queue = source.clone();
        if (getPosition() > 3000) {
            seekTo(0);
            return;
        }
        if (player != null && player.getMediaItemCount() == source.length) {
            player.seekToPreviousMediaItem();
            player.play();
            return;
        }
        int index = indexOf(source, currentSong);
        int previous = index - 1;
        if (previous < 0) previous = source.length - 1;
        play(appContext, source[previous]);
    }

    public static void setShuffle(boolean value) {
        shuffle = value;
        if (player != null) player.setShuffleModeEnabled(value);
        notifyChanged();
    }

    public static void setRepeat(boolean value) {
        repeat = value;
        if (player != null) {
            player.setRepeatMode(value
                    ? Player.REPEAT_MODE_ONE
                    : Player.REPEAT_MODE_OFF);
        }
        notifyChanged();
    }

    public static void release() {
        preparing = false;
        if (player != null) {
            player.release();
            player = null;
        }
        notifyChanged();
    }

    private static void failPlayback(String message) {
        preparing = false;
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
        }
        notifyError(message);
        notifyChanged();
    }

    private static void notifyError(String message) {
        if (listener != null) listener.onPlayerError(message);
    }

    private static void startPlaybackService() {
        if (appContext == null) return;
        Intent intent = new Intent(appContext, PlaybackService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                appContext.startForegroundService(intent);
            } else appContext.startService(intent);
        } catch (Exception ignored) { }
    }

    private static void notifyChanged() {
        if (listener != null) listener.onPlayerChanged();
        if (appContext == null) return;
        Intent intent = new Intent(appContext, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_UPDATE);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                appContext.startForegroundService(intent);
            } else appContext.startService(intent);
        } catch (Exception ignored) { }
    }

    private static int indexOf(Song[] songs, Song song) {
        if (song == null) return 0;
        for (int i = 0; i < songs.length; i++) {
            if (songs[i] != null && songs[i].uri.equals(song.uri)) return i;
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
