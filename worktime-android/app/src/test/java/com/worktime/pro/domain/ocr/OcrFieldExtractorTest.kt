/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/test/java/com/worktime/pro/domain/ocr/OcrFieldExtractorTest.kt
 * @Description: OCR 字段抽取测试，验证考勤机屏幕各类文本的类型识别与时间提取
 */
package com.worktime.pro.domain.ocr

import com.worktime.pro.domain.model.Confidence
import com.worktime.pro.domain.model.PunchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrFieldExtractorTest {
    private val extractor = OcrFieldExtractor()

    @Test
    fun `recognize work start with time`() {
        val result = extractor.extractFromLines(listOf("上班签到 09:02"))
        assertEquals(1, result.size)
        assertEquals(PunchType.WORK_START, result[0].type)
        assertEquals(9, result[0].time?.hour)
        assertEquals(2, result[0].time?.minute)
        assertEquals(Confidence.HIGH, result[0].confidence)
    }

    @Test
    fun `recognize overtime end with time`() {
        val result = extractor.extractFromLines(listOf("加班签退  21:05"))
        assertEquals(PunchType.OT_END, result[0].type)
        assertEquals(21, result[0].time?.hour)
    }

    @Test
    fun `overtime keyword not mistaken as work end`() {
        // 关键：加班签退含"签退"，必须优先识别为加班退而非下班
        val result = extractor.extractFromLines(listOf("加班签退 20:30"))
        assertEquals(PunchType.OT_END, result[0].type)
    }

    @Test
    fun `recognize work end with time`() {
        val result = extractor.extractFromLines(listOf("下班打卡 18:05"))
        assertEquals(PunchType.WORK_END, result[0].type)
        assertEquals(18, result[0].time?.hour)
    }

    @Test
    fun `type only without time is medium confidence`() {
        val result = extractor.extractFromLines(listOf("上班签到成功"))
        assertEquals(PunchType.WORK_START, result[0].type)
        assertNull(result[0].time)
        assertEquals(Confidence.MEDIUM, result[0].confidence)
    }

    @Test
    fun `time only without type is medium confidence`() {
        val result = extractor.extractFromLines(listOf("09:15"))
        assertNull(result[0].type)
        assertEquals(9, result[0].time?.hour)
        assertEquals(Confidence.MEDIUM, result[0].confidence)
    }

    @Test
    fun `multiple lines mixed`() {
        val result = extractor.extractFromLines(
            listOf("2026-11-20 星期五", "工号: 10086 姓名: 张三", "上班签到 09:00", "下班签退 18:30")
        )
        // 日期行含"11-20"无冒号不匹配时间；工号行无时间；后两行各识别一条
        assertEquals(2, result.size)
        assertEquals(PunchType.WORK_START, result[0].type)
        assertEquals(PunchType.WORK_END, result[1].type)
    }

    @Test
    fun `recognize overtime start`() {
        val result = extractor.extractFromLines(listOf("加班开始 19:10"))
        assertEquals(PunchType.OT_START, result[0].type)
        assertEquals(19, result[0].time?.hour)
    }
}
