package com.auren.musicplayer;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import io.sigpipe.jbsdiff.Patch;

public class UpdateManager {
    private static final String API =
            "https://api.github.com/repos/nexauren1/"
                    + "music-player-auren/releases/latest";

    public interface Callback {
        void onResult(UpdateInfo info);
        void onError();
    }

    public static class UpdateInfo {
        public final String version;
        public final String tag;
        public final String apkUrl;
        public final String patchUrl;

        UpdateInfo(String version, String tag, String apkUrl, String patchUrl) {
            this.version = version;
            this.tag = tag;
            this.apkUrl = apkUrl;
            this.patchUrl = patchUrl;
        }

        public boolean hasDelta() {
            return patchUrl != null && !patchUrl.isEmpty();
        }
    }

    public static void check(Context context, Callback callback) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(API);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty(
                        "Accept", "application/vnd.github+json");
                connection.setRequestProperty(
                        "User-Agent", "Auren-Music-Player");

                if (connection.getResponseCode() != 200) {
                    throw new Exception("GitHub response "
                            + connection.getResponseCode());
                }

                String json = read(connection.getInputStream());
                JSONObject release = new JSONObject(json);
                String tag = release.optString("tag_name", "");
                String version = extractVersion(tag);
                JSONArray assets = release.optJSONArray("assets");
                String apkUrl = findApk(assets);
                String current = BuildConfig.VERSION_NAME;
                String patchUrl = findPatch(assets, current, version);

                if (version.isEmpty() || apkUrl.isEmpty()) {
                    throw new Exception("No APK release found");
                }

