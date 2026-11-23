/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/today/TodayScreen.kt
 * @Description: 今日页，「工时台账」时间卡：状态与实时累计 + 24 小时时间带 + 打卡时刻存根 + 今日流水
 */
package com.worktime.pro.ui.today

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worktime.pro.data.datastore.AppSettings
import com.worktime.pro.data.db.PunchEventEntity
import com.worktime.pro.data.db.WorkSessionEntity
import com.worktime.pro.data.db.toDayTotals
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.ui.LocalSettings
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.ui.components.PunchStamp
import com.worktime.pro.ui.components.TimePickerDialog
import com.worktime.pro.ui.sourceLabel
import com.worktime.pro.ui.theme.StampVermillion
import com.worktime.pro.ui.typeLabel
import com.worktime.pro.util.TimeFormatter
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    factory: ViewModelFactory,
    onOpenManual: () -> Unit,
    onOpenDayDetail: (String) -> Unit
) {
    val viewModel: TodayViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val now by viewModel.now.collectAsState()
    val currentMinute by viewModel.nowMinute.collectAsState()
    val plannedShift by viewModel.plannedShift.collectAsState()
    val context = LocalContext.current
    val settings by LocalSettings.current.settings.collectAsState(initial = AppSettings())
    val use24Hour = TimeFormatter.use24Hour(context, settings.timeFormat)

    var quickType by remember { mutableStateOf(PunchType.WORK_START) }
    var pendingDelete by remember { mutableStateOf<PunchEventEntity?>(null) }
    // 仅记录用户手动选过的时间；未手动选择的类型跟随手机当前分钟自动刷新
    val manualQuickTimes = remember { mutableStateMapOf<PunchType, LocalTime>() }
    val quickTime = manualQuickTimes[quickType] ?: currentMinute
    var showQuickTimePicker by remember { mutableStateOf(false) }
    // 打卡成功后浮现金章确认
    var stampInfo by remember { mutableStateOf<StampInfo?>(null) }

    val live = computeLiveTotals(state.events, state.sessions, now)
    val status = computeTodayStatus(state.events, formatClock(live.liveSeconds))
    // 当前状态只允许闭合正在进行的段，或开启新的上班/加班段
    val allowedQuickTypes = remember(state.events) { allowedQuickTypesFor(state.events) }
    // 设置里的默认动作：上班 / 加班到
    val preferredQuickType = remember(settings.defaultPunchType) {
        PunchType.fromName(settings.defaultPunchType) ?: PunchType.WORK_START
    }
    LaunchedEffect(allowedQuickTypes, preferredQuickType) {
        // 两种起始动作都可用时听设置；只剩唯一下一步（下班/加班退）时以状态为准
        quickType = preferredQuickType.takeIf { it in allowedQuickTypes }
            ?: allowedQuickTypes.first()
    }

    val onClock = live.workStartMillis != null || live.otStartMillis != null
    val zone = remember { ZoneId.systemDefault() }
    val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
    val dayStartMillis = state.today.atStartOfDay(zone).toInstant().toEpochMilli()
    val bands = remember(
        state.sessions,
        live.workStartMillis,
        live.otStartMillis,
        nowMillis,
        dayStartMillis
    ) {
        buildTapeBands(
            sessions = state.sessions,
            openWorkStartMillis = live.workStartMillis,
            openOtStartMillis = live.otStartMillis,
            nowMillis = nowMillis,
            dayStartMillis = dayStartMillis
        )
    }
    val nowMinuteOfDay = now.hour * 60f + now.minute + now.second / 60f
    val planEndMinutes = Duration.between(
        now,
        now.toLocalDate().atTime(plannedShift.end)
    ).toMinutes()

    val lastEvent = state.events.maxByOrNull { it.punchTime }
    val contextLine = when {
        onClock && planEndMinutes > 0 ->
            "距计划下班 ${TimeFormatter.formatDuration(planEndMinutes.toInt())}"
        onClock && planEndMinutes < 0 ->
            "已超计划下班 ${TimeFormatter.formatDuration((-planEndMinutes).toInt())}"
        onClock -> "已到计划下班时间"
        state.events.isEmpty() -> "今天还没开始记录"
        lastEvent != null ->
            "最近记录 ${viewModel.formatTime(lastEvent, use24Hour)} · ${lastEvent.typeLabel()}"
        else -> "今日记录已闭合"
    }

    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        // 页头：日期只保留月日与星期，年对「今日」没有信息量
        Text("今日", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(
            "${state.today.monthValue}月${state.today.dayOfMonth}日 · ${TimeFormatter.weekdayCN(state.today)}",
            style = MaterialTheme.typography.bodyMedium,
            color = muted
        )

        Spacer(Modifier.height(18.dp))

        // 时间卡：状态、实时累计、时间带与打卡存根放在同一张纸面上
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (onClock) MaterialTheme.colorScheme.tertiary
                                else muted.copy(alpha = 0.45f)
                            )
                    )
                    Text(
                        status.label,
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "计划下班 ${TimeFormatter.formatTime(plannedShift.end, use24Hour)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    "今日累计",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    status.timerLabel,
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-1).sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    contextLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (onClock && planEndMinutes < 0) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        muted
                    }
                )

                Spacer(Modifier.height(16.dp))

                DayTape(
                    bands = bands,
                    nowMinuteOfDay = nowMinuteOfDay,
                    plannedStartMinute = plannedShift.start.hour * 60f + plannedShift.start.minute,
                    plannedEndMinute = plannedShift.end.hour * 60f + plannedShift.end.minute,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // 色点同时充当时间带图例：蓝=正常，橙=加班
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    TotalItem(
                        color = MaterialTheme.colorScheme.secondary,
                        label = "正常",
                        minutes = live.regularMinutes
                    )
                    TotalItem(
                        color = MaterialTheme.colorScheme.tertiary,
                        label = "加班",
                        minutes = live.overtimeMinutes
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(14.dp))

                // 打卡存根：时间在上，动作在下；允许两种动作时才出现切换
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { showQuickTimePicker = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "打卡时刻",
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                TimeFormatter.formatTime(quickTime, use24Hour),
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = muted
                            )
                        }
                    }
                    if (allowedQuickTypes.size > 1) {
                        PunchTypeToggle(
                            options = allowedQuickTypes.sortedBy { it.ordinal },
                            selected = quickType,
                            onSelect = { quickType = it }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        val type = quickType
                        val time = quickTime
                        viewModel.quickPunch(type, time) { error ->
                            if (error == null) {
                                // 下一段默认回到当前分钟，避免沿用上一段的旧时间
                                manualQuickTimes.remove(type)
                                stampInfo = StampInfo(
                                    type.label,
                                    TimeFormatter.formatTime(time, use24Hour)
                                )
                            }
                            Toast.makeText(
                                context,
                                error ?: type.savedCopy(),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StampVermillion,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        quickType.ctaLabel(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(26.dp))

        // 今日流水：台账式横线表，时间单独成列
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "今日流水",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${state.events.size} 条",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = muted
            )
        }
        Spacer(Modifier.height(10.dp))

        if (state.events.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "今天还没有打卡记录",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "点击上方按钮，或使用底部「手动录入」记录今天的时间。",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted
                    )
                }
            }
        } else {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                state.events.forEachIndexed { index, event ->
                    PunchRecordRow(
                        typeLabel = event.typeLabel(),
                        sourceLabel = event.sourceLabel(),
                        timeLabel = viewModel.formatTime(event, use24Hour),
                        onDelete = { pendingDelete = event }
                    )
                    if (index < state.events.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        Spacer(Modifier.height(20.dp))

        // 手动录入入口：补齐今天任意时刻的多段记录
        OutlinedButton(
            onClick = onOpenManual,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("手动录入")
        }

        if (state.events.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { onOpenDayDetail(TimeFormatter.formatDateKey(state.today)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("查看当日明细")
            }
        }
    }

    pendingDelete?.let { event ->
        val typeLabel = event.typeLabel()
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条打卡？") },
            text = { Text("将删除「$typeLabel」记录，当日工时随即重算。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteEvent(event)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    if (showQuickTimePicker) {
        TimePickerDialog(
            title = "打卡时刻",
            subtitle = "今天 · ${TimeFormatter.weekdayCN(state.today)}",
            initialTime = quickTime,
            onDismiss = { showQuickTimePicker = false },
            onConfirm = { picked ->
                manualQuickTimes[quickType] = picked
                showQuickTimePicker = false
            }
        )
    }

    stampInfo?.let { stamp ->
        PunchStamp(
            typeLabel = stamp.typeLabel,
            timeLabel = stamp.timeLabel,
            onDismiss = { stampInfo = null }
        )
    }
}

/** 打卡盖章确认的内容 */
private data class StampInfo(
    val typeLabel: String,
    val timeLabel: String
)

/** 正常 / 加班累计，色点即时间带图例 */
@Composable
private fun TotalItem(
    color: Color,
    label: String,
    minutes: Int
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            TimeFormatter.formatDuration(minutes),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 两种合法动作并存时的切换：上班 / 加班到，或补录两个开始动作 */
@Composable
private fun PunchTypeToggle(
    options: List<PunchType>,
    selected: PunchType,
    onSelect: (PunchType) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp)
    ) {
        options.forEach { type ->
            val isSelected = type == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(type) }
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    type.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/** 流水行：类型 + 来源在左，时刻单独成列，删除入口保持克制 */
@Composable
private fun PunchRecordRow(
    typeLabel: String,
    sourceLabel: String,
    timeLabel: String,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                typeLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                sourceLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            timeLabel,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(2.dp))
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "删除「$typeLabel」$timeLabel 的记录",
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 按钮动词与实际动作同名：开口说「开始加班」，完成说「结束加班」 */
private fun PunchType.ctaLabel(): String = when (this) {
    PunchType.WORK_START -> "上班打卡"
    PunchType.WORK_END -> "下班打卡"
    PunchType.OT_START -> "开始加班"
    PunchType.OT_END -> "结束加班"
}

/** 成功提示沿用四类时间点的业务说法，避免「加班到打卡成功」这类别扭组合 */
private fun PunchType.savedCopy(): String = when (this) {
    PunchType.WORK_START -> "已记录上班时间"
    PunchType.WORK_END -> "已记录下班时间"
    PunchType.OT_START -> "已记录加班开始"
    PunchType.OT_END -> "已记录加班结束"
}

private data class TodayStatus(
    val label: String,
    val actionType: PunchType?,
    val timerLabel: String
)

/** 单日实时累计：已结算工时段 + 当前未闭合的上班/加班段 */
private data class TodayLive(
    val regularMinutes: Int,
    val overtimeMinutes: Int,
    val liveSeconds: Long,
    val workStartMillis: Long?,
    val otStartMillis: Long?
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
        // 进行中的段先按其会话类型计入，避免打卡前累计一直显示 0
        regularMinutes = totals.regularMinutes + (openWorkSeconds / 60).toInt(),
        overtimeMinutes = totals.overtimeMinutes + (openOtSeconds / 60).toInt(),
        liveSeconds = totals.netMinutes * 60L + openWorkSeconds + openOtSeconds,
        workStartMillis = if (workOpen) workStart?.punchTime else null,
        otStartMillis = if (otOpen) otStart?.punchTime else null
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
            TodayStatus("未开始", PunchType.WORK_START, timerLabel)
        working ->
            TodayStatus("工作中", PunchType.WORK_END, timerLabel)
        overtime ->
            TodayStatus("加班中", PunchType.OT_END, timerLabel)
        workEnd != null && otStart == null ->
            TodayStatus("已下班", PunchType.OT_START, timerLabel)
        else ->
            TodayStatus("今日已完成", null, timerLabel)
    }
}
