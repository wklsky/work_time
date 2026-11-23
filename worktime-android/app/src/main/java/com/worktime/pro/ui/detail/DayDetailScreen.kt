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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import com.worktime.pro.data.datastore.PunchHabit
import com.worktime.pro.data.db.PunchEventEntity
import com.worktime.pro.data.db.toDayTotals
import com.worktime.pro.domain.engine.PunchPairing
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.ui.LocalSettings
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.ui.components.EmptyState
import com.worktime.pro.ui.components.TimePickerDialog
import com.worktime.pro.ui.sourceLabel
import com.worktime.pro.ui.typeLabel
import com.worktime.pro.util.TimeFormatter
import java.time.Duration
import java.time.LocalTime

private data class PunchEditorTarget(
    val type: PunchType,
    val event: PunchEventEntity?
)

/** 完整加班段：开始与结束两条打卡配对归组，删除/编辑都以整段为单位 */
private data class OvertimeGroup(
    val start: PunchEventEntity,
    val end: PunchEventEntity
)

/** 待确认删除的目标：普通打卡单条，完整加班段两条 */
private data class DeleteIntent(
    val label: String,
    val events: List<PunchEventEntity>
)

/** 整段编辑加班的两步流程：先改开始、再改结束，合并提交校验 */
private enum class SegmentEditStep { START, END }

/** 列表渲染单元：普通打卡单条，或归组的完整加班段 */
private sealed interface DayEventItem {
    data class Single(val event: PunchEventEntity) : DayEventItem
    data class Overtime(val group: OvertimeGroup) : DayEventItem
}

/**
 * 把当日打卡事件按重算同款配对规则分组。
 * 业务规则：分组必须复用 PunchPairing 的贪心配对，保证列表展示的"段"与实际工时计算结果一致；
 * 未配对成功的孤立加班端点仍按单条展示，可单独删除或补录另一端。
 */
