/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/test/java/com/worktime/pro/domain/ocr/OcrFieldExtractorTest.kt
 * @Description: OCR 字段抽取测试，用实拍中控考勤机表格文本验证状态列判定与日期补全
 */
package com.worktime.pro.domain.ocr

import com.worktime.pro.domain.model.Confidence
import com.worktime.pro.domain.model.PunchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class OcrFieldExtractorTest {
    private val extractor = OcrFieldExtractor()
    private val photoDate = LocalDate.of(2026, 11, 20)

    /** 实拍样本：11-19 的完整一天，四行按屏幕自上而下（新→旧）排列 */
    private val realScreenLines = listOf(
        "工号 姓名 时间 验证 状态",
        "006962 王建 11-19 23:10 F 5",
        "006962 王建 11-19 18:00 F 4",
        "006962 王建 11-19 17:59 F 1",
        "006962 王建 11-19 08:25 F 0",
        "记录(1/4)   F:指纹  5:加班签退"
    )

    @Test
    fun `parse real attendance machine screen all four types`() {
        val result = extractor.extractFromLines(realScreenLines, photoDate)
        assertEquals(4, result.size)
        // 屏幕自上而下：状态5加班退 → 4加班到 → 1下班 → 0上班
        assertEquals(PunchType.OT_END, result[0].type)
        assertEquals(PunchType.OT_START, result[1].type)
        assertEquals(PunchType.WORK_END, result[2].type)
        assertEquals(PunchType.WORK_START, result[3].type)
        assertEquals(23, result[0].time?.hour)
        assertEquals(10, result[0].time?.minute)
    }

    @Test
    fun `status column maps to correct type`() {
        val result = extractor.extractFromLines(realScreenLines, photoDate)
        // 全部来自状态列，应为高置信度
        result.forEach { assertEquals(Confidence.HIGH, it.confidence) }
    }

    @Test
    fun `date without year completed by photo date`() {
        val result = extractor.extractFromLines(realScreenLines, photoDate)
        result.forEach { assertEquals(LocalDate.of(2026, 11, 19), it.date) }
    }

    @Test
    fun `legend line excluded even with overtime keyword`() {
        // 图例行"5:加班签退"含加班关键词但无日期时间，必须被排除，否则会多出候选
        val result = extractor.extractFromLines(realScreenLines, photoDate)
        assertEquals(4, result.size)
        assert(result.none { it.rawText.contains("记录(1/4)") })
    }

    @Test
    fun `overtime only screen with two records`() {
        // 实拍 11-15：当天只有加班签到/签退两条
        val lines = listOf(
            "006962 王建 11-15 20:28 F 5",
            "006962 王建 11-15 19:53 F 4",
            "记录(1/2)   F:指纹  5:加班签退"
        )
        val result = extractor.extractFromLines(lines, photoDate)
        assertEquals(2, result.size)
        assertEquals(PunchType.OT_END, result[0].type)
        assertEquals(PunchType.OT_START, result[1].type)
        assertEquals(20, result[0].time?.hour)
        assertEquals(28, result[0].time?.minute)
    }

    @Test
    fun `missing status leaves type null for user to confirm`() {
        val lines = listOf("006962 王建 11-19 08:25 F")
        val result = extractor.extractFromLines(lines, photoDate)
        assertEquals(1, result.size)
        assertNull(result[0].type)
        assertEquals(Confidence.MEDIUM, result[0].confidence)
    }

    @Test
    fun `fallback keyword line still recognized`() {
        val result = extractor.extractFromLines(listOf("上班签到 09:02"), photoDate)
        assertEquals(PunchType.WORK_START, result[0].type)
        assertEquals(9, result[0].time?.hour)
    }

    @Test
    fun `invalid date skipped`() {
        val result = extractor.extractFromLines(listOf("006962 王建 13-40 08:25 F 0"), photoDate)
        assertEquals(0, result.size)
    }

    @Test
    fun `custom mapping overrides default`() {
        // 用户自定义：状态3=上班、状态6=下班，与默认编码完全不同
        val custom = mapOf(
            "3" to PunchType.WORK_START,
            "6" to PunchType.WORK_END
        )
        val lines = listOf(
            "001 张三 11-19 08:30 F 3",
            "001 张三 11-19 18:30 F 6"
        )
        val result = extractor.extractFromLines(lines, photoDate, custom)
        assertEquals(2, result.size)
        assertEquals(PunchType.WORK_START, result[0].type)
        assertEquals(PunchType.WORK_END, result[1].type)
    }

    @Test
    fun `unmapped status returns null type`() {
        // 自定义映射未包含状态5，该行应为中置信度待用户指定
        val custom = mapOf("0" to PunchType.WORK_START)
        val result = extractor.extractFromLines(
            listOf("001 张三 11-19 23:10 F 5"), photoDate, custom
        )
        assertEquals(1, result.size)
        assertNull(result[0].type)
        assertEquals(Confidence.MEDIUM, result[0].confidence)
    }

    @Test
    fun `status mapping serialize and deserialize`() {
        val map = mapOf(
            "0" to PunchType.WORK_START,
            "4" to PunchType.OT_START,
            "5" to PunchType.OT_END
        )
        val serialized = StatusMapping.serialize(map)
        assertEquals(map, StatusMapping.deserialize(serialized))
    }

    @Test
    fun `deserialize skips malformed entries`() {
        val value = "0=WORK_START,bad,1=UNKNOWN,4=OT_START"
        val result = StatusMapping.deserialize(value)
        assertEquals(2, result.size)
        assertEquals(PunchType.WORK_START, result["0"])
        assertEquals(PunchType.OT_START, result["4"])
    }
}
