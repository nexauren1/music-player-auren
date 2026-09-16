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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

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
        public final String url;

        UpdateInfo(String version, String tag, String url) {
            this.version = version;
            this.tag = tag;
            this.url = url;
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
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "Auren-Music-Player");

                if (connection.getResponseCode() != 200) {
                    throw new Exception("GitHub response "
                            + connection.getResponseCode());
                }

                String json = read(connection.getInputStream());
                JSONObject release = new JSONObject(json);
                String tag = release.optString("tag_name", "");
                String version = extractVersion(tag);
                String apkUrl = findApk(release.optJSONArray("assets"));

                if (version.isEmpty() || apkUrl.isEmpty()) {
                    throw new Exception("No APK release found");
                }

                UpdateInfo info = new UpdateInfo(version, tag, apkUrl);
                ((Activity) context).runOnUiThread(
                        () -> callback.onResult(info));
            } catch (Exception e) {
                ((Activity) context).runOnUiThread(callback::onError);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
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
            Activity activity,
            UpdateInfo info) {
        Toast.makeText(
                activity,
                "A baixar atualização...",
                Toast.LENGTH_SHORT).show();

        DownloadManager manager = (DownloadManager)
                activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            Toast.makeText(
                    activity,
                    "Não foi possível iniciar o download.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Uri uri = Uri.parse(info.url);
        DownloadManager.Request request =
                new DownloadManager.Request(uri);
        request.setTitle("Auren Music Player " + info.version);
        request.setDescription("Baixando atualização...");
        request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalFilesDir(
                activity,
                Environment.DIRECTORY_DOWNLOADS,
                "Auren-Music-Player-" + info.version + ".apk");

        long id = manager.enqueue(request);
        waitForDownload(activity, manager, id, info.version);
    }

    private static void waitForDownload(
            Activity activity,
            DownloadManager manager,
            long id,
            String version) {
        new Thread(() -> {
            DownloadManager.Query query =
                    new DownloadManager.Query().setFilterById(id);
            boolean finished = false;

            while (!finished) {
                try {
                    Thread.sleep(500);
                    android.database.Cursor cursor =
                            manager.query(query);
                    if (cursor == null) continue;
                    try {
                        if (!cursor.moveToFirst()) continue;
                        int status = cursor.getInt(
                                cursor.getColumnIndexOrThrow(
                                        DownloadManager.COLUMN_STATUS));
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            String path = cursor.getString(
                                    cursor.getColumnIndexOrThrow(
                                            DownloadManager.COLUMN_LOCAL_URI));
                            finished = true;
                            activity.runOnUiThread(() -> {
                                Toast.makeText(
                                        activity,
                                        "Atualização baixada. A instalar...",
                                        Toast.LENGTH_SHORT).show();
                                install(activity, Uri.parse(path));
                            });
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            finished = true;
                            activity.runOnUiThread(() -> Toast.makeText(
                                    activity,
                                    "Não foi possível baixar a atualização.",
                                    Toast.LENGTH_LONG).show());
                        }
                    } finally {
                        cursor.close();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private static void install(Activity activity, Uri localUri) {
        try {
            Uri contentUri = localUri;
            if ("file".equalsIgnoreCase(localUri.getScheme())) {
                File file = new File(localUri.getPath());
                contentUri = FileProvider.getUriForFile(
                        activity,
                        activity.getPackageName() + ".fileprovider",
                        file);
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(
                    contentUri,
                    "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(
                    activity,
                    "Não foi possível iniciar a instalação.",
                    Toast.LENGTH_LONG).show();
        }
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
