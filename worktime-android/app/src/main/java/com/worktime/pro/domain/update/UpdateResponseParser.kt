/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/update/UpdateResponseParser.kt
 * @Description: 更新信息解析（GitHub Release 响应 + 仓库版本清单两种格式），纯函数便于单测覆盖发版异常场景
 */
package com.worktime.pro.domain.update

import org.json.JSONObject

object UpdateResponseParser {

    /**
     * 解析 `releases/latest` 响应体。
     * 约定 tag 为 `vX.Y.Z`，只取 tag 比对而不读 APK 内的 versionCode——
     * 拿 versionCode 必须先下载整个安装包，代价不可接受。
     */
    fun parse(body: String, currentVersionName: String): UpdateCheckResult {
        val json = try {
            JSONObject(body)
        } catch (e: Exception) {
            return UpdateCheckResult.Failed("更新信息解析失败")
        }

        val tag = json.optString("tag_name").trim()
        val version = tag.removePrefix("v").removePrefix("V").trim()
        if (version.isEmpty()) return UpdateCheckResult.Failed("发布信息缺少版本号")

        if (!VersionComparator.isNewer(version, currentVersionName)) {
            return UpdateCheckResult.UpToDate
        }

        // 资产列表可能为空（发版时漏传 APK），此时回落固定直链，保证仍能下载
        val assets = json.optJSONArray("assets")
        val apkAsset = assets?.let { list ->
            val candidates = (0 until list.length()).mapNotNull { list.optJSONObject(it) }
            candidates.firstOrNull { it.optString("name").equals(UpdateConfig.APK_ASSET_NAME, ignoreCase = true) }
                ?: candidates.firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
        }

        val directUrl = apkAsset?.optString("browser_download_url")?.takeIf { it.isNotBlank() }
            ?: UpdateConfig.FALLBACK_APK_URL
        return UpdateCheckResult.Available(
            UpdateInfo(
                versionName = version,
                notes = json.optString("body").trim(),
                downloadUrls = UpdateConfig.buildDownloadUrls(directUrl),
                sizeBytes = apkAsset?.optLong("size") ?: 0L
            )
        )
    }

    /**
     * 解析仓库内 `update/update.json` 版本清单（jsDelivr/raw 镜像源返回的格式）。
     * 清单字段比 Release API 精简，是 API 被限流或国内直连超时时的兜底数据源。
     */
    fun parseManifest(body: String, currentVersionName: String): UpdateCheckResult {
        val json = try {
            JSONObject(body)
        } catch (e: Exception) {
            return UpdateCheckResult.Failed("更新信息解析失败")
        }

        val version = json.optString("versionName").trim()
        if (version.isEmpty()) return UpdateCheckResult.Failed("发布信息缺少版本号")

        if (!VersionComparator.isNewer(version, currentVersionName)) {
            return UpdateCheckResult.UpToDate
        }

        // 清单由发布者手填，apkUrl 缺失说明发版配置遗漏，直接报错让用户走浏览器兜底
        val directUrl = json.optString("apkUrl").trim()
        if (directUrl.isEmpty()) return UpdateCheckResult.Failed("发布信息缺少下载地址，请到发布页手动下载")

        return UpdateCheckResult.Available(
            UpdateInfo(
                versionName = version,
                notes = json.optString("notes").trim(),
                downloadUrls = UpdateConfig.buildDownloadUrls(directUrl),
                sizeBytes = json.optLong("sizeBytes", 0L)
            )
        )
    }
}
