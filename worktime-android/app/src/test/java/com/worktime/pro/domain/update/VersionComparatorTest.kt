/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/test/java/com/worktime/pro/domain/update/VersionComparatorTest.kt
 * @Description: 版本号比较单测，覆盖 tag 前缀、后缀与段数不一致等发布场景
 */
package com.worktime.pro.domain.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun `tag 的 v 前缀不参与比较`() {
        assertTrue(VersionComparator.isNewer("v2.1.0", "2.0.0"))
    }

    @Test
    fun `补丁位递增算新版本`() {
        assertTrue(VersionComparator.isNewer("2.0.1", "2.0.0"))
    }

    @Test
    fun `段数不同时缺省补零视为同版本`() {
        // 2.1 与 2.1.0 是同一个版本，双向都不应判定为"有新版本"
        assertFalse(VersionComparator.isNewer("2.1", "2.1.0"))
        assertFalse(VersionComparator.isNewer("2.1.0", "2.1"))
        assertTrue(VersionComparator.isNewer("2.1.1", "2.1"))
    }

    @Test
    fun `主版本号优先于次版本号`() {
        assertTrue(VersionComparator.isNewer("3.0.0", "2.9.9"))
    }

    @Test
    fun `数字按数值比较而不是字符串比较`() {
        // 字符串比较会得出 "2.10" < "2.9"，必须按数值比
        assertTrue(VersionComparator.isNewer("2.10.0", "2.9.0"))
    }

    @Test
    fun `同版本与降级都不算新版本`() {
        assertFalse(VersionComparator.isNewer("2.1.0", "2.1.0"))
        assertFalse(VersionComparator.isNewer("2.0.0", "2.1.0"))
    }

    @Test
    fun `预发布后缀按同版本处理避免误判`() {
        assertFalse(VersionComparator.isNewer("2.1.0-beta", "2.1.0"))
    }

    @Test
    fun `异常格式回落为零不影响判断`() {
        assertFalse(VersionComparator.isNewer("", "2.1.0"))
        assertTrue(VersionComparator.isNewer("2.1.0", ""))
    }
}
