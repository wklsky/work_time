/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/settings/SettingsScreen.kt
 * @Description: 设置界面，核心为日历下方显示内容（全局统一切换），以及加班色阶、OCR 自动填充
 */
package com.worktime.pro.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worktime.pro.data.datastore.CalendarDisplayMode
import com.worktime.pro.ui.ViewModelFactory

@Composable
fun SettingsScreen(
    factory: ViewModelFactory,
    onBack: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val settings by viewModel.settingsFlow.collectAsState(
        initial = com.worktime.pro.data.datastore.AppSettings()
    )
    val currentMode = CalendarDisplayMode.entries.firstOrNull { it.value == settings.calendarDisplayMode }
        ?: CalendarDisplayMode.WORK_TIME

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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

                // 日历下方显示内容：用户决策——全局统一，设置中切换
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
