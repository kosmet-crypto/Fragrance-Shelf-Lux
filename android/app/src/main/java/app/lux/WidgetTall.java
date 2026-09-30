package app.lux;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/** Home screen widget, 1 x 2 upright quick log; taps are handled by WidgetLog. See Widgets. */
public class WidgetTall extends AppWidgetProvider {
    @Override
    public void onUpdate(Context ctx, AppWidgetManager manager, int[] ids) {
        Widgets.updateAll(ctx);
    }
}
