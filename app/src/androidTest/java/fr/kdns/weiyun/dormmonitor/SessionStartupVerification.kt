package fr.kdns.weiyun.dormmonitor

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONArray
import org.json.JSONObject

/** Exercises startup with isolated credentials and a gated session response. */
class SessionStartupVerification(private val instrumentation: Instrumentation) {
    fun run() = with(instrumentation) {
        val previous = SessionStore.get(targetContext)
        try {
            SessionStore.clear(targetContext)
            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val app = activity.application
            var checks = 0
            fun expect(condition: Boolean, message: String) { check(condition) { message }; checks++ }
            fun awaitSettled(vm: MonitorModel) {
                repeat(200) {
                    var busy = true
                    runOnMainSync { busy = vm.busy }
                    if (!busy) { waitForIdleSync(); return }
                    Thread.sleep(20)
                }
                error("Session startup did not settle")
            }
            fun screenText(): String {
                fun collect(node: AccessibilityNodeInfo?): String {
                    if (node == null) return ""
                    return node.text?.toString().orEmpty() + " " + (0 until node.childCount).joinToString(" ") { collect(node.getChild(it)) }
                }
                waitForIdleSync()
                uiAutomation.waitForIdle(100, 3000)
                return collect(uiAutomation.rootInActiveWindow)
            }
            for (scenario in listOf("valid", "unauthorized", "offline")) {
                SessionStore.save(targetContext, "dorm_monitor_session=isolated_startup_fixture")
                val gate = CompletableDeferred<Unit>()
                var sessionCalls = 0
                var offline = scenario == "offline"
                val api = object : DormApi {
                    override suspend fun call(path: String, method: String, body: JSONObject?): JSONObject {
                        if (path == "/api/session") {
                            sessionCalls++
                            gate.await()
                            if (scenario == "unauthorized") throw DormApiException(401, "登录已失效，请重新登录")
                            if (offline) throw java.io.IOException("isolated network failure")
                            return JSONObject().put("role", "user").put("username", "startup_fixture")
                        }
                        check(path.startsWith("/api/dorms")) { "Unexpected request: $path" }
                        return JSONObject().put("dorms", JSONArray())
                    }
                }
                lateinit var vm: MonitorModel
                runOnMainSync {
                    vm = MonitorModel(app, api, UpdateSource { null })
                    expect(vm.sessionStartup == SessionStartup.RESTORING, "Restore must be set before first frame")
                    activity.setContent { DormTheme { MonitorScreen(vm) } }
                }
                Thread.sleep(350)
                expect(screenText().let { "用户名" !in it && "密码" !in it && "登录" !in it }, "Login form flashed while restoring")
                gate.complete(Unit)
                awaitSettled(vm)
                runOnMainSync {
                    when (scenario) {
                        "valid" -> {
                            expect(vm.sessionStartup == SessionStartup.READY && vm.role == "user", "Saved session did not enter home")
                            expect(SessionStore.get(targetContext).isNotEmpty(), "Valid session was cleared")
                        }
                        "unauthorized" -> {
                            expect(vm.sessionStartup == SessionStartup.READY && vm.role.isEmpty(), "Rejected session did not return to login")
                            expect(SessionStore.get(targetContext).isEmpty(), "Rejected session was retained")
                        }
                        else -> {
                            expect(vm.sessionStartup == SessionStartup.RETRY, "Network error must offer retry")
                            expect(SessionStore.get(targetContext).isNotEmpty(), "Network error cleared session")
                        }
                    }
                }
                if (scenario == "offline") {
                    var text = screenText()
                    repeat(30) {
                        if ("重试" !in text) { Thread.sleep(100); text = screenText() }
                    }
                    expect("重试" in text && "用户名" !in text, "Network error exposed login instead of retry: $text")
                    offline = false
                    runOnMainSync { vm.restoreSession(); vm.restoreSession() }
                    awaitSettled(vm)
                    runOnMainSync { expect(vm.role == "user" && sessionCalls == 2, "Retry failed or duplicated requests") }
                }
                runOnMainSync { activity.setContent { } }
            }
            SessionStore.clear(targetContext)
            runOnMainSync {
                val vm = MonitorModel(app, object : DormApi {
                    override suspend fun call(path: String, method: String, body: JSONObject?): JSONObject = error("No session must not call API")
                }, UpdateSource { null })
                expect(vm.sessionStartup == SessionStartup.READY && vm.role.isEmpty(), "New installation must show login immediately")
            }
            activity.finish()
            finish(0, Bundle().apply { putString("result", "$checks startup checks passed") })
        } catch (e: Throwable) {
            finish(1, Bundle().apply { putString("error", e.stackTraceToString()) })
        } finally {
            SessionStore.clear(targetContext)
            if (previous.isNotEmpty()) SessionStore.save(targetContext, previous)
        }
    }
}
