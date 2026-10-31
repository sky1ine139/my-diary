package com.mydiary.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日记核心逻辑的单元测试。
 * 只测纯逻辑（不碰 SQLite / Android 框架），保证这些行为在真机上可靠。
 */
class DiaryLogicTest {

    // ---------- 标题与摘要兜底 ----------

    @Test
    fun `有标题时直接用标题`() {
        val d = Diary(date = "2026-10-31", title = "今天很好", content = "正文内容")
        assertEquals("今天很好", d.displayTitle())
    }

    @Test
    fun `没标题时用正文首行兜底`() {
        val d = Diary(date = "2026-10-31", content = "\n\n  第一行正文  \n第二行")
        assertEquals("第一行正文", d.displayTitle())
    }

    @Test
    fun `完全空白显示无标题日记`() {
        val d = Diary(date = "2026-10-31", title = "", content = "")
        assertEquals("无标题日记", d.displayTitle())
    }

    @Test
    fun `超长首行会被截断`() {
        val d = Diary(date = "2026-10-31", content = "字".repeat(100))
        assertEquals(40, d.displayTitle().length)
    }

    @Test
    fun `有标题时摘要包含全部正文`() {
        // 编辑页里标题、正文是分开填的两个字段，正文通常不含标题，
        // 所以有标题时摘要应当就是完整正文，不做删减。
        val d = Diary(date = "2026-10-31", title = "标题", content = "正文第一句\n正文第二句")
        assertTrue(d.preview().contains("正文第一句"))
        assertTrue(d.preview().contains("正文第二句"))
    }

    @Test
    fun `没标题时摘要不重复当作标题的第一行`() {
        // 没有标题时，第一行在列表里已被当成标题显示，摘要就该从第二行开始，
        // 否则同一条日记在卡片里会出现两遍相同内容。
        val d = Diary(date = "2026-10-31", title = "", content = "第一行当标题\n第二行是正文")
        assertEquals("第一行当标题", d.displayTitle())
        assertFalse(d.preview().contains("第一行当标题"))
        assertTrue(d.preview().contains("第二行是正文"))
    }

    // ---------- 标签 ----------

    @Test
    fun `标签解析忽略空项与空格`() {
        val d = Diary(date = "2026-10-31", tags = "工作, 读书 ,, 生活")
        assertEquals(listOf("工作", "读书", "生活"), d.tagList())
    }

    @Test
    fun `空标签返回空列表`() {
        assertEquals(emptyList<String>(), Diary(date = "2026-10-31", tags = "").tagList())
    }

    // ---------- 搜索匹配 ----------

    @Test
    fun `关键词能匹配标题正文标签`() {
        val d = Diary(date = "2026-10-31", title = "桂花", content = "加班", tags = "生活")
        assertTrue(d.matches("桂花"))
        assertTrue(d.matches("加班"))
        assertTrue(d.matches("生活"))
        assertFalse(d.matches("下雨"))
    }

    @Test
    fun `搜索忽略大小写`() {
        val d = Diary(date = "2026-10-31", content = "Android Studio")
        assertTrue(d.matches("android"))
        assertTrue(d.matches("STUDIO"))
    }

    @Test
    fun `空关键词匹配一切`() {
        assertTrue(Diary(date = "2026-10-31", content = "").matches(""))
    }

    // ---------- 日期格式 ----------

    @Test
    fun `日期显示为人话`() {
        assertEquals("10月31日", DateUtil.pretty("2026-10-31"))
    }

    @Test
    fun `非法日期不崩溃`() {
        assertEquals("abc", DateUtil.pretty("abc"))
    }

    @Test
    fun `今天格式是标准日期串`() {
        val t = DateUtil.today()
        assertTrue("today() 应为 yyyy-MM-dd：$t", Regex("""\d{4}-\d{2}-\d{2}""").matches(t))
    }

    // ---------- 心情天气映射 ----------

    @Test
    fun `心情键能映射到文案与表情`() {
        assertEquals("开心", DiaryDbHelper.moodLabel("happy"))
        assertEquals("😄", DiaryDbHelper.moodEmoji("happy"))
    }

    @Test
    fun `未知心情返回空串而不是崩溃`() {
        assertEquals("", DiaryDbHelper.moodLabel("不存在的键"))
        assertEquals("", DiaryDbHelper.moodEmoji("不存在的键"))
    }

    @Test
    fun `天气键能映射`() {
        assertEquals("雨", DiaryDbHelper.weatherLabel("rain"))
        assertEquals("🌧️", DiaryDbHelper.weatherEmoji("rain"))
    }
}
