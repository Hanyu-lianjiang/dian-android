package fr.kdns.weiyun.dormmonitor

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog

@Composable
internal fun AboutScreen(vm: MonitorModel) {
    val context = LocalContext.current
    LaunchedEffect(vm) { vm.loadFeedbackConfig() }
    val state = vm.updateState
    var openError by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(112.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(36.dp)), contentAlignment = Alignment.Center) {
                    BrandMark(Modifier.size(72.dp))
                }
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text(stringResource(R.string.app_tagline), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Heading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            AppCard {
                AppButton(if (state.checking) "正在检查…" else "检查更新", { openError = ""; vm.checkForUpdates() }, Modifier.fillMaxWidth(),
                    enabled = !state.checking, primary = true, icon = Icons.Outlined.SystemUpdate)
                if (state.message.isNotEmpty()) Text(state.message, style = MaterialTheme.typography.bodyMedium)
                state.release?.let { release ->
                    AppButton(if (release.apk != null) "下载更新" else "查看新版本", {
                        openError = if (runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.apk ?: release.page))) }.isSuccess) "" else "无法打开下载页面，请安装或启用浏览器后重试"
                    }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Download)
                }
                if (openError.isNotEmpty()) Text(openError, color = MaterialTheme.colorScheme.error)
            }
            AppCard(onClick = if (vm.qqGroup.isNotEmpty()) ({
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("QQ 反馈群", vm.qqGroup))
                scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar("已复制") }
            }) else null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    IconTile(Icons.Outlined.Forum, 48)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("QQ 反馈群", style = MaterialTheme.typography.titleMedium)
                        Text(when {
                            vm.qqGroup.isNotEmpty() -> vm.qqGroup
                            vm.feedbackLoading -> "正在读取…"
                            vm.feedbackError.isNotEmpty() -> vm.feedbackError
                            else -> "暂未设置"
                        }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (vm.qqGroup.isNotEmpty()) Icon(Icons.Outlined.ContentCopy, "复制群号", tint = MaterialTheme.colorScheme.primary)
                    else IconButton(onClick = { vm.loadFeedbackConfig(force = true) }, enabled = !vm.feedbackLoading) {
                        Icon(Icons.Outlined.Refresh, "刷新反馈群", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp)) { data ->
            Snackbar { Text(data.visuals.message, Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        }
    }
}

@Composable
fun WidgetSettings(onBack: () -> Unit, dorms: List<JSONObject> = emptyList(), showBack: Boolean = true) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val prefs = remember { context.getSharedPreferences(DormWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs, lifecycle) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener); lifecycle.addObserver(observer)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener); lifecycle.removeObserver(observer) }
    }
    val status = remember(revision) { prefs.getString("refresh_state", "等待首次同步") ?: "等待首次同步" }
    val lastSuccess = remember(revision) { prefs.getLong("last_success", 0) }
    val allowed = remember(revision) { Build.VERSION.SDK_INT < 31 || (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms() }
    val widgetIds = remember(revision) { AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, DormWidgetProvider::class.java)) }
    val choosingWidget = remember { mutableStateOf(false) }
    fun configure(id: Int) { context.startActivity(Intent(context, DormWidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (showBack) IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "返回") }
        IconTile(Icons.Outlined.Widgets, 72)
        AppButton("更换宿舍", onClick = { if (widgetIds.size == 1) configure(widgetIds.single()) else choosingWidget.value = true }, enabled = widgetIds.isNotEmpty(), modifier = Modifier.fillMaxWidth(), primary = true, icon = Icons.Outlined.SwapHoriz)
        if (widgetIds.isEmpty()) Text("尚未添加桌面小组件", color = MaterialTheme.colorScheme.onSurfaceVariant)
        top.yukonga.miuix.kmp.basic.Card {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("同步状态", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("刷新状态：$status")
                Text("最近同步：${widgetTime(lastSuccess)}")
                Text("定时刷新：${if (allowed) "已允许" else "未允许"}")
                AppButton("立即刷新", onClick = { context.sendBroadcast(Intent(context, DormWidgetProvider::class.java).setAction(DormWidgetProvider.ACTION_REFRESH)) }, modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.Sync)
            }
        }
        top.yukonga.miuix.kmp.basic.Card {
            if (Build.VERSION.SDK_INT >= 31 && !allowed) SettingsLink(Icons.Outlined.Schedule, "允许定时刷新") { runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))) } }
            SettingsLink(Icons.Outlined.Settings, "后台运行设置") { runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))) } }
        }
        Text("系统省电策略可能延迟刷新。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(20.dp))
    }
    SuperDialog(show = choosingWidget, onDismissRequest = { choosingWidget.value = false }, title = "选择要修改的小组件") {
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            widgetIds.forEachIndexed { index, id ->
                val selector = prefs.getString(DormWidgetProvider.PREF_SELECTOR_PREFIX + id, "")
                val dorm = dorms.find { it.optString("id") == selector || it.optString("title") == selector || it.optString("roomLabel") == selector }
                val title = dorm?.optString("title")?.takeIf { it.isNotBlank() }
                AppButton("小组件 ${index + 1}" + if (title == null) "" else " · $title", onClick = { choosingWidget.value = false; configure(id) }, modifier = Modifier.fillMaxWidth())
            }
        }
        AppButton("取消", onClick = { choosingWidget.value = false }, modifier = Modifier.fillMaxWidth())
    }
}
private fun widgetTime(ms: Long) = if (ms == 0L) "暂无记录" else DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai")).format(Instant.ofEpochMilli(ms))
