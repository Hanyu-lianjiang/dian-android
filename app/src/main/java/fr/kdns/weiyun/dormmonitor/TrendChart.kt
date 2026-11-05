package fr.kdns.weiyun.dormmonitor

import android.graphics.Paint
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max

@Composable fun TrendChart(points: List<TrendPoint>, compact: Boolean = false) {
    val data = remember(points) { normalizedTrendPoints(points) }
    if (data.isEmpty()) { Text("暂无历史采样", color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    var selected by remember(data) { mutableIntStateOf(data.lastIndex) }
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val firstTime = data.first().timestamp
    val lastTime = data.last().timestamp
    val timeSpan = (lastTime - firstTime).coerceAtLeast(1L)
    val bounds = remember(data) {
        val minimum = data.minOf { it.remaining }; val maximum = data.maxOf { it.remaining }
        val padding = max((maximum - minimum) * .12, .04)
        (if (minimum >= 0) (minimum - padding).coerceAtLeast(0.0) else minimum - padding) to (maximum + padding)
    }
    val low = bounds.first; val high = bounds.second
    val format = remember(firstTime, lastTime) { DateTimeFormatter.ofPattern(if (lastTime - firstTime >= 86400000L) "MM-dd HH:mm" else "HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai")) }
    val valueFormat = remember { DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai")) }
    val sampleCount = remember(data) { data.sumOf { it.samples } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!compact) Text("剩余电量（度）", style = MaterialTheme.typography.labelMedium, color = label)
        val interaction = if (compact) Modifier else Modifier.pointerInput(data) {
            detectTapGestures { position ->
                val left = 62.dp.toPx(); val width = (size.width - left - 12.dp.toPx()).coerceAtLeast(1f)
                val target = firstTime + (((position.x - left) / width).coerceIn(0f, 1f) * timeSpan).toLong()
                selected = data.indices.minByOrNull { abs(data[it].timestamp - target) } ?: 0
            }
        }
        Box(Modifier.fillMaxWidth().height(if (compact) 76.dp else 210.dp).then(interaction).drawWithCache {
            val left = if (compact) 4.dp.toPx() else 62.dp.toPx()
            val right = size.width - if (compact) 4.dp.toPx() else 12.dp.toPx()
            val top = 10.dp.toPx(); val bottom = size.height - if (compact) 8.dp.toPx() else 32.dp.toPx()
            val plotWidth = (right - left).coerceAtLeast(1f)
            fun x(point: TrendPoint) = if (data.size == 1) left + plotWidth / 2 else left + ((point.timestamp - firstTime).toDouble() / timeSpan * plotWidth).toFloat()
            fun y(value: Double) = bottom - ((value - low) / (high - low) * (bottom - top)).toFloat()
            val vertices = data.map { Offset(x(it), y(it.remaining)) }
            val path = Path().apply {
                moveTo(vertices.first().x, vertices.first().y)
                for (index in 1 until vertices.size) lineTo(vertices[index].x, vertices[index].y)
            }
            val stroke = Stroke(width = (if (compact) 1.25.dp else 1.5.dp).toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = label.toArgb(); textSize = 10.sp.toPx() }
            val gridLines = (0..4).map { index -> val value = low + (high - low) * index / 4; y(value) to decimal(value) }
            val indices = when {
                data.size == 1 -> listOf(0)
                lastTime - firstTime >= 86400000L && plotWidth < 280.dp.toPx() -> listOf(0, data.lastIndex)
                else -> listOf(0, data.lastIndex / 2, data.lastIndex).distinct()
            }
            val labels = indices.map { index -> Triple(vertices[index].x, format.format(Instant.ofEpochMilli(data[index].timestamp)), when { data.size == 1 -> Paint.Align.CENTER; index == 0 -> Paint.Align.LEFT; index == data.lastIndex -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }) }
            onDrawBehind {
                gridLines.forEach { (py, text) ->
                    drawLine(grid.copy(alpha = .45f), Offset(left, py), Offset(right, py), .5.dp.toPx())
                    if (!compact) { paint.textAlign = Paint.Align.RIGHT; drawContext.canvas.nativeCanvas.drawText(text, left - 8.dp.toPx(), py + 3.dp.toPx(), paint) }
                }
                if (data.size > 1) drawPath(path, line, style = stroke)
                if (!compact) {
                    if (plotWidth / data.size > 5.dp.toPx()) vertices.forEach { drawCircle(line, 1.dp.toPx(), it) }
                    labels.forEach { (px, text, align) -> paint.textAlign = align; drawContext.canvas.nativeCanvas.drawText(text, px, size.height - 8.dp.toPx(), paint) }
                }
                val active = vertices[if (compact) data.lastIndex else selected.coerceIn(data.indices)]
                if (!compact) drawLine(line.copy(alpha = .22f), Offset(active.x, top), Offset(active.x, bottom), .75.dp.toPx())
                drawCircle(line, if (compact) 2.5.dp.toPx() else 3.5.dp.toPx(), active)
            }
        })
        if (!compact) {
            val active = data[selected.coerceIn(data.indices)]
            Text("${valueFormat.format(Instant.ofEpochMilli(active.timestamp))} · ${decimal(active.remaining)} 度", style = MaterialTheme.typography.bodyMedium)
            Text("$sampleCount 条采样 · ${data.size} 个绘图点", style = MaterialTheme.typography.bodySmall, color = label)
        }
    }
}
