/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/engine/WorkdayClassifier.kt
 * @Description: 工作日判定器，按用户配置的工作日列表判断某日是否为工作日
 */
package com.worktime.pro.domain.engine

import java.time.LocalDate

class WorkdayClassifier {
    fun isWorkday(date: LocalDate, workDays: List<Int>): Boolean =
        workDays.contains(date.dayOfWeek.value)

    fun isWeekend(date: LocalDate, workDays: List<Int>): Boolean = !isWorkday(date, workDays)
}
