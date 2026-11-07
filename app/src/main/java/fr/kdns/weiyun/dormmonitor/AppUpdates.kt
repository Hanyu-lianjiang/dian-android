package fr.kdns.weiyun.dormmonitor

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.io.File
import java.security.MessageDigest

internal const val UPDATE_REPOSITORY = "Hanyu-lianjiang/dian-android"
data class AppRelease(val version: String, val page: String, val apk: String?, val versionCode: Long? = null, val sha256: String? = null)
internal data class UpdateState(val checking: Boolean = false, val message: String = "", val release: AppRelease? = null,
    val downloading: Boolean = false, val downloadProgress: Int = 0, val downloadedFile: String? = null)
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
internal fun releaseMetadataUrlAllowed(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.port == -1 &&
        uri.rawPath.startsWith("/$UPDATE_REPOSITORY/releases/download/") && uri.rawPath.endsWith("/update.json") &&
        !uri.rawPath.contains("..") && !uri.rawPath.contains('%')
}.getOrDefault(false)

internal fun releaseIsNewer(release: AppRelease, installedVersion: String, installedCode: Long): Boolean =
    release.versionCode?.let { it > installedCode } ?: (compareAppVersions(release.version, installedVersion)?.let { it > 0 } == true)

internal fun releaseTagMatchesBuild(tag: String, versionName: String, versionCode: Long): Boolean {
    val normalizedTag = tag.trim().removePrefix("v").removePrefix("V")
    return normalizedTag == versionName || normalizedTag == "$versionName.$versionCode"
}

internal suspend fun downloadReleaseApk(url: String, expectedSha256: String?, context: Context, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
    require(releaseUrlAllowed(url, true)) { "更新地址无效" }
    val directory = File(context.filesDir, "updates").apply { mkdirs() }
    val target = File(directory, "update.apk")
    val partial = File(directory, "update.apk.part")
    var connection: HttpURLConnection? = null
    try {
        var current = URL(url)
        repeat(6) { attempt ->
            connection = current.openConnection() as HttpURLConnection
            connection!!.instanceFollowRedirects = false
            connection!!.connectTimeout = 15_000
            connection!!.readTimeout = 30_000
            connection!!.setRequestProperty("User-Agent", "Dian/${BuildConfig.VERSION_NAME}")
            val status = connection!!.responseCode
            if (status in 300..399) {
                val next = URI(current.toString()).resolve(connection!!.getHeaderField("Location")).toURL()
                require(next.protocol == "https" && next.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com", "githubusercontent.com")) { "下载服务器地址无效" }
                connection!!.disconnect(); connection = null; current = next
                if (attempt == 5) error("下载地址重定向过多")
            } else {
                check(status == HttpURLConnection.HTTP_OK) { "下载失败（HTTP $status）" }
                val total = connection!!.contentLengthLong
                var readTotal = 0L
                val sha = MessageDigest.getInstance("SHA-256")
                connection!!.inputStream.use { input -> partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        output.write(buffer, 0, count); sha.update(buffer, 0, count); readTotal += count
                        if (total > 0) onProgress(((readTotal * 100 / total).toInt()).coerceIn(0, 99))
                        require(readTotal <= 200L * 1024 * 1024) { "安装包超过允许大小" }
                    }
                } }
                check(readTotal > 0) { "下载文件为空" }
                val digest = sha.digest().joinToString("") { "%02x".format(it) }
                require(expectedSha256 == null || digest.equals(expectedSha256, ignoreCase = true)) { "安装包校验失败，请重新下载" }
                if (target.exists()) check(target.delete()) { "无法替换旧安装包" }
                check(partial.renameTo(target)) { "无法保存安装包" }
                onProgress(100)
                return@withContext target
            }
        }
        error("下载失败")
    } catch (e: Exception) { partial.delete(); throw e }
    finally { connection?.disconnect() }
}

internal fun verifiedInstallIntent(context: Context, apk: File): Intent {
    val packageManager = context.packageManager
    val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
    val info = packageManager.getPackageArchiveInfo(apk.absolutePath, flags) ?: error("安装包无法识别")
    require(info.packageName == context.packageName) { "安装包不属于此应用" }
    val current = packageManager.getPackageInfo(context.packageName, flags)
    val currentSignatures = if (Build.VERSION.SDK_INT >= 28) current.signingInfo?.apkContentsSigners else @Suppress("DEPRECATION") current.signatures
    val updateSignatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else @Suppress("DEPRECATION") info.signatures
    fun fingerprints(signatures: Array<android.content.pm.Signature>?) = signatures.orEmpty().map {
        MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
    }.toSet()
    require(currentSignatures != null && fingerprints(currentSignatures).isNotEmpty() && fingerprints(currentSignatures) == fingerprints(updateSignatures)) { "签名不匹配，请从官方发布页获取更新" }
    val updateCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    val installedCode = if (Build.VERSION.SDK_INT >= 28) current.longVersionCode else @Suppress("DEPRECATION") current.versionCode.toLong()
    require(updateCode > installedCode) { "此安装包不是更新版本" }
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
    return Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
}

internal class GitHubUpdates : UpdateSource {
    override suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        val connection = URL("https://api.github.com/repos/$UPDATE_REPOSITORY/releases/latest").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12_000; connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Dian/${BuildConfig.VERSION_NAME}")
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
            val rows = (0 until (assets?.length() ?: 0)).mapNotNull { assets?.optJSONObject(it) }
            val metadataUrl = rows.firstOrNull { it.optString("name") == "update.json" }?.optString("browser_download_url")
            val metadata = metadataUrl?.takeIf(::releaseMetadataUrlAllowed)?.let { assetUrl ->
                val assetConnection = URL(assetUrl).openConnection() as HttpURLConnection
                try {
                    assetConnection.connectTimeout = 12_000; assetConnection.readTimeout = 12_000
                    assetConnection.setRequestProperty("User-Agent", "Dian/${BuildConfig.VERSION_NAME}")
                    JSONObject(assetConnection.inputStream.bufferedReader().use { it.readText() })
                } finally { assetConnection.disconnect() }
            }
            val apkAsset = rows.firstOrNull { it.optString("name").startsWith("dian-", true) && it.optString("name").endsWith(".apk", true) }
                ?: rows.firstOrNull { it.optString("name").endsWith(".apk", true) }
            val apk = apkAsset?.optString("browser_download_url")?.takeIf { releaseUrlAllowed(it, true) }
            val versionCode = metadata?.optLong("versionCode")?.takeIf { it > 0 }
            val sha256 = metadata?.optString("sha256")?.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
            if (metadata != null) {
                check(metadata.optString("packageName") == BuildConfig.APPLICATION_ID) { "更新包信息与应用不匹配" }
                check(versionCode != null && sha256 != null) { "更新包校验信息缺失" }
                check(releaseTagMatchesBuild(version, metadata.optString("versionName"), versionCode)) { "更新版本信息不一致" }
            }
            AppRelease(version, page, apk, versionCode, sha256)
        } finally { connection.disconnect() }
    }
}
