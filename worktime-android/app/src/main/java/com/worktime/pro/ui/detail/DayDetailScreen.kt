/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/detail/DayDetailScreen.kt
 * @Description: 日详情界面，展示四类打卡时间、凭证、工时明细，支持点击编辑/补录打卡时间与删除
 */
package com.worktime.pro.ui.detail

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worktime.pro.data.datastore.AppSettings
import com.worktime.pro.data.db.PunchEventEntity
import com.worktime.pro.data.db.toDayTotals
import com.worktime.pro.domain.evidence.EvidenceStore
import com.worktime.pro.domain.model.PunchSource
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.ui.LocalSettings
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.ui.components.EvidenceViewer
import com.worktime.pro.util.TimeFormatter
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    dateKey: String,
    factory: ViewModelFactory,
    onBack: () -> Unit
) {
    val viewModel: DayDetailViewModel = viewModel(
        key = dateKey,
        factory = viewModelFactoryFor(factory, dateKey)
    )
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val settings by LocalSettings.current.settings.collectAsState(initial = AppSettings())
    val use24Hour = TimeFormatter.use24Hour(context, settings.timeFormat)

    // 正在编辑的打卡类型：null 表示未打开时间选择器
    var editingType by remember { mutableStateOf<PunchType?>(null) }
    // 正在查看的凭证文件
    var viewingEvidence by remember { mutableStateOf<File?>(null) }
    // 待确认删除的打卡
    var pendingDelete by remember { mutableStateOf<PunchEventEntity?>(null) }
    val editingEvent = editingType?.let { type ->
        state.events.firstOrNull { it.punchType == type.name }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
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

        // 四类时间卡片：点击行或编辑图标修改时间，未打卡点击 + 补录
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PunchType.entries.forEach { type ->
                    val event = state.events.firstOrNull { it.punchType == type.name }
                    PunchRow(
                        type = type,
                        event = event,
                        use24Hour = use24Hour,
                        onViewEvidence = { path ->
                            val file = EvidenceStore.resolve(context, path)
                            if (file != null) {
                                viewingEvidence = file
                            } else {
                                Toast.makeText(context, "凭证图片已丢失", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onEdit = { editingType = type },
                        onDelete = { if (event != null) pendingDelete = event }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "点击打卡时间可修改，未打卡可点 + 补录；修改后工时自动重算",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        // 工时汇总：正常与加班互不重叠
        val totals = state.sessions.toDayTotals()
        val totalWork = totals.regularMinutes
        val totalOvertime = totals.overtimeMinutes

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

    viewingEvidence?.let { file ->
        EvidenceViewer(file = file, onDismiss = { viewingEvidence = null })
    }

    pendingDelete?.let { event ->
        val typeLabel = PunchType.entries.firstOrNull { it.name == event.punchType }?.label
            ?: event.punchType
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除打卡") },
            text = { Text("确定删除这条「$typeLabel」记录吗？删除后当日工时将自动重算。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEvent(event)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    if (editingType != null) {
        val initial = editingEvent?.let {
            Instant.ofEpochMilli(it.punchTime).atZone(ZoneId.systemDefault()).toLocalTime()
        } ?: LocalTime.now()
        val pickerState = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = use24Hour
        )
        AlertDialog(
            onDismissRequest = { editingType = null },
            confirmButton = {
                TextButton(onClick = {
                    val hour = pickerState.hour
                    val minute = pickerState.minute
                    if (hour in 0..23 && minute in 0..59) {
                        val time = LocalTime.of(hour, minute)
                        val type = editingType
                        if (type != null) {
                            if (editingEvent != null) {
                                viewModel.updateEventTime(editingEvent, time) {
                                    Toast.makeText(context, "${type.label}时间已更新", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                viewModel.addEvent(type, time) {
                                    Toast.makeText(context, "${type.label}打卡已补录", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        Toast.makeText(context, "时间无效", Toast.LENGTH_SHORT).show()
                    }
                    editingType = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingType = null }) { Text("取消") }
            },
            text = { TimePicker(state = pickerState) }
        )
    }
}

@Composable
private fun PunchRow(
    type: PunchType,
    event: PunchEventEntity?,
    use24Hour: Boolean,
    onViewEvidence: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(type.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        if (event != null) {
            val time = Instant.ofEpochMilli(event.punchTime).atZone(ZoneId.systemDefault()).toLocalTime()
            Text(
                TimeFormatter.formatTime(time, use24Hour),
                modifier = Modifier.clickable(onClick = onEdit),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
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
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "修改时间")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除")
            }
        } else {
            Text("未打卡", color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Add, contentDescription = "补录")
            }
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
