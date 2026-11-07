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
import androidx.core.graphics.ColorUtils

/** Recolor existing widget views without fetching or persisting monitoring data. */
object WidgetAppearance {
    const val NORMAL = "normal"
    const val WARNING = "warning"
    const val ERROR = "error"
    const val LOADING = "loading"
    private const val STATUS_STYLE = "status_style_"
    private const val VALUE_STYLE = "value_style_"

    internal data class Swatch(val foreground: Int, val container: Int)

    private fun scheme(context: Context): ColorScheme {
        val p = Appearance.read(context.getSharedPreferences("appearance", 0))
        val dark = when (p.mode) { "dark" -> true; "light" -> false; else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES }
        val c = if (Build.VERSION.SDK_INT >= 31 && p.palette == "wallpaper") {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else tonalScheme(paletteOptions.find { it.id == p.palette }?.color ?: Color(0xFF000000 or p.custom.toLong(16)), dark)
        return if (dark && p.black) c.copy(surfaceContainer = Color.Black) else c
    }
    internal fun swatch(context: Context, style: String) = swatch(scheme(context), style)
    private fun swatch(c: ColorScheme, style: String): Swatch {
        val dark = c.surfaceContainer.luminance() < .5f
        val accent = when (style) {
            WARNING, LOADING -> if (dark) 0xFFEDC070.toInt() else 0xFF865918.toInt()
            ERROR -> c.error.toArgb()
            else -> c.primary.toArgb()
        }
        // A subtle wash of the same state color keeps the pill part of the card.
        fun blend(from: Int, to: Int, amount: Float) = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(from, to, amount), 255)
        val container = blend(c.surfaceContainer.toArgb(), accent, .09f)
        val readable = if (ColorUtils.calculateContrast(accent, container) >= 4.5) accent else {
            val target = if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            var low = 0f
            var high = 1f
            repeat(16) {
                val middle = (low + high) / 2
                if (ColorUtils.calculateContrast(blend(accent, target, middle), container) >= 4.5) high = middle else low = middle
            }
            blend(accent, target, high)
        }
        return Swatch(readable, container)
    }
    @JvmStatic fun accent(context: Context) = swatch(context, NORMAL).foreground
    @JvmStatic fun warning(context: Context) = swatch(context, WARNING).foreground
    @JvmStatic fun danger(context: Context) = swatch(context, ERROR).foreground

    private fun applyLabel(context: Context, views: RemoteViews, style: String) {
        val colors = swatch(context, style)
        views.setInt(R.id.widget_status_background, "setColorFilter", colors.container)
        views.setTextColor(R.id.widget_status, colors.foreground)
        views.setTextColor(R.id.widget_status_dot, colors.foreground)
    }

    @JvmStatic @JvmOverloads fun setStatus(context: Context, views: RemoteViews, widgetId: Int, style: String, updateValue: Boolean = true) {
        applyLabel(context, views, style)
        val valueStyle = if (style == LOADING) NORMAL else style
        if (updateValue) views.setTextColor(R.id.widget_remaining, swatch(context, valueStyle).foreground)
        // Only presentation roles are retained; no readings, account data or tokens.
        context.getSharedPreferences(DormWidgetProvider.PREFS_NAME, 0).edit().apply {
            putString(STATUS_STYLE + widgetId, style)
            if (updateValue) putString(VALUE_STYLE + widgetId, valueStyle)
        }.apply()
    }

    @JvmStatic fun forgetStatus(context: Context, widgetId: Int) {
        context.getSharedPreferences(DormWidgetProvider.PREFS_NAME, 0).edit()
            .remove(STATUS_STYLE + widgetId).remove(VALUE_STYLE + widgetId).apply()
    }
    @JvmStatic @JvmOverloads fun apply(context: Context, views: RemoteViews, recolorRemaining: Boolean = true) {
        val c = scheme(context)
        views.setInt(R.id.widget_background_tint, "setColorFilter", c.surfaceContainer.toArgb())
        views.setInt(R.id.widget_icon, "setColorFilter", c.primary.toArgb())
        views.setTextColor(R.id.widget_title, c.onSurface.toArgb())
        listOf(R.id.widget_location, R.id.widget_updated, R.id.widget_unit).forEach { views.setTextColor(it, c.onSurfaceVariant.toArgb()) }
        views.setTextColor(R.id.widget_refresh, c.primary.toArgb())
        applyLabel(context, views, NORMAL)
        if (recolorRemaining) views.setTextColor(R.id.widget_remaining, accent(context))
    }
    internal fun recolor(context: Context, views: RemoteViews, widgetId: Int) {
        val prefs = context.getSharedPreferences(DormWidgetProvider.PREFS_NAME, 0)
        apply(context, views, recolorRemaining = false)
        applyLabel(context, views, prefs.getString(STATUS_STYLE + widgetId, NORMAL) ?: NORMAL)
        views.setTextColor(R.id.widget_remaining, swatch(context, prefs.getString(VALUE_STYLE + widgetId, NORMAL) ?: NORMAL).foreground)
    }
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        manager.getAppWidgetIds(ComponentName(context, DormWidgetProvider::class.java)).forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_dorm_electric)
            recolor(context, views, id)
            manager.partiallyUpdateAppWidget(id, views)
        }
    }
}
