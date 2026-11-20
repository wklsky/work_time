/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/detail/DayDetailScreen.kt
 * @Description: 日详情界面，展示四类打卡时间、凭证入口、工时计算明细与删除操作
 */
package com.worktime.pro.ui.detail

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worktime.pro.domain.model.PunchSource
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.domain.model.SessionType
import com.worktime.pro.ui.LocalSettings
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.util.TimeFormatter
import java.time.Instant
import java.time.ZoneId

@Composable
fun DayDetailScreen(
    dateKey: String,
    factory: ViewModelFactory,
    onBack: () -> Unit,
    onViewEvidence: (String) -> Unit
) {
    val viewModel: DayDetailViewModel = viewModel(
        key = dateKey,
        factory = viewModelFactoryFor(factory, dateKey)
    )
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            val date = TimeFormatter.parseDateKey(dateKey)
            Text(
                "${TimeFormatter.formatDate(date)} ${TimeFormatter.weekdayCN(date)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(16.dp))

        // 四类时间卡片
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PunchType.entries.forEach { type ->
                    val event = state.events.firstOrNull { it.punchType == type.name }
                    PunchRow(
                        type = type,
                        event = event,
                        onViewEvidence = onViewEvidence,
                        onDelete = { if (event != null) viewModel.deleteEvent(event) }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 工时汇总
        val regular = state.sessions.filter { it.sessionType == SessionType.REGULAR.name }
        val overtime = state.sessions.filter { it.sessionType == SessionType.OVERTIME.name }
        val totalWork = regular.sumOf { it.workMinutes }
        val totalOvertime = state.sessions.sumOf { it.overtimeMinutes }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("正常工作：${TimeFormatter.formatDuration(totalWork)}", fontWeight = FontWeight.Bold)
                Text("加班时长：${TimeFormatter.formatDuration(totalOvertime)}", fontWeight = FontWeight.Bold)
                state.sessions.forEach { session ->
                    Text(
                        session.formula,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PunchRow(
    type: PunchType,
    event: com.worktime.pro.data.db.PunchEventEntity?,
    onViewEvidence: (String) -> Unit,
    onDelete: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(type.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        if (event != null) {
            val time = Instant.ofEpochMilli(event.punchTime).atZone(ZoneId.systemDefault()).toLocalTime()
            Text(TimeFormatter.formatTime(time))
            val sourceLabel = PunchSource.entries.firstOrNull { it.name == event.source }?.label ?: event.source
            Text(" ($sourceLabel)", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (event.imagePath != null) {
                Icon(
                    Icons.Filled.Image,
                    contentDescription = "查看凭证",
                    modifier = Modifier.clickable { onViewEvidence(event.imagePath) }
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除")
            }
        } else {
            Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 包装工厂，注入日期参数 */
@Composable
private fun viewModelFactoryFor(factory: ViewModelFactory, dateKey: String): ViewModelProvider.Factory {
    val repository = com.worktime.pro.ui.LocalRepository.current
    val settings = LocalSettings.current
    return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            DayDetailViewModel(dateKey, repository, settings) as T
    }
}
