/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/settings/BackupViewModel.kt
 * @Description: 备份状态管理，处理 JSON 备份导出、CSV 明细导出与带二次确认的数据恢复
 */
package com.worktime.pro.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worktime.pro.data.datastore.SettingsDataStore
import com.worktime.pro.domain.backup.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BackupViewModel(
    private val backupManager: BackupManager,
    private val settings: SettingsDataStore
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 待确认的恢复：缓存文件 + 摘要。UI 展示摘要并二次确认后才调用 confirmRestore */
    private val _pending = MutableStateFlow<BackupManager.Summary?>(null)
    val pending: StateFlow<BackupManager.Summary?> = _pending.asStateFlow()
    private var pendingFile: File? = null

    suspend fun export(): Result<File> {
        val currentSettings = settings.settings.first()
        return withContext(Dispatchers.IO) {
            backupManager.export(currentSettings)
        }.also { result ->
            _message.value = result.fold(
                onSuccess = { "备份已导出：${it.name}" },
                onFailure = { "备份失败：${it.message ?: "未知错误"}" }
            )
        }
    }

    /** 导出可读 CSV 明细内容，UI 负责写入用户选择的位置 */
    suspend fun exportCsv(): Result<String> = withContext(Dispatchers.IO) {
        backupManager.exportCsv()
    }.also { result ->
        _message.value = result.fold(
            onSuccess = { "工时明细已生成，请选择保存位置" },
            onFailure = { "导出失败：${it.message ?: "未知错误"}" }
        )
    }

    /**
     * 读取用户选择的备份文件并解析摘要，不立即覆盖数据。
     * 业务要求：导入会覆盖全部数据，必须先展示摘要让用户确认选对了文件。
     */
    fun prepareRestore(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val tempFile = File(context.cacheDir, "restore_backup.json")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.writeBytes(input.readBytes())
                    } ?: throw IllegalArgumentException("无法读取所选文件")
                    val validation = backupManager.validate(tempFile)
                    if (validation.isFailure) {
                        throw IllegalArgumentException(
                            "版本校验失败：${validation.exceptionOrNull()?.message}"
                        )
                    }
                    val summary = backupManager.readSummary(tempFile).getOrThrow()
                    summary to tempFile
                }
            }
            result.onSuccess { (summary, file) ->
                pendingFile = file
                _pending.value = summary
            }.onFailure { error ->
                _message.value = "读取备份失败：${error.message ?: "未知错误"}"
            }
        }
    }

    /** 用户二次确认后执行恢复 */
    fun confirmRestore() {
        val file = pendingFile ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { backupManager.restore(file) }
            _message.value = result.fold(
                onSuccess = { "数据恢复成功" },
                onFailure = { "恢复失败：${it.message ?: "未知错误"}" }
            )
            pendingFile = null
            _pending.value = null
        }
    }

    fun cancelRestore() {
        pendingFile = null
        _pending.value = null
    }

    fun clearMessage() {
        _message.value = null
    }
}
