/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/model/SummaryModels.kt
 * @Description: 统计汇总领域模型，定义五种汇总维度、每日工时与区间汇总结果结构
 */
package com.worktime.pro.domain.model

/** 汇总维度：日 / 星期 / 月 / 年 / 自定义 */
enum class SummaryRange(val label: String) {
    DAY("日"),
    WEEK("星期"),
    MONTH("月"),
    YEAR("年"),
    CUSTOM("自定义")
}

/** 某日的工时明细（由工时段聚合而来），date 格式 yyyy-MM-dd，字典序即时间序 */
data class DailyMinutes(
    val date: String,
    val workMinutes: Int = 0,
    val overtimeMinutes: Int = 0
)

/** 区间汇总结果，供统计页与报表导出共用 */
data class SummaryResult(
    val range: SummaryRange,
    val startDate: String,
    val endDate: String,
    val totalWorkMinutes: Int,
    val totalOvertimeMinutes: Int,
    val attendanceDays: Int,
    val averageWorkMinutes: Int,
    val daily: List<DailyMinutes>
)
