package fr.kdns.weiyun.dormmonitor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.extra.SuperDialog

private data class MainDestination(val title: String, val icon: ImageVector)

private fun mainDestination(page: String) = when (page) {
    "catalog" -> MainDestination("添加", Icons.Outlined.AddCircleOutline)
    "settings" -> MainDestination("设置", Icons.Outlined.Tune)
    else -> MainDestination("宿舍", Icons.Outlined.Cottage)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ViewModelProvider(this)[MonitorModel::class.java].openDorm(intent.getStringExtra("dorm_id"))
        setContent { DormTheme { MonitorScreen() } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        ViewModelProvider(this)[MonitorModel::class.java].openDorm(intent.getStringExtra("dorm_id"))
    }
    override fun onResume() {
        super.onResume()
        ViewModelProvider(this)[MonitorModel::class.java].continueInstallAfterPermission()
    }
}

@Composable fun MonitorScreen(vm: MonitorModel = viewModel()) {
    if (vm.sessionStartup != SessionStartup.READY) {
        SessionStartupScreen(vm)
        return
    }
    var json by remember { mutableStateOf("") }
    var appearanceOrigin by remember { mutableStateOf("home") }
    val confirmRemove = remember { mutableStateOf(false) }
    val confirmAuth = remember { mutableStateOf(false) }
    val updateDialog = remember { mutableStateOf(false) }
    LaunchedEffect(vm.updateState.release) { if (vm.updateState.release != null) updateDialog.value = true }
    val overviewScroll = rememberLazyListState()
    val backdropLayer = rememberGraphicsLayer()
    val backdrop = remember(backdropLayer) { NavigationBackdrop(backdropLayer) }
    val glass = LocalAppearance.current.glass
    val loggedIn = vm.role.isNotEmpty()
    val destination = mainDestination(vm.page)
    val subPage = vm.page in listOf("detail", "widget", "about", "register", "appearance", "threshold", "authorization")
    fun appearance() { appearanceOrigin = vm.page; vm.clearError(); vm.page = "appearance" }
    fun back() {
        vm.clearError()
        vm.page = when (vm.page) {
            "appearance" -> appearanceOrigin
            "widget", "threshold", "authorization" -> "settings"
            "about" -> if (loggedIn) "settings" else "home"
            else -> "home"
        }
    }
    BackHandler(vm.page != "home") { if (!vm.busy || vm.page == "detail") back() }
    MiuixScaffold(topBar = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (subPage) IconButton(onClick = ::back, enabled = !vm.busy || vm.page == "detail") { Icon(Icons.Outlined.ArrowBack, "返回") }
            else if (loggedIn) Icon(destination.icon, null, Modifier.padding(end = 12.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary)
            else BrandMark(Modifier.padding(end = 12.dp).size(28.dp))
            Text(when (vm.page) { "register" -> "注册账号"; "appearance" -> "主题与外观"; "widget" -> "桌面小组件"; "threshold" -> "低电量提醒"; "authorization" -> "学校查询授权"; "detail" -> vm.selected?.optString("title") ?: "宿舍详情"; "about" -> stringResource(R.string.about_app, stringResource(R.string.app_name)); else -> if (loggedIn) destination.title else stringResource(R.string.app_name) }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (vm.page != "appearance") IconButton(onClick = ::appearance) { Icon(Icons.Outlined.Palette, "主题与外观", tint = MaterialTheme.colorScheme.primary) }
        }
    }, bottomBar = {
        if (loggedIn && !subPage) DormNavigation(vm, backdrop)
    }) { padding ->
        val underBar = loggedIn && !subPage && vm.page in listOf("home", "settings")
        val contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = if (underBar) 0.dp else padding.calculateBottomPadding())
        Box(Modifier.fillMaxSize().navigationBackdrop(backdrop, glass && loggedIn && !subPage).background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(padding).imePadding().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (vm.error.isNotEmpty()) Feedback(vm.error, true)
            if (vm.notice.isNotEmpty()) Feedback(vm.notice, false)
            when {
                vm.page == "appearance" -> AppearanceScreen()
                vm.page == "about" -> AboutScreen(vm)
                !loggedIn -> AuthPage(vm, vm.page == "register")
                else -> when (vm.page) {
                    "home" -> HomePage(vm, overviewScroll, padding.calculateBottomPadding())
                    "catalog" -> ImportPage(vm)
                    "detail" -> DetailPage(vm) { confirmRemove.value = true }
                    "settings" -> AccountSettings(vm, ::appearance, padding.calculateBottomPadding())
                    "threshold" -> ThresholdPage(vm)
                    "authorization" -> AuthorizationPage(json, { json = it }, vm.busy) { confirmAuth.value = true }
                    "widget" -> WidgetSettings(onBack = { vm.page = "settings" }, dorms = vm.dorms, showBack = false)
                }
            }
        }
        // Overlay loading instead of inserting a row and shifting the entire page.
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = padding.calculateTopPadding()).height(2.dp), trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        }
    }
    SuperDialog(show = confirmRemove, title = "移除宿舍", summary = "将从你的列表中移除，不影响其他用户和历史记录。", onDismissRequest = { confirmRemove.value = false }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { AppButton("取消", { confirmRemove.value = false }, Modifier.weight(1f)); AppButton("移除", { vm.selected?.let(vm::delete); confirmRemove.value = false }, Modifier.weight(1f), primary = true) }
    }
    SuperDialog(show = confirmAuth, title = "更新学校授权", summary = "所有宿舍将使用新的登录态继续查询。", onDismissRequest = { confirmAuth.value = false }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { AppButton("取消", { confirmAuth.value = false }, Modifier.weight(1f)); AppButton("更新", { vm.updateGlobalAuth(json) { json = "" }; confirmAuth.value = false }, Modifier.weight(1f), primary = true) }
    }
    SuperDialog(show = updateDialog, title = "发现新版本 ${vm.updateState.release?.version.orEmpty()}",
        onDismissRequest = { updateDialog.value = false }) {
        val update = vm.updateState
        if (update.downloading) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(progress = { update.downloadProgress / 100f }, Modifier.fillMaxWidth())
                Text("正在下载 ${update.downloadProgress}%")
            }
        } else if (update.downloadedFile != null) {
            Text(update.message)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton("稍后", { updateDialog.value = false }, Modifier.weight(1f))
                AppButton("安装更新", { vm.installDownloadedUpdate() }, Modifier.weight(1f), primary = true, icon = Icons.Outlined.InstallMobile)
            }
        } else {
            if (update.message.isNotEmpty()) Text(update.message)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton("稍后", { updateDialog.value = false }, Modifier.weight(1f))
                AppButton("下载并安装", { vm.downloadUpdate() }, Modifier.weight(1f), primary = true, icon = Icons.Outlined.Download,
                    enabled = update.release?.apk != null)
            }
        }
    }
}

