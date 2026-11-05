package fr.kdns.weiyun.dormmonitor

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColors
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColors

/** Only presentation preferences live here. Account and monitoring data remain in their own stores. */
@Immutable internal data class Appearance(
    val mode: String = "system", val palette: String = "mist", val custom: String = "477582",
    val black: Boolean = false, val floating: Boolean = true, val importTab: Boolean = true,
    val compact: Boolean = false, val miniTrend: Boolean = true, val glass: Boolean = true
) {
    companion object {
        fun read(p: SharedPreferences): Appearance = runCatching {
            Appearance(
                mode = p.getString("mode", "system").takeIf { it in listOf("system", "light", "dark") } ?: "system",
                palette = p.getString("palette", "mist").takeIf { it in paletteOptions.map { c -> c.id } + "custom" } ?: "mist",
                custom = p.getString("custom", "477582").orEmpty().takeIf { it.matches(Regex("[A-Fa-f0-9]{6}")) } ?: "477582",
                black = p.getBoolean("black", false), floating = p.getBoolean("floating", true),
                importTab = p.getBoolean("importTab", true), compact = p.getBoolean("compact", false), miniTrend = p.getBoolean("miniTrend", true),
                glass = p.getBoolean("glass", true)
            )
        }.getOrDefault(Appearance())
    }
    fun save(p: SharedPreferences) { p.edit().putString("mode", mode).putString("palette", palette).putString("custom", custom)
        .putBoolean("black", black).putBoolean("floating", floating).putBoolean("importTab", importTab)
        .putBoolean("compact", compact).putBoolean("miniTrend", miniTrend).putBoolean("glass", glass).apply() }
}
internal data class PaletteOption(val id: String, val name: String, val color: Color)
internal val paletteOptions = listOf(
    PaletteOption("wallpaper", "壁纸", Color(0xFF527986)), PaletteOption("mist", "雾蓝", Color(0xFF477582)),
    PaletteOption("sage", "青苔", Color(0xFF5F7862)), PaletteOption("iris", "鸢尾", Color(0xFF786695)),
    PaletteOption("rose", "蔷薇", Color(0xFF9A6579)), PaletteOption("sand", "麦芽", Color(0xFF8B7349))
)
internal val LocalAppearance = staticCompositionLocalOf { Appearance() }
internal val LocalSaveAppearance = staticCompositionLocalOf<(Appearance) -> Unit> { {} }
internal val LocalDormDark = staticCompositionLocalOf { false }

@Composable internal fun DormTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("appearance", Context.MODE_PRIVATE) }
    var appearance by remember { mutableStateOf(Appearance.read(prefs)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> appearance = Appearance.read(prefs) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val dark = when (appearance.mode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val systemColors = if (Build.VERSION.SDK_INT >= 31 && appearance.palette == "wallpaper") {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else null
    val seed = paletteOptions.find { it.id == appearance.palette }?.color ?: Color(0xFF000000 or appearance.custom.toLong(16))
    val colors = remember(dark, appearance, systemColors) {
        var c = systemColors ?: tonalScheme(seed, dark)
        if (dark && appearance.black) c = c.copy(background = Color.Black, surface = Color.Black,
            surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF101214))
        c
    }
    val activity = LocalActivity.current
    SideEffect { activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
        isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
    }; if (Build.VERSION.SDK_INT >= 29) it.window.isNavigationBarContrastEnforced = false } }
    val miuix = remember(colors, dark) { (if (dark) miuixDarkColors() else miuixLightColors()).copy(
        primary = colors.primary, onPrimary = colors.onPrimary, primaryVariant = colors.primary,
        onPrimaryVariant = colors.onPrimary, primaryContainer = colors.primaryContainer, onPrimaryContainer = colors.onPrimaryContainer,
        disabledPrimary = colors.primary.copy(alpha = .38f), disabledOnPrimary = colors.onPrimary.copy(alpha = .38f),
        disabledPrimaryButton = colors.primary.copy(alpha = .24f), disabledOnPrimaryButton = colors.onSurfaceVariant,
        secondary = colors.secondary, onSecondary = colors.onSecondary,
        secondaryVariant = colors.secondaryContainer, onSecondaryVariant = colors.onSecondaryContainer,
        secondaryContainer = colors.surfaceContainerHigh, onSecondaryContainer = colors.onSurfaceVariant,
        secondaryContainerVariant = colors.surfaceContainerHigh, onSecondaryContainerVariant = colors.onSurfaceVariant,
        tertiaryContainer = colors.tertiaryContainer, onTertiaryContainer = colors.onTertiaryContainer,
        background = colors.background, onBackground = colors.onBackground, onBackgroundVariant = colors.onSurfaceVariant,
        surface = colors.surfaceContainerLow, onSurface = colors.onSurface, surfaceVariant = colors.surfaceContainerHigh,
        onSurfaceSecondary = colors.onSurfaceVariant, onSurfaceVariantSummary = colors.onSurfaceVariant,
        onSurfaceVariantActions = colors.onSurfaceVariant, outline = colors.outline, dividerLine = colors.outlineVariant,
        surfaceContainer = colors.surfaceContainer, onSurfaceContainer = colors.primary,
        onSurfaceContainerVariant = colors.onSurfaceVariant, surfaceContainerHigh = colors.surfaceContainerHigh,
        onSurfaceContainerHigh = colors.onSurface, surfaceContainerHighest = colors.surfaceContainerHighest, onSurfaceContainerHighest = colors.onSurface
    ) }
    CompositionLocalProvider(LocalAppearance provides appearance, LocalSaveAppearance provides { appearance = it; it.save(prefs); WidgetAppearance.updateAll(context) }, LocalDormDark provides dark) {
        MiuixTheme(colors = miuix) { MaterialTheme(colorScheme = colors) {
            CompositionLocalProvider(LocalContentColor provides colors.onSurface, content = content)
        } }
    }
}

