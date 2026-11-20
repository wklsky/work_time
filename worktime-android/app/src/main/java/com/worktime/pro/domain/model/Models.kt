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
        /** OCR 识别出的中文关键词到类型的映射，供字段抽取器使用 */
        val KEYWORDS: Map<PunchType, List<String>> = mapOf(
            WORK_START to listOf("上班签到", "上班打卡", "上班签入", "上班"),
            WORK_END to listOf("下班签退", "下班打卡", "下班签出", "下班"),
            OT_START to listOf("加班签到", "加班开始", "加班签入", "加班"),
            OT_END to listOf("加班签退", "加班结束", "加班签出")
        )
    }
}

enum class PunchSource(val label: String) {
    OCR("拍照识别"),
    MANUAL("手动录入"),
    BUTTON("按钮打卡")
}

enum class Confidence(val label: String) {
    HIGH("高"), MEDIUM("中"), LOW("低")
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

/** 某日工时段汇总，供首页日历下方显示与日详情使用 */
data class DaySummary(
    val date: String,
    val regularMinutes: Int = 0,
    val overtimeMinutes: Int = 0,
    val firstWorkStart: String? = null,
    val lastWorkEnd: String? = null,
    val firstOtStart: String? = null,
    val lastOtEnd: String? = null,
    val recordCount: Int = 0
)
