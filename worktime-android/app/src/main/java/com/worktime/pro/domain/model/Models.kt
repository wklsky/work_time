/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/model/Models.kt
 * @Description: 领域模型，定义打卡类型/来源/置信度/段类型枚举及工时计算结果结构
 */
package com.worktime.pro.domain.model

import java.time.LocalDate

/** 四类打卡时间点，对应考勤机屏幕的四种打卡动作 */
enum class PunchType(val label: String) {
    WORK_START("上班"),
    WORK_END("下班"),
    OT_START("加班到"),
    OT_END("加班退");

    companion object {
        /**
         * 考勤机「状态」列数字到打卡类型的映射。
         * 业务依据：实拍中控指纹考勤机屏幕底部图例标注"5:加班签退"，结合单日四条记录的时间顺序推断：
         * 0=上班签到（早晨）、1=下班签退（傍晚）、4=加班签到（下班后）、5=加班签退（夜间）。
         * 该编码为中控考勤机通用约定，类型判定优先用状态列而非文字关键词。
         */
        val STATUS_CODES: Map<String, PunchType> = mapOf(
            "0" to WORK_START,
            "1" to WORK_END,
            "4" to OT_START,
            "5" to OT_END
        )

        fun fromStatusCode(code: String): PunchType? = STATUS_CODES[code.trim()]

        /** 按枚举名反查，供实体里的 punchType 字符串转回类型使用 */
        fun fromName(name: String): PunchType? = entries.firstOrNull { it.name == name }

        /** 中文关键词映射，仅作为无状态列屏幕的兜底方案 */
        val KEYWORDS: Map<PunchType, List<String>> = mapOf(
            WORK_START to listOf("上班签到", "上班打卡", "上班签入", "上班"),
            WORK_END to listOf("下班签退", "下班打卡", "下班签出", "下班"),
            OT_START to listOf("加班签到", "加班开始", "加班签入", "加班"),
            OT_END to listOf("加班签退", "加班结束", "加班签出")
        )
    }
}

enum class PunchSource(val label: String) {
    MANUAL("手动录入"),
    BUTTON("按钮打卡");

    companion object {
        /** 按枚举名反查，供实体里的 source 字符串转回来源使用 */
        fun fromName(name: String): PunchSource? = entries.firstOrNull { it.name == name }
    }
}

enum class SessionType(val label: String) {
    REGULAR("正常工作"), OVERTIME("加班")
}

/** 工时引擎计算结果，formula 用于详情页追溯计算过程 */
data class CalculationResult(
    val workDate: LocalDate,
    val crossDay: Boolean,
    val workMinutes: Int,
    val overtimeMinutes: Int,
    val restMinutes: Int,
    val standardMinutes: Int,
    val bufferMinutes: Int,
    val formula: String
)
