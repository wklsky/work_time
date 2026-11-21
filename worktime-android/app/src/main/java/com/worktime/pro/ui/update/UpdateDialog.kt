/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/update/UpdateDialog.kt
 * @Description: 更新流程弹窗，按状态机渲染发现新版本 / 下载进度 / 权限引导 / 结果提示
 */
package com.worktime.pro.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worktime.pro.domain.update.UpdateUiState

/**
 * @param state 更新状态机，Idle 时不渲染任何弹窗
 * @param currentVersion 本机版本号，用于展示版本对比
 * @param onDownload 用户点击「立即更新」
 * @param onInstall 用户点击「去设置」，授权后需再触发安装
 * @param onDismiss 关闭弹窗（稍后再说 / 知道了）
 */
@Composable
fun UpdateDialogHost(
    state: UpdateUiState,
    currentVersion: String,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        UpdateUiState.Idle -> Unit

        UpdateUiState.Checking -> AlertDialog(
            onDismissRequest = {},
            title = { Text("检查更新") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(12.dp))
                    Text("正在获取最新版本…")
                }
            },
            confirmButton = {}
        )

        UpdateUiState.UpToDate -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("检查更新") },
            text = { Text("当前已是最新版本 V$currentVersion") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
        )

        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("发现新版本 V${state.info.versionName}") },
            text = {
                Column {
                    Text(
                        "当前版本 V$currentVersion" + sizeLabel(state.info.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        // 更新说明由用户手写，长度不可控，限高滚动避免弹窗被撑破
                        Text(
                            text = state.info.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDownload) { Text("立即更新") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } }
        )

        is UpdateUiState.Downloading -> AlertDialog(
            // 下载交给 DownloadManager，弹窗关掉也不会中断，但保持可见能让用户知道进度
            onDismissRequest = {},
            title = { Text("正在下载更新") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${state.percent}% · 可继续使用应用，下载完成后会自动提示安装",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {}
        )

        is UpdateUiState.NeedPermission -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("需要安装权限") },
            text = {
                Text("系统要求先允许「工时助手」安装未知应用。请在设置中打开该开关，返回后点击「继续安装」。")
            },
            confirmButton = { TextButton(onClick = onInstall) { Text("去设置") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } }
        )

        is UpdateUiState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("更新未完成") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
        )
    }
}

private fun sizeLabel(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / 1024.0 / 1024.0
    return " · 安装包 %.1f MB".format(mb)
}
