/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/reminder/ReminderType.kt
 * @Description: 累计工时提醒类型，决定达到阈值时按加班、正常工时还是净投入计算
 */
package com.worktime.pro.domain.reminder

enum class ReminderType(val value: String, val label: String) {
    OVERTIME("OVERTIME", "加班"),
    WORK("WORK", "正常工时"),
    NET("NET", "净投入")
}
