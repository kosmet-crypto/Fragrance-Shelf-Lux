package app.lux;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * Home screen widgets: quick log 2 x 2 and 1 x 2 upright (today's pick as a picture, Wear logs it in one tap, + opens
 * the wear log), today's pick as a picture, and a small stats card. They read what the page sends with the reminders (syncReminders and
 * widgetData in index.html). A wear logged from the widget waits in "pending" until the page takes
 * it (takePendingWears) and logs it like any other wear.
 */
final class Widgets {

    static final String ACTION_LOG = "app.lux.WIDGET_LOG";
    static final String ACTION_NEXT = "app.lux.WIDGET_NEXT";

    private Widgets() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("widget", Context.MODE_PRIVATE);
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** Redraws every widget of every kind that is on the home screen. */
    static void updateAll(Context ctx) {
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        if (m == null) return;
        int[] a = m.getAppWidgetIds(new ComponentName(ctx, WidgetLog.class));
        if (a.length > 0) m.updateAppWidget(a, logViews(ctx, R.layout.widget_log));
        int[] b = m.getAppWidgetIds(new ComponentName(ctx, WidgetPick.class));
        if (b.length > 0) m.updateAppWidget(b, pickViews(ctx));
        int[] c = m.getAppWidgetIds(new ComponentName(ctx, WidgetStats.class));
        if (c.length > 0) m.updateAppWidget(c, statsViews(ctx));
        int[] d = m.getAppWidgetIds(new ComponentName(ctx, WidgetTall.class));
        if (d.length > 0) m.updateAppWidget(d, logViews(ctx, R.layout.widget_tall));
    }

    /** The data the page sent, but only when it is from today. */
    private static JSONObject fresh(JSONObject cfg) {
        JSONObject w = cfg.optJSONObject("widget");
        return w != null && today().equals(w.optString("day")) ? w : null;
    }

    /** Today's suggestions (the pick first) that the arrow steps through; empty when none from today. */
    private static JSONArray choices(JSONObject cfg) {
        JSONObject w = fresh(cfg);
        JSONArray a = w == null ? null : w.optJSONArray("choices");
        return a == null ? new JSONArray() : a;
    }

    /** Which suggestion the arrow is on; back to the first one every new day. */
    private static int index(Context ctx, int n) {
        SharedPreferences sp = prefs(ctx);
        if (n == 0 || !today().equals(sp.getString("idxDay", ""))) return 0;
        return Math.max(0, sp.getInt("idx", 0)) % n;
    }

    /** One step of the arrow. */
    static void next(Context ctx) {
        int n = choices(ReminderReceiver.config(ctx)).length();
        prefs(ctx).edit().putString("idxDay", today()).putInt("idx", n == 0 ? 0 : (index(ctx, n) + 1) % n).apply();
        updateAll(ctx);
    }

    /** The suggestion on show: today's choices, else the pick the page prepared for today. */
    private static JSONObject pick(Context ctx, JSONObject cfg) {
        JSONArray c = choices(cfg);
        if (c.length() > 0) return c.optJSONObject(index(ctx, c.length()));
        JSONObject w = fresh(cfg);
        if (w != null && w.optJSONObject("pick") != null) return w.optJSONObject("pick");
        JSONObject picks = cfg.optJSONObject("picks");
        JSONObject p = picks == null ? null : picks.optJSONObject(today());
        if (p == null) return null;
        try {
            return new JSONObject().put("id", p.optString("id")).put("t", p.optString("t"))
                    .put("b", p.optString("br")).put("why", p.optString("why")).put("n", p.optInt("n", 3));
        } catch (Exception e) {
            return null;
        }
    }

