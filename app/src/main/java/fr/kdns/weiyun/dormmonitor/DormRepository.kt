package fr.kdns.weiyun.dormmonitor

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class DormApiException(val status: Int, message: String) : Exception(message)

interface DormApi {
    suspend fun call(path: String, method: String = "GET", body: JSONObject? = null): JSONObject
}

class DormRepository(private val ctx: Context) : DormApi {
    override suspend fun call(path: String, method: String, body: JSONObject?): JSONObject =
        withContext(Dispatchers.IO) { request(path, method, body) }
    private fun request(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 15000; c.readTimeout = 15000
        c.setRequestProperty("Accept", "application/json")
        SessionStore.get(ctx).takeIf { it.isNotEmpty() }?.let { c.setRequestProperty("Cookie", it) }
        if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toString().toByteArray()) } }
        try {
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: "{}"
            val data = JSONObject(text)
            if (code !in 200..299) throw DormApiException(code, when(data.optString("error")) {
                "account_disabled" -> "账号已被禁用，请联系管理员"
                "import_forbidden" -> "当前账号暂不能添加宿舍，请联系管理员"
                "upstream_auth_expired" -> "学校授权已失效，请联系管理员更新后重试"
                "upstream_query_failed", "upstream_invalid_response" -> "学校服务暂时无法查询，请稍后重试"
                "selection_unavailable", "invalid_selection" -> "该宿舍选项已不可用，请重新选择"
                "dorm_not_found" -> "宿舍已移除，请刷新列表"
                "invalid_credentials" -> "用户名或密码错误，请重试"
                "unauthorized" -> "登录已失效，请重新登录"
                "too_many_attempts" -> "尝试次数过多，请稍后重试"
                "username_taken" -> "用户名已被使用"
                "invalid_credentials_format" -> "用户名须为 3–40 位字母、数字或下划线，密码须为 10–200 位"
                else -> "服务暂时不可用，请稍后重试"
            })
            c.getHeaderField("Set-Cookie")?.substringBefore(';')?.let { SessionStore.save(ctx, it) }
            return data
        } finally { c.disconnect() }
    }
    private companion object { const val BASE = "https://bill.weiyun.kdns.fr" }
}
