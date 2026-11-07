package fr.kdns.weiyun.dormmonitor

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.io.File

private fun org.json.JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }
private fun historyRows(dorm: JSONObject) = dorm.optJSONArray("history")?.objects().orEmpty()

class MonitorModel @JvmOverloads constructor(app: Application, private val repository: DormApi = DormRepository(app), private val updates: UpdateSource = GitHubUpdates()) : AndroidViewModel(app) {
    var role by mutableStateOf(""); private set
    var username by mutableStateOf(""); private set
    var page by mutableStateOf("home")
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf(""); private set
    var notice by mutableStateOf(""); private set
    var dorms by mutableStateOf<List<JSONObject>>(emptyList()); private set
    var overviewPoints by mutableStateOf<Map<String, List<TrendPoint>>>(emptyMap()); private set
    var catalog by mutableStateOf<List<JSONObject>>(emptyList()); private set
    var canImport by mutableStateOf(true); private set
    var importStep by mutableIntStateOf(0); private set
    var importOptions by mutableStateOf<List<JSONObject>>(emptyList()); private set
    var importChoices by mutableStateOf<List<JSONObject>>(emptyList()); private set
    var importLoading by mutableStateOf(false); private set
    var importError by mutableStateOf(""); private set
    var selected by mutableStateOf<JSONObject?>(null)
    var detailPoints by mutableStateOf<List<TrendPoint>>(emptyList()); private set
    var detailRange by mutableStateOf(TrendRange.DAY); private set
    var detailRefreshing by mutableStateOf(false); private set
    var detailError by mutableStateOf(""); private set
    var threshold by mutableStateOf("")
    var thresholdLoading by mutableStateOf(false); private set
    var thresholdError by mutableStateOf(""); private set
    internal var updateState by mutableStateOf(UpdateState()); private set
    private var waitingForInstallPermission = false
    var qqGroup by mutableStateOf(""); private set
    var feedbackLoading by mutableStateOf(false); private set
    var feedbackError by mutableStateOf(""); private set
    private var feedbackLoadedAt: Long? = null
    private val ctx get() = getApplication<Application>()
    private val historyCache = HistoryMemoryCache()
    private var detailJob: Job? = null
    private var detailRequest: Pair<String, TrendRange>? = null
    private var detailSerial = 0L
    private var sessionGeneration = 0L
    private var pendingDormId: String? = null
    private val importCache = LinkedHashMap<String, Pair<Long, List<JSONObject>>>()
    private var importJob: Job? = null
    private var importRequest: String? = null
    private var importSerial = 0L
    private var preferencesJob: Job? = null
    private var preferencesLoadedAt: Long? = null

