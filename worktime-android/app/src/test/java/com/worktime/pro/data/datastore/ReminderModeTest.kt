package com.worktime.pro.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderModeTest {

    /** 历史设置里没有该字段或值是脏数据时，必须回落到会出声的默认值，不能静默变哑 */
    @Test
    fun `unknown mode falls back to sound and vibrate`() {
        assertEquals(ReminderMode.SOUND_VIBRATE, ReminderMode.fromValue(""))
        assertEquals(ReminderMode.SOUND_VIBRATE, ReminderMode.fromValue("UNKNOWN"))
        assertEquals(ReminderMode.SOUND_VIBRATE, ReminderMode.fromValue("vibrate_only"))
    }

    @Test
    fun `known values round trip`() {
        ReminderMode.entries.forEach { mode ->
            assertEquals(mode, ReminderMode.fromValue(mode.value))
        }
    }

    @Test
    fun `three modes are offered`() {
        assertEquals(
            listOf("声音 + 震动", "仅震动", "跟随系统"),
            ReminderMode.entries.map { it.label }
        )
    }
}
