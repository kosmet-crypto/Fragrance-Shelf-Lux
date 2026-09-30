package app.lux;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * Home screen widgets: quick log (today's pick in one tap, or open the wear log), today's pick, and
 * a small stats card. They read what the page sends with the reminders (syncReminders and
 * widgetData in index.html). A wear logged from the widget waits in "pending" until the page takes
 * it (takePendingWears) and logs it like any other wear.
 */
final class Widgets {

    static final String ACTION_LOG = "app.lux.WIDGET_LOG";

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
        if (a.length > 0) m.updateAppWidget(a, logViews(ctx));
        int[] b = m.getAppWidgetIds(new ComponentName(ctx, WidgetPick.class));
        if (b.length > 0) m.updateAppWidget(b, pickViews(ctx));
        int[] c = m.getAppWidgetIds(new ComponentName(ctx, WidgetStats.class));
        if (c.length > 0) m.updateAppWidget(c, statsViews(ctx));
    }

    /** The data the page sent, but only when it is from today. */
    private static JSONObject fresh(JSONObject cfg) {
        JSONObject w = cfg.optJSONObject("widget");
        return w != null && today().equals(w.optString("day")) ? w : null;
    }

    /** Today's pick: from today's data, else from the picks the page prepared for the coming days. */
    private static JSONObject pick(JSONObject cfg) {
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

    private static RemoteViews logViews(Context ctx) {
        JSONObject cfg = ReminderReceiver.config(ctx);
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_log);
        JSONObject p = pick(cfg);
        String done = loggedToday(ctx, cfg);
        v.setTextViewText(R.id.w_head, done != null ? "LOGGED TODAY" : "TODAY'S PICK");
        if (p == null) {
            v.setTextViewText(R.id.w_name, done != null ? done : "Open Lux");
            v.setTextViewText(R.id.w_sub, "Your pick appears after Lux opens once today.");
            v.setTextViewText(R.id.w_wear, "Log a wear");
            v.setOnClickPendingIntent(R.id.w_wear, open(ctx, 31, "pm"));
        } else {
            int n = Math.max(1, p.optInt("n", 3));
            boolean pickDone = p.optString("t").equals(done);
            v.setTextViewText(R.id.w_name, done != null ? "✓ " + done : p.optString("t"));
            v.setTextViewText(R.id.w_sub, done != null ? "Tap Other to add one more." : join(p.optString("b"), p.optString("why")));
            v.setTextViewText(R.id.w_wear, pickDone ? "Logged ✓" : (done != null ? "Also " + p.optString("t") : "Wear it · " + n + (n == 1 ? " spray" : " sprays")));
            if (pickDone) {
                v.setOnClickPendingIntent(R.id.w_wear, open(ctx, 30, null));
            } else {
                Intent log = new Intent(ctx, WidgetLog.class).setAction(ACTION_LOG)
                        .putExtra("pid", p.optString("id")).putExtra("name", p.optString("t")).putExtra("n", n);
                v.setOnClickPendingIntent(R.id.w_wear, PendingIntent.getBroadcast(ctx, 34, log,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            }
        }
        v.setOnClickPendingIntent(R.id.w_other, open(ctx, 31, "pm"));
        v.setOnClickPendingIntent(R.id.w_root, open(ctx, 30, null));
        return v;
    }

    private static RemoteViews pickViews(Context ctx) {
        JSONObject cfg = ReminderReceiver.config(ctx);
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_pick);
        JSONObject p = pick(cfg);
        v.setTextViewText(R.id.w_name, p == null ? "Open Lux" : p.optString("t"));
        v.setTextViewText(R.id.w_sub, p == null ? "Your pick appears after Lux opens once today." : p.optString("b"));
        v.setTextViewText(R.id.w_why, p == null ? "" : p.optString("why"));
        v.setOnClickPendingIntent(R.id.w_root, open(ctx, 32, "am"));
        return v;
    }

    private static RemoteViews statsViews(Context ctx) {
        JSONObject cfg = ReminderReceiver.config(ctx);
        JSONObject w = cfg.optJSONObject("widget");
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_stats);
        int month = w == null ? 0 : w.optInt("month");
        int streak = w == null || fresh(cfg) == null ? 0 : w.optInt("streak");
        // wears waiting from the widget count too
        JSONArray pend = pending(ctx);
        month += pend.length();
        v.setTextViewText(R.id.w_streak, String.valueOf(streak));
        v.setTextViewText(R.id.w_month, String.valueOf(month));
        JSONObject low = w == null ? null : w.optJSONObject("low");
        v.setTextViewText(R.id.w_low, low == null ? "No bottle is running low." : "Runs out first: " + low.optString("t") + " · " + low.optString("left"));
        v.setOnClickPendingIntent(R.id.w_root, open(ctx, 33, null));
        return v;
    }

    private static String join(String a, String b) {
        return b == null || b.isEmpty() ? a : a + " · " + b;
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
