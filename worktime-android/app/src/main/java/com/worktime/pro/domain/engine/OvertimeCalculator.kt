/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/engine/OvertimeCalculator.kt
 * @Description: 加班计算核心，区分工作日/周末公式，修复旧版跨天午休漏算与负时长缺陷
 */
package com.worktime.pro.domain.engine

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.max
import kotlin.math.min

class OvertimeCalculator {

    /** 工作日：加班 = 净工时 − 标准工时 − 缓冲，且不超过净工时 */
    fun calculateWorkday(
        clockIn: LocalDateTime,
        clockOut: LocalDateTime,
        rule: WorkRuleSnapshot
    ): RawResult {
        val totalMinutes = Duration.between(clockIn, clockOut).toMinutes().toInt()
        val restMinutes = calculateRestOverlap(clockIn, clockOut, rule)
        val workMinutes = totalMinutes - restMinutes

        val standardMinutes = if (rule.standardMinutes > 0) {
            rule.standardMinutes
        } else {
            val derived = Duration.between(rule.startTime, rule.endTime).toMinutes().toInt()
            if (derived > 0) derived else rule.standardMinutes
        }

        var overtime = workMinutes - standardMinutes - rule.bufferMinutes
        overtime = max(0, overtime)
        overtime = min(overtime, workMinutes)

        val formula = "(${totalMinutes}分 - 午休${restMinutes}分 - 标准${standardMinutes}分 - 缓冲${rule.bufferMinutes}分) = ${overtime}分"
        return RawResult(workMinutes, overtime, restMinutes, standardMinutes, rule.bufferMinutes, formula)
    }

    /**
     * 周末/休息日：全部净工时算加班。
     * 业务背景：休息日不存在"标准工时"概念，投入即加班。
     */
    fun calculateWeekend(
        clockIn: LocalDateTime,
        clockOut: LocalDateTime,
        rule: WorkRuleSnapshot
    ): RawResult {
        val totalMinutes = Duration.between(clockIn, clockOut).toMinutes().toInt()
        val restMinutes = calculateRestOverlap(clockIn, clockOut, rule)
        val workMinutes = totalMinutes - restMinutes
        val formula = "(${totalMinutes}分 - 午休${restMinutes}分) = ${workMinutes}分（休息日全部计入加班）"
        return RawResult(workMinutes, workMinutes, restMinutes, 0, 0, formula)
    }

    /**
     * 午休重叠计算。
     * 修复旧版缺陷：原实现仅比较 LocalTime，跨天场景（如 13:30 → 次日 12:30）会漏扣次日午休，
     * 且 overlapEnd < overlapStart 时产生负时长。此处将日期纳入构造完整区间求交，并做负值保护。
     */
    private fun calculateRestOverlap(
        clockIn: LocalDateTime,
        clockOut: LocalDateTime,
        rule: WorkRuleSnapshot
    ): Int {
        val restStart = rule.restStart ?: return 0
        val restEnd = rule.restEnd ?: return 0
        if (!restEnd.isAfter(restStart)) return 0

        var total = 0L
        var day = clockIn.toLocalDate()
        val lastDay = clockOut.toLocalDate()

        // 业务规则：午休为每日重复时段，跨天工作需逐日扣减与工作区间重叠的部分
        while (!day.isAfter(lastDay)) {
            val restBegin = LocalDateTime.of(day, restStart)
            val restFinish = LocalDateTime.of(day, restEnd)
            val overlapStart = maxOf(clockIn, restBegin)
            val overlapEnd = minOf(clockOut, restFinish)
            if (overlapEnd.isAfter(overlapStart)) {
                total += Duration.between(overlapStart, overlapEnd).toMinutes()
            }
            day = day.plusDays(1)
        }
        return total.toInt()
    }
}

/** 引擎内部使用的规则快照，屏蔽 Room 实体，便于单测构造 */
data class WorkRuleSnapshot(
    val startTime: LocalTime,
    val endTime: LocalTime,
    val restStart: LocalTime?,
    val restEnd: LocalTime?,
    val bufferMinutes: Int,
    val standardMinutes: Int = 0
)

/** 未叠加日期归属的原始计算结果 */
data class RawResult(
    val workMinutes: Int,
    val overtimeMinutes: Int,
    val restMinutes: Int,
    val standardMinutes: Int,
    val bufferMinutes: Int,
    val formula: String
)
