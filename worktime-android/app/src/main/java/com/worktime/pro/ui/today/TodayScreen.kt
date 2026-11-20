/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/today/TodayScreen.kt
 * @Description: 今日页，展示当天四类打卡时间与工时汇总，提供拍照/手动快捷打卡入口
 */
package com.worktime.pro.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worktime.pro.domain.model.PunchSource
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.domain.model.SessionType
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.util.TimeFormatter

@Composable
fun TodayScreen(
    factory: ViewModelFactory,
    onOpenCamera: () -> Unit,
    onOpenManual: () -> Unit,
    onOpenDayDetail: (String) -> Unit
) {
    val viewModel: TodayViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        // 标题区
        Text("今日", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "${TimeFormatter.formatDate(viewModel.today)} ${TimeFormatter.weekdayCN(viewModel.today)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        // 工时汇总卡片
        val totalWork = state.sessions.filter { it.sessionType == SessionType.REGULAR.name }
            .sumOf { it.workMinutes }
        val totalOvertime = state.sessions.sumOf { it.overtimeMinutes }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("工作时长", style = MaterialTheme.typography.bodySmall)
                    Text(TimeFormatter.formatDuration(totalWork), fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium)
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("加班时长", style = MaterialTheme.typography.bodySmall)
                    Text(TimeFormatter.formatDuration(totalOvertime), fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 四类打卡时间
        Text("打卡记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PunchType.entries.forEach { type ->
                    val event = state.events.firstOrNull { it.punchType == type.name }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(type.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        if (event != null) {
                            val source = PunchSource.entries.firstOrNull { it.name == event.source }?.label ?: event.source
                            Text(viewModel.formatTime(event))
                            Text(" ($source)", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            IconButton(onClick = { viewModel.deleteEvent(event) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除")
                            }
                        } else {
                            Text("未打卡", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 快捷打卡
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onOpenCamera, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("拍照打卡", modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(onClick = onOpenManual, modifier = Modifier.weight(1f)) {
                Text("手动打卡")
            }
        }

        if (state.events.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { onOpenDayDetail(TimeFormatter.formatDateKey(viewModel.today)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("查看详情") }
        }
    }
}
