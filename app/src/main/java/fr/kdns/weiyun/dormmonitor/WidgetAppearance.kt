package fr.kdns.weiyun.dormmonitor

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance

/** Recolor existing widget views without fetching or persisting monitoring data. */
object WidgetAppearance {
    private fun scheme(context: Context): ColorScheme {
        val p = Appearance.read(context.getSharedPreferences("appearance", 0))
        val dark = when (p.mode) { "dark" -> true; "light" -> false; else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES }
        val c = if (Build.VERSION.SDK_INT >= 31 && p.palette == "wallpaper") {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else tonalScheme(paletteOptions.find { it.id == p.palette }?.color ?: Color(0xFF000000 or p.custom.toLong(16)), dark)
        return if (dark && p.black) c.copy(surfaceContainer = Color.Black) else c
    }
    @JvmStatic fun accent(context: Context) = scheme(context).primary.toArgb()
    @JvmStatic fun warning(context: Context) = if (scheme(context).background.luminance() > .5f) 0xFF865918.toInt() else 0xFFEDC070.toInt()
    @JvmStatic fun danger(context: Context) = scheme(context).error.toArgb()
    @JvmStatic @JvmOverloads fun apply(context: Context, views: RemoteViews, recolorRemaining: Boolean = true) {
        val c = scheme(context)
        views.setInt(R.id.widget_background_tint, "setColorFilter", c.surfaceContainer.toArgb())
        views.setInt(R.id.widget_icon, "setColorFilter", c.primary.toArgb())
        views.setTextColor(R.id.widget_title, c.onSurface.toArgb())
        listOf(R.id.widget_location, R.id.widget_updated, R.id.widget_unit).forEach { views.setTextColor(it, c.onSurfaceVariant.toArgb()) }
        views.setTextColor(R.id.widget_refresh, c.primary.toArgb())
        if (recolorRemaining) views.setTextColor(R.id.widget_remaining, c.primary.toArgb())
    }
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        manager.getAppWidgetIds(ComponentName(context, DormWidgetProvider::class.java)).forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_dorm_electric)
            apply(context, views, recolorRemaining = false)
            // Preserve the existing remaining-value warning color until the next refresh.
            manager.partiallyUpdateAppWidget(id, views)
        }
    }
}
