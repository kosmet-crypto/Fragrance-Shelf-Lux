package app.lux;

import android.app.Activity;
import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Hosts the Lux web app (bundled in assets/www) in a full-screen WebView.
 * Pages are served from https://appassets.androidplatform.net so localStorage
 * and IndexedDB behave like on a normal https site.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/assets/www/index.html";
    private static final int REQ_PICK_FILE = 1;
    private static final int REQ_SAVE_FILE = 2;
    private static final int REQ_LOCATION = 3;
    private static final int REQ_NOTIFY = 4;

    private WebView webView;
    private ValueCallback<Uri[]> pendingPick;
    private byte[] pendingSave;
    private boolean pendingSaveIsBackup;
    private String pendingOpen;
    private String pendingGeoOrigin;
    private GeolocationPermissions.Callback pendingGeo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            Ota.prepare(this, installedVersionCode());
        } catch (Exception ignored) {
        }
        // Downloaded web content (see Ota) wins over the copy inside the APK.
        final WebViewAssetLoader.AssetsPathHandler bundled = new WebViewAssetLoader.AssetsPathHandler(this);
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", path -> {
                    WebResourceResponse r = Ota.serve(this, path);
                    return r != null ? r : bundled.handle(path);
                })
                .build();

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF0D0F0E);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setGeolocationEnabled(true);

        webView.addJavascriptInterface(new Bridge(), "LuxAndroid");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (pendingOpen != null) openInPage(pendingOpen);
                pendingOpen = null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (HOST.equals(url.getHost())) return false;
                // Anything outside the app opens in the browser.
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, url));
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (pendingPick != null) pendingPick.onReceiveValue(null);
                pendingPick = callback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                // Photos use image/*; backups and library imports are JSON, CSV or text, whose
                // MIME types vary by file manager, so allow any file and let the page validate it.
                String[] types = params.getAcceptTypes();
                boolean images = types.length > 0;
                for (String t : types) images &= t.startsWith("image/");
                i.setType(images ? "image/*" : "*/*");
                try {
                    startActivityForResult(i, REQ_PICK_FILE);
                } catch (ActivityNotFoundException e) {
                    pendingPick = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false);
                    return;
                }
                pendingGeoOrigin = origin;
                pendingGeo = callback;
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            }
        });

        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else webView.loadUrl(START_URL);

        if (savedInstanceState == null) checkForUpdate(false);
        pendingOpen = openTarget(getIntent());
        ReminderReceiver.schedule(this);
    }

    /* ---------- notifications ---------- */

    /** A tapped notification says which screen to show: the morning pick or the wear log. */
    private static String openTarget(Intent intent) {
        String from = intent == null ? null : intent.getStringExtra("from");
        return "pm".equals(from) ? "log" : "am".equals(from) ? "pick" : null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String target = openTarget(intent);
        if (target != null) openInPage(target);
    }

    private void openInPage(String target) {
        webView.evaluateJavascript("window.luxOpen&&luxOpen('" + target + "')", null);
    }

    /* ---------- update check ---------- */

    private static final long UPDATE_CHECK_INTERVAL = 60 * 60 * 1000L;

    /**
     * Two kinds of update, checked together:
     * a new APK (GitHub Release tagged v1.0.<versionCode>), installed by the app itself, and
     * new web content on main, downloaded silently by Ota and used from the next launch.
     * The automatic check (on launch and when the app comes back) is throttled and silent;
     * a manual check (the "Check for updates" button) always runs, reports the result and
     * switches to new web content right away.
     */
    private void checkForUpdate(final boolean manual) {
        final SharedPreferences prefs = getSharedPreferences("update", MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!manual && now - prefs.getLong("lastCheck", 0) < UPDATE_CHECK_INTERVAL) return;
        prefs.edit().putLong("lastCheck", now).apply();
        if (manual) toast("Checking for updates…");

        new Thread(() -> {
            try {
                String body = new String(Ota.get("https://api.github.com/repos/" + BuildConfig.UPDATE_REPO
                        + "/releases/latest", "application/vnd.github+json"), StandardCharsets.UTF_8);
                String tag = new JSONObject(body).optString("tag_name", "");
                final long latest = Long.parseLong(tag.substring(tag.lastIndexOf('.') + 1));
                final String name = tag.startsWith("v") ? tag.substring(1) : tag;
                // A newer release whose Android part is the same as ours only differs in web content,
                // which Ota below downloads silently; no need to install an APK for that.
                java.util.regex.Matcher native_ = java.util.regex.Pattern.compile("native: ([0-9a-f]{12})")
                        .matcher(new JSONObject(body).optString("body", ""));
                boolean sameNative = native_.find() && native_.group(1).equals(BuildConfig.NATIVE_HASH);
                if (latest > installedVersionCode() && !sameNative) {
                    runOnUiThread(() -> showUpdateDialog(name));
                    return;
                }
                boolean staged = Ota.check(this);
                if (staged && manual) runOnUiThread(() -> {
                    if (Ota.apply(this)) {
                        webView.reload();
                        toast("Lux is up to date");
                    }
                });
                else if (manual) toast("You have the latest version");
            } catch (Exception e) {
                // No network, rate limit or unexpected response: the automatic check tries again later.
                if (manual) toast("Could not check. Are you online?");
            }
        }).start();
    }

    private void toast(final String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    private long installedVersionCode() throws Exception {
        PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private void showUpdateDialog(String version) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("Update available")
                .setMessage("Lux " + version + " is ready. The app closes for a moment while it updates. Your data stays in place.")
                .setPositiveButton("Update", (d, w) -> startSelfUpdate())
                .setNegativeButton("Later", null)
                .show();
    }

    private boolean waitingForInstallPermission;

    /** Android asks once whether Lux may install apps; after that updates need no more steps. */
    private void startSelfUpdate() {
        if (!getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this)
                    .setTitle("Allow updates")
                    .setMessage("To update itself, Lux needs permission to install apps. Turn on \"Allow from this source\" on the next screen, then come back.")
                    .setPositiveButton("Continue", (d, w) -> {
                        waitingForInstallPermission = true;
                        try {
                            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (ActivityNotFoundException e) {
                            waitingForInstallPermission = false;
                            downloadInBrowser();
                        }
                    })
                    .setNegativeButton("Download instead", (d, w) -> downloadInBrowser())
                    .show();
            return;
        }
        toast("Downloading the update…");
        new Thread(() -> {
            try {
                SelfUpdate.downloadAndInstall(this);
            } catch (Exception e) {
                toast("Update failed. Opening the download instead.");
                runOnUiThread(this::downloadInBrowser);
            }
        }).start();
    }

    private void downloadInBrowser() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/" + BuildConfig.UPDATE_REPO
                    + "/releases/latest/download/lux.apk")));
        } catch (ActivityNotFoundException ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForInstallPermission) {
            waitingForInstallPermission = false;
            if (getPackageManager().canRequestPackageInstalls()) startSelfUpdate();
        } else {
            checkForUpdate(false);
        }
    }

    private void saveAs(String name, String mime, byte[] data, boolean backup) {
        pendingSave = data;
        pendingSaveIsBackup = backup;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE, name);
        try {
            startActivityForResult(i, REQ_SAVE_FILE);
        } catch (ActivityNotFoundException e) {
            pendingSave = null;
            toast("No app available to save files");
        }
    }

    /** Methods index.html can call as window.LuxAndroid.*. */
    private class Bridge {
        /** Reminder settings and upcoming picks as JSON; see syncReminders in index.html. */
        @JavascriptInterface
        public void setReminders(String json) {
            ReminderReceiver.prefs(MainActivity.this).edit().putString("cfg", json).apply();
            ReminderReceiver.schedule(MainActivity.this);
        }

        @JavascriptInterface
        public boolean notificationsAllowed() {
            NotificationManager nm = getSystemService(NotificationManager.class);
            return nm != null && nm.areNotificationsEnabled();
        }

        @JavascriptInterface
        public void requestNotifications() {
            if (Build.VERSION.SDK_INT < 33) return;
            runOnUiThread(() -> {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
                } else if (!notificationsAllowed()) {
                    // Allowed once and later switched off: only the system settings can turn it back on.
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName()));
                    } catch (ActivityNotFoundException ignored) {
                    }
                }
            });
        }

        /** Shares a PNG (base64) through the Android share sheet. */
        @JavascriptInterface
        public void shareImage(final String base64) {
            runOnUiThread(() -> {
                try {
                    File dir = new File(getCacheDir(), "share");
                    if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("no cache dir");
                    File f = new File(dir, "lux.png");
                    try (FileOutputStream out = new FileOutputStream(f)) {
                        out.write(Base64.decode(base64, Base64.DEFAULT));
                    }
                    Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f);
                    Intent send = new Intent(Intent.ACTION_SEND)
                            .setType("image/png")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    send.setClipData(ClipData.newRawUri("", uri));
                    startActivity(Intent.createChooser(send, "Share"));
                } catch (Exception e) {
                    toast("Could not share the image");
                }
            });
        }

        @JavascriptInterface
        public void saveImage(final String name, final String base64) {
            runOnUiThread(() -> saveAs(name, "image/png", Base64.decode(base64, Base64.DEFAULT), false));
        }

        @JavascriptInterface
        public String getVersion() {
            String sha = Ota.currentSha(MainActivity.this);
            return BuildConfig.VERSION_NAME + (Ota.isDownloaded(MainActivity.this) && sha.length() >= 7
                    ? " (content " + sha.substring(0, 7) + ")" : "");
        }

        @JavascriptInterface
        public void checkForUpdate() {
            runOnUiThread(() -> MainActivity.this.checkForUpdate(true));
        }

        /** Saves a backup; WebView cannot download blob: URLs. */
        @JavascriptInterface
        public void saveFile(final String name, final String text) {
            runOnUiThread(() -> saveAs(name, "application/json", text.getBytes(StandardCharsets.UTF_8), true));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Uri uri = (resultCode == RESULT_OK && data != null) ? data.getData() : null;

        if (requestCode == REQ_PICK_FILE && pendingPick != null) {
            pendingPick.onReceiveValue(uri != null ? new Uri[]{uri} : null);
            pendingPick = null;
        } else if (requestCode == REQ_SAVE_FILE) {
            byte[] bytes = pendingSave;
            pendingSave = null;
            if (uri == null || bytes == null) return;
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                out.write(bytes);
                // A backup lets the page record the date and show its own confirmation.
                if (pendingSaveIsBackup) webView.evaluateJavascript("window.luxBackupSaved&&luxBackupSaved()", null);
                else toast("Image saved");
            } catch (Exception e) {
                toast(pendingSaveIsBackup ? "Could not save backup" : "Could not save the image");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == REQ_NOTIFY) {
            webView.evaluateJavascript("window.luxNotifChanged&&luxNotifChanged()", null);
            return;
        }
        if (requestCode != REQ_LOCATION || pendingGeo == null) return;
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        pendingGeo.invoke(pendingGeoOrigin, granted, false);
        pendingGeo = null;
        pendingGeoOrigin = null;
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }
}
