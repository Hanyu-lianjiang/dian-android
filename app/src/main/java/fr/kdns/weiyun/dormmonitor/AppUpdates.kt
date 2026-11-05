package fr.kdns.weiyun.dormmonitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

internal const val UPDATE_REPOSITORY = "Hanyu-lianjiang/dianshi-android"
data class AppRelease(val version: String, val page: String, val apk: String?)
internal data class UpdateState(val checking: Boolean = false, val message: String = "", val release: AppRelease? = null)
fun interface UpdateSource { suspend fun latest(): AppRelease? }

/** Compare stable version tags only; a prerelease must never replace a stable build. */
internal fun compareAppVersions(candidate: String, installed: String): Int? {
    fun parts(value: String): List<Int>? {
        val core = value.trim().removePrefix("v").removePrefix("V")
        if (!core.matches(Regex("[0-9]+(?:\\.[0-9]+){1,3}(?:\\+[A-Za-z0-9.-]+)?"))) return null
        return core.substringBefore('+').split('.').map { it.toIntOrNull() ?: return null }
    }
    val next = parts(candidate) ?: return null
    val current = parts(installed) ?: return null
    for (i in 0 until maxOf(next.size, current.size)) {
        val comparison = (next.getOrElse(i) { 0 }).compareTo(current.getOrElse(i) { 0 })
        if (comparison != 0) return comparison
    }
    return 0
}

internal fun releaseUrlAllowed(value: String, asset: Boolean): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.port == -1 &&
        uri.rawPath.startsWith("/$UPDATE_REPOSITORY/releases/${if (asset) "download/" else "tag/"}") &&
        !uri.rawPath.contains("..") && !uri.rawPath.contains('%') &&
        (!asset || uri.path.endsWith(".apk", ignoreCase = true))
}.getOrDefault(false)

internal class GitHubUpdates : UpdateSource {
    override suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        val connection = URL("https://api.github.com/repos/$UPDATE_REPOSITORY/releases/latest").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12_000; connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Dianshi/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            when (connection.responseCode) {
                404 -> return@withContext null
                403, 429 -> error("检查过于频繁，请稍后重试")
                200 -> Unit
                else -> error("暂时无法检查更新，请稍后重试")
            }
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            check(!release.optBoolean("draft") && !release.optBoolean("prerelease")) { "暂时无法读取正式版本" }
            val version = release.getString("tag_name")
            check(compareAppVersions(version, BuildConfig.VERSION_NAME) != null) { "版本信息暂不可识别，请稍后重试" }
            val page = release.getString("html_url")
            check(releaseUrlAllowed(page, false)) { "更新地址无效，请稍后重试" }
            val assets = release.optJSONArray("assets")
            val urls = (0 until (assets?.length() ?: 0)).mapNotNull { assets?.optJSONObject(it)?.optString("browser_download_url") }
                .filter { releaseUrlAllowed(it, true) }
            val apk = urls.firstOrNull { it.substringAfterLast('/').startsWith("dianshi-", true) }
                ?: urls.firstOrNull { it.contains("universal", true) } ?: urls.singleOrNull()
            AppRelease(version, page, apk)
        } finally { connection.disconnect() }
    }
}
