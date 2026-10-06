package com.poyal.perilog.widgettest;

import android.app.Activity;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import android.util.SizeF;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Disposable emulator only. Runs separately from the application under test. */
public class HostActivity extends Activity {
    private static class TrackedWidgetView extends AppWidgetHostView {
        long renderedAt;
        TrackedWidgetView(Context context) { super(context); }
        @Override public void updateAppWidget(RemoteViews remoteViews) {
            super.updateAppWidget(remoteViews);
            if (remoteViews != null) renderedAt = System.currentTimeMillis();
        }
    }
    private AppWidgetHost host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AppWidgetHostView> views = new ArrayList<>();
    private String mode, target;
    private final Runnable report = new Runnable() {
        @Override public void run() { writeReport(); handler.postDelayed(this, 500); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mount(getIntent());
    }
    @Override public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if ("refresh".equals(intent.getStringExtra("command"))) {
            for (AppWidgetHostView view : views) clickRefresh(view);
        } else mount(intent);
    }
    private void mount(Intent intent) {
        handler.removeCallbacks(report);
        if (host != null) host.stopListening();
        host = new AppWidgetHost(this, 9027) {
            @Override protected AppWidgetHostView onCreateView(Context context, int id, AppWidgetProviderInfo info) {
                return new TrackedWidgetView(context);
            }
        };
        target = intent.getStringExtra("target");
        if (target == null) target = "com.poyal.perilog";
        mode = intent.getStringExtra("mode");
        if (mode == null) mode = "both";
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        SharedPreferences prefs = getSharedPreferences("host", MODE_PRIVATE);
        if (!mode.equals(prefs.getString("mode", "")) || !target.equals(prefs.getString("target", ""))) {
            host.deleteHost(); prefs.edit().clear().putString("mode", mode).putString("target", target).apply();
        }
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        setContentView(container); views.clear();
        String[] receivers = "appointment".equals(mode)
            ? new String[]{"AppointmentWidgetReceiver"}
            : "all".equals(mode)
            ? new String[]{"DailyRecordWidgetReceiver", "CompactRecordWidgetReceiver", "AppointmentWidgetReceiver", "AppointmentWidgetReceiver"}
            : new String[]{"DailyRecordWidgetReceiver", "AppointmentWidgetReceiver", "AppointmentWidgetReceiver"};
        for (int index = 0; index < receivers.length; index++) {
            int width = "CompactRecordWidgetReceiver".equals(receivers[index]) ? 158 : 320;
            ComponentName component = new ComponentName(target, "com.poyal.perilog.widget." + receivers[index]);
            int id = prefs.getInt("id" + index, -1);
            if (manager.getAppWidgetInfo(id) == null) {
                id = host.allocateAppWidgetId();
                Bundle options = new Bundle();
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width);
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width);
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 172);
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 172);
                options.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    new ArrayList<>(Collections.singletonList(new SizeF(width, 172))));
                if (!manager.bindAppWidgetIdIfAllowed(id, component, options)) throw new IllegalStateException("Grant test host widget binding first");
                prefs.edit().putInt("id" + index, id).apply();
            }
            AppWidgetHostView view = host.createView(this, id, manager.getAppWidgetInfo(id));
            view.setPadding(0, 0, 0, 0);
            float density = getResources().getDisplayMetrics().density;
            container.addView(view, new LinearLayout.LayoutParams((int)(width*density), (int)(172*density)));
            views.add(view);
        }
        host.startListening(); handler.post(report);
    }
    private boolean clickRefresh(View view) {
        if ("위젯 새로고침".contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) {
            // Glance places the PendingIntent on a wrapper around the labelled image.
            View target = view;
            while (!(target instanceof AppWidgetHostView)) {
                if (target.isClickable()) return target.performClick();
                if (!(target.getParent() instanceof View)) break;
                target = (View) target.getParent();
            }
            return false;
        }
        if (view instanceof ViewGroup) for (int i=0; i<((ViewGroup)view).getChildCount(); i++)
            if (clickRefresh(((ViewGroup)view).getChildAt(i))) return true;
        return false;
    }
    private void texts(View view, JSONArray result) {
        if (view instanceof TextView) result.put(((TextView)view).getText().toString());
        if (view instanceof ViewGroup) for (int i=0; i<((ViewGroup)view).getChildCount(); i++) texts(((ViewGroup)view).getChildAt(i), result);
    }
    private void writeReport() {
        try {
            JSONArray widgets = new JSONArray();
            for (AppWidgetHostView view : views) {
                JSONArray content = new JSONArray(); texts(view, content);
                widgets.put(new JSONObject().put("id", view.getAppWidgetId())
                    .put("renderedAt", ((TrackedWidgetView)view).renderedAt)
                    .put("receiver", view.getAppWidgetInfo().provider.getClassName()).put("texts", content));
            }
            JSONObject result = new JSONObject().put("at", System.currentTimeMillis()).put("widgets", widgets);
            try (java.io.FileOutputStream stream = openFileOutput("report.json", MODE_PRIVATE)) {
                stream.write(result.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) { /* A concurrent reader retries a partial report. */ }
    }
    @Override public void onDestroy() {
        handler.removeCallbacks(report); if(host != null) host.stopListening(); super.onDestroy();
    }
}
