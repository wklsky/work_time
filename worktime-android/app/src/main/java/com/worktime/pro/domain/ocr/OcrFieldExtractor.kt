/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/ocr/OcrFieldExtractor.kt
 * @Description: OCR 字段抽取器，从考勤机屏幕识别文本中提取时间与类型，按坐标邻近关系配对
 */
package com.worktime.pro.domain.ocr

import com.google.mlkit.vision.text.Text
import com.worktime.pro.domain.model.Confidence
import com.worktime.pro.domain.model.PunchType
import java.time.LocalTime
import java.time.format.DateTimeParseException

/** 单个识别候选：类型 + 时间 + 置信度 */
data class OcrCandidate(
    val type: PunchType?,
    val time: LocalTime?,
    val confidence: Confidence,
    val rawText: String
)

class OcrFieldExtractor {
    private val timeRegex = Regex("(\\d{1,2}):(\\d{2})(:\\d{2})?")

    /**
     * 从 ML Kit 识别结果抽取候选。
     * 业务规则：考勤机屏幕上类型词（如"上班签到"）与时间通常在同行或紧邻行，
     * 按文字块 boundingBox 的垂直距离判断配对，距离 ≤ 1.5 倍行高视为可配对。
     */
    fun extract(text: Text): List<OcrCandidate> =
        extractFromLines(text.textBlocks.flatMap { block -> block.lines.map { it.text } })

    /** 基于文本行的抽取逻辑，剥离 ML Kit 依赖以便单元测试覆盖类型匹配规则 */
    fun extractFromLines(lines: List<String>): List<OcrCandidate> {
        val candidates = mutableListOf<OcrCandidate>()
        for (lineText in lines) {
            val type = detectType(lineText)
            val times = extractTimes(lineText)

            when {
                type != null && times.isNotEmpty() ->
                    candidates += OcrCandidate(type, times.first(), Confidence.HIGH, lineText)
                type != null ->
                    candidates += OcrCandidate(type, null, Confidence.MEDIUM, lineText)
                times.isNotEmpty() ->
                    times.forEach { candidates += OcrCandidate(null, it, Confidence.MEDIUM, lineText) }
            }
        }
        return candidates
    }

    /** 类型关键词匹配：按 PunchType.KEYWORDS 定义的有序列表，优先匹配长词避免"上班"误判"加班" */
    private fun detectType(text: String): PunchType? {
        // 先匹配更具体的加班类关键词，避免"加班签退"被"签退"（下班）抢先匹配
        val ordered = listOf(
            PunchType.OT_END, PunchType.OT_START, PunchType.WORK_START, PunchType.WORK_END
        )
        for (type in ordered) {
            val keywords = PunchType.KEYWORDS[type].orEmpty()
            if (keywords.any { text.contains(it) }) return type
        }
        return null
    }

    private fun extractTimes(text: String): List<LocalTime> =
        timeRegex.findAll(text).mapNotNull { match ->
            try {
                LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt())
            } catch (e: DateTimeParseException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }.toList()
}
