package fr.kdns.weiyun.dormmonitor

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.extra.SuperArrow

data class WidgetDorm(val id: String, val title: String, val location: String)

class WidgetConfigModel(app: Application) : AndroidViewModel(app) {
    var dorms by mutableStateOf<List<WidgetDorm>>(emptyList()); private set
    var selectedId by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf(""); private set
    var loginRequired by mutableStateOf(false); private set
    private var loadedSession = ""
    private var initialized = false
    private val ctx get() = getApplication<Application>()
    private val repository = DormRepository(app)

    fun load(widgetId: Int) {
        if (busy) return
        viewModelScope.launch {
            busy = true; error = ""; loginRequired = false
            try {
                val session = SessionStore.get(ctx)
                if (session.isEmpty()) throw DormApiException(401, "请先登录应用")
                val array = repository.call("/api/dorms?minutes=5&points=12").getJSONArray("dorms")
                if (session != SessionStore.get(ctx)) throw DormApiException(401, "账号已变更，请重新选择")
                val rows = (0 until array.length()).map { array.getJSONObject(it) }
                dorms = rows.map { WidgetDorm(it.getString("id"), it.optString("title"), dormLocation(it)) }
                if (!initialized || loadedSession != session) {
                    val old = ctx.getSharedPreferences(DormWidgetProvider.PREFS_NAME, 0).getString(DormWidgetProvider.PREF_SELECTOR_PREFIX + widgetId, "")
                    selectedId = rows.firstOrNull { it.optString("id") == old || it.optString("title") == old || it.optString("roomLabel") == old }?.getString("id")
                    initialized = true
                }
                if (dorms.none { it.id == selectedId }) selectedId = null
                loadedSession = session
            } catch (e: Exception) {
                dorms = emptyList(); selectedId = null
                loginRequired = e is DormApiException && e.status == 401
                error = if (e is DormApiException) e.message ?: "加载失败，请重试" else "宿舍列表加载失败，请检查网络后重试"
            } finally { busy = false }
        }
    }

    fun select(id: String) { if (!busy && dorms.any { it.id == id }) selectedId = id }
    fun save(widgetId: Int, onSaved: () -> Unit) {
        val id = selectedId ?: return
        if (busy || dorms.none { it.id == id }) return
        viewModelScope.launch {
            busy = true; error = ""
            try {
                if (loadedSession != SessionStore.get(ctx)) throw DormApiException(401, "账号已变更，请重新选择")
                val saved = withContext(Dispatchers.IO) {
                    ctx.getSharedPreferences(DormWidgetProvider.PREFS_NAME, 0).edit().putString(DormWidgetProvider.PREF_SELECTOR_PREFIX + widgetId, id).commit()
                }
                if (!saved) error = "保存失败，请重试" else onSaved()
            } catch (e: Exception) { error = e.message ?: "保存失败，请重试" }
            finally { busy = false }
        }
    }
}

class DormWidgetConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var model: WidgetConfigModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(RESULT_CANCELED)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        model = ViewModelProvider(this)[WidgetConfigModel::class.java]
        setContent {
            DormTheme {
                Scaffold(topBar = {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { finish() }, enabled = !model.busy) { Text("取消") }
                        Text("选择宿舍", style = MaterialTheme.typography.titleLarge)
                    }
                }, bottomBar = {
                    AppButton("保存", onClick = { model.save(widgetId) {
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                        DormWidgetProvider.requestImmediateRefresh(this@DormWidgetConfigActivity, widgetId)
                        finish()
                    } }, enabled = !model.busy && model.selectedId != null, primary = true, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp).height(52.dp))
                }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (model.error.isNotEmpty()) Text(model.error, color = MaterialTheme.colorScheme.error)
                        if (!model.busy && model.dorms.isEmpty() && model.error.isEmpty()) Text("当前账号尚未选择宿舍")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (model.loginRequired || model.dorms.isEmpty()) AppButton(if (model.loginRequired) "登录应用" else "添加宿舍", onClick = { startActivity(Intent(this@DormWidgetConfigActivity, MainActivity::class.java)) })
                            AppButton("刷新", onClick = { model.load(widgetId) }, enabled = !model.busy)
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(model.dorms, key = { it.id }) { dorm ->
                                top.yukonga.miuix.kmp.basic.Card {
                                    SuperArrow(title = dorm.title, summary = dorm.location, rightText = if (model.selectedId == dorm.id) "已选择" else "", enabled = !model.busy, onClick = { model.select(dorm.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    override fun onResume() { super.onResume(); if (::model.isInitialized) model.load(widgetId) }
}

class RefreshSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DormTheme { Surface(Modifier.fillMaxSize()) { Box(Modifier.statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) { WidgetSettings(onBack = { finish() }) } } } }
    }
}
