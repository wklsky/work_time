/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/engine/WorkTimeCalculator.kt
 * @Description: 工时引擎主入口，编排日期归属、跨天判定、工作日判定与加班计算，支持四类时间点场景
 */
package com.worktime.pro.domain.engine

import com.worktime.pro.domain.model.CalculationResult
import java.time.LocalDateTime

class WorkTimeCalculator(
    private val dateResolver: DateAttributionResolver = DateAttributionResolver(),
    private val workdayClassifier: WorkdayClassifier = WorkdayClassifier(),
    private val overtimeCalculator: OvertimeCalculator = OvertimeCalculator()
) {
    /**
     * 标准场景：上班→下班（场景 A）。
     * 加班按规则推导：工作日扣除标准工时与缓冲，休息日全计。
     */
    fun calculate(
        clockIn: LocalDateTime,
        clockOut: LocalDateTime,
        rule: WorkRuleConfig
    ): CalculationResult {
        val workDate = dateResolver.resolveWorkDate(clockIn, rule.dayCutTime)
        val crossDay = dateResolver.isCrossDay(clockIn, clockOut)
        val isWorkday = workdayClassifier.isWorkday(workDate, rule.workDays)

        val raw = if (isWorkday) {
            overtimeCalculator.calculateWorkday(clockIn, clockOut, rule.toSnapshot())
        } else {
            overtimeCalculator.calculateWeekend(clockIn, clockOut, rule.toSnapshot())
        }

        return CalculationResult(
            workDate = workDate,
            crossDay = crossDay,
            workMinutes = raw.workMinutes,
            overtimeMinutes = raw.overtimeMinutes,
            restMinutes = raw.restMinutes,
            standardMinutes = raw.standardMinutes,
            bufferMinutes = raw.bufferMinutes,
            formula = raw.formula
        )
    }

    /**
     * 独立加班段场景（场景 B/C）：加班到→加班退直接采用段时长，不再按规则推导。
     * 业务背景：用户已在考勤机单独打了加班卡，该时段即为真实加班，无需标准工时扣减。
     */
    fun calculateOvertimeSegment(
        otStart: LocalDateTime,
        otEnd: LocalDateTime,
        rule: WorkRuleConfig
    ): CalculationResult {
        val workDate = dateResolver.resolveWorkDate(otStart, rule.dayCutTime)
        val crossDay = dateResolver.isCrossDay(otStart, otEnd)
        val duration = java.time.Duration.between(otStart, otEnd).toMinutes().toInt().coerceAtLeast(0)

        return CalculationResult(
            workDate = workDate,
            crossDay = crossDay,
            workMinutes = 0,
            overtimeMinutes = duration,
            restMinutes = 0,
            standardMinutes = 0,
            bufferMinutes = 0,
            formula = "加班段 ($otStart → $otEnd) = ${duration}分"
        )
    }
}

/** 对外暴露的规则配置，由 Repository 从 Room 实体转换而来 */
data class WorkRuleConfig(
    val startTime: java.time.LocalTime,
    val endTime: java.time.LocalTime,
    val restStart: java.time.LocalTime?,
    val restEnd: java.time.LocalTime?,
    val bufferMinutes: Int,
    val standardMinutes: Int,
    val dayCutTime: java.time.LocalTime,
    val workDays: List<Int>
) {
    fun toSnapshot() = WorkRuleSnapshot(
        startTime = startTime,
        endTime = endTime,
        restStart = restStart,
        restEnd = restEnd,
        bufferMinutes = bufferMinutes,
        standardMinutes = standardMinutes
    )
}
