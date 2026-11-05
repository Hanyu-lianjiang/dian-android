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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.extra.SuperDialog

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
}

@Composable fun MonitorScreen(vm: MonitorModel = viewModel()) {
    var json by remember { mutableStateOf("") }
    var appearanceOrigin by remember { mutableStateOf("home") }
    val confirmRemove = remember { mutableStateOf(false) }
    val confirmAuth = remember { mutableStateOf(false) }
    val overviewScroll = rememberLazyListState()
    val backdropLayer = rememberGraphicsLayer()
    val backdrop = remember(backdropLayer) { NavigationBackdrop(backdropLayer) }
    val glass = LocalAppearance.current.glass
    val loggedIn = vm.role.isNotEmpty()
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
            else { BrandMark(Modifier.padding(end = 12.dp).size(30.dp)) }
            Text(when (vm.page) { "register" -> "注册账号"; "appearance" -> "主题与外观"; "widget" -> "桌面小组件"; "threshold" -> "低电量提醒"; "authorization" -> "学校查询授权"; "detail" -> vm.selected?.optString("title") ?: "宿舍详情"; "about" -> "关于"; else -> "电示" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (vm.page != "appearance") IconButton(onClick = ::appearance) { Icon(Icons.Outlined.Palette, "主题与外观", tint = MaterialTheme.colorScheme.primary) }
        }
    }, bottomBar = {
        if (loggedIn && !subPage) DormNavigation(vm, backdrop)
    }) { padding ->
        val underBar = loggedIn && !subPage && vm.page in listOf("home", "settings")
        val contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = if (underBar) 0.dp else padding.calculateBottomPadding())
        Box(Modifier.fillMaxSize().navigationBackdrop(backdrop, glass && loggedIn && !subPage)) {
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
    val items = pages.map { when (it) {
        "catalog" -> NavigationItem("添加", Icons.Outlined.AddCircleOutline)
        "settings" -> NavigationItem("设置", Icons.Outlined.Tune)
        else -> NavigationItem("宿舍", Icons.Outlined.Cottage)
    } }
    Box(Modifier.fillMaxWidth().then(if (p.floating) Modifier.navigationBarsPadding() else Modifier).padding(horizontal = if (p.floating) 24.dp else 0.dp, vertical = if (p.floating) 12.dp else 0.dp)) {
        GlassNavigationSurface(backdrop, p.glass, RoundedCornerShape(if (p.floating) 40.dp else 0.dp)) {
            val inset = if (p.floating) 6.dp else 4.dp
            Box(Modifier.then(if (p.floating) Modifier else Modifier.navigationBarsPadding()).padding(horizontal = inset)) {
                // Discrete layout positions avoid drag feedback or competing spring animations.
                Row(Modifier.matchParentSize().padding(vertical = inset), verticalAlignment = Alignment.CenterVertically) {
                    pages.forEachIndexed { index, _ ->
                        val active = index == selected
                        val capsule = RoundedCornerShape(32.dp)
                        Box(Modifier.weight(1f).fillMaxHeight().padding(2.dp).clip(capsule)
                            .background(if (active && p.glass) Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .8f), MaterialTheme.colorScheme.primaryContainer.copy(alpha = .44f)))
                                else Brush.verticalGradient(List(2) { if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent }))
                            .then(if (active && p.glass) Modifier.border(.7.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .4f), MaterialTheme.colorScheme.primary.copy(alpha = .08f))), capsule) else Modifier))
                    }
                }
                // The stock bar reserves extra space below its labels. Balance that space
                // above the items while keeping the outer bar height and touch regions stable.
                MiuixNavigationBar(items = items, selected = selected, modifier = Modifier.padding(top = inset * 2), color = Color.Transparent, showDivider = false, defaultWindowInsetsPadding = false,
                    onClick = { index -> if (!vm.busy) when (pages[index]) {
                        "home" -> { vm.clearError(); vm.page = "home" }
                        "catalog" -> if (vm.page != "catalog") vm.choose()
                        "settings" -> if (vm.page != "settings") vm.settings()
                    } })
            }
        }
    }
}
