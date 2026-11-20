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
import androidx.compose.runtime.LaunchedEffect
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
import com.worktime.pro.data.db.WorkSessionEntity
import com.worktime.pro.data.db.toDayTotals
import com.worktime.pro.domain.model.PunchSource
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.domain.reminder.ReminderScheduler
import com.worktime.pro.domain.reminder.ReminderType
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

    // 根据累计工作量排定/取消提醒；闹钟在后台也能触发
    LaunchedEffect(
        settings.reminderEnabled,
        settings.reminderType,
        settings.reminderMinutes,
        settings.lastReminderKey,
        state.today,
        state.events,
        state.sessions
    ) {
        if (!settings.reminderEnabled) {
            ReminderScheduler.cancel(context)
            return@LaunchedEffect
        }
        val type = ReminderType.entries.firstOrNull { it.value == settings.reminderType }
            ?: ReminderType.OVERTIME
        val live = computeLiveTotals(state.events, state.sessions, LocalDateTime.now())
        val openWorkMinutes = (live.openWorkSeconds / 60).toInt()
        val openOtMinutes = (live.openOtSeconds / 60).toInt()
        val targetMinutes = when (type) {
            ReminderType.OVERTIME -> live.overtimeMinutes + openOtMinutes
            ReminderType.WORK -> live.regularMinutes + openWorkMinutes
            ReminderType.NET -> live.regularMinutes + live.overtimeMinutes +
                openWorkMinutes + openOtMinutes
        }
        val threshold = settings.reminderMinutes
        val key = "${TimeFormatter.formatDateKey(state.today)}:${type.value}:$threshold"
        if (settings.lastReminderKey == key) {
            ReminderScheduler.cancel(context)
            return@LaunchedEffect
        }
        val triggerAt = if (targetMinutes >= threshold) {
            System.currentTimeMillis() + 1_000
        } else {
            val hasOpen = when (type) {
                ReminderType.OVERTIME -> live.otOpen
                ReminderType.WORK -> live.workOpen
                ReminderType.NET -> live.workOpen || live.otOpen
            }
            if (!hasOpen) {
                ReminderScheduler.cancel(context)
                return@LaunchedEffect
            }
            System.currentTimeMillis() +
                (threshold - targetMinutes).coerceAtLeast(1) * 60_000L
        }
        ReminderScheduler.schedule(context, triggerAt, type, threshold, state.today)
    }

    var quickType by remember { mutableStateOf(PunchType.WORK_START) }
    var pendingDelete by remember { mutableStateOf<PunchEventEntity?>(null) }
    // 仅记录用户手动选过的时间；未手动选择的类型跟随手机当前分钟自动刷新
    val manualQuickTimes = remember { mutableStateMapOf<PunchType, LocalTime>() }
    val quickTime = manualQuickTimes[quickType] ?: currentMinute
    var showQuickTimePicker by remember { mutableStateOf(false) }
    // 当前状态只允许闭合正在进行的段，或开启新的上班/加班段
    val allowedQuickTypes = remember(state.events) { allowedQuickTypesFor(state.events) }
    LaunchedEffect(allowedQuickTypes) {
        if (quickType !in allowedQuickTypes) {
            quickType = allowedQuickTypes.first()
        }
    }

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

        // 工作状态 hero：多段累计实时计时 + 下一步动作
        val live = computeLiveTotals(state.events, state.sessions, now)
        val status = computeTodayStatus(state.events, formatClock(live.liveSeconds))
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
                    "正常 ${TimeFormatter.formatDuration(live.regularMinutes)} · " +
                        "加班 ${TimeFormatter.formatDuration(live.overtimeMinutes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )

                val actionType = status.actionType
                if (actionType != null) {
                    Button(
                        onClick = {
                            viewModel.quickPunch(actionType, LocalTime.now()) { error ->
                                if (error == null) {
                                    manualQuickTimes.remove(actionType)
                                }
                                Toast.makeText(
                                    context,
                                    error ?: "${actionType.label}打卡成功",
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
                            enabled = type in allowedQuickTypes,
                            label = { Text(type.label) }
                        )
                    }
                }
                Text(
                    when {
                        allowedQuickTypes == setOf(PunchType.WORK_END) ->
                            "上班段尚未结束，只能打下班卡"
                        allowedQuickTypes == setOf(PunchType.OT_END) ->
                            "加班段尚未结束，只能打加班退卡"
                        else ->
                            "没有进行中的班段，可开始上班或加班"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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
                        viewModel.quickPunch(type, time) { error ->
                            if (error == null) {
                                // 下一段默认回到当前分钟，避免沿用上一段的旧时间
                                manualQuickTimes.remove(type)
                            }
                            Toast.makeText(
                                context,
                                error ?: "${type.label}打卡成功",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = quickType in allowedQuickTypes
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

/** 单日实时累计：已结算工时段 + 当前未闭合的上班/加班段 */
private data class TodayLive(
    val regularMinutes: Int,
    val overtimeMinutes: Int,
    val liveSeconds: Long,
    val openWorkSeconds: Long,
    val openOtSeconds: Long,
    val workOpen: Boolean,
    val otOpen: Boolean
)

private fun computeLiveTotals(
    events: List<PunchEventEntity>,
    sessions: List<WorkSessionEntity>,
    now: LocalDateTime
): TodayLive {
    val totals = sessions.toDayTotals()
    fun latest(type: PunchType) = events.lastOrNull { it.punchType == type.name }
    val workStart = latest(PunchType.WORK_START)
    val workEnd = latest(PunchType.WORK_END)
    val otStart = latest(PunchType.OT_START)
    val otEnd = latest(PunchType.OT_END)
    val nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // 结束卡与新开始卡同分钟时，结束优先、开始卡已开启新段，因此用 >= 判断开放段
    val workOpen = workStart != null &&
        (workEnd == null || workStart.punchTime >= workEnd.punchTime)
    val otOpen = otStart != null &&
        (otEnd == null || otStart.punchTime >= otEnd.punchTime)

    val openWorkSeconds = if (workOpen) {
        ((nowMillis - workStart!!.punchTime) / 1000).coerceAtLeast(0)
    } else 0L

    val openOtSeconds = if (otOpen) {
        ((nowMillis - otStart!!.punchTime) / 1000).coerceAtLeast(0)
    } else 0L

    return TodayLive(
        regularMinutes = totals.regularMinutes,
        overtimeMinutes = totals.overtimeMinutes,
        liveSeconds = totals.netMinutes * 60L + openWorkSeconds + openOtSeconds,
        openWorkSeconds = openWorkSeconds,
        openOtSeconds = openOtSeconds,
        workOpen = workOpen,
        otOpen = otOpen
    )
}

private fun formatClock(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0)
    return String.format(
        Locale.US,
        "%02d:%02d:%02d",
        (safe / 3600).toInt(),
        ((safe % 3600) / 60).toInt(),
        (safe % 60).toInt()
    )
}

/** 当前状态下允许的快速打卡类型：开放段只能被对应的结束卡关闭 */
private fun allowedQuickTypesFor(events: List<PunchEventEntity>): Set<PunchType> {
    fun latest(type: PunchType) = events.lastOrNull { it.punchType == type.name }
    val workStart = latest(PunchType.WORK_START)
    val workEnd = latest(PunchType.WORK_END)
    val otStart = latest(PunchType.OT_START)
    val otEnd = latest(PunchType.OT_END)
    val workOpen = workStart != null && (workEnd == null || workStart.punchTime >= workEnd.punchTime)
    val otOpen = otStart != null && (otEnd == null || otStart.punchTime >= otEnd.punchTime)
    return when {
        workOpen -> setOf(PunchType.WORK_END)
        otOpen -> setOf(PunchType.OT_END)
        else -> setOf(PunchType.WORK_START, PunchType.OT_START)
    }
}

/** 由四类打卡事件推导工作状态与下一步动作；多段时以最近一段是否闭合为准 */
private fun computeTodayStatus(
    events: List<PunchEventEntity>,
    timerLabel: String
): TodayStatus {
    fun latest(type: PunchType) = events.lastOrNull { it.punchType == type.name }
    val workStart = latest(PunchType.WORK_START)
    val workEnd = latest(PunchType.WORK_END)
    val otStart = latest(PunchType.OT_START)
    val otEnd = latest(PunchType.OT_END)

    val working = workStart != null && (workEnd == null || workStart.punchTime >= workEnd.punchTime)
    val overtime = otStart != null && (otEnd == null || otStart.punchTime >= otEnd.punchTime)

    return when {
        workStart == null && otStart == null ->
            TodayStatus("未开始", PunchType.WORK_START, "上班打卡", timerLabel)
        working ->
            TodayStatus("工作中", PunchType.WORK_END, "下班打卡", timerLabel)
        overtime ->
            TodayStatus("加班中", PunchType.OT_END, "加班结束", timerLabel)
        workEnd != null && otStart == null ->
            TodayStatus("已下班", PunchType.OT_START, "加班打卡", timerLabel)
        else ->
            TodayStatus("今日已完成", null, "已完成", timerLabel)
    }
}
