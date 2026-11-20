/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/export/ReportExporter.kt
 * @Description: 报表导出工具，将汇总结果生成 CSV/Excel 兼容文件，供系统分享
 */
package com.worktime.pro.domain.export

import android.content.Context
import com.worktime.pro.data.db.PunchEventEntity
import com.worktime.pro.domain.model.DailyMinutes
import com.worktime.pro.domain.model.SummaryResult
import com.worktime.pro.util.Constants
import com.worktime.pro.util.TimeFormatter
import java.io.File

/** 导出格式：CSV 与 Excel 均用逗号分隔文本，Excel 以 UTF-8 BOM 开头保证中文不乱码 */
enum class ExportFormat(val extension: String) {
    CSV("csv"),
    EXCEL("xls")
}

data class ReportRow(
    val date: String,
    val weekday: String,
    val workStart: String,
    val workEnd: String,
    val otStart: String,
    val otEnd: String,
    val workMinutes: Int,
    val overtimeMinutes: Int
)

class ReportExporter(private val context: Context) {

    private val header = listOf("日期", "星期", "上班时间", "下班时间", "加班到", "加班退", "工作分钟", "加班分钟")

    /**
     * 导出报表。
     * 业务规则：四类时间从当日打卡事件按类型取首尾，与工时段分钟数合并成一行。
     * Excel 文件本质是制表符分隔的 UTF-8 文本，可被 Excel 正常打开，无需引入重型 POI 依赖。
     */
    fun export(
        result: SummaryResult,
        events: List<PunchEventEntity>,
        format: ExportFormat
    ): Result<File> = try {
        val eventsByDate = events.groupBy { it.workDate }
        val rows = result.daily.map { daily ->
            buildRow(daily, eventsByDate[daily.date].orEmpty())
        }
        val separator = if (format == ExportFormat.EXCEL) "\t" else ","
        val content = buildString {
            // UTF-8 BOM：Excel 打开 CSV/文本时识别中文编码
            append('\uFEFF')
            append(header.joinToString(separator)).append('\n')
            rows.forEach { row ->
                append(listOf(
                    row.date, row.weekday, row.workStart, row.workEnd,
                    row.otStart, row.otEnd,
                    row.workMinutes.toString(), row.overtimeMinutes.toString()
                ).joinToString(separator)).append('\n')
            }
        }
        val dir = File(context.filesDir, Constants.BACKUP_DIR).apply { mkdirs() }
        val file = File(dir, "工时报表_${result.startDate}_${result.endDate}.${format.extension}")
        file.writeText(content, Charsets.UTF_8)
        Result.success(file)
    } catch (e: Exception) {
        // 导出异常必须返回失败结果，由 UI 层提示用户，避免静默失败
        Result.failure(e)
    }

    private fun buildRow(daily: DailyMinutes, dayEvents: List<PunchEventEntity>): ReportRow {
        val date = TimeFormatter.parseDateKey(daily.date)
        fun firstTime(type: String) = dayEvents.firstOrNull { it.punchType == type }
            ?.let { TimeFormatter.formatTime(java.time.Instant.ofEpochMilli(it.punchTime)
                .atZone(java.time.ZoneId.systemDefault()).toLocalTime()) } ?: ""
        fun lastTime(type: String) = dayEvents.lastOrNull { it.punchType == type }
            ?.let { TimeFormatter.formatTime(java.time.Instant.ofEpochMilli(it.punchTime)
                .atZone(java.time.ZoneId.systemDefault()).toLocalTime()) } ?: ""
        return ReportRow(
            date = daily.date,
            weekday = TimeFormatter.weekdayCN(date),
            workStart = firstTime("WORK_START"),
            workEnd = lastTime("WORK_END"),
            otStart = firstTime("OT_START"),
            otEnd = lastTime("OT_END"),
            workMinutes = daily.workMinutes,
            overtimeMinutes = daily.overtimeMinutes
        )
    }
}
