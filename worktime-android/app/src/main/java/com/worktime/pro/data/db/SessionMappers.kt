/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/data/db/SessionMappers.kt
 * @Description: 工时段实体的聚合映射，统一各页面与导出使用同一工时口径
 */
package com.worktime.pro.data.db

import com.worktime.pro.domain.model.DayTotals
import com.worktime.pro.domain.model.SessionMinutes
import com.worktime.pro.domain.model.SessionType

fun WorkSessionEntity.toSessionMinutes(): SessionMinutes = SessionMinutes(
    isOvertimeSegment = sessionType == SessionType.OVERTIME.name,
    workMinutes = workMinutes,
    overtimeMinutes = overtimeMinutes
)

/** 一组工时段拆解为互不重叠的正常/加班工时 */
fun List<WorkSessionEntity>.toDayTotals(): DayTotals =
    DayTotals.fromSessions(map { it.toSessionMinutes() })
