package fr.kdns.weiyun.dormmonitor

import android.app.Application
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Signed, separate instrumentation APK. Fixtures never enter the distributed application. */
class VisualVerification : Instrumentation() {
    private lateinit var args: Bundle
    override fun onCreate(arguments: Bundle?) { args = arguments ?: Bundle(); start() }
    override fun onStart() {
        try {
            verifyAppearance()
            val intent = Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val activity = startActivitySync(intent) as MainActivity
            lateinit var vm: MonitorModel
            val api = FixtureApi(args.getString("page") == "empty", args.getString("feedback", ""), args.getString("delay", "0").toLong())
            runOnMainSync {
                val source = when (args.getString("update", "live")) {
                    "new" -> UpdateSource { AppRelease("v99.0.0", "https://github.com/$UPDATE_REPOSITORY/releases/tag/v99.0.0", "https://github.com/$UPDATE_REPOSITORY/releases/download/v99.0.0/dianshi-99.0.0.apk") }
                    "current" -> UpdateSource { AppRelease(BuildConfig.VERSION_NAME, "https://github.com/$UPDATE_REPOSITORY/releases/tag/v${BuildConfig.VERSION_NAME}", null) }
                    "empty" -> UpdateSource { null }
                    "failure" -> UpdateSource { throw java.io.IOException("Fixture network interruption") }
                    else -> GitHubUpdates()
                }
                vm = MonitorModel(activity.application as Application, api, source)
                if (args.getString("page") != "login") vm.auth("design_fixture", "unused_fixture", false)
            }
            fun settle() {
                repeat(100) {
                    waitForIdleSync()
                    var busy = false
                    runOnMainSync { busy = vm.busy || vm.importLoading || vm.thresholdLoading }
                    if (!busy) return
                    Thread.sleep(50)
                }
                error("Fixture request did not finish")
            }
            settle()
            if (args.getString("page") == "navigation") {
                runOnMainSync {
                    vm.settings(); check(!vm.busy && vm.page == "settings")
                    check(api.requests["/api/preferences"] == null) { "Settings tab fetched preferences" }
                    vm.choose(); check(!vm.busy && vm.importLoading)
                    vm.settings(); check(vm.page == "settings")
                    vm.choose()
                }
                settle()
                val campusRequests = api.requests.filterKeys { it.startsWith("/api/upstream/options") }.values.sum()
                check(campusRequests == 1) { "In-flight campus request was duplicated" }
                runOnMainSync {
                    vm.settings(); vm.choose(); check(!vm.busy && !vm.importLoading)
                    vm.chooseOption(vm.importOptions.first()); check(vm.importStep == 1)
                    vm.settings(); check(vm.page == "settings"); vm.choose(); check(vm.importStep == 1)
                }
                settle()
                check(api.requests.filterKeys { it.startsWith("/api/upstream/options") }.values.sum() == 2)
                runOnMainSync {
                    vm.chooseStep(0); check(!vm.importLoading && vm.importOptions.isNotEmpty())
                    vm.openThreshold(); check(!vm.busy && vm.thresholdLoading)
                    vm.settings(); check(vm.page == "settings")
                }
                settle()
                runOnMainSync { vm.openThreshold(); check(!vm.thresholdLoading && vm.threshold == "30.0"); vm.page = "home" }
                check(api.requests["/api/preferences"] == 1)
            }
            if (args.getString("page") == "confirmation") {
                runOnMainSync { vm.choose() }; settle()
                repeat(3) { runOnMainSync { vm.chooseOption(vm.importOptions.first()) }; settle() }
                check(vm.importStep == 3)
            }
            runOnMainSync {
                if (args.getString("page") != "login") {
                    when (args.getString("page", "home")) {
                        "settings" -> vm.settings()
                        "detail" -> vm.detail(vm.dorms.first())
                        "catalog" -> vm.choose()
                        "widget" -> vm.page = "widget"
                        "appearance" -> vm.page = "appearance"
                        "about" -> vm.page = "about"
                    }
                }
                val remote = RemoteViews(targetContext.packageName, R.layout.widget_dorm_electric)
                WidgetAppearance.apply(targetContext, remote)
                val nativeWidget = remote.apply(targetContext, FrameLayout(activity))
                check(nativeWidget.findViewById<TextView>(R.id.widget_remaining).currentTextColor == WidgetAppearance.accent(targetContext))
                activity.setContent { DormTheme {
                    val contentColor = LocalContentColor.current
                    val expected = MaterialTheme.colorScheme.onSurface
                    SideEffect { check(contentColor == expected) { "Unstyled content text" } }
                    MonitorScreen(vm)
                } }
            }
            waitForIdleSync()
            sendStatus(0, Bundle().apply { putString("result", "ready; appearance persistence, corrupt preferences and 30 contrast checks passed; ${args.getString("page")} checks passed") })
            // Leave the fixture visible for UIAutomator interaction and screenshots.
        } catch (e: Exception) { finish(1, Bundle().apply { putString("error", e.toString()) }) }
    }
    private fun verifyAppearance() {
        val prefs = context.getSharedPreferences("appearance-verification", 0)
        prefs.edit().clear().commit()
        val wanted = Appearance(mode = "dark", palette = "iris", custom = "AA4499", black = true, floating = false, importTab = false, compact = true, miniTrend = false, glass = false)
        wanted.save(prefs)
        check(Appearance.read(prefs) == wanted) { "Appearance persistence failed" }
        prefs.edit().putString("mode", "invalid").putString("palette", "invalid").putString("custom", "xxxxxx").commit()
        check(Appearance.read(prefs).let { it.mode == "system" && it.palette == "mist" && it.custom == "477582" })
        prefs.edit().putInt("mode", 1).commit()
        check(Appearance.read(prefs) == Appearance())
        var checks = 0
        paletteOptions.filter { it.id != "wallpaper" }.forEach { option ->
            listOf(false, true).forEach { dark ->
                val c = tonalScheme(option.color, dark)
                listOf(c.onBackground to c.background, c.onPrimary to c.primary, c.onPrimaryContainer to c.primaryContainer).forEach { (text, background) ->
                    check(ColorUtils.calculateContrast(text.toArgb(), background.toArgb()) >= 4.5) { "Insufficient contrast: ${option.id}, $dark" }; checks++
                }
            }
        }
        check(checks == 30)
        prefs.edit().clear().commit()
    }
}

