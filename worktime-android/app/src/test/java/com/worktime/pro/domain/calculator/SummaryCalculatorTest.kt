/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/test/java/com/worktime/pro/domain/calculator/SummaryCalculatorTest.kt
 * @Description: 汇总计算单元测试，验证五维度区间解析、聚合与边界保护
 */
package com.worktime.pro.domain.calculator

import com.worktime.pro.domain.model.DailyMinutes
import com.worktime.pro.domain.model.SummaryRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SummaryCalculatorTest {
    private val calculator = SummaryCalculator()
    private val anchor = LocalDate.of(2026, 11, 20) // 星期五

    @Test
    fun `day range is single day`() {
        val r = calculator.resolveRange(SummaryRange.DAY, anchor)
        assertEquals(anchor, r.start)
        assertEquals(anchor, r.end)
    }

    @Test
    fun `week range starts monday ends sunday`() {
        val r = calculator.resolveRange(SummaryRange.WEEK, anchor)
        assertEquals(LocalDate.of(2026, 11, 16), r.start) // 周一
        assertEquals(LocalDate.of(2026, 11, 22), r.end)   // 周日
    }

    @Test
    fun `month range covers full month`() {
        val r = calculator.resolveRange(SummaryRange.MONTH, anchor)
        assertEquals(LocalDate.of(2026, 11, 1), r.start)
        assertEquals(LocalDate.of(2026, 11, 30), r.end)
    }

    @Test
    fun `year range covers full year`() {
        val r = calculator.resolveRange(SummaryRange.YEAR, anchor)
        assertEquals(LocalDate.of(2026, 1, 1), r.start)
        assertEquals(LocalDate.of(2026, 12, 31), r.end)
    }

    @Test
    fun `custom range reversed is corrected`() {
        val r = calculator.resolveRange(
            SummaryRange.CUSTOM, anchor,
            LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 1)
        )
        assertEquals(LocalDate.of(2026, 11, 1), r.start)
        assertEquals(LocalDate.of(2026, 11, 20), r.end)
        assertFalse(r.truncated)
    }

    @Test
    fun `custom range over 366 days is truncated`() {
        val r = calculator.resolveRange(
            SummaryRange.CUSTOM, anchor,
            LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31)
        )
        assertTrue(r.truncated)
        assertEquals(LocalDate.of(2025, 1, 1).plusDays(365), r.end)
    }

    @Test
    fun `summarize aggregates work and overtime`() {
        val daily = listOf(
            DailyMinutes("2026-11-18", 480, 30),
            DailyMinutes("2026-11-19", 480, 60),
            DailyMinutes("2026-11-20", 420, 0)
        )
        val result = calculator.summarize(
            daily, LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 20), SummaryRange.CUSTOM
        )
        assertEquals(1380, result.totalWorkMinutes)
        assertEquals(90, result.totalOvertimeMinutes)
        assertEquals(3, result.attendanceDays)
        assertEquals(460, result.averageWorkMinutes)
    }

    @Test
    fun `summarize excludes out of range dates`() {
        val daily = listOf(
            DailyMinutes("2026-11-19", 480, 30),
            DailyMinutes("2026-11-25", 480, 30) // 区间外
        )
        val result = calculator.summarize(
            daily, LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 20), SummaryRange.CUSTOM
        )
        assertEquals(480, result.totalWorkMinutes)
        assertEquals(1, result.attendanceDays)
    }

    @Test
    fun `summarize with no data returns zero average`() {
        val result = calculator.summarize(
            emptyList(), LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 20), SummaryRange.CUSTOM
        )
        assertEquals(0, result.attendanceDays)
        assertEquals(0, result.averageWorkMinutes)
    }
}
