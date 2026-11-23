/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/test/java/com/worktime/pro/data/datastore/ClockDialStyleTest.kt
 * @Description: 表盘数字样式转换函数的单元测试，锁住罗马数字映射与极简/阿拉伯两种边界
 */
package com.worktime.pro.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockDialStyleTest {

    @Test
    fun `阿拉伯数字不带补零`() {
        assertEquals("1", ClockDialStyle.ARABIC.hourText(1))
        assertEquals("12", ClockDialStyle.ARABIC.hourText(12))
        assertEquals("24", ClockDialStyle.ARABIC.hourText(24))
    }

    @Test
    fun `阿拉伯数字带补零`() {
        assertEquals("00", ClockDialStyle.ARABIC.hourText(0, pad = true))
        assertEquals("02", ClockDialStyle.ARABIC.hourText(2, pad = true))
        assertEquals("22", ClockDialStyle.ARABIC.hourText(22, pad = true))
    }

    @Test
    fun `罗马数字外圈 1-12`() {
        assertEquals("I", ClockDialStyle.ROMAN.hourText(1))
        assertEquals("IV", ClockDialStyle.ROMAN.hourText(4))
        assertEquals("IX", ClockDialStyle.ROMAN.hourText(9))
        assertEquals("XII", ClockDialStyle.ROMAN.hourText(12))
    }

    @Test
    fun `罗马数字内圈 13-24 与零点`() {
        assertEquals("XIII", ClockDialStyle.ROMAN.hourText(13))
        assertEquals("XIX", ClockDialStyle.ROMAN.hourText(19))
        assertEquals("XXIV", ClockDialStyle.ROMAN.hourText(24))
        // 0 点在 12 小时制表盘顶部显示为十二点
        assertEquals("XII", ClockDialStyle.ROMAN.hourText(0))
    }

    @Test
    fun `极简样式不绘制任何数字`() {
        assertEquals("", ClockDialStyle.MINIMAL.hourText(1))
        assertEquals("", ClockDialStyle.MINIMAL.hourText(12))
        assertEquals("", ClockDialStyle.MINIMAL.hourText(24))
        assertEquals("", ClockDialStyle.MINIMAL.hourText(0))
    }

    @Test
    fun `未知值回退到默认阿拉伯`() {
        assertEquals(ClockDialStyle.ARABIC, ClockDialStyle.fromValue(""))
        assertEquals(ClockDialStyle.ARABIC, ClockDialStyle.fromValue("不存在的样式"))
        assertEquals(ClockDialStyle.ROMAN, ClockDialStyle.fromValue("ROMAN"))
        assertEquals(ClockDialStyle.MINIMAL, ClockDialStyle.fromValue("MINIMAL"))
    }
}
