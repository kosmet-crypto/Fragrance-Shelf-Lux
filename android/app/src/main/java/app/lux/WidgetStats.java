package app.lux;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/** Home screen widget; see Widgets. */
public class WidgetStats extends AppWidgetProvider {
    @Override
    public void onUpdate(Context ctx, AppWidgetManager manager, int[] ids) {
        Widgets.updateAll(ctx);
    }
}
