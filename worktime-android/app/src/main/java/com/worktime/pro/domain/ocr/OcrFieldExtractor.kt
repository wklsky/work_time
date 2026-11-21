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
     * 类型由末尾状态列数字按用户自定义映射判定，屏幕底部图例行无时间，自动排除。
     */
    fun extract(
        text: Text,
        photoDate: LocalDate = LocalDate.now(),
        statusMapping: Map<String, PunchType> = StatusMapping.DEFAULT
    ): List<OcrCandidate> {
        // 按行重组文本：ML Kit 可能将一行拆分成多个文本块
        val lines = groupTextElementsByRow(text)
        return extractFromLines(lines, photoDate, statusMapping)
    }

    /**
     * 按行重组文本元素：将同一水平线上的文本块按从左到右顺序拼接
     */
    private fun groupTextElementsByRow(text: Text): List<String> {
        val elements = mutableListOf<TextElement>()

        // 收集所有文本元素及其位置
        for (block in text.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    element.boundingBox?.let { box ->
                        elements.add(TextElement(element.text, box))
                    }
                }
            }
        }

        if (elements.isEmpty()) {
            return text.textBlocks.flatMap { block -> block.lines.map { it.text } }
        }

        // 按 Y 坐标排序
        val sorted = elements.sortedBy { it.box.top }
        val rows = mutableListOf<MutableList<TextElement>>()
        var currentRow = mutableListOf(sorted.first())

        for (i in 1 until sorted.size) {
            val current = sorted[i]
            val previous = sorted[i - 1]

            // Y 坐标差异小于阈值则认为在同一行
            val yThreshold = previous.box.height() * 0.6f
            if (kotlin.math.abs(current.box.top - previous.box.top) < yThreshold) {
                currentRow.add(current)
            } else {
                rows.add(currentRow)
                currentRow = mutableListOf(current)
            }
        }
        rows.add(currentRow)

        // 每行内按 X 坐标排序并拼接
        return rows.map { row ->
            row.sortedBy { it.box.left }
                .joinToString(" ") { it.text }
        }
    }

    private data class TextElement(
        val text: String,
        val box: android.graphics.Rect
    )

    /**
     * 基于文本行的抽取逻辑，剥离 ML Kit 依赖以便单元测试覆盖真实考勤机版式。
     * @param photoDate 拍照当天日期，用于把屏幕上的"MM-DD"补全为完整年份
     * @param statusMapping 用户自定义的状态数字→类型映射
     */
    fun extractFromLines(
        lines: List<String>,
        photoDate: LocalDate = LocalDate.now(),
        statusMapping: Map<String, PunchType> = StatusMapping.DEFAULT
    ): List<OcrCandidate> {
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
                val type = status?.let { statusMapping[it] }
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

        // 验证和优化识别结果
        return validateAndOptimize(candidates)
    }

    /**
     * 验证识别结果的合理性，提升准确率
     * - 过滤重复记录
     * - 调整置信度
     * - 按日期时间升序返回（确认页按「第 1 段…第 N 段」展示）
     * - 限制记录数量（考虑多天记录最多6条）
     */
    private fun validateAndOptimize(candidates: List<OcrCandidate>): List<OcrCandidate> {
        return candidates
            .distinctBy { "${it.date}_${it.time}_${it.type}" }
            .map { candidate -> adjustConfidenceByTime(candidate) }
            .sortedWith(compareBy({ it.date }, { it.time }))
            .take(6)
    }

    /**
     * 根据打卡时间合理性调整置信度
     */
    private fun adjustConfidenceByTime(candidate: OcrCandidate): OcrCandidate {
        val time = candidate.time ?: return candidate
        val hour = time.hour

        val isReasonable = when (candidate.type) {
            PunchType.WORK_START -> hour in 6..10  // 上班通常在 6-10 点
            PunchType.WORK_END -> hour in 16..20   // 下班通常在 16-20 点
            PunchType.OT_START -> hour in 17..23   // 加班开始在 17-23 点
            PunchType.OT_END -> hour in 18..23 || hour in 0..2  // 加班结束在 18-23 点或凌晨
            null -> true // 类型未知，不调整
        }

        // 如果时间不合理，降低置信度
        return if (!isReasonable && candidate.confidence == Confidence.HIGH) {
            candidate.copy(confidence = Confidence.MEDIUM)
        } else {
            candidate
        }
    }

    /**
     * 提取状态列数字：取日期时间之后、行尾附近的独立 1 位数字。
     * 业务规则：考勤机状态列只可能是 0/1/4/5，行尾" F 5"中取最后的 5；
     * 工号（006962）等在日期之前，不会被误取。
     *
     * 优化：优先识别有效状态码（0/1/4/5），过滤其他数字干扰
     */
    private fun extractStatusCode(line: String, afterIndex: Int): String? {
        val tail = line.substring(afterIndex + 1)

        // 用前后断言而非 (?:^|\D) / (?:\D|$) 消费分隔符：形如 " F 2 0" 时，
        // 旧写法会把 "2" 后的空格吃掉，导致紧邻的 "0" 匹配不到、状态码误判为 2。
        val allMatches = Regex("(?<![0-9])([0-9])(?![0-9])")
            .findAll(tail)
            .map { it.groupValues[1] }
            .toList()

        if (allMatches.isEmpty()) return null

        // 优先选择有效状态码 0/1/4/5
        val validCodes = allMatches.filter { it in listOf("0", "1", "4", "5") }

        // 如果有有效状态码，取最后一个；否则取最后一个数字
        return validCodes.lastOrNull() ?: allMatches.lastOrNull()
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
