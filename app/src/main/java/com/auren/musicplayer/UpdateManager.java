package com.auren.musicplayer;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.TextView;

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

    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor();

    private static final Handler MAIN =
            new Handler(Looper.getMainLooper());

    private UpdateManager() {}

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

        status.setText(
                "Verificando a versão mais recente…");

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
                        "\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"");

                if (tag == null || apk == null) {
                    throw new IllegalStateException(
                            "Latest release has no APK");
                }

                release = new Release(
                        tag,
                        apk);
            } catch (Exception e) {
                error = e;
            }

            Release result = release;
            Exception problem = error;

            MAIN.post(() -> {
                if (problem != null
                        || result == null) {

                    status.setText(
                            "Não foi possível verificar agora. "
                                    + "Verifique a internet e tente novamente.");

                    finish(callback);
                    return;
                }

                String latest =
                        cleanVersion(result.tag);

                if (compare(
                        latest,
                        BuildConfig.VERSION_NAME) <= 0) {

                    status.setText(
                            "Você já está usando a versão mais recente • "
                                    + BuildConfig.VERSION_NAME);

                    finish(callback);
                    return;
                }

                status.setText(
                        "Nova versão "
                                + result.tag
                                + " encontrada.");

                showUpdatePrompt(
                        activity,
                        status,
                        result,
                        callback);
            });
        });
    }

    private static void showUpdatePrompt(
            Activity activity,
            TextView status,
            Release release,
            Callback callback) {

        new android.app.AlertDialog.Builder(
                activity)
                .setTitle("Atualização disponível")
                .setMessage(
                        "A versão "
                                + release.tag
                                + " será baixada. "
                                + "Depois o Android abrirá a confirmação "
                                + "de instalação.")
                .setNegativeButton(
                        "Agora não",
                        (dialog, which) ->
                                finish(callback))
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
                .show();
    }

    private static void downloadAndInstall(
            Activity activity,
            TextView status,
            String url,
            String version,
            Callback callback) {

        status.setText(
                "Preparando a atualização…");

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
                (HttpURLConnection) new URL(address)
                        .openConnection();

        connection.setRequestProperty(
                "User-Agent",
                "Music-Player-Nexauren");

        connection.setRequestProperty(
                "Accept",
                APK_TYPE);

        connection.setConnectTimeout(
                15000);

        connection.setReadTimeout(
                30000);

        connection.setInstanceFollowRedirects(
                true);

        connection.connect();

        if (connection.getResponseCode() < 200
                || connection.getResponseCode() >= 300) {

            throw new IllegalStateException(
                    "Download HTTP "
                            + connection.getResponseCode());
        }

        File updateDir =
                new File(
                        activity.getFilesDir(),
                        "updates");

        if (!updateDir.exists()
                && !updateDir.mkdirs()) {

            throw new IllegalStateException(
                    "Could not create update directory");
        }

        File[] oldFiles =
                updateDir.listFiles();

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
                        "nexauren-update-"
                                + safeVersion
                                + ".apk");

        int total =
                connection.getContentLength();

        int done = 0;
        int lastProgress = -1;

        try (InputStream in =
                     connection.getInputStream();
             FileOutputStream fos =
                     new FileOutputStream(out)) {

            byte[] buffer =
                    new byte[16 * 1024];

            int n;

            while ((n = in.read(buffer)) != -1) {
                fos.write(
                        buffer,
                        0,
                        n);

                done += n;

                if (total > 0) {
                    int progress =
                            Math.max(
                                    0,
                                    Math.min(
                                            100,
                                            done * 100 / total));

                    if (progress != lastProgress) {
                        lastProgress = progress;
                        int p = progress;

                        MAIN.post(() ->
                                status.setText(
                                        "Baixando atualização… "
                                                + p
                                                + "%"));
                    }
                } else {
                    final int mb =
                            done / (1024 * 1024);

                    MAIN.post(() ->
                            status.setText(
                                    "Baixando atualização… "
                                            + mb
                                            + " MB"));
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

        MAIN.post(() ->
                status.setText(
                        "Download concluído. "
                                + "A preparar a instalação…"));

        return out;
    }

    private static void setPending(
            Activity activity,
            File file) {

        activity.getSharedPreferences(
                        "auren_player",
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

        if (!file.isFile()
                || file.length() < 100_000) {

            status.setText(
                    "O arquivo da atualização está inválido. "
                            + "Tente novamente.");

            finish(callback);
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            PackageManager pm = activity.getPackageManager();

            if (!pm.canRequestPackageInstalls()) {
                status.setText(
                        "Ative 'Permitir desta fonte' para o Music Player - Nexauren e volte aqui para instalar.");

                Intent settings =
                        new Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse(
                                        "package:"
                                                + activity.getPackageName()));

                activity.startActivity(settings);
                return;
            }
        }

        Uri uri = FileProvider.getUriForFile(
                activity,
                "com.auren.musicplayer.fileprovider",
                file);

        Intent installer =
                new Intent(Intent.ACTION_VIEW);

        installer.setDataAndType(
                uri,
                APK_TYPE);

        installer.setClipData(
                ClipData.newRawUri(
                        "NexaurenUpdate",
                        uri));

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

        fallback.setDataAndType(
                uri,
                APK_TYPE);

        fallback.setClipData(
                ClipData.newRawUri(
                        "NexaurenUpdate",
                        uri));

        fallback.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            activity.startActivity(fallback);

            status.setText(
                    "Instalador do Android aberto. "
                            + "Confirme em 'Instalar'.");

            finish(callback);
        } catch (Exception error) {
            status.setText(
                    "O APK foi baixado, mas o instalador "
                            + "não abriu. Verifique a permissão "
                            + "de instalação desta fonte.");

            finish(callback);
        }
    }

    private static String request(
            String address) throws Exception {

        HttpURLConnection connection =
                (HttpURLConnection) new URL(address)
                        .openConnection();

        connection.setRequestProperty(
                "Accept",
                "application/vnd.github+json");

        connection.setRequestProperty(
                "User-Agent",
                "Music-Player-Nexauren");

        connection.setConnectTimeout(
                10000);

        connection.setReadTimeout(
                15000);

        try (InputStream in =
                     connection.getInputStream();
             ByteArrayOutputStream out =
                     new ByteArrayOutputStream()) {

            byte[] buffer =
                    new byte[8192];

            int n;

            while ((n = in.read(buffer)) != -1) {
                out.write(
                        buffer,
                        0,
                        n);
            }

            return out.toString(
                    "UTF-8");
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

    private static String cleanVersion(
            String value) {

        String v =
                value == null
                        ? "0.0.0"
                        : value.trim();

        if (v.startsWith("v")) {
            v = v.substring(1);
        }

        int dash =
                v.indexOf('-');

        if (dash >= 0) {
            v = v.substring(
                    0,
                    dash);
        }

        return v;
    }

    private static int compare(
            String a,
            String b) {

        String[] x =
                cleanVersion(a)
                        .split("\\.");

        String[] y =
                cleanVersion(b)
                        .split("\\.");

        int count =
                Math.max(
                        x.length,
                        y.length);

        for (int i = 0;
                i < count;
                i++) {

            int left =
                    i < x.length
                            ? num(x[i])
                            : 0;

            int right =
                    i < y.length
                            ? num(y[i])
                            : 0;

            if (left != right) {
                return Integer.compare(
                        left,
                        right);
            }
        }

        return 0;
    }

    private static int num(
            String value) {

        Matcher matcher =
                Pattern.compile(
                        "\\d+")
                        .matcher(value);

        return matcher.find()
                ? Integer.parseInt(
                        matcher.group())
                : 0;
    }

    public static void resumePending(
            Activity activity,
            TextView status) {

        String path =
                activity.getSharedPreferences(
                                "auren_player",
                                Activity.MODE_PRIVATE)
                        .getString(
                                "pending_update_apk",
                                "");

        if (path == null
                || path.isEmpty()) {
            return;
        }

        File file =
                new File(path);

        if (!file.isFile()
                || file.length() < 100_000) {

            clearPending(activity);
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= 26
                && !activity.getPackageManager()
                .canRequestPackageInstalls()) {

            status.setText(
                    "Ative 'Permitir desta fonte' "
                            + "e volte para concluir.");

            return;
        }

        status.setText(
                "Atualização pronta. "
                        + "A abrir o instalador…");

        openInstaller(
                activity,
                status,
                file,
                null);
    }

    private static void clearPending(
            Activity activity) {

        activity.getSharedPreferences(
                        "auren_player",
                        Activity.MODE_PRIVATE)
                .edit()
                .remove(
                        "pending_update_apk")
                .apply();
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

        Release(
                String tag,
                String apk) {
            this.tag = tag;
            this.apk = apk;
        }
    }
}
