/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/util/TimeFormatter.kt
 * @Description: 时间与时长格式化工具，统一 24H/12H 转换，"X小时Y分"展示，避免各页面自行拼接字符串
 */
package com.worktime.pro.util

import android.content.Context
import android.text.format.DateFormat
import com.worktime.pro.data.datastore.TimeFormatMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object TimeFormatter {
    private val HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm")
    private val HOUR_MINUTE_12 = DateTimeFormatter.ofPattern("a h:mm", Locale.CHINA)
    private val DATE_CN = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
    private val DATE_KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA)

    /** 存储层统一用 24H，展示层才按 timeFormat 转换 */
    fun formatTime(time: LocalTime, use24Hour: Boolean = true): String =
        if (use24Hour) time.format(HOUR_MINUTE) else time.format(HOUR_MINUTE_12)

    /**
     * 将用户设置的时间制式解析为是否 24 小时制。
     * 跟随系统时读取系统区域设置，保证与手机其他地方显示一致。
     */
    fun use24Hour(context: Context, modeValue: String): Boolean = when (modeValue) {
        TimeFormatMode.HOUR_24.value -> true
        TimeFormatMode.HOUR_12.value -> false
        else -> DateFormat.is24HourFormat(context)
    }

    fun formatDateTime(dateTime: LocalDateTime, use24Hour: Boolean = true): String =
        "${formatDate(dateTime.toLocalDate())} ${formatTime(dateTime.toLocalTime(), use24Hour)}"

    fun formatDate(date: LocalDate): String = date.format(DATE_CN)

    fun formatDateKey(date: LocalDate): String = date.format(DATE_KEY)

    fun parseDateKey(key: String): LocalDate = LocalDate.parse(key, DATE_KEY)

    fun parseTime(value: String): LocalTime = LocalTime.parse(value, HOUR_MINUTE)

    fun weekdayCN(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA)

    fun weekdayShort(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "一"
        DayOfWeek.TUESDAY -> "二"
        DayOfWeek.WEDNESDAY -> "三"
        DayOfWeek.THURSDAY -> "四"
        DayOfWeek.FRIDAY -> "五"
        DayOfWeek.SATURDAY -> "六"
        DayOfWeek.SUNDAY -> "日"
    }

    /** 时长统一展示为"X小时Y分"，0 分钟显示"0分" */
    fun formatDuration(minutes: Int): String {
        if (minutes <= 0) return "0分"
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours == 0 -> "${mins}分"
            mins == 0 -> "${hours}小时"
            else -> "${hours}小时${mins}分"
        }
    }
}
