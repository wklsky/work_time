/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/update/UpdateConfig.kt
 * @Description: 更新源配置，集中放置仓库地址、镜像源与资源命名约定，发布流程改动只涉及此文件
 */
package com.worktime.pro.domain.update

object UpdateConfig {
    /** 发布仓库（公开仓库，客户端匿名读取，不需要 token） */
    const val REPO = "wklsky/work_time"

    const val RELEASE_API_URL = "https://api.github.com/repos/$REPO/releases/latest"

    /**
     * Release 里 APK 资产必须固定叫这个名字：`releases/latest/download/` 是唯一不依赖 API 的固定直链，
     * API 限流或资产列表缺失时用它兜底。
     * 改名时（含大小写）必须同步改这里，否则兜底直链会 404；主路径仍可凭 `.apk` 后缀识别。
     */
    const val APK_ASSET_NAME = "WorkTime.apk"

    const val FALLBACK_APK_URL = "https://github.com/$REPO/releases/latest/download/$APK_ASSET_NAME"

    /** 发布页地址，检查失败或需要看完整更新说明时给用户一个可点入口 */
    const val RELEASES_PAGE_URL = "https://github.com/$REPO/releases"

    /**
     * 版本清单文件：随每次发版提交到仓库（记录 versionName/notes/apkUrl）。
     * 国内手机直连 api.github.com 经常超时，jsDelivr CDN 在国内可直连，作为 API 不可达时的首选镜像；
     * ghfast.top 再代理 raw.githubusercontent.com 作为第二镜像。
     * 注意 jsDelivr 对分支引用有最长约 12 小时缓存，发版后新清单最多延迟半天生效，可访问
     * purge.jsdelivr.net/gh/$REPO@main/update/update.json 手动刷新。
     */
    const val UPDATE_JSON_PATH = "update/update.json"
    const val UPDATE_JSON_URL_JSDELIVR = "https://cdn.jsdelivr.net/gh/$REPO@main/$UPDATE_JSON_PATH"
    const val UPDATE_JSON_URL_RAW_PROXY = "https://ghfast.top/https://raw.githubusercontent.com/$REPO/main/$UPDATE_JSON_PATH"

    /**
     * APK 下载加速代理前缀（拼接在 GitHub 直链前，形如 `https://ghfast.top/https://github.com/...`）。
     * 第三方公益加速域名会随时间失效：失效表现为下载失败而不是返回错误包，适合自动逐个降级重试；
     * 若全部失效需更新此列表并等待用户侧应用更新（鸡生蛋问题，靠浏览器下载兜底）。
     */
    val APK_PROXY_PREFIXES = listOf(
        "https://ghfast.top/",
        "https://gh-proxy.com/",
        "https://ghproxy.net/"
    )

    /** 由 GitHub 直链生成候选下载列表：直链优先（信息可靠），失败后按序走加速代理 */
    fun buildDownloadUrls(directUrl: String): List<String> =
        buildList {
            add(directUrl)
            APK_PROXY_PREFIXES.forEach { add(it + directUrl) }
        }
}
