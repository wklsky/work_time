/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/test/java/com/worktime/pro/domain/update/UpdateResponseParserTest.kt
 * @Description: Release 响应解析单测，覆盖发版时常见的资产缺失与版本判定场景
 */
package com.worktime.pro.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateResponseParserTest {

    @Test
    fun `解析出 apk 资产与更新说明`() {
        val body = """
            {
              "tag_name": "v2.1.0",
              "body": "新增自动更新",
              "assets": [
                {"name": "app-release.apk", "size": 65263446, "browser_download_url": "https://example.com/app-release.apk"}
              ]
            }
        """.trimIndent()

        val result = UpdateResponseParser.parse(body, currentVersionName = "2.0.0")

        assertTrue(result is UpdateCheckResult.Available)
        val info = (result as UpdateCheckResult.Available).info
        assertEquals("2.1.0", info.versionName)
        assertEquals("新增自动更新", info.notes)
        assertEquals("https://example.com/app-release.apk", info.apkUrl)
        assertEquals(65263446L, info.sizeBytes)
    }

    @Test
    fun `资产缺失时回落固定直链`() {
        // 发版时漏传 APK 会走到这里：仍需给出可下载地址，不能直接判失败
        val body = """{"tag_name": "v2.1.0", "body": "", "assets": []}"""

        val result = UpdateResponseParser.parse(body, currentVersionName = "2.0.0")

        val info = (result as UpdateCheckResult.Available).info
        assertEquals(UpdateConfig.FALLBACK_APK_URL, info.apkUrl)
        assertEquals(0L, info.sizeBytes)
    }

    @Test
    fun `资产名不是约定名字时取第一个 apk`() {
        val body = """
            {
              "tag_name": "v2.1.0",
              "assets": [
                {"name": "source.zip", "browser_download_url": "https://example.com/source.zip"},
                {"name": "worktime-2.1.0.apk", "size": 100, "browser_download_url": "https://example.com/w.apk"}
              ]
            }
        """.trimIndent()

        val result = UpdateResponseParser.parse(body, currentVersionName = "2.0.0")

        assertEquals("https://example.com/w.apk", (result as UpdateCheckResult.Available).info.apkUrl)
    }

    @Test
    fun `同版本或更旧版本判定为已是最新`() {
        assertTrue(
            UpdateResponseParser.parse("""{"tag_name": "v2.1.0"}""", "2.1.0") is UpdateCheckResult.UpToDate
        )
        assertTrue(
            UpdateResponseParser.parse("""{"tag_name": "v2.0.0"}""", "2.1.0") is UpdateCheckResult.UpToDate
        )
    }

    @Test
    fun `tag 缺少版本号时报错而不是崩溃`() {
        assertTrue(
            UpdateResponseParser.parse("""{"tag_name": ""}""", "2.0.0") is UpdateCheckResult.Failed
        )
        assertTrue(
            UpdateResponseParser.parse("""{}""", "2.0.0") is UpdateCheckResult.Failed
        )
    }

    @Test
    fun `非法 JSON 返回解析失败`() {
        assertTrue(UpdateResponseParser.parse("not json", "2.0.0") is UpdateCheckResult.Failed)
    }
}