private fun buildDayItems(events: List<PunchEventEntity>): List<DayEventItem> {
    val otStarts = events.filter { it.punchType == PunchType.OT_START.name }.sortedBy { it.punchTime }
    val otEnds = events.filter { it.punchType == PunchType.OT_END.name }.sortedBy { it.punchTime }
    val partnerOf = mutableMapOf<Long, Long>()
    PunchPairing.pair(otStarts, otEnds) { it.punchTime }.forEach { (start, end) ->
        partnerOf[start.id] = end.id
        partnerOf[end.id] = start.id
    }
    val byId = events.associateBy { it.id }
    val emitted = mutableSetOf<Long>()
    val items = mutableListOf<DayEventItem>()
    events.sortedBy { it.punchTime }.forEach { event ->
        if (event.id in emitted) return@forEach
        emitted += event.id
        val partnerId = partnerOf[event.id]
        if (event.punchType == PunchType.OT_START.name && partnerId != null) {
            val partner = byId.getValue(partnerId)
            emitted += partnerId
            items += DayEventItem.Overtime(OvertimeGroup(event, partner))
        } else {
            items += DayEventItem.Single(event)
        }
    }
    return items
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    dateKey: String,
    factory: ViewModelFactory,
    onBack: () -> Unit,
    onOpenHabits: () -> Unit
) {
    val viewModel: DayDetailViewModel = viewModel(
        key = dateKey,
        factory = viewModelFactoryFor(factory, dateKey)
    )
    val state by viewModel.state.collectAsState()
    val habits by viewModel.habits.collectAsState()
    val context = LocalContext.current
    val settings by LocalSettings.current.settings.collectAsState(initial = AppSettings())
    val use24Hour = TimeFormatter.use24Hour(context, settings.timeFormat)

    // 正在编辑/补录的目标：event 为空表示新增一段
    var editingTarget by remember { mutableStateOf<PunchEditorTarget?>(null) }
    // 整段编辑加班：先改开始、再改结束，确认后一次性校验提交
    var editingSegment by remember { mutableStateOf<OvertimeGroup?>(null) }
    var segmentStep by remember { mutableStateOf<SegmentEditStep?>(null) }
    var segmentStartTime by remember { mutableStateOf<LocalTime?>(null) }
    // 待确认删除的目标：普通打卡单条，完整加班段两条
    var pendingDelete by remember { mutableStateOf<DeleteIntent?>(null) }
    // 当日已有记录时，应用习惯需先确认追加还是覆盖
    var pendingHabit by remember { mutableStateOf<PunchHabit?>(null) }

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
                    buildDayItems(state.events).forEach { item ->
                        when (item) {
                            is DayEventItem.Single -> {
                                val event = item.event
                                val type = PunchType.fromName(event.punchType)
                                if (type != null) {
                                    EventRow(
                                        event = event,
                                        type = type,
                                        use24Hour = use24Hour,
                                        onEdit = { editingTarget = PunchEditorTarget(type, event) },
                                        onDelete = {
                                            pendingDelete = DeleteIntent(event.typeLabel(), listOf(event))
                                        }
                                    )
                                }
                            }
                            is DayEventItem.Overtime -> OvertimeRow(
                                group = item.group,
                                use24Hour = use24Hour,
                                onEdit = {
                                    editingSegment = item.group
                                    segmentStep = SegmentEditStep.START
                                    segmentStartTime = null
                                },
                                onDelete = {
                                    pendingDelete = DeleteIntent(
                                        "整段加班",
                                        listOf(item.group.start, item.group.end)
                                    )
                                }
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

                // 习惯打卡：一键把固定班次写入当前查看的这一天；已有记录时先确认追加/覆盖
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "习惯打卡",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onOpenHabits) { Text("管理") }
                }
                if (habits.isEmpty()) {
                    Text(
                        "暂无习惯，点「管理」添加固定班次后可一键写入当天",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        habits.forEach { habit ->
                            AssistChip(
                                onClick = {
                                    if (state.events.isEmpty()) {
                                        viewModel.applyHabit(habit, overwrite = false) { error ->
                                            Toast.makeText(
                                                context,
                                                error ?: "已应用「${habit.name}」",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } else {
                                        pendingHabit = habit
                                    }
                                },
                                label = { Text(habit.name) }
                            )
                        }
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

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除打卡") },
            text = {
                Text(
                    if (target.events.size > 1) {
                        "确定删除这一段「${target.label}」吗？将同时删除开始与结束共 ${target.events.size} 条记录，删除后当日工时将自动重算。"
                    } else {
                        "确定删除这条「${target.label}」记录吗？删除后当日工时将自动重算。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // 整段删除只需传入任一端点，Repository 会按配对关系连带删除另一端
                    target.events.firstOrNull()?.let { viewModel.deleteEvent(it) }
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    // 当日已有记录：确认习惯是追加写入还是清空后覆盖
    pendingHabit?.let { habit ->
        val applyHabit: (Boolean, String) -> Unit = { overwrite, successLabel ->
            viewModel.applyHabit(habit, overwrite) { error ->
                Toast.makeText(
                    context,
                    error ?: "${successLabel}「${habit.name}」",
                    Toast.LENGTH_SHORT
                ).show()
            }
            pendingHabit = null
        }
        ModalBottomSheet(onDismissRequest = { pendingHabit = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "应用习惯「${habit.name}」",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "这一天已有打卡记录。追加会保留现有记录并写入习惯时间；覆盖会先清空当日记录，再写入习惯时间。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { applyHabit(false, "已追加应用") },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("追加应用") }
                OutlinedButton(
                    onClick = { applyHabit(true, "已覆盖应用") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("覆盖应用") }
                TextButton(
                    onClick = { pendingHabit = null },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("取消") }
            }
        }
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

    // 整段编辑加班：两步弹窗依次收集新的开始与结束时间，最后一次校验提交
    editingSegment?.let { group ->
        when (segmentStep) {
            SegmentEditStep.START -> TimePickerDialog(
                title = "修改加班开始",
                initialTime = TimeFormatter.millisToLocalTime(group.start.punchTime),
                onDismiss = {
                    editingSegment = null
                    segmentStep = null
                },
                onConfirm = { time ->
                    segmentStartTime = time
                    segmentStep = SegmentEditStep.END
                }
            )
            SegmentEditStep.END -> TimePickerDialog(
                title = "修改加班结束",
                initialTime = TimeFormatter.millisToLocalTime(group.end.punchTime),
                onDismiss = {
                    editingSegment = null
                    segmentStep = null
                },
                onConfirm = { time ->
                    val startTime = segmentStartTime
                    if (startTime != null) {
                        viewModel.updateOvertimeSegment(group.start, group.end, startTime, time) { error ->
                            if (error != null) {
                                // 校验失败（如结束早于开始、与其他段重叠）时回到结束步骤重试
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                segmentStep = SegmentEditStep.END
                            } else {
                                Toast.makeText(context, "加班段时间已更新", Toast.LENGTH_SHORT).show()
                                editingSegment = null
                                segmentStep = null
                            }
                        }
                    }
                }
            )
            null -> Unit
        }
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

/** 完整加班段行：开始与结束归组展示，编辑/删除作用于整段 */
@Composable
private fun OvertimeRow(
    group: OvertimeGroup,
    use24Hour: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val start = TimeFormatter.millisToLocalTime(group.start.punchTime)
    val end = TimeFormatter.millisToLocalTime(group.end.punchTime)
    val durationMinutes = Duration.between(
        TimeFormatter.millisToLocalDateTime(group.start.punchTime),
        TimeFormatter.millisToLocalDateTime(group.end.punchTime)
    ).toMinutes()

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("加班段（${TimeFormatter.formatDuration(durationMinutes.toInt())}）", fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${TimeFormatter.formatTime(start, use24Hour)} – ${TimeFormatter.formatTime(end, use24Hour)}",
                    modifier = Modifier.clickable(onClick = onEdit),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    " · ${group.start.sourceLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "修改整段加班时间")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "删除整段加班")
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
