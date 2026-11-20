/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/model/DayTotals.kt
 * @Description: 工时口径统一：正常工时与加班工时互不重叠，合计等于当日净投入
 */
package com.worktime.pro.domain.model

/** 参与汇总的单个工时段，屏蔽 Room 实体，便于纯函数测试 */
data class SessionMinutes(
    val isOvertimeSegment: Boolean,
    val workMinutes: Int,
    val overtimeMinutes: Int
)

/**
 * 单日工时口径。
 *
 * 引擎的 workMinutes 表示「净投入」，overtimeMinutes 是其中的加班部分；
 * 独立加班段（OT_START/OT_END）则是 workMinutes=0、overtimeMinutes=段时长。
 * 展示层若直接把两者相加，会把加班重复计数，因此统一在这里做拆解：
 *
 * - 正常段：正常 = max(0, 净投入 - 加班)，加班 = min(加班, 净投入)
 * - 加班段：正常 = 0，加班 = 段时长
 *
 * 约定：regularMinutes + overtimeMinutes == 当日净投入。
 */
data class DayTotals(
    val regularMinutes: Int,
    val overtimeMinutes: Int
) {
    val netMinutes: Int get() = regularMinutes + overtimeMinutes

    companion object {
        fun fromSessions(sessions: List<SessionMinutes>): DayTotals {
            var regular = 0
            var overtime = 0
            sessions.forEach { session ->
                val work = session.workMinutes.coerceAtLeast(0)
                val ot = session.overtimeMinutes.coerceAtLeast(0)
                if (session.isOvertimeSegment) {
                    overtime += ot
                } else {
                    val segmentOt = ot.coerceAtMost(work)
                    regular += work - segmentOt
                    overtime += segmentOt
                }
            }
            return DayTotals(regularMinutes = regular, overtimeMinutes = overtime)
        }
    }
}