@Composable private fun SessionStartupScreen(vm: MonitorModel) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            BrandMark(Modifier.size(64.dp))
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            if (vm.sessionStartup == SessionStartup.RESTORING) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Text(vm.error.ifEmpty { "暂时无法连接，请稍后重试" }, style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                AppButton("重试", vm::restoreSession, icon = Icons.Outlined.Refresh)
            }
        }
    }
}

@Composable private fun Feedback(message: String, error: Boolean) {
    Surface(shape = RoundedCornerShape(18.dp), color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (error) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircleOutline, null, tint = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
            Text(message, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium,
                color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable private fun DormNavigation(vm: MonitorModel, backdrop: NavigationBackdrop) {
    val p = LocalAppearance.current
    val pages = if (p.importTab) listOf("home", "catalog", "settings") else listOf("home", "settings")
    // Keep the current import destination reachable if its tab is hidden while importing.
    val selected = pages.indexOf(vm.page).coerceAtLeast(0)
    val destinations = pages.map(::mainDestination)
    val items = destinations.map { NavigationItem(it.title, it.icon) }
    val onSelect: (Int) -> Unit = { index ->
        if (!vm.busy) when (pages[index]) {
            "home" -> { vm.clearError(); vm.page = "home" }
            "catalog" -> if (vm.page != "catalog") vm.choose()
            "settings" -> if (vm.page != "settings") vm.settings()
        }
    }
    Box(Modifier.fillMaxWidth()
        .then(if (p.floating) Modifier.navigationBarsPadding() else Modifier.background(MaterialTheme.colorScheme.surfaceContainer))
        .padding(horizontal = if (p.floating) 24.dp else 0.dp, vertical = if (p.floating) 6.dp else 0.dp),
        contentAlignment = Alignment.Center) {
        if (p.glass) {
            GlassNavigationBar(
                layer = backdrop.layer, layerOrigin = backdrop.origin,
                items = destinations.map { GlassNavigationItem(it.title, it.icon) },
                selected = selected, onSelect = onSelect,
                modifier = Modifier.then(if (p.floating) Modifier else Modifier.navigationBarsPadding()),
                enabled = !vm.busy, floating = p.floating
            )
        } else {
            val inset = if (p.floating) 6.dp else 4.dp
            Box(Modifier.clip(RoundedCornerShape(if (p.floating) 40.dp else 0.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .then(if (p.floating) Modifier else Modifier.navigationBarsPadding()).padding(horizontal = inset)) {
                Row(Modifier.matchParentSize().padding(vertical = inset), verticalAlignment = Alignment.CenterVertically) {
                    pages.forEachIndexed { index, _ ->
                        Box(Modifier.weight(1f).fillMaxHeight().padding(2.dp).clip(RoundedCornerShape(32.dp))
                            .background(if (index == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent))
                    }
                }
                MiuixNavigationBar(items = items, selected = selected, modifier = Modifier.padding(top = inset * 2),
                    color = Color.Transparent, showDivider = false, defaultWindowInsetsPadding = false, onClick = onSelect)
            }
        }
    }
}
