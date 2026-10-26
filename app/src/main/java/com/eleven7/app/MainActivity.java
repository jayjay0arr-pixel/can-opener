package com.eleven7.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class MainActivity extends Activity {
    static final int REQ_PERM = 1, REQ_PICK = 2;
    static final String HOME = "file:///android_asset/launcher.html";
    WebView web;
    TextView homeBtn;
    File dir;
    String injectJs = null;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dir = new File(Environment.getExternalStorageDirectory(), "Eleven7");

        FrameLayout root = new FrameLayout(this);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        homeBtn = new TextView(this);
        homeBtn.setText("\u2302");
        homeBtn.setTextSize(22);
        homeBtn.setTextColor(0xFFFFFFFF);
        homeBtn.setBackgroundColor(0x55000000);
        homeBtn.setGravity(Gravity.CENTER);
        homeBtn.setVisibility(View.GONE);
        int s = (int) (40 * getResources().getDisplayMetrics().density);
        root.addView(homeBtn, new FrameLayout.LayoutParams(s, s, Gravity.TOP | Gravity.END));
        homeBtn.setOnClickListener(v -> web.loadUrl(HOME));
        setContentView(root);

        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Bridge(), "Eleven7Native");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap icon) {
                homeBtn.setVisibility(url.startsWith(HOME) ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                if (injectJs != null) v.evaluateJavascript(injectJs, null);
            }
        });

        try { injectJs = readAsset("e7-inject.js"); } catch (Exception ignored) { }
        if (hasAccess()) prepare();
        else if (Build.VERSION.SDK_INT < 30) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERM);
        }
        web.loadUrl(HOME);
    }

    boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    void prepare() {
        dir.mkdirs();
        String[] existing = dir.list();
        if (existing != null && existing.length == 0) {
            try (InputStream in = getAssets().open("starter/hello.html")) {
                copy(in, new File(dir, "hello.html"));
            } catch (Exception ignored) { }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasAccess()) { prepare(); js("window.refreshAll&&refreshAll()"); }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (hasAccess()) { prepare(); js("window.refreshAll&&refreshAll()"); }
    }

    String readAsset(String n) throws IOException {
        try (InputStream in = getAssets().open(n)) {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int k;
            while ((k = in.read(b)) > 0) o.write(b, 0, k);
            return o.toString("UTF-8");
        }
    }

    File saveBytes(String name, byte[] bytes) throws IOException {
        name = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (name.isEmpty()) name = "download";
        File d = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Eleven7");
        d.mkdirs();
        if (!d.isDirectory() || !d.canWrite()) { d = new File(dir, "Saved"); d.mkdirs(); }
        File out = new File(d, name);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; out.exists(); i++) out = new File(d, base + " (" + i + ")" + ext);
        try (OutputStream o = new FileOutputStream(out)) { o.write(bytes); }
        return out;
    }

    void js(String code) { runOnUiThread(() -> web.evaluateJavascript(code, null)); }

    void toast(String m) { runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show()); }

    void copy(InputStream in, File out) throws IOException {
        try (OutputStream o = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        }
    }

    File safe(String p) throws IOException {
        File f = new File(dir, p == null ? "" : p);
        String c = f.getCanonicalPath(), b = dir.getCanonicalPath();
        return (c.equals(b) || c.startsWith(b + File.separator)) ? f : null;
    }

    String size(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return (n / 1024) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", n / 1048576.0);
    }

    class Bridge {
        @JavascriptInterface
        public boolean hasAccess() { return MainActivity.this.hasAccess(); }

        @JavascriptInterface
        public String folder() { return dir.getPath(); }

        @JavascriptInterface
        public void requestAccess() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 30) {
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Allow file access")
                        .setMessage("Eleven 7 reads your pages from the Eleven7 folder in your phone storage. On the next screen, turn on \"Allow access to manage all files\".")
                        .setPositiveButton("Continue", (d, w) -> {
                            try {
                                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
                            } catch (Exception e) {
                                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                            }
                        })
                        .setNegativeButton("Not now", null)
                        .show();
                } else {
                    requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERM);
                }
            });
        }

        @JavascriptInterface
        public String list(String p) {
            try {
                File d = safe(p);
                if (d == null || !d.isDirectory()) return "[]";
                File[] fs = d.listFiles();
                if (fs == null) return "[]";
                Arrays.sort(fs, (a, c) -> a.isDirectory() == c.isDirectory()
                        ? a.getName().compareToIgnoreCase(c.getName())
                        : (a.isDirectory() ? -1 : 1));
                JSONArray arr = new JSONArray();
                for (File f : fs) {
                    if (f.getName().startsWith(".")) continue;
                    JSONObject o = new JSONObject();
                    o.put("n", f.getName());
                    o.put("d", f.isDirectory());
                    if (f.isDirectory()) {
                        String[] k = f.list();
                        o.put("s", (k == null ? 0 : k.length) + " items");
                    } else {
                        o.put("s", size(f.length()));
                    }
                    arr.put(o);
                }
                return arr.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public void open(String rel) {
            runOnUiThread(() -> {
                try {
                    File f = safe(rel);
                    if (f != null && f.isFile()) web.loadUrl(Uri.fromFile(f).toString());
                    else toast("File not found");
                } catch (Exception e) { toast("Could not open file"); }
            });
        }

        @JavascriptInterface
        public void remove(String rel) {
            runOnUiThread(() -> {
                try {
                    File f = safe(rel);
                    if (f == null || !f.isFile()) return;
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Remove file")
                        .setMessage("Delete " + f.getName() + " from the Eleven7 folder?")
                        .setPositiveButton("Delete", (d, w) -> {
                            if (f.delete()) toast("Removed"); else toast("Could not delete");
                            js("window.refreshAll&&refreshAll()");
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                } catch (Exception ignored) { }
            });
        }

        @JavascriptInterface
        public void saveData(String name, String dataUrl) {
            try {
                int c = dataUrl.indexOf(',');
                String meta = dataUrl.substring(0, c), body = dataUrl.substring(c + 1);
                byte[] bytes = meta.contains(";base64")
                        ? Base64.decode(body, Base64.DEFAULT)
                        : Uri.decode(body).getBytes("UTF-8");
                File out = saveBytes(name, bytes);
                toast("Saved: " + out.getPath());
                js("window.refreshAll&&refreshAll()");
            } catch (Exception e) {
                toast("Could not save file");
            }
        }

        @JavascriptInterface
        public void print() {
            runOnUiThread(() -> {
                try {
                    PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                    PrintDocumentAdapter a = web.createPrintDocumentAdapter("Eleven7");
                    pm.print("Eleven7", a, null);
                } catch (Exception e) { toast("Print is not available"); }
            });
        }

        @JavascriptInterface
        public void pick() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(i, REQ_PICK);
            });
        }
    }

    @Override
    protected void onActivityResult(int code, int res, Intent data) {
        super.onActivityResult(code, res, data);
        if (code != REQ_PICK || res != RESULT_OK || data == null) return;
        int added = 0;
        dir.mkdirs();
        ClipData cd = data.getClipData();
        if (cd != null) {
            for (int i = 0; i < cd.getItemCount(); i++) if (addFile(cd.getItemAt(i).getUri())) added++;
        } else if (data.getData() != null) {
            if (addFile(data.getData())) added++;
        }
        toast(added > 0 ? "Added " + added + (added == 1 ? " file" : " files") : "Could not add file");
        js("window.refreshAll&&refreshAll()");
    }

    boolean addFile(Uri u) {
        String name = "page.html";
        try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (ix >= 0 && c.getString(ix) != null) name = c.getString(ix);
            }
        } catch (Exception ignored) { }
        name = name.replace("/", "_");
        try (InputStream in = getContentResolver().openInputStream(u)) {
            copy(in, new File(dir, name));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
