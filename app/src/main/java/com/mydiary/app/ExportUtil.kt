package com.mydiary.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据导出与备份。
 *
 * 设计原则：数据永远是用户的 —— 两种格式都能直接看到明文：
 *  - Markdown：可读、可丢进任何笔记软件
 *  - JSON：结构化，便于以后迁移到别的 App 或网页版
 */
object ExportUtil {

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(Date())

    /** 导出全部日记为 Markdown 并唤起系统分享 */
    fun exportMarkdownAndShare(ctx: Context, db: DiaryDbHelper) {
        val list = db.listAll()
        if (list.isEmpty()) {
            Toast.makeText(ctx, "还没有日记可以导出", Toast.LENGTH_SHORT).show()
            return
        }

        val sb = StringBuilder()
        sb.append("# 我的日记\n\n")
        sb.append("导出时间：").append(DateUtil.prettyFull(DateUtil.today())).append("\n\n")
        sb.append("共 ").append(list.size).append(" 篇\n\n---\n\n")

        // 按时间正序排列，读起来像一本书
        list.sortedBy { it.date }.forEach { d ->
            sb.append("## ").append(DateUtil.prettyFull(d.date)).append("\n\n")
            if (d.title.isNotBlank()) sb.append("**").append(d.title).append("**\n\n")

            val meta = buildList {
                if (d.mood.isNotEmpty()) add(DiaryDbHelper.moodEmoji(d.mood) + " " + DiaryDbHelper.moodLabel(d.mood))
                if (d.weather.isNotEmpty()) add(DiaryDbHelper.weatherEmoji(d.weather) + " " + DiaryDbHelper.weatherLabel(d.weather))
                if (d.tags.isNotEmpty()) add(d.tagList().joinToString(" ") { "#$it" })
            }
            if (meta.isNotEmpty()) sb.append("> ").append(meta.joinToString(" · ")).append("\n\n")

            sb.append(d.content).append("\n\n---\n\n")
        }

        val file = File(ctx.cacheDir, "exports").apply { mkdirs() }
            .resolve("我的日记_${stamp()}.md")
        runCatching { file.writeText(sb.toString(), Charsets.UTF_8) }
            .onSuccess { share(ctx, file, "text/markdown") }
            .onFailure { Toast.makeText(ctx, "导出失败：${it.message}", Toast.LENGTH_LONG).show() }
    }

    /** 备份为 JSON 并唤起分享（可存网盘/发给自己） */
    fun backupAndShare(ctx: Context, db: DiaryDbHelper) {
        val list = db.listAll()
        if (list.isEmpty()) {
            Toast.makeText(ctx, "还没有数据可以备份", Toast.LENGTH_SHORT).show()
            return
        }

        val arr = org.json.JSONArray()
        list.sortedBy { it.date }.forEach { d ->
            arr.put(org.json.JSONObject().apply {
                put("date", d.date)
                put("title", d.title)
                put("content", d.content)
                put("mood", d.mood)
                put("weather", d.weather)
                put("tags", d.tags)
                put("updated_at", d.updatedAt)
            })
        }
        val root = org.json.JSONObject().apply {
            put("app", "MyDiary")
            put("version", 1)
            put("exported_at", System.currentTimeMillis())
            put("count", list.size)
            put("entries", arr)
        }

        val file = File(ctx.cacheDir, "exports").apply { mkdirs() }
            .resolve("日记备份_${stamp()}.json")
        runCatching { file.writeText(root.toString(2), Charsets.UTF_8) }
            .onSuccess { share(ctx, file, "application/json") }
            .onFailure { Toast.makeText(ctx, "备份失败：${it.message}", Toast.LENGTH_LONG).show() }
    }

    private fun share(ctx: Context, file: File, mime: String) {
        val uri: Uri = FileProvider.getUriForFile(ctx, "com.mydiary.app.fileprovider", file)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "保存或分享").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
