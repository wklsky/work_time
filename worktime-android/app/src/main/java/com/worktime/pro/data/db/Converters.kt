/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/data/db/Converters.kt
 * @Description: Room 类型转换器，将工作日列表与逗号分隔字符串互转，避免额外关联表
 */
package com.worktime.pro.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromWorkDays(value: String): List<Int> =
        value.split(",").filter { it.isNotBlank() }.mapNotNull { it.trim().toIntOrNull() }

    @TypeConverter
    fun toWorkDays(days: List<Int>): String = days.joinToString(",")
}
