package com.mydiary.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 一篇日记。
 *
 * @param date     日记归属日期，格式 "yyyy-MM-dd"，一天一篇
 * @param title    标题，可为空（列表里会回退显示正文首行）
 * @param content  正文
 * @param mood     心情标识，如 "happy"，空串表示未记录
 * @param weather  天气标识，如 "sunny"，空串表示未记录
 * @param tags     标签，逗号分隔存储，如 "工作,读书"
 * @param updatedAt 最后修改时间戳（毫秒）
 */
data class Diary(
    val id: Long = 0,
    val date: String,
    val title: String = "",
    val content: String = "",
    val mood: String = "",
    val weather: String = "",
    val tags: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** 列表标题：没写标题时，用正文第一行非空内容兜底 */
    fun displayTitle(): String {
        if (title.isNotBlank()) return title
        val first = content.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""
        return if (first.isEmpty()) "无标题日记" else first.take(40)
    }

    /** 列表摘要：正文去掉首行后的前若干字 */
    fun preview(): String {
        val lines = content.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return ""
        val body = if (title.isNotBlank()) lines.joinToString(" ") else lines.drop(1).joinToString(" ")
        return body.trim().replace("\n", " ").take(80)
    }

    fun tagList(): List<String> =
        tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** 用于全文搜索的关键词匹配（标题 + 正文 + 标签） */
    fun matches(keyword: String): Boolean {
        val k = keyword.trim()
        if (k.isEmpty()) return true
        return title.contains(k, true) || content.contains(k, true) || tags.contains(k, true)
    }
}

class DiaryDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "diary.db"
        private const val DB_VERSION = 1
        private const val TABLE = "diary"
        private const val DRAFT_TABLE = "draft"

        // 心情与天气的标识 -> 展示文案与表情，集中在这里方便以后增删
        val MOODS: List<Triple<String, String, String>> = listOf(
            Triple("happy", "开心", "😄"),
            Triple("calm", "平静", "🙂"),
            Triple("tired", "疲惫", "😪"),
            Triple("sad", "难过", "😔"),
            Triple("angry", "生气", "😠"),
            Triple("excited", "兴奋", "🤩")
        )
        val WEATHERS: List<Triple<String, String, String>> = listOf(
            Triple("sunny", "晴", "☀️"),
            Triple("cloudy", "云", "⛅"),
            Triple("overcast", "阴", "☁️"),
            Triple("rain", "雨", "🌧️"),
            Triple("snow", "雪", "❄️"),
            Triple("wind", "风", "🌬️")
        )

        fun moodLabel(key: String): String = MOODS.firstOrNull { it.first == key }?.second ?: ""
        fun moodEmoji(key: String): String = MOODS.firstOrNull { it.first == key }?.third ?: ""
        fun weatherLabel(key: String): String = WEATHERS.firstOrNull { it.first == key }?.second ?: ""
        fun weatherEmoji(key: String): String = WEATHERS.firstOrNull { it.first == key }?.third ?: ""
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL UNIQUE,
                title TEXT NOT NULL DEFAULT '',
                content TEXT NOT NULL DEFAULT '',
                mood TEXT NOT NULL DEFAULT '',
                weather TEXT NOT NULL DEFAULT '',
                tags TEXT NOT NULL DEFAULT '',
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_diary_date ON $TABLE(date)")
        // 草稿表：正在写、还没点保存的内容。与正式表分开，
        // 这样误操作不会污染日记，恢复草稿时也能明确判断有没有未保存内容。
        db.execSQL(
            """
            CREATE TABLE $DRAFT_TABLE (
                date TEXT PRIMARY KEY,
                title TEXT NOT NULL DEFAULT '',
                content TEXT NOT NULL DEFAULT '',
                mood TEXT NOT NULL DEFAULT '',
                weather TEXT NOT NULL DEFAULT '',
                tags TEXT NOT NULL DEFAULT '',
                saved_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 第一版暂无升级逻辑；后续版本在此按 oldVersion 增量迁移，避免丢数据
    }

    fun upsert(diary: Diary): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("date", diary.date)
            put("title", diary.title)
            put("content", diary.content)
            put("mood", diary.mood)
            put("weather", diary.weather)
            put("tags", diary.tags)
            put("updated_at", System.currentTimeMillis())
        }
        val existing = findByDate(diary.date)
        return if (existing == null) {
            db.insert(TABLE, null, values)
        } else {
            values.put("id", existing.id)
            db.update(TABLE, values, "id = ?", arrayOf(existing.id.toString()))
            existing.id
        }
    }

    fun deleteByDate(date: String) {
        writableDatabase.delete(TABLE, "date = ?", arrayOf(date))
        clearDraft(date)
    }

    // ---------------- 草稿 ----------------

    /** 保存草稿（未点"保存"时自动调用，防止切走/杀进程丢字） */
    fun saveDraft(d: Diary) {
        val values = ContentValues().apply {
            put("date", d.date)
            put("title", d.title)
            put("content", d.content)
            put("mood", d.mood)
            put("weather", d.weather)
            put("tags", d.tags)
            put("saved_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(
            DRAFT_TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun findDraft(date: String): Diary? {
        readableDatabase.query(DRAFT_TABLE, null, "date = ?", arrayOf(date), null, null, null).use { c ->
            if (!c.moveToFirst()) return null
            return Diary(
                date = c.getString(c.getColumnIndexOrThrow("date")),
                title = c.getString(c.getColumnIndexOrThrow("title")),
                content = c.getString(c.getColumnIndexOrThrow("content")),
                mood = c.getString(c.getColumnIndexOrThrow("mood")),
                weather = c.getString(c.getColumnIndexOrThrow("weather")),
                tags = c.getString(c.getColumnIndexOrThrow("tags")),
                updatedAt = c.getLong(c.getColumnIndexOrThrow("saved_at"))
            )
        }
    }

    fun clearDraft(date: String) {
        writableDatabase.delete(DRAFT_TABLE, "date = ?", arrayOf(date))
    }

    // ---------------- 恢复 ----------------

    /**
     * 从备份 JSON 恢复。同一天已存在时以备份为准（备份是用户主动选的，
     * 视作"我就是要回到这个状态"）。
     *
     * @return Pair(新增条数, 覆盖条数)
     */
    fun restoreFromJson(json: String): Pair<Int, Int> {
        val root = org.json.JSONObject(json)
        val arr = root.optJSONArray("entries") ?: throw IllegalArgumentException("备份文件格式不对")
        var added = 0
        var replaced = 0
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val date = o.optString("date")
                if (date.isBlank()) continue
                val existed = findByDate(date) != null
                upsert(
                    Diary(
                        date = date,
                        title = o.optString("title"),
                        content = o.optString("content"),
                        mood = o.optString("mood"),
                        weather = o.optString("weather"),
                        tags = o.optString("tags")
                    )
                )
                if (existed) replaced++ else added++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return added to replaced
    }

    fun findByDate(date: String): Diary? = queryOne("date = ?", arrayOf(date))

    fun findById(id: Long): Diary? = queryOne("id = ?", arrayOf(id.toString()))

    private fun queryOne(where: String, args: Array<String>): Diary? {
        readableDatabase.query(TABLE, null, where, args, null, null, null).use { c ->
            return if (c.moveToFirst()) c.toDiary() else null
        }
    }

    /** 倒序列出全部日记；keyword 非空时交给 SQL 过滤，避免把整库读进内存 */
    fun listAll(keyword: String = ""): List<Diary> {
        val kw = keyword.trim()
        val selection: String?
        val args: Array<String>?
        if (kw.isEmpty()) {
            selection = null
            args = null
        } else {
            // 转义 LIKE 的通配符，避免用户输入的 % _ 变成模式
            val esc = kw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            val p = "%$esc%"
            selection = "(title LIKE ? ESCAPE '\\' OR content LIKE ? ESCAPE '\\' OR tags LIKE ? ESCAPE '\\')"
            args = arrayOf(p, p, p)
        }
        readableDatabase.query(TABLE, null, selection, args, null, null, "date DESC").use { c ->
            val result = ArrayList<Diary>(c.count)
            while (c.moveToNext()) result.add(c.toDiary())
            return result
        }
    }

    /** 某月有日记的日期集合，日历打点用 */
    fun datesInMonth(monthPrefix: String): Set<String> {
        val set = HashSet<String>()
        readableDatabase.query(
            TABLE, arrayOf("date"), "date LIKE ?", arrayOf("$monthPrefix-%"),
            null, null, null
        ).use { c ->
            while (c.moveToNext()) set.add(c.getString(0))
        }
        return set
    }

    /** 所有出现过的心情标识 -> 次数，用于"心情回顾" */
    fun moodCounts(): Map<String, Int> {
        val map = HashMap<String, Int>()
        readableDatabase.rawQuery(
            "SELECT mood, COUNT(*) FROM $TABLE WHERE mood != '' GROUP BY mood", null
        ).use { c ->
            while (c.moveToNext()) map[c.getString(0)] = c.getInt(1)
        }
        return map
    }

    /** 所有标签及其使用次数 */
    fun tagCounts(): Map<String, Int> {
        val map = HashMap<String, Int>()
        listAll().forEach { d -> d.tagList().forEach { t -> map[t] = (map[t] ?: 0) + 1 } }
        return map
    }

    fun count(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    private fun android.database.Cursor.toDiary() = Diary(
        id = getLong(getColumnIndexOrThrow("id")),
        date = getString(getColumnIndexOrThrow("date")),
        title = getString(getColumnIndexOrThrow("title")),
        content = getString(getColumnIndexOrThrow("content")),
        mood = getString(getColumnIndexOrThrow("mood")),
        weather = getString(getColumnIndexOrThrow("weather")),
        tags = getString(getColumnIndexOrThrow("tags")),
        updatedAt = getLong(getColumnIndexOrThrow("updated_at"))
    )
}

object DateUtil {
    private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    fun format(date: Date): String = iso.format(date)
    fun parse(s: String): Date? = runCatching { iso.parse(s) }.getOrNull()

    /**
     * "今天"的日期。注意不是系统日期。
     *
     * 很多人习惯凌晨写前一天的日记：周二 00:30 写下周一的流水账。
     * 若直接取系统日期，这篇会被记到周二，和心里默认的那天对不上。
     *
     * 所以引入「一天的开始时间」dayStartHour（默认 4 点）：
     * 凌晨 0:00–3:59 写的内容，仍算前一天。把当前时间往前推 dayStartHour 小时
     * 再取日期，就自然得到了"概念上的今天"。
     *
     * 这样两个场景一次覆盖：
     *  - 周二 00:30 写周一的日记  → 减 4h → 周一 ✓
     *  - 周一 23:00 写周一的日记  → 减 4h → 仍是周一 ✓
     *  - 周二 09:00 写           → 减 4h → 周二 ✓
     */
    fun today(dayStartHour: Int = 4): String = logicalToday(dayStartHour, System.currentTimeMillis())

    /** 纯函数版本，方便单元测试（不受真实时钟影响） */
    fun logicalToday(dayStartHour: Int, nowMillis: Long): String {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.HOUR_OF_DAY, -dayStartHour.coerceIn(0, 12))
        }
        return iso.format(calendar.time)
    }

    /** 当前是否处于"延长的深夜"（凌晨 0 点到 dayStartHour 之间），用于界面提示 */
    fun isLateNight(dayStartHour: Int): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return dayStartHour > 0 && h < dayStartHour
    }

    /** 增减天数，用于编辑页"前一天/后一天"翻页 */
    fun shiftDate(dateStr: String, days: Int): String {
        val cal = Calendar.getInstance().apply {
            time = parse(dateStr) ?: Date()
            add(Calendar.DAY_OF_MONTH, days)
        }
        return iso.format(cal.time)
    }

    /** "2026-10-31" -> "10月31日" */
    fun pretty(s: String): String {
        val d = parse(s) ?: return s
        return SimpleDateFormat("M月d日", Locale.CHINA).format(d)
    }

    /** "2026-10-31" -> "2026年10月31日 周六" */
    fun prettyFull(s: String): String {
        val d = parse(s) ?: return s
        return SimpleDateFormat("yyyy年M月d日 EEE", Locale.CHINA).format(d)
    }
}
