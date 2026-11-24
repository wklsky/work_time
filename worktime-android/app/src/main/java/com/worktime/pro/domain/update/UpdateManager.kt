/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/update/UpdateManager.kt
 * @Description: 更新编排器，负责节流检查、下载进度跟踪与安装拉起，向界面暴露单一状态流
 */
package com.worktime.pro.domain.update

import android.app.DownloadManager
import android.content.Context
import com.worktime.pro.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdateManager(
    private val context: Context,
    private val scope: CoroutineScope,
    val currentVersionName: String = BuildConfig.VERSION_NAME
) {
    /**
     * 检查时间戳单独放 SharedPreferences 而不是 DataStore 设置项：
     * 它属于运行期状态，不该出现在「设置」页，也不该被备份导入导出带走，
     * 否则换机恢复备份后 24 小时内都不会再检查更新。
     */
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val checker = UpdateChecker(currentVersionName)

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var pendingInfo: UpdateInfo? = null
    private var checking = false
    private var downloadJob: Job? = null

    /** 当前下载使用的候选地址序号，GitHub 直链失败后自动切到加速代理 */
    private var downloadAttemptIndex = 0

    /** 冷启动静默检查：24 小时内只查一次，失败或已是最新都不打扰用户 */
    fun checkOnLaunch() {
        if (withinCooldown()) return
        check(silent = true)
    }

    /** 手动检查：跳过冷却，把结果（包括「已是最新」与失败原因）如实反馈 */
    fun checkManually() {
        check(silent = false)
    }

    fun startDownload() {
        val info = pendingInfo ?: return
        // 先要安装权限再下载：否则 62MB 下完才发现装不上，白等
        if (!UpdateInstaller.canInstallPackages(context)) {
            UpdateInstaller.openInstallPermissionSettings(context)
            _state.value = UpdateUiState.NeedPermission(info)
            return
        }
        downloadAttemptIndex = 0
        enqueueDownload(info)
    }

    /** 按当前尝试序号从候选列表取源发起下载；候选列表为空说明数据源异常，不发起下载 */
    private fun enqueueDownload(info: UpdateInfo) {
        val url = info.downloadUrls.getOrNull(downloadAttemptIndex) ?: return
        val downloadId = UpdateInstaller.enqueue(context, info, url)
        if (downloadId == null) {
            _state.value = UpdateUiState.Failed("无法启动下载，请检查网络后重试")
            return
        }
        _state.value = UpdateUiState.Downloading(info, percent = 0)
        watchDownload(downloadId, info)
    }

    /** 用户从系统设置授权「安装未知应用」返回后，再次拉起安装 */
    fun installPending() {
        val info = pendingInfo ?: return
        scope.launch { installDownloaded(info) }
    }

    /** 「稍后再说」只关闭本次弹窗；24 小时冷却期内不会再自动打扰 */
    fun dismiss() {
        _state.value = UpdateUiState.Idle
    }

    private fun check(silent: Boolean) {
        if (checking) return
        checking = true
        if (!silent) _state.value = UpdateUiState.Checking
        scope.launch {
            val result = checker.check()
            // 无论成败都记录时间：失败多为限流或网络问题，立刻重试只会继续失败
            markChecked()
            checking = false
            _state.value = when (result) {
                is UpdateCheckResult.Available -> {
                    pendingInfo = result.info
                    UpdateUiState.Available(result.info)
                }
                UpdateCheckResult.UpToDate -> if (silent) UpdateUiState.Idle else UpdateUiState.UpToDate
                is UpdateCheckResult.Failed ->
                    if (silent) UpdateUiState.Idle else UpdateUiState.Failed(result.message)
            }
        }
    }

    private fun watchDownload(downloadId: Long, info: UpdateInfo) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            while (isActive) {
                val progress = UpdateInstaller.query(context, downloadId)
                if (progress.failed) {
                    // 业务流转要求：直连 GitHub 不稳定是常态，失败先静默换下一个加速代理源重试，
                    // 候选耗尽才向用户报错并引导浏览器下载兜底。失败任务先移除，避免 DownloadManager 里堆积废记录
                    removeDownload(downloadId)
                    if (downloadAttemptIndex + 1 < info.downloadUrls.size) {
                        downloadAttemptIndex++
                        enqueueDownload(info)
                        return@launch
                    }
                    _state.value = UpdateUiState.Failed(
                        progress.reason ?: "下载失败，请检查网络后重试，或改用浏览器下载"
                    )
                    return@launch
                }
                if (progress.finished) {
                    installDownloaded(info)
                    return@launch
                }
                _state.value = UpdateUiState.Downloading(info, progress.percent)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun installDownloaded(info: UpdateInfo) {
        val apk = UpdateInstaller.apkFile(context, info)
        if (apk == null || !apk.exists()) {
            _state.value = UpdateUiState.Failed("安装包不存在，请重新更新")
            return
        }
        // 拉起系统安装器涉及 Activity 启动，切主线程执行更稳妥
        val result = withContext(Dispatchers.Main) { UpdateInstaller.install(context, apk) }
        _state.value = when (result) {
            // 系统安装器已接管进程，界面不再占着状态
            InstallResult.Started -> UpdateUiState.Idle
            InstallResult.NeedPermission -> UpdateUiState.NeedPermission(info)
            is InstallResult.Failed -> UpdateUiState.Failed(result.message)
        }
    }

    private fun removeDownload(downloadId: Long) {
        val manager = context.getSystemService(DownloadManager::class.java) ?: return
        runCatching { manager.remove(downloadId) }
    }

    private fun withinCooldown(): Boolean {
        val lastCheckAt = prefs.getLong(KEY_LAST_CHECK_AT, 0L)
        return System.currentTimeMillis() - lastCheckAt < CHECK_COOLDOWN_MS
    }

    private fun markChecked() {
        prefs.edit().putLong(KEY_LAST_CHECK_AT, System.currentTimeMillis()).apply()
    }

    private companion object {
        const val PREFS_NAME = "update_state"
        const val KEY_LAST_CHECK_AT = "last_check_at"

        /** 一天最多自动查一次，兼顾"及时"与 GitHub 未认证接口 60 次/小时的限流 */
        const val CHECK_COOLDOWN_MS = 24 * 60 * 60 * 1000L

        /** 下载进度轮询间隔，与 DownloadManager 的进度更新节奏相当 */
        const val POLL_INTERVAL_MS = 600L
    }
}
