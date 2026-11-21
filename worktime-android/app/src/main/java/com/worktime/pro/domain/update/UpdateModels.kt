/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/update/UpdateModels.kt
 * @Description: 更新检查的数据模型与界面状态定义
 */
package com.worktime.pro.domain.update

/** 从 GitHub Release 解析出的新版本信息 */
data class UpdateInfo(
    /** tag 去掉 v 前缀后的版本号，如 2.1.0 */
    val versionName: String,
    /** Release 正文，作为更新说明展示 */
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long
)

/** 检查结果三态：失败文案直接面向用户，不吞异常 */
sealed interface UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

/**
 * 更新弹窗状态机。
 * 静默检查（启动时）失败不进入 [Failed]，直接回 [Idle]，避免用户被打扰；
 * 只有手动点击「检查更新」才把错误暴露出来。
 */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateUiState
    /** 下载完成但缺「安装未知应用」权限，需引导用户去系统设置授权 */
    data class NeedPermission(val info: UpdateInfo) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}