/** A restrained tonal fallback for pre-Android-12 devices and user-selected colors. */
internal fun tonalScheme(seed: Color, dark: Boolean): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(android.graphics.Color.rgb((seed.red * 255).toInt(), (seed.green * 255).toInt(), (seed.blue * 255).toInt()), hsl)
    fun tone(lightness: Float, saturation: Float = hsl[1].coerceIn(.18f, .48f), shift: Float = 0f): Color =
        Color(ColorUtils.HSLToColor(floatArrayOf((hsl[0] + shift + 360f) % 360f, saturation, lightness)))
    return if (dark) darkColorScheme(
        primary = tone(.79f), onPrimary = tone(.17f), primaryContainer = tone(.27f), onPrimaryContainer = tone(.91f),
        secondary = tone(.78f, .14f), onSecondary = tone(.16f, .14f), secondaryContainer = tone(.25f, .16f), onSecondaryContainer = tone(.9f, .15f),
        tertiary = tone(.8f, .22f, 55f), tertiaryContainer = tone(.25f, .2f, 55f), onTertiaryContainer = tone(.91f, .2f, 55f),
        background = tone(.075f, .11f), onBackground = tone(.91f, .06f), surface = tone(.09f, .11f), onSurface = tone(.91f, .06f),
        surfaceVariant = tone(.23f, .10f), onSurfaceVariant = tone(.75f, .07f), outline = tone(.57f, .08f), outlineVariant = tone(.29f, .09f),
        surfaceContainerLowest = tone(.055f, .1f), surfaceContainerLow = tone(.12f, .12f), surfaceContainer = tone(.15f, .12f),
        surfaceContainerHigh = tone(.19f, .12f), surfaceContainerHighest = tone(.23f, .12f)
    ) else lightColorScheme(
        primary = tone(.35f), onPrimary = Color.White, primaryContainer = tone(.90f, .28f), onPrimaryContainer = tone(.18f),
        secondary = tone(.38f, .14f), onSecondary = Color.White, secondaryContainer = tone(.91f, .19f), onSecondaryContainer = tone(.21f, .18f),
        tertiary = tone(.40f, .24f, 55f), tertiaryContainer = tone(.92f, .28f, 55f), onTertiaryContainer = tone(.23f, .23f, 55f),
        background = tone(.978f, .28f), onBackground = tone(.14f, .12f), surface = tone(.978f, .28f), onSurface = tone(.14f, .12f),
        surfaceVariant = tone(.91f, .14f), onSurfaceVariant = tone(.38f, .09f), outline = tone(.52f, .07f), outlineVariant = tone(.82f, .11f),
        surfaceContainerLowest = Color.White, surfaceContainerLow = tone(.945f, .25f), surfaceContainer = tone(.92f, .23f),
        surfaceContainerHigh = tone(.895f, .23f), surfaceContainerHighest = tone(.87f, .21f)
    )
}
