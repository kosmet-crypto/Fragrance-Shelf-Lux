package app.lux;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Silent updates of the web app (index.html and lux-test.js) without a new APK.
 *
 * The newest commit on main is downloaded into files/ota-next, then swapped into files/ota on the
 * next launch (or right away after a manual check). Pages are still served from the same
 * https://appassets.androidplatform.net origin, so localStorage and IndexedDB stay in place.
 * A page declares the native bridge it needs with <meta name="lux-native" content="N">; content
 * that needs a newer app than this one is skipped until the APK is updated.
 */
final class Ota {

    /** Bump when the bridge gains something index.html depends on, and set the meta tag to match. */
    static final int NATIVE_API = 3;
    private static final String[] FILES = {"index.html", "lux-test.js"};
    private static final Pattern NATIVE_META = Pattern.compile("<meta name=\"lux-native\" content=\"(\\d+)\"");

    private Ota() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("ota", Context.MODE_PRIVATE);
    }

    private static File live(Context ctx) {
        return new File(ctx.getFilesDir(), "ota");
    }

    private static File next(Context ctx) {
        return new File(ctx.getFilesDir(), "ota-next");
    }

    /** Commit the page currently comes from: the APK's own build or a downloaded one. */
    static String currentSha(Context ctx) {
        return prefs(ctx).getString("sha", BuildConfig.WEB_SHA);
    }

    static boolean isDownloaded(Context ctx) {
        return new File(live(ctx), "www/index.html").isFile();
    }

    /**
     * Call before loading the page. A new APK brings its own copy of the web app, so downloaded
     * content from an older install is dropped; a staged download is switched on.
     */
    static void prepare(Context ctx, long versionCode) {
        SharedPreferences p = prefs(ctx);
        if (p.getLong("versionCode", -1) != versionCode) {
            deleteTree(live(ctx));
            deleteTree(next(ctx));
            p.edit().clear().putLong("versionCode", versionCode).putString("sha", BuildConfig.WEB_SHA).apply();
            return;
        }
        apply(ctx);
    }

    /** Switches to the staged download, if there is one. Returns true when it did. */
    static boolean apply(Context ctx) {
        SharedPreferences p = prefs(ctx);
        String staged = p.getString("staged", null);
        File n = next(ctx);
        if (staged == null || !new File(n, "ready").isFile()) return false;
        deleteTree(live(ctx));
        if (!n.renameTo(live(ctx))) return false;
        p.edit().putString("sha", staged).remove("staged").apply();
        return true;
    }

    /**
     * Looks for a newer commit on main and stages it. Returns true when new content is staged
     * (now or earlier) and waiting for apply(). Runs network I/O: call off the main thread.
     */
    static boolean check(Context ctx) throws IOException {
        SharedPreferences p = prefs(ctx);
        String sha = new String(get("https://api.github.com/repos/" + BuildConfig.UPDATE_REPO + "/commits/main",
                "application/vnd.github.sha"), StandardCharsets.UTF_8).trim();
        if (!sha.matches("[0-9a-f]{40}")) throw new IOException("unexpected commit id");
        if (sha.equals(currentSha(ctx))) return false;
        if (sha.equals(p.getString("staged", null)) && new File(next(ctx), "ready").isFile()) return true;

        File n = next(ctx);
        deleteTree(n);
        File www = new File(n, "www");
        if (!www.mkdirs()) throw new IOException("cannot create " + www);
        for (String f : FILES) {
            byte[] body = get("https://raw.githubusercontent.com/" + BuildConfig.UPDATE_REPO + "/" + sha + "/" + f, null);
            if (f.equals("index.html") && !compatible(new String(body, StandardCharsets.UTF_8))) {
                deleteTree(n);
                return false; // needs a newer app; the APK update brings it
            }
            try (FileOutputStream out = new FileOutputStream(new File(www, f))) {
                out.write(body);
            }
        }
        if (!new File(n, "ready").createNewFile()) throw new IOException("cannot mark download");
        p.edit().putString("staged", sha).apply();
        return true;
    }

    private static boolean compatible(String html) {
        if (!html.contains("</html>")) return false; // truncated download
        Matcher m = NATIVE_META.matcher(html);
        return m.find() && Integer.parseInt(m.group(1)) <= NATIVE_API;
    }

    /** Serves downloaded files for /assets/<path>; null means "use the copy inside the APK". */
    static WebResourceResponse serve(Context ctx, String path) {
        File root = live(ctx), f = new File(root, path);
        try {
            if (!f.isFile() || !f.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)) return null;
            String mime = path.endsWith(".js") ? "text/javascript" : path.endsWith(".html") ? "text/html" : null;
            if (mime == null) return null;
            return new WebResourceResponse(mime, "utf-8", new FileInputStream(f));
        } catch (IOException e) {
            return null;
        }
    }

    static byte[] get(String url, String accept) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        if (accept != null) c.setRequestProperty("Accept", accept);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode() + " for " + url);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[16384];
                for (int r; (r = in.read(b)) > 0; ) buf.write(b, 0, r);
                return buf.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
