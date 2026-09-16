package com.auren.musicplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class PlaybackService extends Service {
    public static final String ACTION_PLAY_PAUSE =
            "com.auren.musicplayer.PLAY_PAUSE";
    public static final String ACTION_NEXT =
            "com.auren.musicplayer.NEXT";
    public static final String ACTION_PREVIOUS =
            "com.auren.musicplayer.PREVIOUS";
    public static final String ACTION_STOP =
            "com.auren.musicplayer.STOP";

    private static final String CHANNEL_ID = "auren_playback";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_PLAY_PAUSE.equals(action)) {
                PlayerManager.toggle();
            } else if (ACTION_NEXT.equals(action)) {
                PlayerManager.next(this, PlayerManager.getQueue());
            } else if (ACTION_PREVIOUS.equals(action)) {
                PlayerManager.previous(this, PlayerManager.getQueue());
            } else if (ACTION_STOP.equals(action)) {
                PlayerManager.release();
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
                return START_NOT_STICKY;
            }
        }

        updateNotification();
        return START_STICKY;
    }

    public void updateNotification() {
        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private Notification buildNotification() {
        PlayerManager.Song song = PlayerManager.getCurrentSong();
        String title = song == null ? "Auren Music Player" : song.title;
        String artist = song == null ? "Nenhuma música" : song.artist;

        PendingIntent previous = action(ACTION_PREVIOUS, 10);
        PendingIntent playPause = action(ACTION_PLAY_PAUSE, 11);
        PendingIntent next = action(ACTION_NEXT, 12);
        PendingIntent stop = action(ACTION_STOP, 13);

        Intent open = new Intent(this, AurenHomeActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(
                this, 20, open, pendingFlags());

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        builder.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(artist)
                .setContentIntent(content)
                .setOngoing(PlayerManager.isPlaying())
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(android.R.drawable.ic_media_previous,
                        "Anterior", previous)
                .addAction(
                        PlayerManager.isPlaying()
                                ? android.R.drawable.ic_media_pause
                                : android.R.drawable.ic_media_play,
                        PlayerManager.isPlaying() ? "Pausar" : "Reproduzir",
                        playPause)
                .addAction(android.R.drawable.ic_media_next,
                        "Próxima", next)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        "Parar", stop);

        return builder.build();
    }

    private PendingIntent action(String action, int requestCode) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        return PendingIntent.getService(
                this, requestCode, intent, pendingFlags());
    }

    private int pendingFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Reprodução de música",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Controles do Auren Music Player");
        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        PlayerManager.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
