/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/export/PdfReportWriter.kt
 * @Description: 使用系统 PdfDocument 生成工时报表，不引入第三方 PDF 依赖
 */
package com.worktime.pro.domain.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.worktime.pro.domain.model.SummaryResult
import com.worktime.pro.util.TimeFormatter
import java.io.File

object PdfReportWriter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 32f

    private val COLUMNS = listOf(
        "日期" to 64f,
        "星期" to 36f,
        "上班" to 52f,
        "下班" to 52f,
        "加班到" to 52f,
        "加班退" to 52f,
        "正常(分)" to 56f,
        "加班(分)" to 56f
    )

    fun write(file: File, result: SummaryResult, rows: List<ReportRow>) {
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            strokeWidth = 0.6f
        }

        var pageNumber = 1
        var page = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        )
        var canvas = page.canvas
        var y = MARGIN + 16f

        fun newPage() {
            document.finishPage(page)
            pageNumber += 1
            page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            )
            canvas = page.canvas
            y = MARGIN + 16f
        }

        fun drawRow(values: List<String>, bold: Boolean) {
            paint.isFakeBoldText = bold
            paint.textSize = 10f
            var x = MARGIN
            values.forEachIndexed { index, value ->
                canvas.drawText(value, x, y, paint)
                x += COLUMNS[index].second
            }
            paint.isFakeBoldText = false
        }

        // 标题与汇总
        paint.isFakeBoldText = true
        paint.textSize = 16f
        canvas.drawText("工时报表", MARGIN, y, paint)
        y += 22f
        paint.isFakeBoldText = false
        paint.textSize = 9f
        canvas.drawText("区间：${result.startDate} ~ ${result.endDate}", MARGIN, y, paint)
        y += 14f
        canvas.drawText(
            "正常 ${TimeFormatter.formatDuration(result.totalWorkMinutes)}   " +
                "加班 ${TimeFormatter.formatDuration(result.totalOvertimeMinutes)}   " +
                "出勤 ${result.attendanceDays} 天   " +
                "日均 ${TimeFormatter.formatDuration(result.averageWorkMinutes)}",
            MARGIN, y, paint
        )
        y += 20f

        drawRow(COLUMNS.map { it.first }, bold = true)
        y += 4f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 14f

        rows.forEach { row ->
            if (y > PAGE_HEIGHT - MARGIN - 12f) {
                newPage()
                drawRow(COLUMNS.map { it.first }, bold = true)
                y += 4f
                canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
                y += 14f
            }
            drawRow(
                listOf(
                    row.date,
                    row.weekday,
                    row.workStart,
                    row.workEnd,
                    row.otStart,
                    row.otEnd,
                    row.workMinutes.toString(),
                    row.overtimeMinutes.toString()
                ),
                bold = false
            )
            y += 14f
        }

        paint.textSize = 8f
        canvas.drawText(
            "第 $pageNumber 页",
            PAGE_WIDTH - MARGIN - 36f,
            PAGE_HEIGHT - MARGIN / 2,
            paint
        )
        document.finishPage(page)
        file.outputStream().use { document.writeTo(it) }
        document.close()
    }
}
