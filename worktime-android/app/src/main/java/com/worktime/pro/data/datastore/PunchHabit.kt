/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/data/datastore/PunchHabit.kt
 * @Description: 习惯打卡模型与序列化，保存一组命名的固定打卡时间模板供一键写入当天
 */
package com.worktime.pro.data.datastore

import java.util.UUID

/**
 * 习惯打卡：用户自定义名称 + 一组固定打卡时间。
 * 业务用途：固定班次（如"正常班 9:00-18:00"）无需每天逐条录入，一键写入整组。
 * times 的 key 为 PunchType.name，value 为 "HH:mm"；只存用户启用的类型。
 */
data class PunchHabit(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val times: Map<String, String>
) {
    companion object {
        private const val HABIT_SEP = "\u001F" // 习惯之间分隔
        private const val FIELD_SEP = "\u001E" // 习惯内字段分隔
        private const val TIME_SEP = "\u001D"  // 时间项之间分隔
        private const val KV_SEP = "="

        /** 清洗名称与 id 中的控制分隔符，避免破坏紧凑格式 */
        private fun sanitize(value: String): String =
            value.replace(HABIT_SEP, "").replace(FIELD_SEP, "").replace(TIME_SEP, "")

        fun serialize(habits: List<PunchHabit>): String = habits.joinToString(HABIT_SEP) { habit ->
            val timesPart = habit.times.entries.joinToString(TIME_SEP) { (type, time) ->
                "$type$KV_SEP$time"
            }
            listOf(sanitize(habit.id), sanitize(habit.name), timesPart).joinToString(FIELD_SEP)
        }

        /** 反序列化容错：跳过名称为空或时间非法的项，单条损坏不影响整份习惯列表 */
        fun deserialize(raw: String): List<PunchHabit> {
            if (raw.isBlank()) return emptyList()
            return raw.split(HABIT_SEP).mapNotNull { chunk ->
                val fields = chunk.split(FIELD_SEP)
                if (fields.size < 3) return@mapNotNull null
                val id = fields[0].ifBlank { UUID.randomUUID().toString() }
                val name = fields[1]
                if (name.isBlank()) return@mapNotNull null
                val times = mutableMapOf<String, String>()
                if (fields[2].isNotBlank()) {
                    fields[2].split(TIME_SEP).forEach { entry ->
                        val kv = entry.split(KV_SEP, limit = 2)
                        if (kv.size == 2 && kv[0].isNotBlank() && isValidTime(kv[1])) {
                            times[kv[0]] = kv[1]
                        }
                    }
                }
                PunchHabit(id = id, name = name, times = times)
            }
        }

        private fun isValidTime(value: String): Boolean {
            val parts = value.split(":")
            if (parts.size != 2) return false
            val hour = parts[0].toIntOrNull() ?: return false
            val minute = parts[1].toIntOrNull() ?: return false
            return hour in 0..23 && minute in 0..59
        }
    }
}