private class FixtureApi(empty: Boolean, private val feedback: String, private val delay: Long) : DormApi {
    val requests = mutableMapOf<String, Int>()
    private val rooms = JSONArray().apply {
        (if (empty) emptyList() else listOf("L2-505" to 151.44, "6B-609" to 434.58, "9A-503" to 22.64)).forEachIndexed { index, (title, remaining) ->
            put(JSONObject().put("id", "fixture-$index").put("title", title).put("roomLabel", title).put("remaining", remaining)
                .put("meterTotal", 8974.53 + index * 250).put("sampleCount", 8552).put("delta24h", -4.95).put("lowThreshold", 30)
                .put("status", "ok").put("fetchedAt", Instant.now().toString())
                .put("location", JSONObject().put("campus", "苏州科技大学石湖校区").put("building", if (index == 0) "塔影下L2号楼" else "学生6组团B栋"))
                .put("history", JSONArray().apply { repeat(71) { sample -> put(JSONObject().put("ts", Instant.now().minusSeconds((70 - sample) * 1200L).toEpochMilli()).put("remaining", remaining + (70 - sample) * .07)) } }))
        }
    }
    override suspend fun call(path: String, method: String, body: JSONObject?): JSONObject {
        requests[path] = (requests[path] ?: 0) + 1
        if (path.startsWith("/api/upstream/options") || path == "/api/preferences") kotlinx.coroutines.delay(delay)
        return when {
        path == "/api/login" -> JSONObject().put("role", "admin").put("username", "design_fixture")
        path == "/api/preferences" -> JSONObject().put("lowThreshold", 30)
        path == "/api/client-config" -> JSONObject().put("qqGroup", feedback)
        path.startsWith("/api/upstream/options") -> JSONObject().put("options", JSONArray().apply {
            val options = when (android.net.Uri.parse(path).getQueryParameter("level")) {
                "1" -> listOf("7" to "7号楼")
                "2" -> listOf("201" to "7-201")
                else -> listOf("campus-jf" to "苏州科技大学江枫校区", "campus-sh" to "苏州科技大学石湖校区", "campus-tp" to "苏州科技大学天平校区")
            }
            options.forEach { (id, name) -> put(JSONObject().put("id", id).put("name", name)) }
        })
        path == "/api/imports" && method == "POST" -> {
            check(body?.getString("campus") == "campus-jf" && body.getString("building") == "7" && body.getString("room") == "201")
            JSONObject().put("ok", true)
        }
        path.startsWith("/api/dorms/") -> JSONObject().put("dorm", rooms.getJSONObject(0))
        else -> JSONObject().put("canImport", true).put("dorms", rooms)
        }
    }
}
