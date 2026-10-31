package com.mydiary.app

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * 更新检查。
 *
 * 设计取舍：不做"每次启动都联网"的后台轮询 —— 日记是私密应用，能少一次联网就少一次。
 * 只在你主动点"检查更新"时请求一次 GitHub Releases 的公开 API，失败也不打扰你。
 *
 * 版本比较用的是「主版本.次版本.修订号」三段数字，比 BuildConfig.VERSION_NAME 更严格，
 * 避免 "1.10" 被误判成比 "1.9" 小。
 */
object UpdateChecker {

    // 发布仓库：改成你自己的仓库即可
    private const val OWNER = "sky1ine139"
    private const val REPO = "my-diary"

    private val io = Executors.newSingleThreadExecutor()

    /**
     * 检查是否有新版本。
     * @param onResult 主线程回调：有新版本时给 (最新版本号, 下载页地址, 更新说明)，否则给 null
     */
    fun check(onResult: (UpdateInfo?) -> Unit) {
        val current = BuildConfig.VERSION_NAME
        io.execute {
            var info: UpdateInfo? = null
            try {
                val url = URL("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "MyDiary-Android")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val o = JSONObject(body)
                    val tag = o.optString("tag_name").removePrefix("v").removePrefix("V")
                    val page = o.optString("html_url")
                    val notes = o.optString("body")
                    if (tag.isNotBlank() && isNewer(tag, current)) {
                        info = UpdateInfo(tag, page, notes, current)
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {
                // 没网、被墙、限流——都不该打扰用户，当作"暂时检查不了"
            }
            val result = info
            android.os.Handler(android.os.Looper.getMainLooper()).post { onResult(result) }
        }
    }

    /** 语义化版本比较：remote 比 local 新则返回 true */
    fun isNewer(remote: String, local: String): Boolean {
        val r = parse(remote) ?: return false
        val l = parse(local) ?: return false
        for (i in 0 until 3) {
            if (r[i] > l[i]) return true
            if (r[i] < l[i]) return false
        }
        return false
    }

    private fun parse(v: String): IntArray? {
        val cleaned = v.trim().removePrefix("v").removePrefix("V")
            .substringBefore("-").substringBefore("+")
        val parts = cleaned.split(".")
        if (parts.isEmpty()) return null
        val nums = IntArray(3)
        for (i in 0 until 3) {
            nums[i] = parts.getOrNull(i)?.toIntOrNull() ?: 0
        }
        return nums
    }

    data class UpdateInfo(
        val version: String,
        val pageUrl: String,
        val notes: String,
        val currentVersion: String
    )
}