                UpdateInfo info = new UpdateInfo(
                        version, tag, apkUrl, patchUrl);
                ((Activity) context).runOnUiThread(
                        () -> callback.onResult(info));
            } catch (Exception e) {
                ((Activity) context).runOnUiThread(callback::onError);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    public static boolean isNewer(String latest, String current) {
        try {
            String[] a = latest.split("\\.");
            String[] b = current.split("\\.");
            int length = Math.max(a.length, b.length);
            for (int i = 0; i < length; i++) {
                int av = i < a.length ? Integer.parseInt(a[i]) : 0;
                int bv = i < b.length ? Integer.parseInt(b[i]) : 0;
                if (av != bv) return av > bv;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static void downloadAndInstall(
            Activity activity, UpdateInfo info) {
        if (info.hasDelta()) {
            downloadDelta(activity, info);
        } else {
            downloadApk(activity, info);
        }
    }

    private static void downloadDelta(
            Activity activity, UpdateInfo info) {
        Toast.makeText(
                activity,
                "A baixar atualização otimizada...",
                Toast.LENGTH_SHORT).show();

        DownloadManager manager = getManager(activity);
        if (manager == null) return;

        File patch = new File(
                activity.getExternalFilesDir(
                        Environment.DIRECTORY_DOWNLOADS),
                "Auren-Update-"
                        + BuildConfig.VERSION_NAME
                        + "-to-"
                        + info.version
                        + ".patch");

        if (patch.exists() && patch.length() > 0) {
            applyDelta(activity, info, patch);
            return;
        }

        DownloadManager.Request request =
                new DownloadManager.Request(Uri.parse(info.patchUrl));
        request.setTitle("Auren Music Player " + info.version);
        request.setDescription("A baixar apenas as alterações...");
        request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationUri(Uri.fromFile(patch));

        long id = manager.enqueue(request);
        waitForPatch(activity, manager, id, info, patch);
    }

    private static void waitForPatch(
            Activity activity,
            DownloadManager manager,
            long id,
            UpdateInfo info,
            File patch) {
        new Thread(() -> {
            DownloadManager.Query query =
                    new DownloadManager.Query().setFilterById(id);

            while (true) {
                try {
                    Thread.sleep(500);
                    android.database.Cursor result = manager.query(query);
                    if (result == null) continue;

                    try {
                        if (!result.moveToFirst()) continue;
                        int status = result.getInt(
                                result.getColumnIndexOrThrow(
                                        DownloadManager.COLUMN_STATUS));

                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            activity.runOnUiThread(() ->
                                    applyDelta(activity, info, patch));
                            return;
                        }

                        if (status == DownloadManager.STATUS_FAILED) {
                            activity.runOnUiThread(() ->
                                    downloadApk(activity, info));
                            return;
                        }
                    } finally {
                        result.close();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private static void applyDelta(
            Activity activity, UpdateInfo info, File patch) {
        new Thread(() -> {
            File oldApk = new File(
                    activity.getApplicationInfo().sourceDir);
            File output = new File(
                    activity.getExternalFilesDir(
                            Environment.DIRECTORY_DOWNLOADS),
                    "Auren-Music-Player-" + info.version + ".apk");

            try {
                if (output.exists()) output.delete();

                byte[] oldBytes = readBytes(oldApk);
                byte[] patchBytes = readBytes(patch);

                FileOutputStream outputStream =
                        new FileOutputStream(output);
                try {
                    Patch.patch(oldBytes, patchBytes, outputStream);
                } finally {
                    outputStream.close();
                }

                if (!output.exists() || output.length() < 100000) {
                    throw new Exception("Invalid patched APK");
                }

                activity.runOnUiThread(() -> {
                    Toast.makeText(
                            activity,
                            "Atualização pronta. A instalar...",
                            Toast.LENGTH_SHORT).show();
                    install(activity, FileProvider.getUriForFile(
                            activity,
                            activity.getPackageName()
                                    + ".fileprovider",
                            output));
                });
            } catch (Exception e) {
                if (output.exists()) output.delete();
                activity.runOnUiThread(() -> {
                    Toast.makeText(
                            activity,
                            "Delta inválido. A baixar APK completo...",
                            Toast.LENGTH_SHORT).show();
                    downloadApk(activity, info);
                });
            }
        }).start();
    }

    private static byte[] readBytes(File file) throws Exception {
        InputStream input = new FileInputStream(file);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        try {
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
        } finally {
            input.close();
        }
        return output.toByteArray();
    }

    private static void downloadApk(
            Activity activity, UpdateInfo info) {
        Toast.makeText(
                activity,
                "A baixar atualização completa...",
                Toast.LENGTH_SHORT).show();

        DownloadManager manager = getManager(activity);
        if (manager == null) return;

        File destination = new File(
                activity.getExternalFilesDir(
                        Environment.DIRECTORY_DOWNLOADS),
                "Auren-Music-Player-" + info.version + ".apk");

        if (destination.exists() && destination.length() > 0) {
            install(activity, FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".fileprovider",
                    destination));
            return;
        }

        DownloadManager.Request request =
                new DownloadManager.Request(Uri.parse(info.apkUrl));
        request.setTitle("Auren Music Player " + info.version);
        request.setDescription("A baixar atualização completa...");
        request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationUri(Uri.fromFile(destination));

        long id = manager.enqueue(request);
        waitForApk(activity, manager, id, info.version);
    }

    private static void waitForApk(
            Activity activity,
            DownloadManager manager,
            long id,
            String version) {
        new Thread(() -> {
            DownloadManager.Query query =
                    new DownloadManager.Query().setFilterById(id);

            while (true) {
                try {
                    Thread.sleep(500);
                    android.database.Cursor result = manager.query(query);
                    if (result == null) continue;

                    try {
                        if (!result.moveToFirst()) continue;
                        int status = result.getInt(
                                result.getColumnIndexOrThrow(
                                        DownloadManager.COLUMN_STATUS));

                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            File file = new File(
                                    activity.getExternalFilesDir(
                                            Environment.DIRECTORY_DOWNLOADS),
                                    "Auren-Music-Player-" + version + ".apk");
                            activity.runOnUiThread(() -> {
                                if (file.exists() && file.length() > 0) {
                                    install(activity, FileProvider.getUriForFile(
                                            activity,
                                            activity.getPackageName()
                                                    + ".fileprovider",
                                            file));
                                } else {
                                    showError(activity,
                                            "APK da atualização não encontrado.");
                                }
                            });
                            return;
                        }

                        if (status == DownloadManager.STATUS_FAILED) {
                            activity.runOnUiThread(() -> showError(
                                    activity,
                                    "Não foi possível baixar a atualização."));
                            return;
                        }
                    } finally {
                        result.close();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private static DownloadManager getManager(Activity activity) {
        DownloadManager manager = (DownloadManager)
                activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            showError(activity, "Não foi possível iniciar o download.");
        }
        return manager;
    }

    private static void install(Activity activity, Uri contentUri) {
        try {
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setData(contentUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            showError(activity,
                    "O Android bloqueou a instalação automática. "
                            + "Permita instalações desta fonte e tente novamente.");
        }
    }

    private static void showError(Activity activity, String message) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }

    private static String findApk(JSONArray assets) {
        if (assets == null) return "";
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            String name = asset.optString("name", "");
            if (name.toLowerCase().endsWith(".apk")) {
                return asset.optString("browser_download_url", "");
            }
        }
        return "";
    }

    private static String findPatch(
            JSONArray assets, String current, String latest) {
        if (assets == null) return "";
        String expected = "Auren-Music-Player-"
                + current + "-to-" + latest + ".patch";
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            if (expected.equals(asset.optString("name", ""))) {
                return asset.optString(
                        "browser_download_url", "");
            }
        }
        return "";
    }

    private static String extractVersion(String tag) {
        String value = tag == null ? "" : tag.trim();
        if (value.startsWith("v")) value = value.substring(1);
        int build = value.indexOf("-build.");
        if (build >= 0) value = value.substring(0, build);
        return value;
    }

    private static String read(InputStream input) throws Exception {
        StringBuilder result = new StringBuilder();
        byte[] buffer = new byte[4096];
        int length;
        while ((length = input.read(buffer)) != -1) {
            result.append(new String(buffer, 0, length, "UTF-8"));
        }
        input.close();
        return result.toString();
    }
}
