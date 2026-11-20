/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/export/XlsxWriter.kt
 * @Description: 零依赖的最小 XLSX 生成器，用内联字符串与数字写出单工作表
 */
package com.worktime.pro.domain.export

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxWriter {

    /**
     * 写出单工作表 XLSX。
     * 业务说明：不引入 POI/Fastexcel，避免包体与构建依赖；表格只有文本与数字两列型。
     */
    fun write(file: File, sheetName: String, header: List<String>, rows: List<List<Any?>>) {
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.putText("[Content_Types].xml", CONTENT_TYPES)
            zip.putText("_rels/.rels", ROOT_RELS)
            zip.putText("xl/workbook.xml", workbookXml(sheetName))
            zip.putText("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            zip.putText("xl/styles.xml", STYLES)
            zip.putText("xl/worksheets/sheet1.xml", sheetXml(header, rows))
        }
    }

    private fun ZipOutputStream.putText(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun sheetXml(header: List<String>, rows: List<List<Any?>>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("<sheetData>")
        appendRow(1, header)
        rows.forEachIndexed { index, row -> appendRow(index + 2, row) }
        append("</sheetData></worksheet>")
    }

    private fun StringBuilder.appendRow(rowNumber: Int, values: List<Any?>) {
        append("""<row r="$rowNumber">""")
        values.forEachIndexed { column, value ->
            val ref = "${columnLetter(column)}$rowNumber"
            if (value is Number) {
                append("""<c r="$ref"><v>${value}</v></c>""")
            } else if (value != null) {
                append("""<c r="$ref" t="inlineStr"><is><t>${escape(value.toString())}</t></is></c>""")
            }
        }
        append("</row>")
    }

    private fun columnLetter(index: Int): String {
        var value = index
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + value % 26))
            value = value / 26 - 1
            if (value < 0) break
        }
        return sb.toString()
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun workbookXml(sheetName: String): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets><sheet name="${escape(sheetName.take(31))}" sheetId="1" r:id="rId1"/></sheets>
        </workbook>
    """.trimIndent()

    private val CONTENT_TYPES = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
          <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
        </Types>
    """.trimIndent()

    private val ROOT_RELS = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
    """.trimIndent()

    private val WORKBOOK_RELS = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
        </Relationships>
    """.trimIndent()

    private val STYLES = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
          <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
          <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
          <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
          <cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>
          <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
        </styleSheet>
    """.trimIndent()
}
