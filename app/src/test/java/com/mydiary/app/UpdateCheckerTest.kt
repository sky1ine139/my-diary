package com.mydiary.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 版本比较是"要不要提示更新"的唯一判据，错判会导致反复骚扰或永不提示 */
class UpdateCheckerTest {

    @Test
    fun `修订号更大算新版本`() {
        assertTrue(UpdateChecker.isNewer("1.0.1", "1.0.0"))
    }

    @Test
    fun `相同版本不算新`() {
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.0"))
    }

    @Test
    fun `旧版本不算新`() {
        assertFalse(UpdateChecker.isNewer("0.9.9", "1.0.0"))
    }

    @Test
    fun `十位数版本要按数字比而不是按字符串比`() {
        // 字符串比较会认为 "1.9" > "1.10"，这是个常见坑
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.0"))
        assertFalse(UpdateChecker.isNewer("1.9.0", "1.10.0"))
    }

    @Test
    fun `带v前缀和预发布后缀也能解析`() {
        assertTrue(UpdateChecker.isNewer("v1.2.0", "1.1.9"))
        assertFalse(UpdateChecker.isNewer("1.2.0-beta", "1.2.0"))
    }

    @Test
    fun `两段式版本号补零处理`() {
        assertTrue(UpdateChecker.isNewer("1.1", "1.0.9"))
    }

    @Test
    fun `乱码版本号不乱提示更新`() {
        assertFalse(UpdateChecker.isNewer("abc", "1.0.0"))
        assertFalse(UpdateChecker.isNewer("", "1.0.0"))
    }
}