    private fun resetDetails() {
        importSerial++; importJob?.cancel(); importJob = null; importRequest = null
        importCache.clear(); importChoices = emptyList(); importOptions = emptyList(); importStep = 0; importLoading = false; importError = ""
        preferencesJob?.cancel(); preferencesJob = null; preferencesLoadedAt = null
        threshold = ""; thresholdLoading = false; thresholdError = ""
        sessionGeneration++; detailSerial++; detailJob?.cancel(); detailJob = null; detailRequest = null
        historyCache.clear(); overviewPoints = emptyMap(); detailPoints = emptyList()
        selected = null; detailError = ""; detailRefreshing = false
    }
    private fun handleError(e: Exception): String {
        if (e is DormApiException && e.status == 401 && role.isNotEmpty()) {
            SessionStore.clear(ctx); resetDetails(); role = ""; username = ""; dorms = emptyList(); catalog = emptyList(); page = "home"; DormWidgetProvider.clearWidgets(ctx)
        }
        return if (e is DormApiException) e.message ?: "操作失败，请重试" else "连接失败，请检查网络后重试"
    }
    private fun run(action: suspend () -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true; error = ""; notice = ""
            try { action(); if (pendingDormId != null && role.isNotEmpty()) load() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = handleError(e) }
            finally { busy = false }
        }
    }
    private suspend fun call(path: String, method: String = "GET", body: JSONObject? = null) = repository.call(path, method, body)
    init {
        checkForUpdates()
        if (SessionStore.get(ctx).isNotEmpty()) run {
            val session = call("/api/session"); role = session.getString("role"); username = session.optString("username"); load()
        }
    }
    private suspend fun load() {
        val response = call("/api/dorms?minutes=1440&points=120")
        canImport = response.optBoolean("canImport", true)
        val rows = response.getJSONArray("dorms").objects()
        val points = withContext(Dispatchers.Default) { rows.associate { it.getString("id") to trendPoints(historyRows(it)) } }
        points.forEach { (id, data) -> historyCache.put(id, TrendRange.DAY, data) }
        overviewPoints = points; dorms = rows
        selected?.optString("id")?.let { id ->
            rows.find { it.optString("id") == id }?.let { selected = it; if (detailRange == TrendRange.DAY) detailPoints = points[id].orEmpty() }
        }
        pendingDormId?.let { id ->
            pendingDormId = null
            val dorm = rows.find { it.optString("id") == id }
            if (dorm != null) detail(dorm) else { page = "home"; error = "当前账号无法查看该宿舍，请重新选择小组件宿舍" }
        }
    }
    fun openDorm(id: String?) {
        if (id.isNullOrBlank()) return
        val dorm = dorms.find { it.optString("id") == id }
        if (role.isNotEmpty() && dorm != null) detail(dorm)
        else { pendingDormId = id; if (role.isNotEmpty() && !busy) run { load() } }
    }
    fun auth(user: String, pass: String, register: Boolean) = run {
        val session = call(if (register) "/api/register" else "/api/login", "POST", JSONObject().put("username", user).put("password", pass))
        resetDetails(); role = session.getString("role"); username = session.optString("username"); page = "home"; load()
    }
    fun refresh() = run { load() }
    fun choose() {
        if (busy) return
        page = "catalog"; clearError()
        if (canImport && importStep < 3) loadImportOptions()
    }
    private fun loadImportOptions(force: Boolean = false) {
        val keys = listOf("campus", "building", "room")
        val query = importChoices.mapIndexed { index, choice -> "&${keys[index]}=" + URLEncoder.encode(choice.getString("id"), "UTF-8") }.joinToString("")
        val path = "/api/upstream/options?level=$importStep$query"
        if (importJob?.isActive == true && importRequest == path) return
        importJob?.cancel()
        val serial = ++importSerial
        val cached = importCache[path]
        importOptions = cached?.second.orEmpty(); importError = ""
        if (!force && cached != null && SystemClock.elapsedRealtime() - cached.first < 300_000L) {
            importLoading = false; importRequest = null
            return
        }
        val generation = sessionGeneration
        importLoading = true; importRequest = path
        importJob = viewModelScope.launch {
            try {
                val options = call(path).getJSONArray("options").objects()
                if (serial != importSerial || generation != sessionGeneration) return@launch
                if (importCache.size >= 64) importCache.remove(importCache.keys.first())
                importCache[path] = SystemClock.elapsedRealtime() to options
                importOptions = options
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (serial == importSerial && generation == sessionGeneration) importError = handleError(e)
            } finally { if (serial == importSerial) { importLoading = false; importRequest = null } }
        }
    }
    fun chooseOption(option: JSONObject) {
        if (busy || importLoading || importStep >= 3 || option !in importOptions) return
        importChoices = importChoices.take(importStep) + option; importStep++; importOptions = emptyList(); error = ""
        importError = ""
        if (importStep < 3) loadImportOptions()
    }
    fun chooseStep(step: Int) {
        if (busy || step !in 0..2 || step > importChoices.size) return
        importChoices = importChoices.take(step); importStep = step; importOptions = emptyList()
        loadImportOptions()
    }
    fun retryImportOptions() { if (!busy && canImport && importStep < 3) loadImportOptions(force = true) }
    fun importSelected() = run {
        val payload = JSONObject()
        listOf("campus", "building", "room").forEachIndexed { index, key -> payload.put(key, importChoices[index].getString("id")) }
        call("/api/imports", "POST", payload)
        importChoices = emptyList(); importOptions = emptyList(); importStep = 0; page = "home"; load()
        ctx.sendBroadcast(Intent(ctx, DormWidgetProvider::class.java).setAction(DormWidgetProvider.ACTION_REFRESH))
    }

    fun detail(dorm: JSONObject, range: TrendRange = TrendRange.DAY) {
        val id = dorm.getString("id")
        selected = dorm; detailRange = range; page = "detail"; error = ""; detailError = ""
        val cached = historyCache.get(id, range)
        detailPoints = cached ?: when (range) {
            TrendRange.DAY -> overviewPoints[id] ?: trendPoints(historyRows(dorm))
            else -> emptyList()
        }
        if (historyCache.isFresh(id, range)) {
            detailSerial++; detailJob?.cancel(); detailRequest = null; detailRefreshing = false
            return
        }
        if (detailRequest == (id to range) && detailJob?.isActive == true) return
        detailJob?.cancel()
        detailRequest = id to range
        val serial = ++detailSerial
        val generation = sessionGeneration
        detailRefreshing = true
        detailJob = viewModelScope.launch {
            try {
                val dorm = call("/api/dorms/${URLEncoder.encode(id, "UTF-8")}?minutes=${range.minutes}&points=${range.points}").getJSONObject("dorm")
                val points = withContext(Dispatchers.Default) { trendPoints(historyRows(dorm)) }
                if (generation != sessionGeneration || serial != detailSerial) return@launch
                historyCache.put(id, range, points)
                if (page == "detail" && selected?.optString("id") == id && detailRange == range) {
                    selected = dorm
                    detailPoints = points
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == sessionGeneration && serial == detailSerial) {
                    val message = handleError(e)
                    if (role.isEmpty()) error = message else detailError = message
                }
            }
            finally { if (serial == detailSerial) { detailRefreshing = false; detailRequest = null } }
        }
    }
    fun settings() { if (!busy) { clearError(); page = "settings" } }
    fun openThreshold(force: Boolean = false) {
        page = "threshold"; clearError()
        if (preferencesJob?.isActive == true) return
        if (!force && preferencesLoadedAt?.let { SystemClock.elapsedRealtime() - it < 300_000L } == true) return
        val generation = sessionGeneration
        thresholdLoading = true; thresholdError = ""
        preferencesJob = viewModelScope.launch {
            try {
                val value = call("/api/preferences").getDouble("lowThreshold").toString()
                if (generation == sessionGeneration) { threshold = value; preferencesLoadedAt = SystemClock.elapsedRealtime() }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation == sessionGeneration) thresholdError = handleError(e) }
            finally { if (generation == sessionGeneration) thresholdLoading = false }
        }
    }
    fun clearError() { error = "" }
    fun loadFeedbackConfig(force: Boolean = false) {
        if (feedbackLoading || !force && feedbackLoadedAt?.let { SystemClock.elapsedRealtime() - it < 60_000L } == true) return
        feedbackLoading = true; feedbackError = ""
        viewModelScope.launch {
            try {
                val config = call("/api/client-config")
                val group = config.optString("qqGroup")
                require(group.isEmpty() || group.matches(Regex("[0-9]{5,12}")))
                qqGroup = group; feedbackLoadedAt = SystemClock.elapsedRealtime()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { feedbackError = "暂时无法读取反馈群，请稍后重试" }
            finally { feedbackLoading = false }
        }
    }
    fun checkForUpdates() {
        if (updateState.checking) return
        updateState = UpdateState(checking = true)
        viewModelScope.launch {
            updateState = try {
                val release = updates.latest()
                when {
                    release == null -> UpdateState(message = "暂未找到可用版本")
                    releaseIsNewer(release, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong()) ->
                        UpdateState(message = "发现新版本 ${release.version}", release = release)
                    else -> UpdateState(message = "已是最新版本")
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                UpdateState(message = if (e is IllegalStateException) e.message ?: "暂时无法检查更新，请稍后重试" else "无法连接更新服务，请检查网络后重试")
            }
        }
    }
    fun downloadUpdate() {
        val release = updateState.release ?: return
        val url = release.apk
        if (url == null) { updateState = updateState.copy(message = "该版本暂未提供安装包"); return }
        if (updateState.downloading) return
        updateState = updateState.copy(downloading = true, downloadProgress = 0, downloadedFile = null, message = "正在下载更新…")
        viewModelScope.launch {
            try {
                val file = downloadReleaseApk(url, release.sha256, ctx) { progress ->
                    updateState = updateState.copy(downloadProgress = progress)
                }
                updateState = updateState.copy(downloading = false, downloadProgress = 100, downloadedFile = file.absolutePath, message = "下载完成，安装包已通过校验")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                updateState = updateState.copy(downloading = false, message = e.message ?: "下载失败，请稍后重试")
            }
        }
    }
    fun installDownloadedUpdate() {
        val path = updateState.downloadedFile ?: return
        if (android.os.Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            waitingForInstallPermission = true
            runCatching {
                ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { updateState = updateState.copy(message = "请在系统设置中允许安装此应用下载的更新") }
            return
        }
        waitingForInstallPermission = false
        runCatching { ctx.startActivity(verifiedInstallIntent(ctx, File(path))) }
            .onFailure { updateState = updateState.copy(message = it.message ?: "无法启动系统安装器") }
    }
    fun continueInstallAfterPermission() {
        if (!waitingForInstallPermission) return
        waitingForInstallPermission = false
        if (android.os.Build.VERSION.SDK_INT >= 26 && ctx.packageManager.canRequestPackageInstalls()) installDownloadedUpdate()
        else updateState = updateState.copy(message = "未允许安装未知应用；可稍后返回此页面重试")
    }
    fun saveThreshold() = run { call("/api/preferences", "PUT", JSONObject().put("lowThreshold", threshold.toDouble())); preferencesLoadedAt = SystemClock.elapsedRealtime(); ctx.sendBroadcast(Intent(ctx, DormWidgetProvider::class.java).setAction(DormWidgetProvider.ACTION_REFRESH)); page = "home"; load() }
    fun updateGlobalAuth(json: String, onSaved: () -> Unit) = run {
        val bootstrap = try { JSONObject(json) } catch (_: Exception) { throw DormApiException(400, "授权 JSON 格式不正确，请检查后重试") }
        call("/api/admin/auth", "PUT", JSONObject().put("bootstrap", bootstrap))
        onSaved(); notice = "学校授权已更新"; load()
    }
    fun delete(d: JSONObject) = run { call("/api/selections/" + d.getString("id"), "DELETE"); historyCache.remove(d.getString("id")); detailSerial++; detailJob?.cancel(); detailRequest = null; detailRefreshing = false; selected = null; detailPoints = emptyList(); page = "home"; load(); ctx.sendBroadcast(Intent(ctx, DormWidgetProvider::class.java).setAction(DormWidgetProvider.ACTION_REFRESH)) }
    fun logout() = run { try { call("/api/logout", "POST") } finally { SessionStore.clear(ctx); resetDetails(); role = ""; username = ""; dorms = emptyList(); catalog = emptyList(); page = "home"; DormWidgetProvider.clearWidgets(ctx) } }
}
