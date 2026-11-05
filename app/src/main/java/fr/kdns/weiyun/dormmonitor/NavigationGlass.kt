package fr.kdns.weiyun.dormmonitor

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

/** One retained display list, reused by the bar. No screenshots, bitmap copies or frame timers. */
@Stable internal class NavigationBackdrop(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
}

internal fun Modifier.navigationBackdrop(backdrop: NavigationBackdrop, enabled: Boolean): Modifier =
    if (!enabled) this else onGloballyPositioned { backdrop.origin = it.positionInRoot() }.drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

@Composable internal fun GlassNavigationSurface(
    backdrop: NavigationBackdrop, enabled: Boolean, shape: Shape,
    modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit
) {
    val dark = LocalDormDark.current
    val colors = MaterialTheme.colorScheme
    var origin by remember { mutableStateOf(Offset.Zero) }
    val liveBlur = enabled && Build.VERSION.SDK_INT >= 31
    Box(modifier.clip(shape).onGloballyPositioned { origin = it.positionInRoot() }) {
        if (liveBlur) Box(Modifier.matchParentSize().graphicsLayer {
            renderEffect = BlurEffect(22.dp.toPx(), 22.dp.toPx(), TileMode.Clamp)
        }.drawWithContent {
            val delta = backdrop.origin - origin
            // A small optical magnification gives the translucent surface depth.
            val lens = 1.035f
            translate(delta.x * lens - size.width * (lens - 1f) / 2f, delta.y * lens - size.height * (lens - 1f) / 2f) {
                scale(lens, lens, pivot = Offset.Zero) { drawLayer(backdrop.layer) }
            }
        })
        Box(Modifier.matchParentSize().background(
            if (enabled) Brush.verticalGradient(listOf(
                colors.surfaceContainer.copy(alpha = if (liveBlur) .76f else .94f),
                colors.surface.copy(alpha = if (liveBlur) .62f else .94f),
                colors.surfaceContainer.copy(alpha = if (liveBlur) .78f else .96f)
            )) else Brush.verticalGradient(listOf(colors.surfaceContainer, colors.surfaceContainer))
        ))
        if (enabled) Box(Modifier.matchParentSize().border(.8.dp, Brush.linearGradient(listOf(
            Color.White.copy(alpha = if (dark) .22f else .8f),
            colors.primary.copy(alpha = .06f),
            Color.White.copy(alpha = if (dark) .08f else .35f)
        )), shape))
        content()
    }
}
