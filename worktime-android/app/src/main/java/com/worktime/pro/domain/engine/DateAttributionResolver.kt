/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/engine/DateAttributionResolver.kt
 * @Description: 日期归属解析器，按日切时间判定业务归属日与跨天标记，替代旧版仅按自然日的粗略处理
 */
package com.worktime.pro.domain.engine

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DateAttributionResolver {
    /**
     * 业务归属日：打卡时间早于日切时间（如 04:00）时归前一日。
     * 业务背景：夜班/加班到凌晨的打卡应算作前一天的工作，避免跨自然日拆账。
     */
    fun resolveWorkDate(clockIn: LocalDateTime, dayCutTime: LocalTime): LocalDate =
        if (clockIn.toLocalTime().isBefore(dayCutTime)) {
            clockIn.toLocalDate().minusDays(1)
        } else {
            clockIn.toLocalDate()
        }

    fun isCrossDay(clockIn: LocalDateTime, clockOut: LocalDateTime): Boolean =
        clockOut.toLocalDate().isAfter(clockIn.toLocalDate()) ||
            clockOut.toLocalTime().isBefore(clockIn.toLocalTime())
}
