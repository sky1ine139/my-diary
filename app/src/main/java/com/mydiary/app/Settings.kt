package com.mydiary.app

import android.content.Context

/**
 * 应用设置。目前只有一项：一天的开始时间。
 *
 * 用 SharedPreferences 而不是数据库，因为这是"设备偏好"而非"日记内容"，
 * 不参与备份/恢复（换手机后用户重新设一次即可，不该被旧备份覆盖）。
 */
object Settings {
    private const val PREF = "mydiary_settings"
    private const val KEY_DAY_START_HOUR = "day_start_hour"

    /** 默认 4 点：0–3 点写的内容算前一天 */
    const val DEFAULT_DAY_START_HOUR = 4

    /**
     * 可选值。0 表示"以自然日为准"（不给任何偏移）。
     * 其余是"这个点之前算前一天"。
     */
    val DAY_START_OPTIONS = listOf(
        0 to "以 0 点分界（自然日）",
        2 to "凌晨 2 点前算前一天",
        3 to "凌晨 3 点前算前一天",
        4 to "凌晨 4 点前算前一天",
        5 to "凌晨 5 点前算前一天",
        6 to "凌晨 6 点前算前一天"
    )

    fun dayStartHour(ctx: Context): Int =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY_DAY_START_HOUR, DEFAULT_DAY_START_HOUR)

    fun setDayStartHour(ctx: Context, hour: Int) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putInt(KEY_DAY_START_HOUR, hour.coerceIn(0, 12)).apply()
    }

    fun dayStartLabel(hour: Int): String =
        DAY_START_OPTIONS.firstOrNull { it.first == hour }?.second ?: "凌晨 $hour 点前算前一天"
}
