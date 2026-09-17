package com.auren.musicplayer;

import android.app.Activity;
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
    private static final String LATEST = "https://api.github.com/repos/nexauren1/music-player-auren/releases/latest";
    private static final String APK_TYPE = "application/vnd.android.package-archive";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private UpdateManager() {}

    public interface Callback {
        void finished();
    }

    public static void check(Activity activity, TextView status) {
        check(activity, status, null);
    }

    public static void check(Activity activity, TextView status, Callback callback) {
        status.setText("Verificando a versão mais recente…");
        EXECUTOR.execute(() -> {
            Release release = null;
            Exception error = null;
            try {
                String json = request(LATEST);
                String tag = find(json, "\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
                String apk = find(json, "\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+\\.apk)\\\"");
                if (tag == null || apk == null) {
                    throw new IllegalStateException("Latest release has no APK");
                }
                release = new Release(tag, apk);
            } catch (Exception e) {
                error = e;
            }

            final Release result = release;
            final Exception problem = error;
            MAIN.post(() -> {
                if (problem != null || result == null) {
                    status.setText("Não foi possível verificar agora. Verifique a internet e tente novamente.");
                    finish(callback);
                    return;
                }

                String latest = cleanVersion(result.tag);
                if (compare(latest, BuildConfig.VERSION_NAME) <= 0) {
                    status.setText("Você já está usando a versão mais recente • " + BuildConfig.VERSION_NAME);
                    finish(callback);
                    return;
                }

                status.setText("Nova versão " + result.tag + " encontrada.");
                showUpdatePrompt(activity, status, result, callback);
            });
        });
    }

    private static void showUpdatePrompt(Activity activity, TextView status,
                                         Release release, Callback callback) {
        new android.app.AlertDialog.Builder(activity)
                .setTitle("Atualização disponível")
                .setMessage("A versão " + release.tag
                        + " está disponível. O Auren vai baixar o APK e abrir o instalador oficial do Android.")
                .setNegativeButton("Agora não", (dialog, which) -> finish(callback))
                .setPositiveButton("Atualizar", (dialog, which) ->
                        downloadAndInstall(activity, status, release.apk, release.tag, callback))
                .setOnCancelListener(dialog -> finish(callback))
                .show();
    }

    private static void downloadAndInstall(Activity activity, TextView status,
                                           String url, String version, Callback callback) {
        status.setText("Preparando a atualização…");
        EXECUTOR.execute(() -> {
            File file = null;
            Exception error = null;
            try {
                file = download(activity, status, url, version);
            } catch (Exception e) {
                error = e;
            }

            final File downloaded = file;
            final Exception problem = error;
            MAIN.post(() -> {
                if (problem != null || downloaded == null || !downloaded.exists()) {
                    status.setText("O download da atualização falhou. Tente novamente.");
                    finish(callback);
                    return;
                }
                openInstaller(activity, status, downloaded, callback);
            });
        });
    }

    private static File download(Activity activity, TextView status,
                                 String address, String version) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setRequestProperty("User-Agent", "Auren-Music-Player");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.connect();

        if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
            throw new IllegalStateException("Download HTTP " + connection.getResponseCode());
        }

        int total = connection.getContentLength();
        int done = 0;
        File cache = activity.getExternalCacheDir();
        if (cache == null) cache = activity.getCacheDir();
        File out = new File(cache, "auren-update.apk");
        if (out.exists() && !out.delete()) {
            throw new IllegalStateException("Could not replace cached APK");
        }

        try (InputStream in = connection.getInputStream();
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buffer = new byte[16 * 1024];
            int n;
            int lastProgress = -1;
            while ((n = in.read(buffer)) != -1) {
                fos.write(buffer, 0, n);
                done += n;
                if (total > 0) {
                    int progress = Math.max(0, Math.min(100, done * 100 / total));
                    if (progress != lastProgress) {
                        lastProgress = progress;
                        final int p = progress;
                        MAIN.post(() -> status.setText("Baixando atualização… " + p + "%"));
                    }
                } else {
                    final int mb = done / (1024 * 1024);
                    MAIN.post(() -> status.setText("Baixando atualização… " + mb + " MB"));
                }
            }
        } finally {
            connection.disconnect();
        }

        if (out.length() < 100_000) {
            throw new IllegalStateException("Downloaded APK is unexpectedly small");
        }
        MAIN.post(() -> status.setText("Download concluído. Abrindo o instalador do Android…"));
        return out;
    }

    private static void openInstaller(Activity activity, TextView status,
                                      File file, Callback callback) {
        if (!file.isFile() || file.length() < 100_000) {
            status.setText("O arquivo da atualização está inválido. Tente novamente.");
            finish(callback);
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            PackageManager pm = activity.getPackageManager();
            if (!pm.canRequestPackageInstalls()) {
                status.setText("Permita instalações do Auren e volte para concluir a atualização.");
                Intent settings = new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName())
                );
                activity.startActivity(settings);
                return;
            }
        }

        Uri uri = FileProvider.getUriForFile(
                activity,
                "com.auren.musicplayer.fileprovider",
                file
        );

        // Give Android both the MIME type and a readable ClipData item.
        // This avoids OEM package installers silently opening/closing the app
        // without showing the update confirmation screen.
        Intent installer = new Intent(Intent.ACTION_VIEW);
        installer.setDataAndType(uri, APK_TYPE);
        installer.setClipData(ClipData.newRawUri("AurenUpdate", uri));
        installer.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        installer.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            activity.startActivity(installer);
            status.setText("Instalador do Android aberto. Toque em Atualizar para concluir.");
        } catch (Exception firstError) {
            Intent fallback = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            fallback.setDataAndType(uri, APK_TYPE);
            fallback.setClipData(ClipData.newRawUri("AurenUpdate", uri));
            fallback.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                activity.startActivity(fallback);
                status.setText("Instalador do Android aberto. Toque em Atualizar para concluir.");
            } catch (Exception secondError) {
                status.setText("Não foi possível abrir o instalador do Android. Verifique as permissões de instalação.");
                finish(callback);
            }
        }
    }

    private static String request(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "Auren-Music-Player");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        try (InputStream in = connection.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        } finally {
            connection.disconnect();
        }
    }

    private static String find(String value, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.DOTALL).matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String cleanVersion(String value) {
        String v = value == null ? "0.0.0" : value.trim();
        if (v.startsWith("v")) v = v.substring(1);
        int dash = v.indexOf('-');
        if (dash >= 0) v = v.substring(0, dash);
        return v;
    }

    private static int compare(String a, String b) {
        String[] x = cleanVersion(a).split("\\.");
        String[] y = cleanVersion(b).split("\\.");
        int count = Math.max(x.length, y.length);
        for (int i = 0; i < count; i++) {
            int left = i < x.length ? num(x[i]) : 0;
            int right = i < y.length ? num(y[i]) : 0;
            if (left != right) return Integer.compare(left, right);
        }
        return 0;
    }

    private static int num(String value) {
        Matcher matcher = Pattern.compile("\\d+").matcher(value);
        return matcher.find() ? Integer.parseInt(matcher.group()) : 0;
    }

    private static void finish(Callback callback) {
        if (callback != null) callback.finished();
    }

    private static final class Release {
        final String tag;
        final String apk;

        Release(String tag, String apk) {
            this.tag = tag;
            this.apk = apk;
        }
    }
}
