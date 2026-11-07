package fr.kdns.weiyun.dormmonitor

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject

/** Uses real launcher/widget intents with isolated account and dorm responses. */
class LaunchRoutingVerification(private val instrumentation: Instrumentation) {
    fun run() = with(instrumentation) {
        val previous = SessionStore.get(targetContext)
        try {
            SessionStore.clear(targetContext)
            fun launcher() = Intent(targetContext, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            var activity = startActivitySync(launcher()) as MainActivity
            val app = activity.application
            val api = FixtureApi(false, "", 0)
            lateinit var vm: MonitorModel
            var checks = 0
            fun expect(value: Boolean, message: String) { check(value) { message }; checks++ }
            fun awaitState(message: String, condition: () -> Boolean) {
                repeat(200) {
                    var ready = false
                    runOnMainSync { ready = condition() }
                    if (ready) { waitForIdleSync(); return }
                    Thread.sleep(25)
                }
                error(message)
            }
            fun launchFromHome() {
                sendKeyDownUpSync(KeyEvent.KEYCODE_HOME)
                awaitState("Home did not take focus") { !activity.hasWindowFocus() }
                Thread.sleep(250)
                // Shell launch carries the same foreground authority as a launcher tap.
                val output = android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(
                    "am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n fr.kdns.weiyun.dormmonitor/.MainActivity"
                )).bufferedReader().use { it.readText() }
                check("Error:" !in output) { output }
            }
            runOnMainSync {
                activity.viewModelStore.clear()
                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        MonitorModel(app, api, UpdateSource { null }) as T
                }
                vm = ViewModelProvider(activity, factory)[MonitorModel::class.java]
                expect(vm.page == "home", "Cold launcher did not open overview")
                activity.setContent { DormTheme { MonitorScreen(vm) } }
                vm.auth("design_fixture", "unused_fixture", false)
            }
            awaitState("Fixture login did not finish") { !vm.busy && vm.dorms.isNotEmpty() }
            fun dormRequests() = api.requests.filterKeys { it.startsWith("/api/dorms") }.values.sum()
            val requestCount = dormRequests()
            for (page in listOf("detail", "about", "appearance", "settings")) {
                runOnMainSync { if (page == "detail") vm.detail(vm.dorms.first()) else vm.page = page }
                launchFromHome()
                awaitState("Launcher retained $page") { vm.page == "home" }
                runOnMainSync {
                    expect(ViewModelProvider(activity)[MonitorModel::class.java] === vm, "Launcher created a duplicate model")
                    expect(vm.selected == null, "Launcher retained a selected dorm")
                }
            }
            runOnMainSync { expect(dormRequests() == requestCount, "Opening overview made extra dorm requests") }
            targetContext.startActivity(Intent(targetContext, MainActivity::class.java)
                .putExtra("dorm_id", "fixture-0")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            awaitState("Widget did not open its dorm") { vm.page == "detail" && vm.selected?.optString("id") == "fixture-0" }
            runOnMainSync { expect(!activity.intent.hasExtra("dorm_id"), "Widget extra was not consumed") }

            val monitor = addMonitor(MainActivity::class.java.name, null, false)
            runOnMainSync { vm.page = "about"; activity.recreate() }
            activity = waitForMonitorWithTimeout(monitor, 5000) as? MainActivity ?: error("Activity did not recreate")
            removeMonitor(monitor)
            awaitState("Recreation replaced retained model") { ViewModelProvider(activity)[MonitorModel::class.java] === vm }
            runOnMainSync { expect(vm.page == "about", "Recreation replayed the consumed widget intent") }
            launchFromHome()
            awaitState("Launcher after recreation did not open overview") { vm.page == "home" }

            SessionStore.save(targetContext, "dorm_monitor_session=isolated_launch_fixture")
            val gate = CompletableDeferred<Unit>()
            val delayedApi = object : DormApi {
                override suspend fun call(path: String, method: String, body: JSONObject?): JSONObject {
                    if (path == "/api/session") {
                        gate.await()
                        return JSONObject().put("role", "user").put("username", "launch_fixture")
                    }
                    return api.call(path, method, body)
                }
            }
            lateinit var restoring: MonitorModel
            runOnMainSync {
                restoring = MonitorModel(app, delayedApi, UpdateSource { null })
                restoring.openDorm("fixture-0")
                restoring.openOverview()
            }
            gate.complete(Unit)
            awaitState("Delayed restore did not finish") { !restoring.busy }
            runOnMainSync {
                expect(restoring.page == "home" && restoring.selected == null, "Pending widget destination overrode launcher")
            }
            runOnMainSync { activity.finish() }
            finish(0, Bundle().apply { putString("result", "$checks launch routing checks passed") })
        } catch (e: Throwable) {
            finish(1, Bundle().apply { putString("error", e.stackTraceToString()) })
        } finally {
            SessionStore.clear(targetContext)
            if (previous.isNotEmpty()) SessionStore.save(targetContext, previous)
        }
    }
}
