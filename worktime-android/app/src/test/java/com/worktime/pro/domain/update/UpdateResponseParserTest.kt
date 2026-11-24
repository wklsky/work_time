/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/test/java/com/worktime/pro/domain/update/UpdateResponseParserTest.kt
 * @Description: 更新信息解析单测，覆盖 Release 响应与版本清单两种格式的资产缺失与版本判定场景
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
        // 候选列表首位必须是 GitHub 直链，其后才是加速代理
        assertEquals("https://example.com/app-release.apk", info.downloadUrls.first())
        assertEquals(65263446L, info.sizeBytes)
    }

    @Test
    fun `直链候选列表自动追加加速代理`() {
        val body = """
            {
              "tag_name": "v2.1.0",
              "assets": [
                {"name": "WorkTime.apk", "browser_download_url": "https://github.com/a/b/releases/download/v2.1.0/WorkTime.apk"}
              ]
            }
        """.trimIndent()

        val info = (UpdateResponseParser.parse(body, "2.0.0") as UpdateCheckResult.Available).info

        assertEquals(UpdateConfig.APK_PROXY_PREFIXES.size + 1, info.downloadUrls.size)
        UpdateConfig.APK_PROXY_PREFIXES.forEachIndexed { index, prefix ->
            assertEquals(prefix + info.downloadUrls.first(), info.downloadUrls[index + 1])
        }
    }

    @Test
    fun `资产缺失时回落固定直链`() {
        // 发版时漏传 APK 会走到这里：仍需给出可下载地址，不能直接判失败
        val body = """{"tag_name": "v2.1.0", "body": "", "assets": []}"""

        val info = (UpdateResponseParser.parse(body, currentVersionName = "2.0.0") as UpdateCheckResult.Available).info

        assertEquals(UpdateConfig.FALLBACK_APK_URL, info.downloadUrls.first())
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

        assertEquals("https://example.com/w.apk", (result as UpdateCheckResult.Available).info.downloadUrls.first())
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

    // ---- 版本清单（update/update.json）格式 ----

    @Test
    fun `清单解析出新版本与下载候选`() {
        val body = """
            {
              "versionName": "2.2.0",
              "notes": "修复若干问题",
              "apkUrl": "https://github.com/wklsky/work_time/releases/download/v2.2.0/WorkTime.apk",
              "sizeBytes": 65263446
            }
        """.trimIndent()

        val result = UpdateResponseParser.parseManifest(body, currentVersionName = "2.1.0")

        assertTrue(result is UpdateCheckResult.Available)
        val info = (result as UpdateCheckResult.Available).info
        assertEquals("2.2.0", info.versionName)
        assertEquals("修复若干问题", info.notes)
        assertEquals("https://github.com/wklsky/work_time/releases/download/v2.2.0/WorkTime.apk", info.downloadUrls.first())
        assertEquals(65263446L, info.sizeBytes)
    }

    @Test
    fun `清单同版本判定为已是最新`() {
        val body = """{"versionName": "2.1.0", "apkUrl": "https://example.com/w.apk"}"""

        assertTrue(
            UpdateResponseParser.parseManifest(body, currentVersionName = "2.1.0") is UpdateCheckResult.UpToDate
        )
        assertTrue(
            UpdateResponseParser.parseManifest(body, currentVersionName = "2.2.0") is UpdateCheckResult.UpToDate
        )
    }

    @Test
    fun `清单缺少下载地址时报错并提示浏览器兜底`() {
        // 清单由发布者手填，漏填 apkUrl 时不能给用户一个点了就崩的下载按钮
        val body = """{"versionName": "2.2.0", "notes": ""}"""

        val result = UpdateResponseParser.parseManifest(body, currentVersionName = "2.1.0")

        assertTrue(result is UpdateCheckResult.Failed)
        assertTrue((result as UpdateCheckResult.Failed).message.contains("发布页"))
    }

    @Test
    fun `清单缺少版本号或非法 JSON 返回解析失败`() {
        assertTrue(
            UpdateResponseParser.parseManifest("""{"notes": "x"}""", "2.0.0") is UpdateCheckResult.Failed
        )
        assertTrue(UpdateResponseParser.parseManifest("not json", "2.0.0") is UpdateCheckResult.Failed)
    }
}
