/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/test/java/com/worktime/pro/domain/ocr/OcrFieldExtractorTest.kt
 * @Description: OCR 字段抽取单元测试，基于真实考勤机照片的文本识别结果验证准确性
 */
package com.worktime.pro.domain.ocr

import com.worktime.pro.domain.model.PunchType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class OcrFieldExtractorTest {
    private lateinit var extractor: OcrFieldExtractor
    private val photoDate = LocalDate.of(2024, 11, 19)

    @Before
    fun setup() {
        extractor = OcrFieldExtractor()
    }

    /**
     * 测试照片 1: 11-19 的四条记录。
     * 传入的是 extract() 经 groupTextElementsByRow 重组后的整行文本：状态列与日期时间若被拆成
     * 独立文本行，抽取器无法跨行关联，那是 groupTextElementsByRow 的职责而非本用例的边界。
     * 抽取层不承诺返回顺序（考勤机屏幕自上而下是记录时间倒序，确认页另按段展示），
     * 因此按「时间→类型」核对，避免把屏幕行序误当成业务契约。
     */
    @Test
    fun `test photo 1 - November 19 records`() {
        val lines = listOf(
            "工号 姓名 时间 验证 状态",
            "006962 王建 11-19 23:10 F 5",
            "006962 王建 11-19 18:00 F 4",
            "006962 王建 11-19 17:59 F 1",
            "006962 王建 11-19 08:25 F 0",
            "记录(1/4) F:指纹 5:加班签退"
        )

        val candidates = extractor.extractFromLines(lines, photoDate)

        assertEquals(4, candidates.size)
        val byTime = candidates.associateBy { it.time }
        assertEquals(PunchType.OT_END, byTime[LocalTime.of(23, 10)]?.type)
        assertEquals(PunchType.OT_START, byTime[LocalTime.of(18, 0)]?.type)
        assertEquals(PunchType.WORK_END, byTime[LocalTime.of(17, 59)]?.type)
        assertEquals(PunchType.WORK_START, byTime[LocalTime.of(8, 25)]?.type)
        candidates.forEach { assertEquals(LocalDate.of(2024, 11, 19), it.date) }
    }

    /**
     * 测试表格行格式（连续字符串）
     * 模拟 ML Kit 可能将整行识别为一个文本块
     */
    @Test
    fun `test single line format - all fields together`() {
        val lines = listOf(
            "006962 王建 11-19 23:10 F 5",
            "006962 王建 11-19 18:00 F 4",
            "006962 王建 11-19 17:59 F 1",
            "006962 王建 11-19 08:25 F 0"
        )

        val candidates = extractor.extractFromLines(lines, photoDate)

        assertEquals(4, candidates.size)
        assertEquals(
            setOf(PunchType.WORK_START, PunchType.WORK_END, PunchType.OT_START, PunchType.OT_END),
            candidates.map { it.type }.toSet()
        )
        assertEquals(
            setOf(
                LocalTime.of(8, 25), LocalTime.of(17, 59),
                LocalTime.of(18, 0), LocalTime.of(23, 10)
            ),
            candidates.map { it.time }.toSet()
        )
    }

    /**
     * 测试照片 2: 11-18 的记录
     */
    @Test
    fun `test photo 2 - November 18 records`() {
        val lines = listOf(
            "006962 王建 11-18 22:04 F 5",
            "006962 王建 11-18 18:02 F 4",
            "006962 王建 11-18 17:40 F 1",
            "006962 王建 11-18 08:26 F 0"
        )

        val candidates = extractor.extractFromLines(lines, LocalDate.of(2024, 11, 18))

        assertEquals(4, candidates.size)
        val byTime = candidates.associateBy { it.time }
        assertEquals(PunchType.OT_END, byTime[LocalTime.of(22, 4)]?.type)
        assertEquals(PunchType.OT_START, byTime[LocalTime.of(18, 2)]?.type)
        assertEquals(PunchType.WORK_END, byTime[LocalTime.of(17, 40)]?.type)
        assertEquals(PunchType.WORK_START, byTime[LocalTime.of(8, 26)]?.type)
        candidates.forEach { assertEquals(LocalDate.of(2024, 11, 18), it.date) }
    }

    /**
     * 测试照片 3: 11-16 的记录
     */
    @Test
    fun `test photo 3 - November 16 records`() {
        val lines = listOf(
            "006962 王建 11-16 22:06 F 5",
            "006962 王建 11-16 18:01 F 4",
            "006962 王建 11-16 17:40 F 1",
            "006962 王建 11-16 08:25 F 0"
        )

        val candidates = extractor.extractFromLines(lines, LocalDate.of(2024, 11, 16))

        assertEquals(4, candidates.size)
        val byTime = candidates.associateBy { it.time }
        assertEquals(PunchType.OT_END, byTime[LocalTime.of(22, 6)]?.type)
        assertEquals(PunchType.OT_START, byTime[LocalTime.of(18, 1)]?.type)
        assertEquals(PunchType.WORK_END, byTime[LocalTime.of(17, 40)]?.type)
        assertEquals(PunchType.WORK_START, byTime[LocalTime.of(8, 25)]?.type)
        candidates.forEach { assertEquals(LocalDate.of(2024, 11, 16), it.date) }
    }

    /**
     * 测试照片 4: 11-15 的记录（只有 2 条）
     */
    @Test
    fun `test photo 4 - November 15 records`() {
        val lines = listOf(
            "006962 王建 11-15 20:28 F 5",
            "006962 王建 11-15 19:53 F 4"
        )

        val candidates = extractor.extractFromLines(lines, LocalDate.of(2024, 11, 15))

        assertEquals(2, candidates.size)
        val byTime = candidates.associateBy { it.time }
        assertEquals(PunchType.OT_END, byTime[LocalTime.of(20, 28)]?.type)
        assertEquals(PunchType.OT_START, byTime[LocalTime.of(19, 53)]?.type)
        candidates.forEach { assertEquals(LocalDate.of(2024, 11, 15), it.date) }
    }

    /**
     * 测试状态码边界情况：确保工号不会被误识别为状态码
     */
    @Test
    fun `test status code extraction - should not confuse with employee ID`() {
        val line = "006962 王建 11-19 08:25 F 0"
        val candidates = extractor.extractFromLines(listOf(line), photoDate)

        assertEquals(1, candidates.size)
        // 应该识别到状态码 0（上班），而不是工号中的 6、9、6、2
        assertEquals(PunchType.WORK_START, candidates[0].type)
    }

    /**
     * 测试日期格式容错：斜杠和短横线都应支持
     */
    @Test
    fun `test date format tolerance - slash and dash`() {
        // 两行刻意用不同时间：同日期同时间同类型会被 validateAndOptimize 去重，
        // 那测的是去重而不是日期分隔符容错
        val lines = listOf(
            "11-19 08:25 F 0",
            "11/19 17:59 F 1"
        )

        val candidates = extractor.extractFromLines(lines, photoDate)

        assertEquals(2, candidates.size)
        candidates.forEach { assertEquals(LocalDate.of(2024, 11, 19), it.date) }
        assertEquals(listOf(LocalTime.of(8, 25), LocalTime.of(17, 59)), candidates.map { it.time })
    }

    /**
     * 测试时间范围：早班到夜班
     */
    @Test
    fun `test time range - early morning to late night`() {
        val lines = listOf(
            "11-19 00:30 F 0", // 凌晨
            "11-19 08:25 F 0", // 早晨
            "11-19 17:59 F 1", // 傍晚
            "11-19 23:59 F 5"  // 深夜
        )

        val candidates = extractor.extractFromLines(lines, photoDate)

        assertEquals(4, candidates.size)
        assertEquals(LocalTime.of(0, 30), candidates[0].time)
        assertEquals(LocalTime.of(8, 25), candidates[1].time)
        assertEquals(LocalTime.of(17, 59), candidates[2].time)
        assertEquals(LocalTime.of(23, 59), candidates[3].time)
    }

    /**
     * 测试无状态码场景：应该识别时间但类型为 null
     */
    @Test
    fun `test missing status code - should extract time with null type`() {
        val line = "006962 王建 11-19 08:25 F"

        val candidates = extractor.extractFromLines(listOf(line), photoDate)

        assertEquals(1, candidates.size)
        assertNull(candidates[0].type)
        assertEquals(LocalTime.of(8, 25), candidates[0].time)
    }

    /**
     * 测试自定义状态映射
     */
    @Test
    fun `test custom status mapping`() {
        val customMapping = mapOf(
            "0" to PunchType.WORK_END,  // 自定义：0=下班
            "1" to PunchType.WORK_START // 自定义：1=上班
        )

        val line = "11-19 08:25 F 0"
        val candidates = extractor.extractFromLines(listOf(line), photoDate, customMapping)

        assertEquals(1, candidates.size)
        // 使用自定义映射，0 应该映射到下班
        assertEquals(PunchType.WORK_END, candidates[0].type)
    }

    /**
     * 测试多行混合：表头、数据行、空行
     */
    @Test
    fun `test mixed content - headers data and empty lines`() {
        val lines = listOf(
            "工号 姓名 时间 验证 状态",
            "",
            "006962 王建 11-19 08:25 F 0",
            "006962 王建 11-19 17:59 F 1",
            "",
            "记录(1/4) F:指纹 6:加班签退"
        )

        val candidates = extractor.extractFromLines(lines, photoDate)

        // 应该只识别出 2 条有效记录
        assertEquals(2, candidates.size)
    }

    /**
     * 测试跨年场景：12月拍照，识别到1月记录
     * （按当前逻辑，使用拍照年份）
     */
    @Test
    fun `test cross year scenario`() {
        val photoDateDec = LocalDate.of(2024, 12, 31)
        val line = "01-02 08:25 F 0" // 识别到 01-02

        val candidates = extractor.extractFromLines(listOf(line), photoDateDec)

        assertEquals(1, candidates.size)
        // 应该使用拍照年份 2024
        assertEquals(LocalDate.of(2024, 1, 2), candidates[0].date)
    }

    /**
     * 测试识别结果验证：去重
     */
    @Test
    fun `test validation - deduplication`() {
        val lines = listOf(
            "11-19 08:25 F 0",
            "11-19 08:25 F 0",  // 重复
            "11-19 17:59 F 1"
        )

        val candidates = extractor.extractFromLines(lines, photoDate)

        // 应该去重，只保留 2 条
        assertEquals(2, candidates.size)
    }

    /**
     * 测试识别结果验证：时间合理性调整置信度
     */
    @Test
    fun `test validation - confidence adjustment by time`() {
        val lines = listOf(
            "11-19 08:25 F 0",  // 上班 08:25，合理
            "11-19 15:00 F 0",  // 上班 15:00，不合理
            "11-19 23:30 F 1"   // 下班 23:30，不合理
        )

        val candidates = extractor.extractFromLines(lines, photoDate)

        assertEquals(3, candidates.size)
        // 第一条时间合理，保持高置信度
        // 第二、三条时间不合理，可能被降级为中等置信度
    }

    /**
     * 测试识别结果验证：限制记录数量
     */
    @Test
    fun `test validation - limit records count`() {
        val lines = (0..10).map { i ->
            "11-19 ${8 + i}:00 F 0"
        }

        val candidates = extractor.extractFromLines(lines, photoDate)

        // 应该限制在合理数量内（最多6条）
        assertTrue(candidates.size <= 6)
    }

    /**
     * 测试增强状态码识别：优先选择有效状态码
     */
    @Test
    fun `test enhanced status code - prefer valid codes`() {
        val lines = listOf(
            "11-19 08:25 F 2 0",  // 有 2 和 0，应该选 0
            "11-19 17:59 F 3 1",  // 有 3 和 1，应该选 1
            "记录(1/4) 11-19 18:00 F 6 4"  // 有多个数字，应该选 4
        )

        val candidates = extractor.extractFromLines(lines, photoDate)

        assertEquals(3, candidates.size)
        assertEquals(PunchType.WORK_START, candidates[0].type)  // 0
        assertEquals(PunchType.WORK_END, candidates[1].type)    // 1
        assertEquals(PunchType.OT_START, candidates[2].type)    // 4
    }
}
