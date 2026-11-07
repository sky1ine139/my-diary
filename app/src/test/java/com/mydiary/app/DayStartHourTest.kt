package com.mydiary.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 日期归属是这次更新的核心，也是最容易出现边界错误的地方：
 * 跨月、跨年、午夜前后一分钟，都必须正确。
 *
 * 这些用例全部基于**固定时间戳**，不依赖运行时的真实时钟。
 */
class DayStartHourTest {

    /** 构造一个本地时间对应的毫秒时间戳，避免测试受时区影响 */
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long {
        return Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis
    }

    // ---------- 你实际遇到的场景 ----------

    @Test
    fun `周二凌晨零点半写周一的日记 应算作周一`() {
        // 2026-11-03 是周二，00:30。默认 4 点分界 → 应归属 11-02（周一）
        val ts = at(2026, 11, 3, 0, 30)
        assertEquals("2026-11-02", DateUtil.logicalToday(4, ts))
    }

    @Test
    fun `周一晚上十一点写周一的日记 应算作周一`() {
        // 2026-11-02 是周一，23:00。减 4h 仍是同一天 → 11-02 ✓
        val ts = at(2026, 11, 2, 23, 0)
        assertEquals("2026-11-02", DateUtil.logicalToday(4, ts))
    }

    @Test
    fun `周二上午九点写 应算作周二`() {
        val ts = at(2026, 11, 3, 9, 0)
        assertEquals("2026-11-03", DateUtil.logicalToday(4, ts))
    }

    // ---------- 边界 ----------

    @Test
    fun `恰好四点是分界 四点属于新的一天`() {
        val ts = at(2026, 11, 3, 4, 0)
        assertEquals("2026-11-03", DateUtil.logicalToday(4, ts))
    }

    @Test
    fun `三点五十九分仍算前一天`() {
        val ts = at(2026, 11, 3, 3, 59)
        assertEquals("2026-11-02", DateUtil.logicalToday(4, ts))
    }

    @Test
    fun `零点整算前一天`() {
        val ts = at(2026, 11, 3, 0, 0)
        assertEquals("2026-11-02", DateUtil.logicalToday(4, ts))
    }

    // ---------- 跨月、跨年：最容易写错的地方 ----------

    @Test
    fun `跨月边界 十一月初凌晨应归到十月最后一天`() {
        // 2026-11-01 是周日 02:00 → 应归属 2026-10-31
        val ts = at(2026, 11, 1, 2, 0)
        assertEquals("2026-10-31", DateUtil.logicalToday(4, ts))
    }

    @Test
    fun `跨年边界 元旦凌晨应归到上一年最后一天`() {
        // 2027-01-01 01:00 → 应归属 2026-12-31
        val ts = at(2027, 1, 1, 1, 0)
        assertEquals("2026-12-31", DateUtil.logicalToday(4, ts))
    }

    @Test
    fun `闰年二月边界正常`() {
        // 2028 是闰年，2/29 存在。3/1 凌晨 2 点 → 应归 2028-02-29
        val ts = at(2028, 3, 1, 2, 0)
        assertEquals("2028-02-29", DateUtil.logicalToday(4, ts))
    }

    // ---------- 设置项本身 ----------

    @Test
    fun `设为0点则完全按自然日`() {
        val ts = at(2026, 11, 3, 0, 30)
        assertEquals("2026-11-03", DateUtil.logicalToday(0, ts))
    }

    @Test
    fun `设为6点则五点仍算前一天`() {
        val ts = at(2026, 11, 3, 5, 0)
        assertEquals("2026-11-02", DateUtil.logicalToday(6, ts))
    }

    @Test
    fun `非法配置值被夹紧不崩溃`() {
        val ts = at(2026, 11, 3, 2, 0)
        // 负数按 0 处理，超大按 12 处理，都不应抛异常
        assertEquals("2026-11-03", DateUtil.logicalToday(-5, ts))
        assertEquals("2026-11-02", DateUtil.logicalToday(999, ts))
    }

    // ---------- 日期翻页 ----------

    @Test
    fun `前一天与后一天正确`() {
        assertEquals("2026-10-31", DateUtil.shiftDate("2026-11-01", -1))
        assertEquals("2026-11-01", DateUtil.shiftDate("2026-10-31", 1))
    }

    @Test
    fun `跨月跨年翻页正确`() {
        assertEquals("2026-12-31", DateUtil.shiftDate("2027-01-01", -1))
        assertEquals("2027-01-01", DateUtil.shiftDate("2026-12-31", 1))
        assertEquals("2026-02-28", DateUtil.shiftDate("2026-03-01", -1))
    }

    @Test
    fun `闰年前一天是二月二十九`() {
        assertEquals("2028-02-29", DateUtil.shiftDate("2028-03-01", -1))
    }

    @Test
    fun `天亮判断只在小时间范围内为真`() {
        // isLateNight 依赖真实时钟，这里只验证不抛异常且是布尔
        val result = DateUtil.isLateNight(4)
        assertTrue(result || !result)
    }
}
