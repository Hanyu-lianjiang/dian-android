package fr.kdns.weiyun.dormmonitor

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Native RemoteViews rendering with isolated presentation fixtures, no network. */
internal fun Instrumentation.verifyWidgetPalettes() {
        try {
            val directory = File(targetContext.getExternalFilesDir(null), "widget-verification").apply { mkdirs() }
            val prefs = targetContext.getSharedPreferences("appearance", 0)
            val original = Appearance.read(prefs)
            val checks = JSONArray()
            try {
                runOnMainSync {
                    val themes = paletteOptions.map { it.id to "477582" } +
                        listOf("FFFFFF", "000000", "FFFF00", "00FF00", "FF00FF").map { "custom" to it }
                    for ((palette, custom) in themes) for (mode in listOf("light", "dark", "black")) {
                        Appearance(mode = if (mode == "black") "dark" else mode, palette = palette, custom = custom, black = mode == "black").save(prefs)
                        for ((style, label) in listOf(WidgetAppearance.NORMAL to "正常", WidgetAppearance.WARNING to "电量偏低", WidgetAppearance.ERROR to "稍后重试", WidgetAppearance.LOADING to "正在同步")) {
                            val id = 99201
                            val remote = RemoteViews(targetContext.packageName, R.layout.widget_dorm_electric)
                            WidgetAppearance.apply(targetContext, remote)
                            remote.setTextViewText(R.id.widget_location, "塔影下L2号楼 · L2-505")
                            remote.setTextViewText(R.id.widget_remaining, if (style == WidgetAppearance.WARNING) "18.20" else "143.07")
                            remote.setTextViewText(R.id.widget_status, label)
                            remote.setTextViewText(R.id.widget_updated, "数据 16:29 · 同步 16:32")
                            WidgetAppearance.setStatus(targetContext, remote, id, style)
                            val root = remote.apply(targetContext, FrameLayout(targetContext))
                            val density = targetContext.resources.displayMetrics.density
                            val width = (320 * density).toInt()
                            val height = (200 * density).toInt()
                            root.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY))
                            root.layout(0, 0, width, height)
                            val colors = WidgetAppearance.swatch(targetContext, style)
                            val status = root.findViewById<TextView>(R.id.widget_status)
                            check(status.currentTextColor == colors.foreground)
                            check(ColorUtils.calculateContrast(colors.foreground, colors.container) >= 4.5)
                            val pill = root.findViewById<ImageView>(R.id.widget_status_background)
                            fun renderedColor(image: ImageView): Int {
                                val sample = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                                image.draw(Canvas(sample))
                                val color = sample.getPixel(image.width / 2, image.height / 2)
                                sample.recycle()
                                return color
                            }
                            val renderedPill = renderedColor(pill)
                            check(renderedPill == colors.container) { "Pill tint $palette $mode $style: ${Integer.toHexString(renderedPill)} expected ${Integer.toHexString(colors.container)} (${pill.width}x${pill.height})" }
                            val background = root.findViewById<ImageView>(R.id.widget_background_tint)
                            val cardColor = renderedColor(background)
                            val pillContrast = maxOf(ColorUtils.calculateContrast(colors.container, cardColor), ColorUtils.calculateContrast(cardColor, colors.container))
                            check(pillContrast <= 1.35) { "Pill too prominent: $palette $mode $style $pillContrast" }
                            check(status.width > 0 && pill.width > 0 && pill.height > 0)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            root.draw(Canvas(bitmap))
                            val name = "$palette-$custom-$mode-$style"
                            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            bitmap.recycle()
                            checks.put(JSONObject().put("theme", "$palette-$custom-$mode").put("state", style).put("labelContrast", ColorUtils.calculateContrast(colors.foreground, colors.container)).put("pillCardContrast", pillContrast))

                            // Reapplying a different palette must recolor both roles without
                            // rewriting content or forgetting the low/error presentation.
                            Appearance(mode = if (mode == "light") "dark" else "light", palette = "rose").save(prefs)
                            val patch = RemoteViews(targetContext.packageName, R.layout.widget_dorm_electric)
                            WidgetAppearance.recolor(targetContext, patch, id)
                            patch.reapply(targetContext, root)
                            check(status.text.toString() == label)
                            check(root.findViewById<TextView>(R.id.widget_remaining).text.toString() == if (style == WidgetAppearance.WARNING) "18.20" else "143.07")
                            check(status.currentTextColor == WidgetAppearance.swatch(targetContext, style).foreground)
                            check(root.findViewById<TextView>(R.id.widget_remaining).currentTextColor == WidgetAppearance.swatch(targetContext, if (style == WidgetAppearance.LOADING) WidgetAppearance.NORMAL else style).foreground)
                            Appearance(mode = if (mode == "black") "dark" else mode, palette = palette, custom = custom, black = mode == "black").save(prefs)
                        }
                    }
                    WidgetAppearance.forgetStatus(targetContext, 99201)
                }
            } finally { original.save(prefs) }
            File(directory, "checks.json").writeText(JSONObject().put("status", "passed").put("renders", checks.length()).put("checks", checks).toString(2))
            finish(0, Bundle().apply { putString("result", "${checks.length()} native widget renders, tonal consistency, text contrast and theme-reapply checks passed") })
        } catch (error: Throwable) { finish(1, Bundle().apply { putString("error", error.toString()) }) }
}