    /** Name of what was logged today, from the page or from the widget itself; null when nothing. */
    private static String loggedToday(Context ctx, JSONObject cfg) {
        JSONArray pend = pending(ctx);
        String day = today();
        for (int i = pend.length() - 1; i >= 0; i--) {
            JSONObject o = pend.optJSONObject(i);
            if (o != null && day.equals(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(o.optLong("t")))))
                return o.optString("name");
        }
        JSONObject w = fresh(cfg);
        JSONArray t = w == null ? null : w.optJSONArray("today");
        return t != null && t.length() > 0 ? t.optString(t.length() - 1) : null;
    }

    private static PendingIntent open(Context ctx, int code, String from) {
        Intent i = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (from != null) i.putExtra("from", from);
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** The pick as a picture when the page sent one for it, else the Lux mark. */
    private static void image(Context ctx, RemoteViews v, JSONObject p) {
        String id = p == null ? "" : p.optString("id");
        File f = imageFile(ctx, id);
        Bitmap bm = !id.isEmpty() && f.isFile() ? BitmapFactory.decodeFile(f.getPath()) : null;
        if (bm != null) v.setImageViewBitmap(R.id.w_img, bm);
        else v.setImageViewResource(R.id.w_img, R.mipmap.ic_launcher_background);
        v.setContentDescription(R.id.w_img, p == null ? "Lux" : "Today's pick: " + p.optString("t"));
        v.setInt(R.id.w_img, "setImageAlpha", 225);
    }

    /** Quick log, square (widget_log) or upright (widget_tall); both have the same views. */
    private static RemoteViews logViews(Context ctx, int layout) {
        JSONObject cfg = ReminderReceiver.config(ctx);
        RemoteViews v = new RemoteViews(ctx.getPackageName(), layout);
        JSONObject p = pick(ctx, cfg);
        String done = loggedToday(ctx, cfg);
        image(ctx, v, p);
        boolean pickDone = p != null && p.optString("t").equals(done);
        v.setViewVisibility(R.id.w_done, done != null ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.w_wear, pickDone ? "Worn \u2713" : "Wear");
        if (p == null || pickDone) {
            v.setOnClickPendingIntent(R.id.w_wear, open(ctx, p == null ? 31 : 30, p == null ? "pm" : null));
        } else {
            int n = Math.max(1, p.optInt("n", 3));
            Intent log = new Intent(ctx, WidgetLog.class).setAction(ACTION_LOG)
                    .putExtra("pid", p.optString("id")).putExtra("name", p.optString("t")).putExtra("n", n);
            v.setOnClickPendingIntent(R.id.w_wear, PendingIntent.getBroadcast(ctx, 34, log,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        v.setOnClickPendingIntent(R.id.w_other, open(ctx, 31, "pm"));
        v.setOnClickPendingIntent(R.id.w_root, open(ctx, 32, "am"));
        boolean more = choices(cfg).length() > 1;
        v.setViewVisibility(R.id.w_next, more ? View.VISIBLE : View.GONE);
        if (more) v.setOnClickPendingIntent(R.id.w_next, PendingIntent.getBroadcast(ctx, 35,
                new Intent(ctx, WidgetLog.class).setAction(ACTION_NEXT), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return v;
    }

    private static RemoteViews pickViews(Context ctx) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_pick);
        image(ctx, v, pick(ctx, ReminderReceiver.config(ctx)));
        v.setOnClickPendingIntent(R.id.w_root, open(ctx, 32, "am"));
        return v;
    }

    private static RemoteViews statsViews(Context ctx) {
        JSONObject cfg = ReminderReceiver.config(ctx);
        JSONObject w = cfg.optJSONObject("widget");
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_stats);
        // wears waiting from the widget count too
        int month = (w == null ? 0 : w.optInt("month")) + pending(ctx).length();
        int streak = fresh(cfg) == null ? 0 : w.optInt("streak");
        v.setTextViewText(R.id.w_streak, String.valueOf(streak));
        v.setTextViewText(R.id.w_month, String.valueOf(month));
        v.setOnClickPendingIntent(R.id.w_root, open(ctx, 33, null));
        return v;
    }

    private static File imageFile(Context ctx, String id) {
        return new File(ctx.getFilesDir(), "w_" + id.replaceAll("[^A-Za-z0-9]", "") + ".png");
    }

    /** Picture of one of today's suggestions from the page (PNG, base64); pictures of others are removed. */
    static void setImage(Context ctx, String id, String base64) {
        try (FileOutputStream out = new FileOutputStream(imageFile(ctx, id))) {
            out.write(Base64.decode(base64, Base64.DEFAULT));
        } catch (Exception ignored) {
        }
        java.util.Set<String> keep = new java.util.HashSet<>();
        keep.add(imageFile(ctx, id).getName());
        JSONArray c = choices(ReminderReceiver.config(ctx));
        for (int i = 0; i < c.length(); i++) keep.add(imageFile(ctx, c.optJSONObject(i).optString("id")).getName());
        File[] old = ctx.getFilesDir().listFiles((d, n) -> n.startsWith("w_") && n.endsWith(".png") && !keep.contains(n));
        if (old != null) for (File f : old) f.delete();
        updateAll(ctx);
    }

    static JSONArray pending(Context ctx) {
        try {
            return new JSONArray(prefs(ctx).getString("pending", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /** One tap on "Wear it": remembered here until the app is opened. */
    static void logPick(Context ctx, String pid, String name, int n) {
        if (pid == null || pid.isEmpty()) return;
        JSONArray a = pending(ctx);
        try {
            a.put(new JSONObject().put("id", UUID.randomUUID().toString()).put("pid", pid).put("name", name)
                    .put("n", n).put("t", System.currentTimeMillis()));
        } catch (Exception ignored) {
        }
        prefs(ctx).edit().putString("pending", a.toString()).apply();
        updateAll(ctx);
    }

    /** Hands the waiting wears to the page and forgets them. */
    static String takePending(Context ctx) {
        String s = prefs(ctx).getString("pending", "[]");
        prefs(ctx).edit().remove("pending").apply();
        return s;
    }
}
