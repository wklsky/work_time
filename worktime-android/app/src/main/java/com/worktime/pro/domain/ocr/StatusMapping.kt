/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/ocr/StatusMapping.kt
 * @Description: 考勤机状态映射序列化工具，在用户自定义映射与本地存储字符串之间转换
 */
package com.worktime.pro.domain.ocr

import com.worktime.pro.domain.model.PunchType

/**
 * 映射格式："0=WORK_START,1=WORK_END,4=OT_START,5=OT_END"。
 * 用紧凑字符串而非 JSON，便于存 DataStore 并随备份一同导出。
 */
object StatusMapping {
    /** 默认映射：实拍中控考勤机编码，作为新用户初始值 */
    val DEFAULT: Map<String, PunchType> = PunchType.STATUS_CODES

    fun serialize(map: Map<String, PunchType>): String =
        map.entries
            .filter { it.key.isNotBlank() }
            .sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
            .joinToString(",") { "${it.key}=${it.value.name}" }

    /**
     * 反序列化时逐项容错：单项格式非法或类型名无法识别则跳过该项，
     * 避免一处错误导致整份映射丢失。
     */
    fun deserialize(value: String): Map<String, PunchType> {
        if (value.isBlank()) return emptyMap()
        return value.split(",").mapNotNull { part ->
            val kv = part.split("=", limit = 2)
            if (kv.size != 2) return@mapNotNull null
            val code = kv[0].trim()
            if (code.isEmpty()) return@mapNotNull null
            val type = runCatching { PunchType.valueOf(kv[1].trim()) }.getOrNull()
            if (type != null) code to type else null
        }.toMap()
    }
}
