/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/update/UpdateConfig.kt
 * @Description: 更新源配置，集中放置仓库地址与资源命名约定，发布流程改动只涉及此文件
 */
package com.worktime.pro.domain.update

object UpdateConfig {
    /** 发布仓库（公开仓库，客户端匿名读取 Release API，不需要 token） */
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
}
