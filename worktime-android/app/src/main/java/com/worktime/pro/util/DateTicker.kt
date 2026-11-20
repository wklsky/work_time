/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/util/DateTicker.kt
 * @Description: 轻量时间源，跨零点时驱动页面日期与今日数据刷新
 */
package com.worktime.pro.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

object DateTicker {

    /** 每分钟对齐发射一次当前时间，零点后首次发射即切换日期 */
    fun clock(): Flow<LocalDateTime> = flow {
        while (true) {
            val now = LocalDateTime.now()
            emit(now)
            val nextMinute = now.withSecond(0).withNano(0).plusMinutes(1)
            delay(Duration.between(now, nextMinute).toMillis().coerceAtLeast(1_000))
        }
    }

    /** 日期变化流：同一天只在跨零点时发射一次 */
    fun dates(): Flow<LocalDate> = clock().map { it.toLocalDate() }.distinctUntilChanged()

    /** 实时计时使用，每秒发射一次 */
    fun seconds(): Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(1_000)
        }
    }
}
