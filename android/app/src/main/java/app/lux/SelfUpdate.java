package app.lux;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Installs a newer APK from within the app through PackageInstaller. On Android 12+ an app that
 * updates itself may skip the install screen once the user has let Lux install apps; otherwise
 * (or when the phone insists) Android shows its usual confirmation.
 */
public class SelfUpdate extends BroadcastReceiver {

    private static final String APK = "/releases/latest/download/lux.apk";

    /** Downloads the latest release and hands it to the installer. Call off the main thread. */
    static void downloadAndInstall(Context ctx) throws IOException {
        PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(ctx.getPackageName());
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        int id = pi.createSession(params);
        try (PackageInstaller.Session session = pi.openSession(id)) {
            HttpURLConnection c = (HttpURLConnection) new URL("https://github.com/" + BuildConfig.UPDATE_REPO + APK).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            try {
                if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
                long size = c.getContentLengthLong();
                try (InputStream in = c.getInputStream(); OutputStream out = session.openWrite("lux.apk", 0, size)) {
                    byte[] b = new byte[65536];
                    for (int r; (r = in.read(b)) > 0; ) out.write(b, 0, r);
                    session.fsync(out);
                }
            } finally {
                c.disconnect();
            }
            ctx.getSharedPreferences("update", Context.MODE_PRIVATE).edit().putBoolean("selfUpdating", true).apply();
            Intent result = new Intent(ctx, SelfUpdate.class);
            // Mutable: the installer adds the status extras to this intent.
            PendingIntent sender = PendingIntent.getBroadcast(ctx, 7, result,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            session.commit(sender.getIntentSender());
        } catch (IOException | RuntimeException e) {
            pi.abandonSession(id);
            throw e;
        }
    }

    /** Install result from PackageInstaller. On success this process is replaced and never sees it. */
    @Override
    public void onReceive(Context ctx, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(confirm);
            }
        } else if (status != PackageInstaller.STATUS_SUCCESS) {
            ctx.getSharedPreferences("update", Context.MODE_PRIVATE).edit().remove("selfUpdating").apply();
            if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                Toast.makeText(ctx, "Update failed. You can download it from GitHub instead.", Toast.LENGTH_LONG).show();
            }
        }
    }

    /** After a self-update the app is closed; a notification offers to open the new version. */
    static void notifyUpdated(Context ctx) {
        if (!ctx.getSharedPreferences("update", Context.MODE_PRIVATE).getBoolean("selfUpdating", false)) return;
        ctx.getSharedPreferences("update", Context.MODE_PRIVATE).edit().remove("selfUpdating").apply();
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null || !nm.areNotificationsEnabled()) return;
        nm.createNotificationChannel(new NotificationChannel("updates", "App updates", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(ctx, 5, new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        nm.notify(3, new Notification.Builder(ctx, "updates")
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(0xFFCAA96B)
                .setContentTitle("Lux is updated")
                .setContentText("Tap to open the new version.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build());
    }
}
