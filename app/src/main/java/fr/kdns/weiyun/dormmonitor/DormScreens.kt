package fr.kdns.weiyun.dormmonitor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable internal fun HomePage(vm: MonitorModel, list: LazyListState, bottomInset: Dp = 0.dp) {
    val p = LocalAppearance.current
    LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(if (p.compact) 12.dp else 20.dp), contentPadding = PaddingValues(bottom = 24.dp + bottomInset)) {
        item(key = "heading") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { PageHeading("我的宿舍", "${vm.dorms.size} 间正在关注") }
                IconButton(onClick = vm::refresh, enabled = !vm.busy) { Icon(Icons.Outlined.Refresh, "刷新宿舍", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = vm::choose, enabled = !vm.busy && vm.canImport) { Icon(Icons.Outlined.AddCircleOutline, "添加宿舍", tint = MaterialTheme.colorScheme.primary) }
            }
        }
        if (vm.dorms.isEmpty()) item(key = "empty") {
            AppCard(tonal = true) {
                IconTile(Icons.Outlined.Cottage, 80)
                Spacer(Modifier.height(8.dp))
                Text("让电量，一目了然", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(if (vm.canImport) "选择校区、楼栋与房间，添加你的宿舍。" else "当前账号暂不能添加宿舍，请联系管理员。", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium)
                AppButton("添加宿舍", vm::choose, Modifier.fillMaxWidth(), enabled = !vm.busy && vm.canImport, primary = true, icon = Icons.Outlined.Add)
            }
        }
        items(vm.dorms, key = { it.optString("id") }, contentType = { "dorm" }) { d ->
            AppCard(onClick = { vm.detail(d) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Outlined.Cottage, if (p.compact) 40 else 48)
                    Spacer(Modifier.width(14.dp))
                    Text(d.optString("title", "宿舍"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    StatusBadge(dormDisplayStatus(d))
                }
                if (dormLocation(d).isNotEmpty()) Text(dormLocation(d), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                EnergyValue(d)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Timeline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("24小时 ${decimal(d.optDouble("delta24h", Double.NaN))} 度", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (p.miniTrend) TrendChart(vm.overviewPoints[d.optString("id")].orEmpty(), compact = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("更新 ${formatTime(d.optString("fetchedAt"))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable private fun EnergyValue(d: JSONObject) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("剩余电量", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (d.isNull("remaining")) "--" else decimal(d.optDouble("remaining")), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            Text(" 度", Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable internal fun DetailPage(vm: MonitorModel, remove: () -> Unit) {
    val d = vm.selected
    if (d == null) { if (vm.detailError.isNotEmpty()) Text(vm.detailError, color = MaterialTheme.colorScheme.error); return }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageHeading(d.optString("title"), dormLocation(d))
        AppCard(tonal = true) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconTile(Icons.Outlined.ElectricBolt, 48); Spacer(Modifier.weight(1f)); StatusBadge(dormDisplayStatus(d)) }
            EnergyValue(d)
            Text("24小时变化 ${decimal(d.optDouble("delta24h", Double.NaN))} 度", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        AppCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric(Icons.Outlined.Speed, "电表总用电量", "${decimal(d.optDouble("meterTotal", Double.NaN))} 度", Modifier.weight(1f))
                Metric(Icons.Outlined.History, "历史采样", "${if (d.has("sampleCount")) d.optLong("sampleCount").toString() else "--"} 条", Modifier.weight(1f))
            }
            Text("更新 ${formatTime(d.optString("fetchedAt"))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.ShowChart, null, tint = MaterialTheme.colorScheme.primary); Text("电量趋势", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleLarge) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendRange.entries.forEach { range -> AppButton(range.label, { vm.detail(d, range) }, primary = vm.detailRange == range) }
        }
        AppCard {
            Text(vm.detailRange.caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (vm.detailPoints.isEmpty() && vm.detailRefreshing) Text("正在读取该时段历史…") else TrendChart(vm.detailPoints)
            if (vm.detailError.isNotEmpty()) Text(vm.detailError, color = MaterialTheme.colorScheme.error)
        }
        AppButton("移除宿舍", remove, Modifier.fillMaxWidth(), enabled = !vm.busy, icon = Icons.Outlined.RemoveCircleOutline)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable private fun Metric(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable internal fun StatusBadge(value: String) {
    val (text, color) = when(value) { "ok" -> "正常" to MaterialTheme.colorScheme.primary; "low" -> "电量偏低" to Color(0xFF996325); "stale" -> "数据过期" to Color(0xFF996325); "needs_rebind" -> "学校授权失效" to MaterialTheme.colorScheme.error; "not_configured" -> "未配置" to Color(0xFF996325); else -> "查询中" to MaterialTheme.colorScheme.secondary }
    Surface(shape = CircleShape, color = color.copy(alpha = .12f)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = if (LocalDormDark.current) MaterialTheme.colorScheme.onSurface else color, style = MaterialTheme.typography.labelMedium)
    }
}
internal fun formatTime(value: String) = try { DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai")).format(Instant.parse(value)) } catch (_: Exception) { "--" }
