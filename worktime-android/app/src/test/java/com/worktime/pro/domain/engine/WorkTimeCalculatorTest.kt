/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/test/java/com/worktime/pro/domain/engine/WorkTimeCalculatorTest.kt
 * @Description: 工时引擎单元测试，覆盖旧版 11 类核心场景与新增缺陷修复场景
 */
package com.worktime.pro.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class WorkTimeCalculatorTest {
    private val calculator = WorkTimeCalculator()
    private val rule = WorkRuleConfig(
        startTime = LocalTime.of(9, 0),
        endTime = LocalTime.of(18, 0),
        restStart = LocalTime.of(12, 0),
        restEnd = LocalTime.of(13, 0),
        bufferMinutes = 30,
        standardMinutes = 480,
        dayCutTime = LocalTime.of(4, 0),
        workDays = listOf(1, 2, 3, 4, 5)
    )

    private fun dt(day: Int, hour: Int, minute: Int) =
        LocalDateTime.of(LocalDate.of(2026, 11, day), LocalTime.of(hour, minute))

    // 1. 正常上下班：9:00-18:00，扣 60 分钟午休，无加班
    @Test
    fun `normal workday no overtime`() {
        val r = calculator.calculate(dt(20, 9, 0), dt(20, 18, 0), rule)
        assertEquals(480, r.workMinutes)
        assertEquals(0, r.overtimeMinutes)
        assertEquals(60, r.restMinutes)
        assertFalse(r.crossDay)
    }

    // 2. 迟到：10:00-18:00
    @Test
    fun `late arrival`() {
        val r = calculator.calculate(dt(20, 10, 0), dt(20, 18, 0), rule)
        assertEquals(420, r.workMinutes)
        assertEquals(0, r.overtimeMinutes)
    }

    // 3. 早退：9:00-17:00
    @Test
    fun `early leave`() {
        val r = calculator.calculate(dt(20, 9, 0), dt(20, 17, 0), rule)
        assertEquals(420, r.workMinutes)
        assertEquals(0, r.overtimeMinutes)
    }

    // 4. 工作日加班：9:00-20:00，扣午休与缓冲后加班 90 分钟
    @Test
    fun `workday overtime`() {
        val r = calculator.calculate(dt(20, 9, 0), dt(20, 20, 0), rule)
        assertEquals(600, r.workMinutes)
        assertEquals(90, r.overtimeMinutes)
    }

    // 5. 跨天工作：22:00-次日 02:00
    @Test
    fun `cross day work`() {
        val r = calculator.calculate(dt(20, 22, 0), dt(21, 2, 0), rule)
        assertTrue(r.crossDay)
        assertEquals(240, r.workMinutes)
    }

    // 6. 周末：10:00-15:00 全部算加班，扣午休 60 分钟
    @Test
    fun `weekend all overtime`() {
        val r = calculator.calculate(dt(21, 10, 0), dt(21, 15, 0), rule)
        assertEquals(240, r.workMinutes)
        assertEquals(240, r.overtimeMinutes)
    }

    // 7. 一日多段：引擎对每段独立计算（Repository 层负责累加）
    @Test
    fun `multiple segments in one day`() {
        val first = calculator.calculate(dt(20, 9, 0), dt(20, 12, 0), rule)
        val second = calculator.calculate(dt(20, 13, 0), dt(20, 18, 0), rule)
        assertEquals(180, first.workMinutes)
        assertEquals(300, second.workMinutes)
        assertEquals(480, first.workMinutes + second.workMinutes)
    }

    // 8. 手动输入时间：直接传入 LocalDateTime，走同一计算路径
    @Test
    fun `manual input same calculation`() {
        val r = calculator.calculate(dt(20, 9, 30), dt(20, 18, 30), rule)
        assertEquals(480, r.workMinutes)
        assertEquals(0, r.overtimeMinutes)
    }

    // 9. 修改后重算：修改结束时间后结果随之变化
    @Test
    fun `recalculate after edit`() {
        val before = calculator.calculate(dt(20, 9, 0), dt(20, 18, 0), rule)
        val after = calculator.calculate(dt(20, 9, 0), dt(20, 20, 0), rule)
        assertEquals(0, before.overtimeMinutes)
        assertEquals(90, after.overtimeMinutes)
    }

    // 10. 日切归属：凌晨 03:00 打卡归前一日
    @Test
    fun `day cut attribution`() {
        val r = calculator.calculate(dt(21, 3, 0), dt(21, 8, 0), rule)
        assertEquals(LocalDate.of(2026, 11, 20), r.workDate)
    }

    // 11. 独立加班段：19:00-21:00 直接计入 120 分钟
    @Test
    fun `standalone overtime segment`() {
        val r = calculator.calculateOvertimeSegment(dt(20, 19, 0), dt(20, 21, 0), rule)
        assertEquals(120, r.overtimeMinutes)
        assertEquals(0, r.workMinutes)
    }

    // 缺陷修复 1：跨天午休漏算——13:30 → 次日 12:30，应扣次日 12:00-12:30 共 30 分钟
    @Test
    fun `cross day rest overlap fixed`() {
        val r = calculator.calculate(dt(20, 13, 30), dt(21, 12, 30), rule)
        // 总 1380 分，午休 30 分，净 1350 分；工作日标准 480 + 缓冲 30 → 加班 840
        assertEquals(30, r.restMinutes)
        assertEquals(1350, r.workMinutes)
    }

    // 缺陷修复 2：负时长保护——加班不超过净工时
    @Test
    fun `overtime never negative or exceed work`() {
        val r = calculator.calculate(dt(20, 9, 0), dt(20, 10, 0), rule)
        assertTrue(r.overtimeMinutes >= 0)
        assertTrue(r.overtimeMinutes <= r.workMinutes)
    }

    // 缺陷修复 3：规则不回溯——使用不同缓冲创建新规则，结果随之变化
    @Test
    fun `rule change affects new records`() {
        val newRule = rule.copy(bufferMinutes = 60)
        val r1 = calculator.calculate(dt(20, 9, 0), dt(20, 20, 0), rule)
        val r2 = calculator.calculate(dt(20, 9, 0), dt(20, 20, 0), newRule)
        assertTrue(r1.overtimeMinutes >= r2.overtimeMinutes)
    }
}
