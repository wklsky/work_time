/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/calculator/SummaryCalculator.kt
 * @Description: 汇总计算纯逻辑，按维度解析区间并聚合每日工时，供各维度统计与报表导出复用
 */
package com.worktime.pro.domain.calculator

import com.worktime.pro.domain.model.DailyMinutes
import com.worktime.pro.domain.model.SummaryRange
import com.worktime.pro.domain.model.SummaryResult
import com.worktime.pro.util.Constants
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class SummaryCalculator {

    /**
     * 按维度解析起止日期。
     * 业务规则：星期以周一为首日；自定义区间上限 366 天，超出时截断到上限并标记。
     */
    fun resolveRange(
        range: SummaryRange,
        anchor: LocalDate,
        customStart: LocalDate? = null,
        customEnd: LocalDate? = null
    ): RangeResult {
        val (start, end, truncated) = when (range) {
            SummaryRange.DAY -> Triple(anchor, anchor, false)
            SummaryRange.WEEK -> Triple(
                anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                anchor.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)),
                false
            )
            SummaryRange.MONTH -> Triple(
                anchor.withDayOfMonth(1),
                anchor.withDayOfMonth(anchor.lengthOfMonth()),
                false
            )
            SummaryRange.YEAR -> Triple(
                LocalDate.of(anchor.year, 1, 1),
                LocalDate.of(anchor.year, 12, 31),
                false
            )
            SummaryRange.CUSTOM -> {
                val rawStart = customStart ?: anchor
                val rawEnd = customEnd ?: anchor
                // 用户可能把起止填反，交换后继续，避免返回空区间
                val (s, e) = if (rawEnd.isBefore(rawStart)) rawEnd to rawStart else rawStart to rawEnd
                val maxEnd = s.plusDays(Constants.MAX_CUSTOM_RANGE_DAYS.toLong() - 1)
                if (e.isAfter(maxEnd)) Triple(s, maxEnd, true)
                else Triple(s, e, false)
            }
        }
        return RangeResult(start, end, truncated)
    }

    /**
     * 聚合区间内每日工时。
     * 出勤天数定义：区间内存在有效工时段（工作或加班分钟 > 0）的自然日数量。
     * 日均工时 = 工作时长 / 出勤天数，出勤为 0 时返回 0，避免除零。
     */
    fun summarize(
        daily: List<DailyMinutes>,
        start: LocalDate,
        end: LocalDate,
        range: SummaryRange
    ): SummaryResult {
        val startKey = start.toString()
        val endKey = end.toString()
        val inRange = daily.filter { it.date >= startKey && it.date <= endKey }
            .sortedBy { it.date }

        val totalWork = inRange.sumOf { it.workMinutes }
        val totalOvertime = inRange.sumOf { it.overtimeMinutes }
        val attendanceDays = inRange.count { it.workMinutes > 0 || it.overtimeMinutes > 0 }
        val average = if (attendanceDays > 0) totalWork / attendanceDays else 0

        return SummaryResult(
            range = range,
            startDate = startKey,
            endDate = endKey,
            totalWorkMinutes = totalWork,
            totalOvertimeMinutes = totalOvertime,
            attendanceDays = attendanceDays,
            averageWorkMinutes = average,
            daily = inRange
        )
    }
}

data class RangeResult(
    val start: LocalDate,
    val end: LocalDate,
    val truncated: Boolean
)
