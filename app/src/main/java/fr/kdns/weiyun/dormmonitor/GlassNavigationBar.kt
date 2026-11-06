package fr.kdns.weiyun.dormmonitor

import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

// Adapted from xiyuchengxu/jianxi, commit fc88d29192549c22d79b38aef7d64f4eed7a3e7d.
// Changes: configurable tabs, application theme, backdrop origin and docked layout.
internal data class GlassNavigationItem(val title: String, val icon: ImageVector)

/** Content and navigation are separate; the lens never samples its own output. */
@Composable
internal fun GlassNavigationBar(
    layer: GraphicsLayer,
    layerOrigin: Offset,
    items: List<GlassNavigationItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    floating: Boolean = true
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var pressed by remember { mutableStateOf(false) }
    var pointerX by remember { mutableFloatStateOf(0f) }
    var previewIndex by remember { mutableIntStateOf(selected) }
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val dark = LocalDormDark.current
    val scheme = MaterialTheme.colorScheme
    val scene = rememberGraphicsLayer()
    val lens = remember {
        if (Build.VERSION.SDK_INT >= 33) runCatching { GlassLensEffect() }.getOrNull() else null
    }
    val pressure by animateFloatAsState(if (pressed) 1f else 0f,
        spring(dampingRatio = .67f, stiffness = 520f), label = "glassPressure")
    val amount = pressure.coerceIn(0f, 1f)
    val trackShape = RoundedCornerShape(if (floating) 30.dp else 0.dp)

    // 60dp visible track; reserve space for the lens to rise above and below it.
    BoxWithConstraints(modifier.widthIn(max = if (floating) (items.size * 126).dp else androidx.compose.ui.unit.Dp.Infinity).fillMaxWidth().height(84.dp)
        .onGloballyPositioned { origin = it.positionInRoot() }) {
        val widthPx = with(density) { maxWidth.toPx() }
        val hostHeight = with(density) { 84.dp.toPx() }
        val trackTop = with(density) { 12.dp.toPx() }
        val paddingPx = with(density) { 5.dp.toPx() }
        val slot = (widthPx - paddingPx * 2) / items.size
        fun indexAt(x: Float) = ((x - paddingPx) / slot).toInt().coerceIn(0, items.lastIndex)
        fun center(index: Int) = paddingPx + slot * (index + .5f)
        val desiredCenter = if (pressed) pointerX.coerceIn(center(0) - 5f, center(items.lastIndex) + 5f) else center(selected)
        val animatedCenter by animateFloatAsState(desiredCenter,
            spring(dampingRatio = .78f, stiffness = if (pressed) 700f else 430f), label = "glassPosition")
        val stretch = if (pressed) (abs(pointerX - animatedCenter) / slot).coerceIn(0f, 1f) else 0f
        val lensWidthPx = slot * (1f + .10f * amount + .08f * stretch)
        val lensHeightPx = with(density) { (50.dp + 30.dp * amount).toPx() }
        val lensLeft = animatedCenter - lensWidthPx / 2
        val lensTop = (hostHeight - lensHeightPx) / 2
        val active = if (pressed) previewIndex else selected

        Box(Modifier.fillMaxWidth().height(60.dp).align(Alignment.Center)
            .shadow(12.dp, trackShape, ambientColor = Color.Black.copy(alpha = .10f), spotColor = Color.Black.copy(alpha = .14f))
            .drawWithContent { scene.record { this@drawWithContent.drawContent() }; drawLayer(scene) }
            .clip(trackShape)
            .pointerInput(enabled, widthPx, items.size) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var cancelled = false
                    try {
                        pointerX = down.position.x
                        previewIndex = indexAt(pointerX)
                        pressed = true
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            pointerX = change.position.x.coerceIn(0f, widthPx)
                            if (change.position.y < -size.height || change.position.y > size.height * 2) cancelled = true
                            val next = indexAt(pointerX)
                            if (previewIndex != next) {
                                previewIndex = next
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            change.consume()
                            if (!change.pressed) {
                                if (!cancelled) currentOnSelect(previewIndex)
                                break
                            }
                        }
                    } finally {
                        pressed = false
                        previewIndex = currentSelected
                    }
                }
            }) {
            Canvas(Modifier.matchParentSize().graphicsLayer {
                if (Build.VERSION.SDK_INT >= 31) {
                    renderEffect = android.graphics.RenderEffect.createBlurEffect(22f, 22f, Shader.TileMode.CLAMP).asComposeRenderEffect()
                }
            }) {
                withTransform({ translate(layerOrigin.x - origin.x, layerOrigin.y - origin.y - trackTop) }) { drawLayer(layer) }
            }
            Box(Modifier.matchParentSize().background(scheme.surface.copy(
                alpha = if (Build.VERSION.SDK_INT < 31) .94f else if (dark) .60f else .62f)))
            Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(
                Color.White.copy(alpha = if (dark) .09f else .38f), Color.Transparent, scheme.primary.copy(alpha = .035f)
            ))).border(.8.dp, Brush.linearGradient(listOf(
                Color.White.copy(alpha = if (dark) .30f else .78f), scheme.outlineVariant.copy(alpha = .20f), Color.White.copy(alpha = .30f)
            )), trackShape))
            Box(Modifier.offset { IntOffset((animatedCenter - slot / 2).roundToInt(), paddingPx.roundToInt()) }
                .width(with(density) { slot.toDp() }).height(50.dp)
                .graphicsLayer { alpha = 1f - amount }
                .background(scheme.primary.copy(alpha = if (dark) .15f else .105f), RoundedCornerShape(25.dp)))
            Row(Modifier.fillMaxSize().padding(5.dp)) {
                items.forEachIndexed { index, (title, icon) ->
                    val color = if (active == index) scheme.primary else scheme.onSurfaceVariant
                    Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(25.dp))
                        .semantics(mergeDescendants = true) {
                            role = Role.Tab
                            this.selected = selected == index
                            if (!enabled) disabled()
                            onClick(label = title) {
                                if (enabled) { currentOnSelect(index); true } else false
                            }
                        }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(title, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        if (pressure > .01f) {
            val lensShape = RoundedCornerShape(with(density) { (lensHeightPx / 2).toDp() })
            Box(Modifier.offset { IntOffset(lensLeft.roundToInt(), lensTop.roundToInt()) }
                .requiredSize(with(density) { lensWidthPx.toDp() }, with(density) { lensHeightPx.toDp() })
                .graphicsLayer { alpha = (amount * 1.8f).coerceIn(0f, 1f) }
                .shadow(15.dp, lensShape, ambientColor = Color.Black.copy(alpha = .16f), spotColor = Color.Black.copy(alpha = .25f))
                .clip(lensShape)) {
                Canvas(Modifier.matchParentSize().graphicsLayer {
                    if (Build.VERSION.SDK_INT >= 33 && lens != null) {
                        renderEffect = lens.configure(size.width, size.height, density.density, amount,
                            (pointerX - animatedCenter) / slot).asComposeRenderEffect()
                    }
                }) {
                    val fallbackZoom = if (Build.VERSION.SDK_INT < 33 || lens == null) 1f + .14f * amount else 1f
                    withTransform({ scale(fallbackZoom, fallbackZoom, center) }) {
                        withTransform({ translate(layerOrigin.x - origin.x - lensLeft, layerOrigin.y - origin.y - lensTop) }) { drawLayer(layer) }
                        withTransform({ translate(-lensLeft, trackTop - lensTop) }) { drawLayer(scene) }
                    }
                }
                Canvas(Modifier.matchParentSize()) {
                    val inset = .8.dp.toPx()
                    val borderSize = Size(size.width - inset * 2, size.height - inset * 2)
                    val corner = androidx.compose.ui.geometry.CornerRadius(borderSize.height / 2)
                    drawRoundRect(Brush.sweepGradient(listOf(
                        Color(0xFFA2BDFF).copy(alpha = .54f * amount), Color.White.copy(alpha = .8f * amount),
                        Color(0xFF9AE9CE).copy(alpha = .30f * amount), Color(0xFFE3AE85).copy(alpha = .42f * amount),
                        Color(0xFFA2BDFF).copy(alpha = .54f * amount)
                    )), topLeft = Offset(inset, inset), size = borderSize, cornerRadius = corner, style = Stroke(1.15.dp.toPx()))
                    drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .12f * amount), Color.Transparent,
                        Color.Black.copy(alpha = .10f * amount))), topLeft = Offset(inset, inset), size = borderSize, cornerRadius = corner)
                }
            }
        }
    }
}
