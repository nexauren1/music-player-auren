package com.auren.musicplayer;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.common.util.UnstableApi;
import android.os.Handler;
import android.os.Looper;


@UnstableApi
public final class PlaybackService extends MediaSessionService {
    private ExoPlayer player;
    private MediaSession mediaSession;
    private final Handler analyticsHandler = new Handler(Looper.getMainLooper());
    private long lastAnalyticsTickMs;

    @Override
    public void onCreate() {
        super.onCreate();

        player = new ExoPlayer.Builder(this).build();
        player.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                true
        );
        player.setHandleAudioBecomingNoisy(true);

        AudioEffectsManager.attach(this, player.getAudioSessionId());
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY && !AudioEffectsManager.available()) {
                    AudioEffectsManager.attach(PlaybackService.this, player.getAudioSessionId());
                }
            }
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                if (!isPlaying) lastAnalyticsTickMs = 0L;
            }
        });
        mediaSession = new MediaSession.Builder(this, player).build();
        analyticsHandler.post(analyticsTicker);
    }

    private final Runnable analyticsTicker = new Runnable() {
        @Override public void run() {
            try {
                if (player != null && player.isPlaying() && player.getCurrentMediaItem() != null) {
                    String mediaId = player.getCurrentMediaItem().mediaId;
                    long id = 0L;
                    try { id = Long.parseLong(mediaId); } catch (Exception ignored) {}
                    if (id > 0L) {
                        long now = System.currentTimeMillis();
                        long delta = lastAnalyticsTickMs == 0L ? 1000L
                                : Math.max(0L, Math.min(5000L, now - lastAnalyticsTickMs));
                        String artist = player.getCurrentMediaItem().mediaMetadata.artist == null
                                ? "" : player.getCurrentMediaItem().mediaMetadata.artist.toString();
                        AurenAnalytics.tick(PlaybackService.this, id, artist, delta, true);
                        lastAnalyticsTickMs = now;
                    }
                }
            } finally {
                analyticsHandler.postDelayed(this, 1000L);
            }
        }
    };

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onDestroy() {
        analyticsHandler.removeCallbacks(analyticsTicker);
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        AudioEffectsManager.release();
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
