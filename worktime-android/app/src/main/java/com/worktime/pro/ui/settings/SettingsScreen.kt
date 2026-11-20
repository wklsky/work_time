/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/settings/SettingsScreen.kt
 * @Description: 设置界面，含全局日历显示切换、打卡设置与本地数据备份恢复
 */
package com.worktime.pro.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worktime.pro.data.datastore.AppSettings
import com.worktime.pro.data.datastore.CalendarDisplayMode
import com.worktime.pro.data.datastore.ThemeMode
import com.worktime.pro.data.datastore.TimeFormatMode
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.domain.ocr.StatusMapping
import com.worktime.pro.ui.ViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    factory: ViewModelFactory,
    onBack: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val backupViewModel: BackupViewModel = viewModel(factory = factory)
    val settings by viewModel.settingsFlow.collectAsState(initial = AppSettings())
    val backupMessage by backupViewModel.message.collectAsState()
    val context = LocalContext.current
    val currentMode = CalendarDisplayMode.entries.firstOrNull { it.value == settings.calendarDisplayMode }
        ?: CalendarDisplayMode.WORK_TIME

    // 导入文件选择器：用户选择备份 JSON 后交给 BackupViewModel 恢复
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) backupViewModel.restore(context, uri) }

    // 恢复完成后，导入操作涉及数据覆盖，此处以 Toast 明确提示结果
    LaunchedEffect(backupMessage) {
        backupMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            backupViewModel.clearMessage()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("显示设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Text("主题", style = MaterialTheme.typography.bodyMedium)
                val currentTheme = ThemeMode.entries.firstOrNull { it.value == settings.themeMode }
                    ?: ThemeMode.SYSTEM
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = currentTheme == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }

                Text("时间制式", style = MaterialTheme.typography.bodyMedium)
                val currentTimeFormat = TimeFormatMode.entries.firstOrNull { it.value == settings.timeFormat }
                    ?: TimeFormatMode.SYSTEM
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = currentTimeFormat == TimeFormatMode.SYSTEM,
                        onClick = { viewModel.setTimeFormat(TimeFormatMode.SYSTEM) },
                        label = { Text("跟随系统") }
                    )
                    FilterChip(
                        selected = currentTimeFormat == TimeFormatMode.HOUR_24,
                        onClick = { viewModel.setTimeFormat(TimeFormatMode.HOUR_24) },
                        label = { Text("24 小时") }
                    )
                    FilterChip(
                        selected = currentTimeFormat == TimeFormatMode.HOUR_12,
                        onClick = { viewModel.setTimeFormat(TimeFormatMode.HOUR_12) },
                        label = { Text("12 小时") }
                    )
                }

                Text("日历每日下方显示", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = currentMode == CalendarDisplayMode.WORK_TIME,
                        onClick = { viewModel.setCalendarDisplayMode(CalendarDisplayMode.WORK_TIME) },
                        label = { Text("上班时间") }
                    )
                    FilterChip(
                        selected = currentMode == CalendarDisplayMode.OVERTIME,
                        onClick = { viewModel.setCalendarDisplayMode(CalendarDisplayMode.OVERTIME) },
                        label = { Text("加班时间") }
                    )
                }
                Text(
                    "全局统一生效：切换后首页所有日期格子的下方显示内容同步变化。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SwitchRow(
                    title = "加班色阶",
                    description = "日历中按加班时长显示底色深浅",
                    checked = settings.showOvertimeShade,
                    onCheckedChange = viewModel::setOvertimeShade
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("打卡设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SwitchRow(
                    title = "OCR 自动填充",
                    description = "开启后识别结果自动填入确认页，仍需人工确认才入库",
                    checked = settings.ocrAutoFill,
                    onCheckedChange = viewModel::setOcrAutoFill
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 状态映射：考勤机状态列数字 → 打卡类型，用户可按机器编码自定义
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("考勤机状态映射", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "设置考勤机状态列数字对应的打卡类型，识别时按此映射判定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusMappingEditor(
                    raw = settings.statusMapping,
                    onChanged = { viewModel.setStatusMapping(it) },
                    onReset = { viewModel.resetStatusMapping() }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("数据管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "单机版数据仅存于本机，建议定期导出备份。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        CoroutineScope(Dispatchers.IO).launch {
                            val result = backupViewModel.export()
                            withContext(Dispatchers.Main) {
                                result.onSuccess { file ->
                                    val uri = FileProvider.getUriForFile(
                                        context, "${context.packageName}.fileprovider", file
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "分享备份"))
                                }
                            }
                        }
                    }) { Text("导出备份") }

                    TextButton(onClick = { importLauncher.launch("application/json") }) {
                        Text("导入恢复")
                    }
                }
                // 业务要求：导入会覆盖当前全部数据，必须在入口明确告知用户
                Text(
                    "注意：导入恢复将覆盖当前全部数据，操作不可撤销。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 状态映射编辑器：考勤机状态列数字（0-9）→ 打卡类型。
 * raw 为空时展示内置默认映射，用户改动后才写入自定义值。
 */
@Composable
private fun StatusMappingEditor(
    raw: String,
    onChanged: (String) -> Unit,
    onReset: () -> Unit
) {
    val mapping = if (raw.isBlank()) StatusMapping.DEFAULT else StatusMapping.deserialize(raw)
    // 只展示 0-9 状态位，覆盖常见考勤机编码
    val statusCodes = (0..9).map { it.toString() }
    val typeOptions = listOf(
        null to "无",
        PunchType.WORK_START to "上班",
        PunchType.WORK_END to "下班",
        PunchType.OT_START to "加班到",
        PunchType.OT_END to "加班退"
    )

    statusCodes.forEach { code ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("状态 $code", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            typeOptions.forEach { (type, label) ->
                FilterChip(
                    selected = mapping[code] == type,
                    onClick = {
                        val updated = mapping.toMutableMap()
                        if (type == null) updated.remove(code) else updated[code] = type
                        onChanged(StatusMapping.serialize(updated))
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
    }

    TextButton(onClick = onReset) {
        Text("恢复默认映射（0上班 1下班 4加班到 5加班退）")
    }
}
