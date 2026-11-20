/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/ocr/OcrFieldExtractor.kt
 * @Description: OCR 字段抽取器，针对中控考勤机表格屏幕解析日期时间、状态列数字并映射打卡类型
 */
package com.worktime.pro.domain.ocr

import com.google.mlkit.vision.text.Text
import com.worktime.pro.domain.model.Confidence
import com.worktime.pro.domain.model.PunchType
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime

/** 单个识别候选：类型 + 日期 + 时间 + 置信度，date/time 可能因识别不全而为空 */
data class OcrCandidate(
    val type: PunchType?,
    val date: LocalDate? = null,
    val time: LocalTime?,
    val confidence: Confidence,
    val rawText: String
)

class OcrFieldExtractor {
    // 考勤机表格行：工号 姓名 MM-DD HH:MM 验证 状态，日期与时间分行内固定格式
    private val dateTimeRegex = Regex("(\\d{1,2})[-/](\\d{1,2})\\s+(\\d{1,2}):(\\d{2})")
    private val timeRegex = Regex("(\\d{1,2}):(\\d{2})")

    /**
     * 从 ML Kit 识别结果抽取候选。
     * 业务规则：实拍考勤机为表格，每行"[工号][姓名][MM-DD HH:MM][F][状态数字]"；
     * 类型由末尾状态列数字判定（0/1/4/5），屏幕底部图例行"F:指纹 5:加班签退"无时间，自动排除。
     */
    fun extract(text: Text, photoDate: LocalDate = LocalDate.now()): List<OcrCandidate> =
        extractFromLines(text.textBlocks.flatMap { block -> block.lines.map { it.text } }, photoDate)

    /**
     * 基于文本行的抽取逻辑，剥离 ML Kit 依赖以便单元测试覆盖真实考勤机版式。
     * @param photoDate 拍照当天日期，用于把屏幕上的"MM-DD"补全为完整年份
     */
    fun extractFromLines(lines: List<String>, photoDate: LocalDate = LocalDate.now()): List<OcrCandidate> {
        val candidates = mutableListOf<OcrCandidate>()
        for (lineText in lines) {
            val dateTime = dateTimeRegex.find(lineText)
            if (dateTime != null) {
                // 表格记录行：解析日期时间 + 末尾状态列
                val month = dateTime.groupValues[1].toIntOrNull() ?: continue
                val day = dateTime.groupValues[2].toIntOrNull() ?: continue
                val hour = dateTime.groupValues[3].toIntOrNull() ?: continue
                val minute = dateTime.groupValues[4].toIntOrNull() ?: continue
                val date = completeYear(month, day, photoDate)
                val time = runCatching { LocalTime.of(hour, minute) }.getOrNull()
                if (date == null || time == null) continue

                val status = extractStatusCode(lineText, dateTime.range.last)
                val type = status?.let { PunchType.fromStatusCode(it) }
                if (type != null) {
                    candidates += OcrCandidate(type, date, time, Confidence.HIGH, lineText)
                } else {
                    // 有日期时间但状态列无法识别：留给用户确认页指定类型
                    candidates += OcrCandidate(null, date, time, Confidence.MEDIUM, lineText)
                }
            } else {
                // 非表格行：可能是纯时间或关键词行，作为兜底候选
                val keywordType = detectTypeByKeyword(lineText)
                val times = extractTimes(lineText)
                when {
                    keywordType != null && times.isNotEmpty() ->
                        candidates += OcrCandidate(keywordType, photoDate, times.first(), Confidence.MEDIUM, lineText)
                    times.isNotEmpty() ->
                        times.forEach { candidates += OcrCandidate(null, photoDate, it, Confidence.LOW, lineText) }
                }
            }
        }
        return candidates
    }

    /**
     * 提取状态列数字：取日期时间之后、行尾附近的独立 1 位数字。
     * 业务规则：考勤机状态列只可能是 0/1/4/5，行尾" F 5"中取最后的 5；
     * 工号（006962）等在日期之前，不会被误取。
     */
    private fun extractStatusCode(line: String, afterIndex: Int): String? {
        val tail = line.substring(afterIndex + 1)
        val matches = Regex("(?:^|\\D)([0-9])(?:\\D|$)").findAll(tail).toList()
        return matches.lastOrNull()?.groupValues?.get(1)
    }

    /** 屏幕日期只有 MM-DD，用拍照年份补全；跨年场景（12月拍到1月记录）按用户拍照年处理 */
    private fun completeYear(month: Int, day: Int, photoDate: LocalDate): LocalDate? =
        try {
            LocalDate.of(photoDate.year, month, day)
        } catch (e: DateTimeException) {
            null
        }

    private fun detectTypeByKeyword(text: String): PunchType? {
        val ordered = listOf(
            PunchType.OT_END, PunchType.OT_START, PunchType.WORK_START, PunchType.WORK_END
        )
        for (type in ordered) {
            if (PunchType.KEYWORDS[type].orEmpty().any { text.contains(it) }) return type
        }
        return null
    }

    private fun extractTimes(text: String): List<LocalTime> =
        timeRegex.findAll(text).mapNotNull { match ->
            val hour = match.groupValues[1].toIntOrNull()
            val minute = match.groupValues[2].toIntOrNull()
            if (hour != null && minute != null) runCatching { LocalTime.of(hour, minute) }.getOrNull() else null
        }.toList()
}
