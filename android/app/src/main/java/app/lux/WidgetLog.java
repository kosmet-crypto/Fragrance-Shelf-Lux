package app.lux;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/** Home screen widget; see Widgets. */
public class WidgetLog extends AppWidgetProvider {
    @Override
    public void onUpdate(Context ctx, AppWidgetManager manager, int[] ids) {
        Widgets.updateAll(ctx);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Widgets.ACTION_LOG.equals(intent.getAction())) {
            String name = intent.getStringExtra("name");
            Widgets.logPick(ctx, intent.getStringExtra("pid"), name, intent.getIntExtra("n", 3));
            Toast.makeText(ctx, "Logged " + name + ". Lux adds it when you open the app.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Widgets.ACTION_NEXT.equals(intent.getAction())) {
            Widgets.next(ctx);
            return;
        }
        super.onReceive(ctx, intent);
    }
}
