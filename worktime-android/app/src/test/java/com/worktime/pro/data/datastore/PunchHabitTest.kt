/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/test/java/com/worktime/pro/data/datastore/PunchHabitTest.kt
 * @Description: 习惯打卡序列化测试，验证往返一致与非法项容错
 */
package com.worktime.pro.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PunchHabitTest {

    @Test
    fun `serialize then deserialize keeps name and times`() {
        val habit = PunchHabit(
            id = "h1",
            name = "正常班",
            times = mapOf("WORK_START" to "09:00", "WORK_END" to "18:00")
        )
        val restored = PunchHabit.deserialize(PunchHabit.serialize(listOf(habit)))
        assertEquals(1, restored.size)
        assertEquals("h1", restored[0].id)
        assertEquals("正常班", restored[0].name)
        assertEquals(mapOf("WORK_START" to "09:00", "WORK_END" to "18:00"), restored[0].times)
    }

    @Test
    fun `deserialize skips invalid time and blank name`() {
        val sep = "\u001F"
        val fsep = "\u001E"
        val tsep = "\u001D"
        // 第一条含一个非法时间，第二条名称为空，均应被跳过对应部分/整条
        val raw = listOf(
            listOf("a", "正常班", "WORK_START=09:00${tsep}WORK_END=99:99").joinToString(fsep),
            listOf("b", "", "WORK_START=08:00").joinToString(fsep)
        ).joinToString(sep)
        val restored = PunchHabit.deserialize(raw)
        assertEquals(1, restored.size)
        assertEquals(mapOf("WORK_START" to "09:00"), restored[0].times)
    }

    @Test
    fun `deserialize blank returns empty list`() {
        assertTrue(PunchHabit.deserialize("").isEmpty())
        assertTrue(PunchHabit.deserialize("garbage").isEmpty())
    }
}
