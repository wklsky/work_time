/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/today/TodayScreen.kt
 * @Description: 今日页，展示当天打卡与工时汇总，支持自定义时间的快速打卡及拍照/手动入口
 */
package com.worktime.pro.ui.today

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worktime.pro.data.datastore.AppSettings
import com.worktime.pro.data.db.PunchEventEntity
import com.worktime.pro.data.db.toDayTotals
import com.worktime.pro.domain.model.PunchSource
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.ui.LocalSettings
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.util.TimeFormatter
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    factory: ViewModelFactory,
    onOpenCamera: () -> Unit,
    onOpenManual: () -> Unit,
    onOpenDayDetail: (String) -> Unit
) {
    val viewModel: TodayViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val now by viewModel.now.collectAsState()
    val currentMinute by viewModel.nowMinute.collectAsState()
    val scheduledEnd by viewModel.scheduledEnd.collectAsState()
    val context = LocalContext.current
    val settings by LocalSettings.current.settings.collectAsState(initial = AppSettings())
    val use24Hour = TimeFormatter.use24Hour(context, settings.timeFormat)

    var quickType by remember { mutableStateOf(PunchType.WORK_START) }
    var pendingDelete by remember { mutableStateOf<PunchEventEntity?>(null) }
    // 仅记录用户手动选过的时间；未手动选择的类型跟随手机当前分钟自动刷新
    val manualQuickTimes = remember { mutableStateMapOf<PunchType, LocalTime>() }
    val quickTime = manualQuickTimes[quickType] ?: currentMinute
    var showQuickTimePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        // 标题区
        Text("今日", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "${TimeFormatter.formatDate(state.today)} ${TimeFormatter.weekdayCN(state.today)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        // 工作状态 hero：实时计时 + 下一步动作；正常/加班口径不重叠
        val totals = state.sessions.toDayTotals()
        val status = computeTodayStatus(state.events, now, totals.netMinutes)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val active = status.label == "工作中" || status.label == "加班中"
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                            )
                    )
                    Text(
                        status.label,
                        modifier = Modifier.padding(start = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "下班 ${TimeFormatter.formatTime(scheduledEnd, use24Hour)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                    )
                }

                Text(
                    status.timerLabel,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    "正常 ${TimeFormatter.formatDuration(totals.regularMinutes)} · " +
                        "加班 ${TimeFormatter.formatDuration(totals.overtimeMinutes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )

                val actionType = status.actionType
                if (actionType != null) {
                    Button(
                        onClick = {
                            viewModel.quickPunch(actionType, LocalTime.now()) {
                                Toast.makeText(
                                    context,
                                    "${actionType.label}打卡成功",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimary,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) { Text(status.actionLabel) }
                } else {
                    Text(
                        "当前记录已闭合，可在下方继续追加一段",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 快速打卡：默认取当前时间，用户可滑动微调后一键写入，省去每次都走完整手动录入流程
        Text("快速打卡", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PunchType.entries.forEach { type ->
                        FilterChip(
                            selected = quickType == type,
                            onClick = { quickType = type },
                            label = { Text(type.label) }
                        )
                    }
                }

                // 时间行点击弹出滑动 TimePicker，制式跟随用户设置
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showQuickTimePicker = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("打卡时间", modifier = Modifier.weight(1f))
                    Text(
                        TimeFormatter.formatTime(quickTime, use24Hour),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        val type = quickType
                        val time = quickTime
                        viewModel.quickPunch(type, time) {
                            Toast.makeText(context, "${type.label}打卡成功", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("一键打卡") }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 打卡记录：按时间顺序展示多段，同类型可出现多次
        Text(
            "打卡记录 (${state.events.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.events.isEmpty()) {
                    Text(
                        "今天还没有打卡，使用上方快速打卡或拍照录入",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.events.forEach { event ->
                        val typeLabel = PunchType.entries
                            .firstOrNull { it.name == event.punchType }?.label
                            ?: event.punchType
                        val source = PunchSource.entries
                            .firstOrNull { it.name == event.source }?.label
                            ?: event.source
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(typeLabel, fontWeight = FontWeight.Medium)
                                Text(
                                    "${viewModel.formatTime(event, use24Hour)} · $source",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { pendingDelete = event }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除这一段")
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 其他打卡入口
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
                onClick = { onOpenDayDetail(TimeFormatter.formatDateKey(state.today)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("查看详情") }
        }
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

    if (showQuickTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = quickTime.hour,
            initialMinute = quickTime.minute,
            is24Hour = use24Hour
        )
        AlertDialog(
            onDismissRequest = { showQuickTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val hour = pickerState.hour
                    val minute = pickerState.minute
                    if (hour in 0..23 && minute in 0..59) {
                        manualQuickTimes[quickType] = LocalTime.of(hour, minute)
                    } else {
                        Toast.makeText(context, "时间无效", Toast.LENGTH_SHORT).show()
                    }
                    showQuickTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showQuickTimePicker = false }) { Text("取消") }
            },
            text = { TimePicker(state = pickerState) }
        )
    }
}

private data class TodayStatus(
    val label: String,
    val actionType: PunchType?,
    val actionLabel: String,
    val timerLabel: String
)

/** 由四类打卡事件推导工作状态与下一步动作；多段时以最近一段是否闭合为准 */
private fun computeTodayStatus(
    events: List<PunchEventEntity>,
    now: LocalDateTime,
    netMinutes: Int
): TodayStatus {
    fun latest(type: PunchType) = events.lastOrNull { it.punchType == type.name }
    val workStart = latest(PunchType.WORK_START)
    val workEnd = latest(PunchType.WORK_END)
    val otStart = latest(PunchType.OT_START)
    val otEnd = latest(PunchType.OT_END)

    val working = workStart != null && (workEnd == null || workStart.punchTime > workEnd.punchTime)
    val overtime = otStart != null && (otEnd == null || otStart.punchTime > otEnd.punchTime)

    fun elapsed(start: PunchEventEntity?): String {
        if (start == null) return "00:00:00"
        val nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val safeSeconds = ((nowMillis - start.punchTime) / 1000).coerceAtLeast(0)
        return String.format(
            Locale.US,
            "%02d:%02d:%02d",
            (safeSeconds / 3600).toInt(),
            ((safeSeconds % 3600) / 60).toInt(),
            (safeSeconds % 60).toInt()
        )
    }

    val closedTimer = if (netMinutes > 0) {
        TimeFormatter.formatDuration(netMinutes)
    } else {
        "00:00:00"
    }

    return when {
        workStart == null && otStart == null ->
            TodayStatus("未开始", PunchType.WORK_START, "上班打卡", "00:00:00")
        working ->
            TodayStatus("工作中", PunchType.WORK_END, "下班打卡", elapsed(workStart))
        overtime ->
            TodayStatus("加班中", PunchType.OT_END, "加班结束", elapsed(otStart))
        workEnd != null && otStart == null ->
            TodayStatus("已下班", PunchType.OT_START, "加班打卡", closedTimer)
        else ->
            TodayStatus("今日已完成", null, "已完成", closedTimer)
    }
}
