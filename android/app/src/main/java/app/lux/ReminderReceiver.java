package app.lux;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Morning pick and evening wear reminder. The page sends its settings and the picks for the
 * coming days (see syncReminders in index.html); alarms are rescheduled after each one fires,
 * after a reboot and after an app update.
 */
public class ReminderReceiver extends BroadcastReceiver {

    static final String ACTION = "app.lux.REMIND";
    private static final String CHANNEL = "daily";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ACTION.equals(intent.getAction())) show(ctx, intent.getStringExtra("kind"));
        schedule(ctx);
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("reminders", Context.MODE_PRIVATE);
    }

    static JSONObject config(Context ctx) {
        try {
            return new JSONObject(prefs(ctx).getString("cfg", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static void schedule(Context ctx) {
        JSONObject c = config(ctx);
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am == null) return;
        set(ctx, am, "am", c.optBoolean("am"), c.optString("amT", "08:30"), 1);
        set(ctx, am, "pm", c.optBoolean("pm"), c.optString("pmT", "20:30"), 2);
    }

    private static void set(Context ctx, AlarmManager am, String kind, boolean on, String time, int code) {
        Intent i = new Intent(ctx, ReminderReceiver.class).setAction(ACTION).putExtra("kind", kind);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, code, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        if (!on) return;
        int h = 8, m = 30;
        try {
            String[] p = time.split(":");
            h = Integer.parseInt(p[0]);
            m = Integer.parseInt(p[1]);
        } catch (Exception ignored) {
        }
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, h);
        cal.set(Calendar.MINUTE, m);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_YEAR, 1);
        // Inexact is fine for a daily nudge and needs no special alarm permission.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
    }

    private static void show(Context ctx, String kind) {
        JSONObject c = config(ctx);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        SharedPreferences sp = prefs(ctx);
        if (today.equals(sp.getString("shown_" + kind, ""))) return;
        boolean evening = "pm".equals(kind);

        String title, text;
        if (evening) {
            if (!c.optBoolean("pm")) return;
            JSONArray worn = c.optJSONArray("worn");
            if (worn != null) for (int i = 0; i < worn.length(); i++) if (today.equals(worn.optString(i))) return;
            title = "What did you wear today?";
            text = "Tap to log it in your journal.";
        } else {
            if (!c.optBoolean("am")) return;
            JSONObject picks = c.optJSONObject("picks");
            JSONObject p = picks == null ? null : picks.optJSONObject(today);
            if (p != null) {
                title = "Today's pick: " + p.optString("t");
                text = p.optString("b");
            } else {
                title = "Your daily pick is ready";
                text = "Open Lux to see what to wear today.";
            }
        }

        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null || !nm.areNotificationsEnabled()) return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Daily pick and reminders",
                NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("from", kind);
        PendingIntent pi = PendingIntent.getActivity(ctx, evening ? 4 : 3, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(0xFFCAA96B)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify(evening ? 2 : 1, n);
        sp.edit().putString("shown_" + kind, today).apply();
    }
}
