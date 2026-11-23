/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/detail/DayDetailScreen.kt
 * @Description: 日详情界面，按时间展示多段打卡、凭证与工时明细，支持编辑、补录与删除
 */
package com.worktime.pro.ui.detail

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.ui.LocalSettings
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.ui.components.EmptyState
import com.worktime.pro.ui.components.TimePickerDialog
import com.worktime.pro.ui.sourceLabel
import com.worktime.pro.ui.typeLabel
import com.worktime.pro.util.TimeFormatter
import java.time.LocalTime

private data class PunchEditorTarget(
    val type: PunchType,
    val event: PunchEventEntity?
)

@OptIn(ExperimentalLayoutApi::class)
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

    // 正在编辑/补录的目标：event 为空表示新增一段
    var editingTarget by remember { mutableStateOf<PunchEditorTarget?>(null) }
    // 待确认删除的打卡
    var pendingDelete by remember { mutableStateOf<PunchEventEntity?>(null) }

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

        // 按时间顺序展示所有打卡段；同类型可以出现多次
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.events.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.History,
                        title = "当日无打卡记录",
                        description = "可在下方补录一段。",
                        iconSize = 48.dp,
                        glyphSize = 22.dp
                    )
                } else {
                    state.events.forEach { event ->
                        val type = PunchType.fromName(event.punchType)
                        if (type != null) {
                            EventRow(
                                event = event,
                                type = type,
                                use24Hour = use24Hour,
                                onEdit = { editingTarget = PunchEditorTarget(type, event) },
                                onDelete = { pendingDelete = event }
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text("补录一段", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PunchType.entries.forEach { type ->
                        AssistChip(
                            onClick = { editingTarget = PunchEditorTarget(type, null) },
                            label = { Text(type.label) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "同一天可有多段；点击时间可修改，删除后工时自动重算",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        // 工时汇总：正常与加班互不重叠
        val totals = state.sessions.toDayTotals()
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "正常工作：${TimeFormatter.formatDuration(totals.regularMinutes)}",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "加班时长：${TimeFormatter.formatDuration(totals.overtimeMinutes)}",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "净投入：${TimeFormatter.formatDuration(totals.netMinutes)}",
                    fontWeight = FontWeight.Bold
                )
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

    pendingDelete?.let { event ->
        val typeLabel = event.typeLabel()
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

    editingTarget?.let { target ->
        val initial = target.event?.let {
            TimeFormatter.millisToLocalTime(it.punchTime)
        } ?: defaultTimeFor(target.type)
        TimePickerDialog(
            title = if (target.event == null) {
                "补录${target.type.label}"
            } else {
                "修改${target.type.label}"
            },
            initialTime = initial,
            onDismiss = { editingTarget = null },
            onConfirm = { time ->
                val event = target.event
                val onResult: (String?) -> Unit = { error ->
                    if (error != null) {
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    } else {
                        val message = if (event != null) {
                            "${target.type.label}时间已更新"
                        } else {
                            "${target.type.label}打卡已补录"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        editingTarget = null
                    }
                }
                if (event != null) {
                    viewModel.updateEventTime(event, time, onResult)
                } else {
                    viewModel.addEvent(target.type, time, onResult)
                }
            }
        )
    }
}

@Composable
private fun EventRow(
    event: PunchEventEntity,
    type: PunchType,
    use24Hour: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val time = TimeFormatter.millisToLocalTime(event.punchTime)
    val sourceLabel = event.sourceLabel()

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(type.label, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    TimeFormatter.formatTime(time, use24Hour),
                    modifier = Modifier.clickable(onClick = onEdit),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    " · $sourceLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "修改时间")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "删除这一段")
        }
    }
}

private fun defaultTimeFor(type: PunchType): LocalTime = when (type) {
    PunchType.WORK_START -> LocalTime.of(9, 0)
    PunchType.WORK_END -> LocalTime.of(18, 0)
    PunchType.OT_START -> LocalTime.of(19, 0)
    PunchType.OT_END -> LocalTime.of(21, 0)
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
