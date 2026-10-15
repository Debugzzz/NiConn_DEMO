package com.niconn.update

import com.niconn.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** 检查更新的结果。 */
sealed interface UpdateResult {
    /** 有新版本可用。[releaseUrl] 为该版本的发布页。 */
    data class Available(val latestVersion: String, val releaseUrl: String) : UpdateResult

    /** 已是最新。 */
    data object UpToDate : UpdateResult

    /** 检查失败（网络不可达、无发布记录、解析失败等）。 */
    data class Failed(val reason: String) : UpdateResult
}

/**
 * 通过 GitHub 仓库页面检查更新。
 *
 * **刻意不使用 GitHub API**：未鉴权 API 按出口 IP 限流 60 次/小时，而移动网络下
 * 运营商共享出口经常已被耗尽（实机实测直接返回 403 rate limit exceeded）。
 * 因此改读两个不受该限额约束的网页端点：
 *
 * 1. 主路径：`/releases/latest` 会 302 到 `/releases/tag/<tag>`，从最终 URL 取标签；
 * 2. 兜底：`/tags.atom`（Atom 订阅源）里第一个条目标题即最新标签。
 *
 * 不引入任何网络依赖，使用 JDK 自带的 `HttpURLConnection`。
 */
object UpdateChecker {

    private const val REPO_WEB = "https://github.com/Debugzzz/NiConn_DEMO"
    private const val LATEST_RELEASE = "$REPO_WEB/releases/latest"
    private const val TAGS_ATOM = "$REPO_WEB/tags.atom"
    private const val TAG_URL_PREFIX = "$REPO_WEB/releases/tag/"
    private const val TAG = "UPDATE"

    /** 查询最新发布版本。 */
    suspend fun check(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val (tag, releaseUrl) = fetchLatestTag()
            AppLog.d(TAG, "检查更新：当前=$currentVersion 最新=$tag")
            if (isNewer(tag, currentVersion)) {
                UpdateResult.Available(tag, releaseUrl)
            } else {
                UpdateResult.UpToDate
            }
        } catch (e: Exception) {
            AppLog.d(TAG, "检查更新失败：${e::class.java.simpleName}: ${e.message}")
            UpdateResult.Failed(e.message ?: "网络请求失败")
        }
    }

    private fun fetchLatestTag(): Pair<String, String> =
        runCatching { latestReleaseTag() }.getOrNull() ?: tagsAtomLatestTag()

    /** 主路径：跟随 `/releases/latest` 的重定向。 */
    private fun latestReleaseTag(): Pair<String, String>? {
        val connection = openConnection(LATEST_RELEASE)
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val finalUrl = connection.url?.toString() ?: return null
            val tag = parseTagFromRedirect(finalUrl) ?: return null
            tag to finalUrl
        } finally {
            connection.disconnect()
        }
    }

    /** 兜底：Atom 订阅源中第一个条目的标题即最新标签。 */
    private fun tagsAtomLatestTag(): Pair<String, String> {
        val tag = parseAtomLatestTag(readText(TAGS_ATOM)) ?: error("未找到版本标签")
        return tag to "$TAG_URL_PREFIX$tag"
    }

    /** 从 `/releases/latest` 的最终 URL 中取标签名；非标签页返回 null。 */
    internal fun parseTagFromRedirect(finalUrl: String): String? =
        finalUrl.takeIf { it.startsWith(TAG_URL_PREFIX) }
            ?.removePrefix(TAG_URL_PREFIX)
            ?.trim('/')
            ?.takeIf { it.isNotEmpty() }

    /**
     * 从 Atom 订阅源取最新标签。真实结构（已按线上数据核对）：
     *
     *     <entry>
     *       <id>tag:github.com,2008:Repository/1325460914/v0.1.3</id>
     *       <link rel="alternate" type="text/html"
     *             href="https://github.com/Debugzzz/NiConn_DEMO/releases/tag/v0.1.3"/>
     *       <title>v0.1.3 — 检查更新</title>
     *
     * 注意 **不能从 `<title>` 取标签名**：附注标签（`git tag -a`）的标题是标签说明的
     * 首行（实测为 "v0.1.3 — 检查更新"），据此拼出的发布页 URL 会失效。
     * 因此优先取条目里的链接／id，二者都只含真正的标签名。
     */
    internal fun parseAtomLatestTag(atom: String): String? {
        val entry = Regex("<entry>[\\s\\S]*?</entry>").find(atom)?.value
        val candidates = listOfNotNull(
            entry?.let { Regex("/releases/tag/([^\"<]+)").find(it)?.groupValues?.get(1) },
            entry?.let { Regex("/Repository/\\d+/([^<]+)").find(it)?.groupValues?.get(1) },
            entry?.let { Regex("<title>([^<]+)</title>").find(it)?.groupValues?.get(1) },
        )
        return candidates.firstOrNull { it.isNotBlank() }?.trim()
    }

    private fun readText(url: String): String {
        val connection = openConnection(url)
        return try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) error("HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = 10_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "NiConn-DEMO")
    }

    /** 版本号比较；容忍 `v` 前缀与 `-rc1` 之类后缀。段数不同时缺位按 0 处理。 */
    internal fun isNewer(latest: String, current: String): Boolean {
        val a = parseVersion(latest)
        val b = parseVersion(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    /**
     * 从任意文本中取出版本号：`v0.1.2` / `0.1.3-rc1` / `v0.1.3 — 检查更新` → `[0, 1, 3]`。
     *
     * 用正则只抓形如 `0.1.3` 的数字段，避免标签说明等附加文字干扰。
     */
    internal fun parseVersion(raw: String): List<Int> {
        val match = VERSION_PATTERN.find(raw) ?: return listOf(0)
        return match.groupValues[1].split('.').map { it.toIntOrNull() ?: 0 }
    }

    private val VERSION_PATTERN = Regex("""v?(\d+(?:\.\d+)*)""", RegexOption.IGNORE_CASE)
}
