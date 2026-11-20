/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/settings/BackupViewModel.kt
 * @Description: 备份状态管理，处理数据导出与恢复的结果反馈
 */
package com.worktime.pro.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worktime.pro.data.datastore.SettingsDataStore
import com.worktime.pro.domain.backup.BackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class BackupViewModel(
    private val backupManager: BackupManager,
    private val settings: SettingsDataStore
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    suspend fun export(): Result<File> {
        val currentSettings = settings.settings.first()
        return backupManager.export(currentSettings).also { result ->
            _message.value = result.fold(
                onSuccess = { "备份已导出：${it.name}" },
                onFailure = { "备份失败：${it.message ?: "未知错误"}" }
            )
        }
    }

    /** 从用户选择的 URI 恢复数据，恢复前需 UI 层已二次确认 */
    fun restore(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val tempFile = File(context.cacheDir, "restore_backup.json")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.writeBytes(input.readBytes())
                } ?: throw IllegalArgumentException("无法读取所选文件")
                val validation = backupManager.validate(tempFile)
                if (validation.isFailure) {
                    _message.value = "版本校验失败：${validation.exceptionOrNull()?.message}"
                    return@launch
                }
                val result = backupManager.restore(tempFile)
                _message.value = result.fold(
                    onSuccess = { "数据恢复成功，请重启应用" },
                    onFailure = { "恢复失败：${it.message ?: "未知错误"}" }
                )
            } catch (e: Exception) {
                _message.value = "恢复失败：${e.message ?: "未知错误"}"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
