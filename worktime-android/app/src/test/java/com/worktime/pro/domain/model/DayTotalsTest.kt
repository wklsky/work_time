/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/test/java/com/worktime/pro/domain/model/DayTotalsTest.kt
 * @Description: 工时口径测试，确保正常工时与加班不重复计数
 */
package com.worktime.pro.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DayTotalsTest {

    @Test
    fun `workday overtime is split out of net work`() {
        val totals = DayTotals.fromSessions(
            listOf(SessionMinutes(isOvertimeSegment = false, workMinutes = 600, overtimeMinutes = 30))
        )
        assertEquals(570, totals.regularMinutes)
        assertEquals(30, totals.overtimeMinutes)
        assertEquals(600, totals.netMinutes)
    }

    @Test
    fun `weekend net work counts as overtime only`() {
        val totals = DayTotals.fromSessions(
            listOf(SessionMinutes(isOvertimeSegment = false, workMinutes = 240, overtimeMinutes = 240))
        )
        assertEquals(0, totals.regularMinutes)
        assertEquals(240, totals.overtimeMinutes)
    }

    @Test
    fun `standalone overtime segment is not regular work`() {
        val totals = DayTotals.fromSessions(
            listOf(SessionMinutes(isOvertimeSegment = true, workMinutes = 0, overtimeMinutes = 120))
        )
        assertEquals(0, totals.regularMinutes)
        assertEquals(120, totals.overtimeMinutes)
    }

    @Test
    fun `multiple sessions sum without double counting`() {
        val totals = DayTotals.fromSessions(
            listOf(
                SessionMinutes(isOvertimeSegment = false, workMinutes = 600, overtimeMinutes = 30),
                SessionMinutes(isOvertimeSegment = true, workMinutes = 0, overtimeMinutes = 120)
            )
        )
        assertEquals(570, totals.regularMinutes)
        assertEquals(150, totals.overtimeMinutes)
        assertEquals(720, totals.netMinutes)
    }

    @Test
    fun `negative and inconsistent values are clamped`() {
        val totals = DayTotals.fromSessions(
            listOf(
                SessionMinutes(isOvertimeSegment = false, workMinutes = -10, overtimeMinutes = 5),
                SessionMinutes(isOvertimeSegment = false, workMinutes = 60, overtimeMinutes = 90)
            )
        )
        assertEquals(0, totals.regularMinutes)
        assertEquals(60, totals.overtimeMinutes)
    }

    @Test
    fun `empty day is zero`() {
        val totals = DayTotals.fromSessions(emptyList())
        assertEquals(0, totals.regularMinutes)
        assertEquals(0, totals.overtimeMinutes)
    }
}
