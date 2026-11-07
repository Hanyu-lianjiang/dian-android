package fr.kdns.weiyun.dormmonitor;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class DormWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "fr.kdns.weiyun.dormmonitor.ACTION_REFRESH";
    public static final String PREFS_NAME = "dorm_widget_preferences";
    public static final String PREF_SELECTOR_PREFIX = "selector_";

    private static final String API_URL = "https://bill.weiyun.kdns.fr/api/dorms?minutes=5&points=12";
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000L;
    private static final long ERROR_RETRY_MS = 60 * 1000L;
    private static final int REFRESH_REQUEST_CODE = 27105;
    // JobService owns the request lifetime. One request updates every widget.
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA).withZone(SHANGHAI);

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            requestImmediateRefresh(context, appWidgetId);
        }
    }

    @Override
    public void onEnabled(Context context) {
        scheduleNextRefresh(context, 1_000L);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            WidgetAppearance.forgetStatus(context, appWidgetId);
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(PREF_SELECTOR_PREFIX + appWidgetId)
                    .apply();
        }
        super.onDeleted(context, appWidgetIds);
    }

    @Override
    public void onDisabled(Context context) {
        cancelRefresh(context);
        super.onDisabled(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_REFRESH.equals(intent.getAction())) {
            super.onReceive(context, intent);
            return;
        }

        final Context appContext = context.getApplicationContext();
        if (getWidgetIds(appContext).length == 0) {
            cancelRefresh(appContext);
            return;
        }
        // Register the next wake-up before starting network work.
        try {
            scheduleNextRefresh(appContext, REFRESH_INTERVAL_MS);
            RefreshJobService.ensurePeriodic(appContext);
            if (!RefreshJobService.enqueue(appContext)) throw new IOException("任务调度被拒绝");
        } catch (Exception error) {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString("last_error", error.getClass().getSimpleName()).apply();
            recordRefreshState(appContext, "刷新未启动");
        }
    }

    static void recordRefreshState(Context context, String state) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString("refresh_state", state).apply();
        for (int id : getWidgetIds(context)) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_dorm_electric);
            views.setTextViewText(R.id.widget_status, state);
            WidgetAppearance.setStatus(context, views, id, WidgetAppearance.LOADING, false);
            AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(id, views);
        }
    }

    static boolean refreshAll(Context appContext) {
            try {
                int[] appWidgetIds = getWidgetIds(appContext);
                if (appWidgetIds.length == 0) {
                    cancelRefresh(appContext);
                    return true;
                }
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .putLong("last_attempt", System.currentTimeMillis()).apply();
                recordRefreshState(appContext, "正在同步");
                String activeSession = SessionStore.get(appContext);
                JSONObject payload = loadPayload(appContext);
                if (!activeSession.equals(SessionStore.get(appContext))) throw new IOException("session changed");
                JSONArray dorms = payload.optJSONArray("dorms");
                if (dorms == null || dorms.length() == 0) {
                    throw new IOException("no dorm data");
                }
                for (int appWidgetId : appWidgetIds) {
                    if (Thread.currentThread().isInterrupted()) return false;
                    String selector = widgetSelector(appContext, appWidgetId);
                    JSONObject selected = selectDorm(dorms, selector);
                    if (selected == null) { renderError(appContext, appWidgetId); continue; }
                    DormData data = DormData.from(selected);
                    renderData(appContext, appWidgetId, data);
                }
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .putLong("last_success", System.currentTimeMillis()).putString("last_error", "")
                        .putString("refresh_state", "同步完成").apply();
                return true;
            } catch (Exception error) {
                if (Thread.currentThread().isInterrupted()) return false;
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .putString("last_error", error.getClass().getSimpleName())
                        .putString("refresh_state", "同步失败").apply();
                int[] appWidgetIds = getWidgetIds(appContext);
                for (int appWidgetId : appWidgetIds) {
                    renderError(appContext, appWidgetId);
                }
                scheduleNextRefresh(appContext, ERROR_RETRY_MS);
                return false;
            }
    }

    public static void requestImmediateRefresh(Context context, int appWidgetId) {
        Context appContext = context.getApplicationContext();
        renderLoading(appContext, appWidgetId);
        appContext.sendBroadcast(new Intent(appContext, DormWidgetProvider.class).setAction(ACTION_REFRESH));
    }

    public static void clearWidgets(Context context) {
        for (int id : getWidgetIds(context)) renderError(context, id);
    }

    public static void scheduleNextRefresh(Context context, long delayMs) {
        if (getWidgetIds(context).length == 0) {
            return;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        long triggerAt = System.currentTimeMillis() + Math.max(1_000L, delayMs);
        PendingIntent pendingIntent = refreshPendingIntent(context);
        try {
            if (Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
        } catch (SecurityException revoked) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putLong("next_alarm", triggerAt).apply();
    }

    private static void cancelRefresh(Context context) {
        RefreshJobService.cancel(context);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(refreshPendingIntent(context));
        }
    }

    private static PendingIntent refreshPendingIntent(Context context) {
        Intent intent = new Intent(context, DormWidgetProvider.class).setAction(ACTION_REFRESH);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, REFRESH_REQUEST_CODE, intent, flags);
    }

    private static int[] getWidgetIds(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, DormWidgetProvider.class);
        return manager.getAppWidgetIds(component);
    }

    private static String widgetSelector(Context context, int appWidgetId) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_SELECTOR_PREFIX + appWidgetId, "")
                .trim();
    }

    private static JSONObject loadPayload(Context context) throws Exception {
        String separator = API_URL.contains("?") ? "&" : "?";
        URL url = new URL(API_URL + separator + "_ts=" + System.currentTimeMillis());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
        connection.setRequestProperty("Pragma", "no-cache");
        String cookie = SessionStore.get(context);
        if (cookie.isEmpty()) throw new IOException("login required");
        connection.setRequestProperty("Cookie", cookie);
        try {
            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("HTTP " + statusCode);
            }
            String body = readBody(connection.getInputStream());
            return new JSONObject(body);
        } finally {
            connection.disconnect();
        }
    }

    private static JSONObject selectDorm(JSONArray dorms, String selector) {
        if (!selector.isEmpty()) {
            for (int index = 0; index < dorms.length(); index += 1) {
                JSONObject dorm = dorms.optJSONObject(index);
                if (dorm == null) {
                    continue;
                }
                if (selector.equals(dorm.optString("id"))
                        || selector.equals(dorm.optString("title"))
                        || selector.equals(dorm.optString("roomLabel"))) {
                    return dorm;
                }
            }
        }
        return selector.isEmpty() ? dorms.optJSONObject(0) : null;
    }

    private static String readBody(InputStream inputStream) throws IOException {
        StringBuilder body = new StringBuilder();
        long deadline = android.os.SystemClock.elapsedRealtime() + 20_000;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()
                        || android.os.SystemClock.elapsedRealtime() > deadline
                        || body.length() > 2_000_000) throw new IOException("request interrupted or timed out");
                body.append(line);
            }
        }
        return body.toString();
    }

    private static void renderLoading(Context context, int appWidgetId) {
        RemoteViews views = baseViews(context);
        views.setTextViewText(R.id.widget_location, "等待首次同步");
        views.setTextViewText(R.id.widget_remaining, "--");
        views.setTextViewText(R.id.widget_status, "等待调度");
        views.setTextViewText(R.id.widget_status_dot, "●");
        WidgetAppearance.setStatus(context, views, appWidgetId, WidgetAppearance.LOADING);
        views.setTextViewText(R.id.widget_updated, "更新 --");
        setWidgetClick(views, context, appWidgetId, null);
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views);
    }

    private static void renderData(Context context, int appWidgetId, DormData data) {
        RemoteViews views = baseViews(context);
        boolean low = "ok".equals(data.status) && data.remaining != null && data.remaining < data.lowThreshold;
        String style = low ? WidgetAppearance.WARNING : statusStyle(data.status);
        views.setTextViewText(R.id.widget_location, data.location);
        views.setTextViewText(R.id.widget_remaining, data.remaining == null ? "--" : String.format(Locale.US, "%.2f", data.remaining));
        views.setTextViewText(R.id.widget_status, low ? "电量偏低" : statusText(data.status));
        views.setTextViewText(R.id.widget_status_dot, "●");
        WidgetAppearance.setStatus(context, views, appWidgetId, style);
        views.setTextViewText(R.id.widget_updated, "数据 " + formatTime(data.fetchedAt)
                + " · 同步 " + TIME_FORMAT.format(Instant.now()));
        setWidgetClick(views, context, appWidgetId, data.id);
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views);
    }

    private static void renderError(Context context, int appWidgetId) {
        RemoteViews views = baseViews(context);
        views.setTextViewText(R.id.widget_location, "暂时无法读取数据");
        views.setTextViewText(R.id.widget_remaining, "--");
        views.setTextViewText(R.id.widget_status, "稍后重试");
        views.setTextViewText(R.id.widget_status_dot, "●");
        WidgetAppearance.setStatus(context, views, appWidgetId, WidgetAppearance.ERROR);
        views.setTextViewText(R.id.widget_updated, "同步失败 · 等待重试");
        setWidgetClick(views, context, appWidgetId, null);
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views);
    }

    private static RemoteViews baseViews(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_dorm_electric);
        WidgetAppearance.apply(context, views);
        views.setViewVisibility(R.id.widget_unit, View.VISIBLE);
        views.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(context, 41000,
                new Intent(context, DormWidgetProvider.class).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return views;
    }

    private static void setWidgetClick(RemoteViews views, Context context, int appWidgetId, String dormId) {
        Intent intent = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (dormId != null) intent.putExtra("dorm_id", dormId);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 30_000 + appWidgetId, intent, flags);
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);
    }

    private static String statusStyle(String status) {
        if ("ok".equals(status)) {
            return WidgetAppearance.NORMAL;
        }
        if ("needs_rebind".equals(status)) {
            return WidgetAppearance.ERROR;
        }
        return WidgetAppearance.WARNING;
    }

    private static String statusText(String status) {
        if ("ok".equals(status)) {
            return "正常";
        }
        if ("needs_rebind".equals(status)) {
            return "需重新绑定";
        }
        if ("stale".equals(status)) {
            return "数据过期";
        }
        if ("starting".equals(status)) return "上游查询中";
        if ("not_configured".equals(status)) return "未配置";
        return "状态未知";
    }

    private static String formatTime(String value) {
        if (value == null || value.isEmpty()) {
            return "--";
        }
        try {
            return TIME_FORMAT.format(Instant.parse(value));
        } catch (Exception ignored) {
            return "--";
        }
    }

    private static final class DormData {
        private final String id;
        private final String location;
        private final String status;
        private final Double remaining;
        private final String fetchedAt;
        private final double lowThreshold;

        private DormData(String id, String location, String status, Double remaining, String fetchedAt, double lowThreshold) {
            this.id = id;
            this.location = location;
            this.status = status;
            this.remaining = remaining;
            this.fetchedAt = fetchedAt;
            this.lowThreshold = lowThreshold;
        }

        private static DormData from(JSONObject dorm) {
            JSONObject location = dorm.optJSONObject("location");
            String building = location == null ? "" : location.optString("building", "");
            String room = location == null ? "" : location.optString("room", "");
            String locationText = joinLocation(building, room);
            if (locationText.isEmpty()) {
                locationText = dorm.optString("roomLabel", dorm.optString("title", "宿舍信息"));
            }
            String id = dorm.optString("id", "primary");
            String status = dorm.optString("status", "stale");
            Double remaining = numberValue(dorm.opt("remaining"));
            String fetchedAt = dorm.optString("fetchedAt", "");
            return new DormData(id, locationText, status, remaining, fetchedAt, dorm.optDouble("lowThreshold", 30));
        }

        private static String joinLocation(String building, String room) {
            if (building.isEmpty()) {
                return room;
            }
            if (room.isEmpty()) {
                return building;
            }
            return building + " · " + room;
        }

        private static Double numberValue(Object value) {
            if (value == null || value == JSONObject.NULL) {
                return null;
            }
            try {
                double number = value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(String.valueOf(value));
                return Double.isFinite(number) ? number : null;
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
