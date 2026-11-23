/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/test/java/com/worktime/pro/data/datastore/ClockDialStyleTest.kt
 * @Description: 表盘数字样式转换函数的单元测试，锁住阿拉伯数字、罗马数字与条丁刻度三种样式
 */
package com.worktime.pro.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockDialStyleTest {

    @Test
    fun `阿拉伯数字 1-12`() {
        assertEquals("1", ClockDialStyle.ARABIC.hourText(1))
        assertEquals("12", ClockDialStyle.ARABIC.hourText(12))
    }

    @Test
    fun `罗马数字 1-12`() {
        assertEquals("I", ClockDialStyle.ROMAN.hourText(1))
        assertEquals("IV", ClockDialStyle.ROMAN.hourText(4))
        assertEquals("IX", ClockDialStyle.ROMAN.hourText(9))
        assertEquals("XII", ClockDialStyle.ROMAN.hourText(12))
    }

    @Test
    fun `条丁刻度不绘制数字`() {
        assertEquals("", ClockDialStyle.INDEX.hourText(1))
        assertEquals("", ClockDialStyle.INDEX.hourText(12))
    }

    @Test
    fun `未知值回退到默认数字`() {
        assertEquals(ClockDialStyle.ARABIC, ClockDialStyle.fromValue(""))
        assertEquals(ClockDialStyle.ARABIC, ClockDialStyle.fromValue("不存在的样式"))
        assertEquals(ClockDialStyle.ROMAN, ClockDialStyle.fromValue("ROMAN"))
        assertEquals(ClockDialStyle.INDEX, ClockDialStyle.fromValue("INDEX"))
    }
}
