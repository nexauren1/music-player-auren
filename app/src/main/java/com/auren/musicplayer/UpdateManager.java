package com.auren.musicplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateManager {
    private static final String LATEST =
            "https://api.github.com/repos/"
                    + "nexauren1/music-player-auren/releases/latest";
    private static final String APK_TYPE =
            "application/vnd.android.package-archive";
    private static final String APK_NAME =
            "Nexauren-Music-Player.apk";
    private static final String PREFS =
            "auren_player";
    private static final String UPDATE_TAG =
            "pending_update_tag";
    private static final String UPDATE_URL =
            "pending_update_url";
    private static final String UPDATE_VERSION =
            "pending_update_version";
    private static final String UPDATE_NAME =
            "pending_update_name";
    private static final String UPDATE_NOTES =
            "pending_update_notes";
    private static final String CHANNEL_ID =
            "app_updates";
    private static final int UPDATE_NOTIFICATION_ID =
            7401;

    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor();

    private static final Handler MAIN =
            new Handler(Looper.getMainLooper());

    private static boolean dialogShowing;

    private UpdateManager() {}

    public static void checkForUpdates(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        TextView status = new TextView(activity);
        status.setText("Verificando atualizações…");

        check(activity, status, () -> {});
    }

    public static void showPendingUpdateDialog(Activity activity) {
        if (activity == null || activity.isFinishing() || dialogShowing) {
            return;
        }

        android.content.SharedPreferences prefs =
                activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);

        String tag = prefs.getString(UPDATE_TAG, "");
        String url = prefs.getString(UPDATE_URL, "");
        if (tag.isEmpty() || url.isEmpty()) return;

        Release release = new Release(
                tag,
                url,
                prefs.getString(UPDATE_NAME,
                        "Nexauren Music Player " + cleanVersion(tag)),
                prefs.getString(UPDATE_NOTES, ""));

        if (compare(cleanVersion(release.tag),
                BuildConfig.VERSION_NAME) <= 0) {
            return;
        }

        showUpdatePrompt(
                activity,
                new TextView(activity),
                release,
                null);
    }

    public static void tryInstallPendingUpdate(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        TextView status = new TextView(activity);
        resumePending(activity, status);
    }

    public interface Callback {
        void finished();
    }

    public static void check(
            Activity activity,
            TextView status) {
        check(activity, status, null);
    }

    public static void check(
            Activity activity,
            TextView status,
            Callback callback) {

        if (status == null) return;
        status.setText("Verificando a versão mais recente…");

        EXECUTOR.execute(() -> {
            Release release = null;
            Exception error = null;

            try {
                String json = request(LATEST);

                String tag = find(
                        json,
                        "\"tag_name\"\\s*:\\s*\"([^\"]+)\"");

                String apk = find(
                        json,
                        "\"name\"\\s*:\\s*\"" + Pattern.quote(APK_NAME)
                                + "\"[\\s\\S]*?\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"");

                if (tag == null || apk == null) {
                    throw new IllegalStateException(
                            "Latest release has no Nexauren APK");
                }

                String name = find(
                        json,
                        "\"name\"\\s*:\\s*\"([^\"]+)\"");
                if (name == null || name.equals(APK_NAME)) {
                    name = "Nexauren Music Player " + cleanVersion(tag);
                }

                release = new Release(
                        tag,
                        apk,
                        name,
                        "");
            } catch (Exception e) {
                error = e;
            }

            Release result = release;
            Exception problem = error;

            MAIN.post(() -> {
                if (problem != null || result == null) {
                    status.setText(
                            "Não foi possível verificar agora. "
                                    + "Verifique a internet e tente novamente.");
                    finish(callback);
                    return;
                }

                String latest = cleanVersion(result.tag);
                if (compare(latest, BuildConfig.VERSION_NAME) <= 0) {
                    status.setText(
                            "Você já está usando a versão mais recente • "
                                    + BuildConfig.VERSION_NAME);
                    finish(callback);
                    return;
                }

                saveRelease(activity, result);
                status.setText(
                        "Nova versão " + result.tag + " encontrada.");

                postUpdateNotification(
                        activity,
                        result.tag);

                showUpdatePrompt(
                        activity,
                        status,
                        result,
                        callback);
            });
        });
    }

    private static void saveRelease(
            Activity activity,
            Release release) {

        activity.getSharedPreferences(
                        PREFS,
                        Activity.MODE_PRIVATE)
                .edit()
                .putString(UPDATE_TAG, release.tag)
                .putString(UPDATE_URL, release.apk)
                .putString(UPDATE_VERSION, cleanVersion(release.tag))
                .putString(UPDATE_NAME, release.name)
                .putString(UPDATE_NOTES, release.notes)
                .apply();
    }

    private static void showUpdatePrompt(
            Activity activity,
            TextView status,
            Release release,
            Callback callback) {

        if (dialogShowing) return;
        dialogShowing = true;

        String notes = release.notes == null
                ? ""
                : release.notes.trim();

        if (notes.length() > 500) {
            notes = notes.substring(0, 500).trim() + "…";
        }

        String message =
                "Uma nova versão do Nexauren Music Player está disponível.\n\n"
                        + release.tag;

        if (!release.name.isEmpty()
                && !release.name.equals(release.tag)) {
            message += " — " + release.name;
        }

        if (!notes.isEmpty()) {
            message += "\n\n" + notes;
        }

        new AlertDialog.Builder(activity)
                .setTitle("Atualização disponível")
                .setMessage(message)
                .setNegativeButton(
                        "Agora não",
                        (dialog, which) -> finish(callback))
                .setPositiveButton(
                        "Baixar e instalar",
                        (dialog, which) ->
                                downloadAndInstall(
                                        activity,
                                        status,
                                        release.apk,
                                        release.tag,
                                        callback))
                .setOnCancelListener(
                        dialog -> finish(callback))
                .setOnDismissListener(
                        dialog -> dialogShowing = false)
                .show();
    }

    private static void downloadAndInstall(
            Activity activity,
            TextView status,
            String url,
            String version,
            Callback callback) {

        status.setText("Preparando a atualização…");

        EXECUTOR.execute(() -> {
            File file = null;
            Exception error = null;

            try {
                file = download(
                        activity,
                        status,
                        url,
                        version);
            } catch (Exception e) {
                error = e;
            }

            File downloaded = file;
            Exception problem = error;

            MAIN.post(() -> {
                if (problem != null
                        || downloaded == null
                        || !downloaded.exists()) {
                    status.setText(
                            "O download da atualização falhou. "
                                    + "Tente novamente.");
                    finish(callback);
                    return;
                }

                setPending(
                        activity,
                        downloaded);

                openInstaller(
                        activity,
                        status,
                        downloaded,
                        callback);
            });
        });
    }

    private static File download(
            Activity activity,
            TextView status,
            String address,
            String version) throws Exception {

        HttpURLConnection connection =
                (HttpURLConnection) new URL(address).openConnection();

        connection.setRequestProperty(
                "User-Agent",
                "Nexauren-Music-Player/" + BuildConfig.VERSION_NAME);
        connection.setRequestProperty(
                "Accept",
                APK_TYPE);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.connect();

        int response = connection.getResponseCode();
        if (response < 200 || response >= 300) {
            throw new IllegalStateException(
                    "Download HTTP " + response);
        }

        File updateDir =
                new File(activity.getFilesDir(), "updates");

        if (!updateDir.exists() && !updateDir.mkdirs()) {
            throw new IllegalStateException(
                    "Could not create update directory");
        }

        File[] oldFiles = updateDir.listFiles();
        if (oldFiles != null) {
            for (File old : oldFiles) {
                try {
                    old.delete();
                } catch (Exception ignored) {
                }
            }
        }

        String safeVersion =
                version.replaceAll(
                        "[^0-9A-Za-z._-]",
                        "_");

        File out =
                new File(
                        updateDir,
                        "nexauren-update-" + safeVersion + ".apk");

        int total = connection.getContentLength();
        int done = 0;
        int lastProgress = -1;

        try (InputStream in = connection.getInputStream();
             FileOutputStream fos = new FileOutputStream(out)) {

            byte[] buffer = new byte[16 * 1024];
            int n;

            while ((n = in.read(buffer)) != -1) {
                fos.write(buffer, 0, n);
                done += n;

                if (total > 0) {
                    int progress = Math.max(
                            0,
                            Math.min(100, done * 100 / total));

                    if (progress != lastProgress) {
                        lastProgress = progress;
                        int p = progress;
                        MAIN.post(() -> status.setText(
                                "Baixando atualização… " + p + "%"));
                    }
                }
            }

            fos.flush();
        } finally {
            connection.disconnect();
        }

        if (out.length() < 100_000) {
            throw new IllegalStateException(
                    "Downloaded APK is unexpectedly small");
        }

        MAIN.post(() -> status.setText(
                "Download concluído. A preparar a instalação…"));

        return out;
    }

    private static void setPending(
            Activity activity,
            File file) {

        activity.getSharedPreferences(
                        PREFS,
                        Activity.MODE_PRIVATE)
                .edit()
                .putString(
                        "pending_update_apk",
                        file.getAbsolutePath())
                .apply();
    }

    private static void openInstaller(
            Activity activity,
            TextView status,
            File file,
            Callback callback) {

        if (!file.isFile() || file.length() < 100_000) {
            status.setText(
                    "O arquivo da atualização está inválido. "
                            + "Tente novamente.");
            finish(callback);
            return;
        }

        if (Build.VERSION.SDK_INT >= 26) {
            PackageManager pm = activity.getPackageManager();
            if (!pm.canRequestPackageInstalls()) {
                status.setText(
                        "Ative 'Permitir desta fonte' para o Nexauren e volte para instalar.");

                Intent settings =
                        new Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse(
                                        "package:"
                                                + activity.getPackageName()));
                try {
                    activity.startActivity(settings);
                } catch (Exception ignored) {
                }
                return;
            }
        }

        Uri uri = FileProvider.getUriForFile(
                activity,
                "com.auren.musicplayer.fileprovider",
                file);

        Intent installer =
                new Intent(Intent.ACTION_INSTALL_PACKAGE);

        installer.setDataAndType(uri, APK_TYPE);
        installer.setClipData(
                ClipData.newRawUri("NexaurenUpdate", uri));
        installer.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            activity.startActivity(installer);
            status.setText(
                    "Instalador do Android aberto. Confirme em 'Instalar'.");
            clearPending(activity);
            finish(callback);
            return;
        } catch (ActivityNotFoundException ignored) {
        } catch (SecurityException ignored) {
        }

        Intent fallback =
                new Intent(Intent.ACTION_VIEW);

        fallback.setDataAndType(uri, APK_TYPE);
        fallback.setClipData(
                ClipData.newRawUri("NexaurenUpdate", uri));
        fallback.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            activity.startActivity(fallback);
            status.setText(
                    "Instalador do Android aberto. Confirme em 'Instalar'.");
            clearPending(activity);
            finish(callback);
        } catch (Exception error) {
            status.setText(
                    "O APK foi baixado, mas o instalador não abriu. "
                            + "Verifique a permissão de instalação desta fonte.");
            finish(callback);
        }
    }

    public static void resumePending(
            Activity activity,
            TextView status) {

        String path =
                activity.getSharedPreferences(
                                PREFS,
                                Activity.MODE_PRIVATE)
                        .getString(
                                "pending_update_apk",
                                "");

        if (path == null || path.isEmpty()) return;

        File file = new File(path);
        if (!file.isFile() || file.length() < 100_000) {
            clearPending(activity);
            return;
        }

        if (Build.VERSION.SDK_INT >= 26
                && !activity.getPackageManager()
                        .canRequestPackageInstalls()) {
            status.setText(
                    "Ative 'Permitir desta fonte' e volte para concluir.");
            return;
        }

        status.setText(
                "Atualização pronta. A abrir o instalador…");

        openInstaller(
                activity,
                status,
                file,
                null);
    }

    private static void clearPending(
            Activity activity) {

        activity.getSharedPreferences(
                        PREFS,
                        Activity.MODE_PRIVATE)
                .edit()
                .remove("pending_update_apk")
                .apply();
    }

    private static void postUpdateNotification(
            Context context,
            String version) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager =
                    context.getSystemService(NotificationManager.class);
            if (manager != null) {
                NotificationChannel channel =
                        new NotificationChannel(
                                CHANNEL_ID,
                                "Atualizações do aplicativo",
                                NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription(
                        "Avisos sobre novas versões do Nexauren Music Player.");
                manager.createNotificationChannel(channel);
            }
        }

        Intent intent =
                new Intent(context, MainActivity.class)
                        .putExtra(
                                "nexauren_update_action",
                                "show_update")
                        .addFlags(
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context,
                        UPDATE_NOTIFICATION_ID,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE);

        try {
            NotificationManagerCompat.from(context).notify(
                    UPDATE_NOTIFICATION_ID,
                    new NotificationCompat.Builder(
                            context,
                            CHANNEL_ID)
                            .setSmallIcon(
                                    R.drawable.nexauren_music_player_icon)
                            .setContentTitle(
                                    "Nova versão do Nexauren")
                            .setContentText(
                                    "A versão " + version + " está disponível.")
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)
                            .setPriority(
                                    NotificationCompat.PRIORITY_DEFAULT)
                            .build());
        } catch (SecurityException ignored) {
            // Android 13+ may block notifications until permission is granted.
        }
    }

    private static String request(
            String address) throws Exception {

        HttpURLConnection connection =
                (HttpURLConnection) new URL(address).openConnection();

        connection.setRequestProperty(
                "Accept",
                "application/vnd.github+json");
        connection.setRequestProperty(
                "User-Agent",
                "Nexauren-Music-Player/" + BuildConfig.VERSION_NAME);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);

        try (InputStream in = connection.getInputStream();
             ByteArrayOutputStream out =
                     new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int n;

            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }

            return out.toString("UTF-8");
        } finally {
            connection.disconnect();
        }
    }

    private static String find(
            String value,
            String regex) {

        Matcher matcher =
                Pattern.compile(
                        regex,
                        Pattern.DOTALL)
                        .matcher(value);

        return matcher.find()
                ? matcher.group(1)
                : null;
    }

    private static String unescape(String value) {
        return value
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static String cleanVersion(
            String value) {

        String v =
                value == null
                        ? "0.0.0"
                        : value.trim();

        while (v.startsWith("v")
                || v.startsWith("V")) {
            v = v.substring(1);
        }

        int dash = v.indexOf('-');
        if (dash >= 0) {
            v = v.substring(0, dash);
        }

        return v;
    }

    private static int compare(
            String a,
            String b) {

        String[] x =
                cleanVersion(a).split("\\.");
        String[] y =
                cleanVersion(b).split("\\.");

        int count = Math.max(x.length, y.length);

        for (int i = 0; i < count; i++) {
            int left = i < x.length ? num(x[i]) : 0;
            int right = i < y.length ? num(y[i]) : 0;

            if (left != right) {
                return Integer.compare(left, right);
            }
        }

        return 0;
    }

    private static int num(String value) {
        Matcher matcher =
                Pattern.compile("\\d+")
                        .matcher(value);

        return matcher.find()
                ? Integer.parseInt(matcher.group())
                : 0;
    }

    private static void finish(
            Callback callback) {

        if (callback != null) {
            callback.finished();
        }
    }

    private static final class Release {
        final String tag;
        final String apk;
        final String name;
        final String notes;

        Release(
                String tag,
                String apk,
                String name,
                String notes) {
            this.tag = tag;
            this.apk = apk;
            this.name = name == null ? "" : name;
            this.notes = notes == null ? "" : notes;
        }
    }
}
