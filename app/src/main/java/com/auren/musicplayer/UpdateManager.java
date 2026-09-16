package com.auren.musicplayer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateManager {
    private static final String LATEST = "https://api.github.com/repos/nexauren1/music-player-auren/releases/latest";
    private UpdateManager() {}

    public static void check(Activity activity, TextView status) {
        status.setText("Checking for the latest release…");
        new AsyncTask<Void, Void, Release>() {
            Exception error;
            protected Release doInBackground(Void... ignored) {
                try {
                    String json = request(LATEST);
                    String tag = find(json, "\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
                    String apk = find(json, "\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+\\.apk)\\\"");
                    String body = find(json, "\\\"body\\\"\\s*:\\s*\\\"(.*?)\\\"\\s*,\\s*\\\"draft\\\"");
                    return new Release(tag, apk, body == null ? "" : body);
                } catch (Exception e) { error = e; return null; }
            }
            protected void onPostExecute(Release r) {
                if (error != null || r == null || r.apk == null) { status.setText("Could not check for updates. Try again later."); return; }
                String latest = r.tag.startsWith("v") ? r.tag.substring(1) : r.tag;
                if (compare(latest, BuildConfig.VERSION_NAME) <= 0) { status.setText("You're running the latest version."); return; }
                status.setText("A new version " + r.tag + " is available. Downloading…");
                new DownloadTask(activity, status, r.apk).execute();
            }
        }.execute();
    }

    private static class DownloadTask extends AsyncTask<Void, Integer, File> {
        private final Activity activity; private final TextView status; private final String url;
        DownloadTask(Activity a, TextView s, String u) { activity=a; status=s; url=u; }
        protected File doInBackground(Void... ignored) {
            try {
                HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setConnectTimeout(15000); c.setReadTimeout(30000); c.connect();
                int total=c.getContentLength(); int done=0;
                File out=new File(activity.getExternalCacheDir(), "auren-update.apk");
                try(InputStream in=c.getInputStream(); FileOutputStream fos=new FileOutputStream(out)) {
                    byte[] buffer=new byte[8192]; int n;
                    while((n=in.read(buffer))!=-1){fos.write(buffer,0,n);done+=n;if(total>0)publishProgress(done*100/total);}
                }
                c.disconnect(); return out;
            } catch(Exception e){return null;}
        }
        protected void onProgressUpdate(Integer... p){status.setText("Downloading update… " + p[0] + "%");}
        protected void onPostExecute(File file){
            if(file==null){status.setText("Update download failed. Please try again.");return;}
            Uri uri=FileProvider.getUriForFile(activity,"com.auren.musicplayer.fileprovider",file);
            Intent intent=new Intent(Intent.ACTION_VIEW); intent.setDataAndType(uri,"application/vnd.android.package-archive"); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); activity.startActivity(intent);
        }
    }

    private static String request(String address) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection(); c.setRequestProperty("Accept","application/vnd.github+json"); c.setConnectTimeout(10000); c.setReadTimeout(15000);
        try(InputStream in=c.getInputStream(); java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}
        finally{c.disconnect();}
    }
    private static String find(String s,String regex){Matcher m=Pattern.compile(regex,Pattern.DOTALL).matcher(s);return m.find()?m.group(1):null;}
    private static int compare(String a,String b){String[] x=a.split("\\."),y=b.split("\\.");for(int i=0;i<Math.max(x.length,y.length);i++){int p=i<x.length?num(x[i]):0,q=i<y.length?num(y[i]):0;if(p!=q)return Integer.compare(p,q);}return 0;}
    private static int num(String s){Matcher m=Pattern.compile("\\d+").matcher(s);return m.find()?Integer.parseInt(m.group()):0;}
    private static class Release { final String tag,apk,body; Release(String t,String a,String b){tag=t;apk=a;body=b;} }
}
