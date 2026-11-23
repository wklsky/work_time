/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/home/HomeScreen.kt
 * @Description: 首页月历界面，「工时台账」签名元素——每日工时堆叠条（工时蓝/加班橙）与今日橙色圆环
 */
package com.worktime.pro.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worktime.pro.data.datastore.CalendarDisplayMode
import com.worktime.pro.data.datastore.PunchHabit
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.util.TimeFormatter
import java.time.YearMonth

private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")
private const val GRID_CELLS = 42

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    factory: ViewModelFactory,
    onOpenDay: (String) -> Unit,
    onOpenManual: () -> Unit,
    onOpenHabits: () -> Unit
) {
    val viewModel: HomeViewModel = viewModel(factory = factory)
    val days by viewModel.days.collectAsState()
    val displayMode by viewModel.calendarDisplayMode.collectAsState()
    val month by viewModel.displayedMonth.collectAsState()
    val habits by viewModel.habits.collectAsState()
    val todayEventCount by viewModel.todayEventCount.collectAsState()
    val showOvertimeShade by viewModel.showOvertimeShade.collectAsState()
    val context = LocalContext.current
    var pendingHabit by remember { mutableStateOf<PunchHabit?>(null) }
    var monthDrag by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }

    val monthDays = days.filter { it.isCurrentMonth }
    val monthRegular = monthDays.sumOf { it.regularMinutes }
    val monthOvertime = monthDays.sumOf { it.overtimeMinutes }
    val attendanceDays = monthDays.count { it.recordCount > 0 }
    val incompleteDays = monthDays.filter { it.isIncomplete }

    // 加垂直滚动：月历 6 行 + 底部操作栏可能超出屏幕，保证手动录入入口可达
    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // 顶部标题：设置与统计入口已移至「我的」页
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "工时助手",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // 月份切换：月份用大号等宽数字，作为台账的时间刻度
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.changeMonth(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
            }
            Text(
                text = "${month.year}年${month.monthValue}月",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            IconButton(onClick = { viewModel.changeMonth(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
            }
            if (month != YearMonth.now()) {
                TextButton(onClick = { viewModel.goToToday() }) { Text("今天") }
            }
        }

        // 本月摘要：出勤 / 正常 / 加班，一次看懂本月结论
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                MonthMetric(
                    label = "出勤",
                    value = "${attendanceDays}天",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                MonthMetric(
                    label = "正常",
                    value = TimeFormatter.formatHours(monthRegular).ifBlank { "0h" },
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                MonthMetric(
                    label = "加班",
                    value = TimeFormatter.formatHours(monthOvertime).ifBlank { "0h" },
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (attendanceDays == 0 && month == YearMonth.now()) {
            Text(
                "本月还没有记录，手动录入开始",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 星期表头
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
            WEEKDAYS.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 月历网格 6×7：左右滑动切月，纵向滚动仍交给外层
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                .pointerInput(month) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, amount -> monthDrag += amount },
                        onDragEnd = {
                            if (monthDrag > swipeThreshold) viewModel.changeMonth(-1)
                            else if (monthDrag < -swipeThreshold) viewModel.changeMonth(1)
                            monthDrag = 0f
                        },
                        onDragCancel = { monthDrag = 0f }
                    )
                }
        ) {
            for (row in 0 until GRID_CELLS / 7) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val index = row * 7 + col
                        if (index < days.size) {
                            DayCell(
                                day = days[index],
                                displayMode = displayMode,
                                showOvertimeShade = showOvertimeShade,
                                modifier = Modifier.weight(1f),
                                onOpenDay = onOpenDay
                            )
                        }
                    }
                }
            }
        }

        // 图例：颜色语义不靠猜
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(MaterialTheme.colorScheme.secondary, "正常")
            LegendItem(MaterialTheme.colorScheme.tertiary, "加班")
            LegendItem(MaterialTheme.colorScheme.error, "缺卡")
        }

        // 缺卡提醒：点最近一天直接进详情补录
        if (incompleteDays.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .clickable {
                        onOpenDay(TimeFormatter.formatDateKey(incompleteDays.last().date))
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚠", color = MaterialTheme.colorScheme.error)
                Text(
                    " ${incompleteDays.size} 天缺卡，最近一天 ${incompleteDays.last().date.dayOfMonth} 日",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // 习惯打卡：横向 Chip，点击即应用；管理入口保留
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "习惯打卡",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onOpenHabits) { Text("管理") }
        }
        if (habits.isEmpty()) {
            Text(
                "暂无习惯，可在设置中添加固定班次。",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                habits.forEach { habit ->
                    AssistChip(
                        onClick = {
                            if (todayEventCount > 0) {
                                pendingHabit = habit
                            } else {
                                viewModel.applyHabit(habit, overwrite = false) { error ->
                                    Toast.makeText(
                                        context,
                                        error ?: "已应用「${habit.name}」",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        label = { Text(habit.name) }
                    )
                }
            }
        }

        // 底部操作栏：手动录入主操作
        Button(
            onClick = onOpenManual,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("手动录入")
        }
    }

    if (pendingHabit != null) {
        val habit = pendingHabit!!
        AlertDialog(
            onDismissRequest = { pendingHabit = null },
            title = { Text("应用习惯") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "今天已有打卡记录。追加会保留现有记录并写入习惯时间；覆盖会先清空今天记录，再写入习惯时间。"
                    )
                    TextButton(onClick = {
                        viewModel.applyHabit(habit, overwrite = true) { error ->
                            Toast.makeText(
                                context,
                                error ?: "已覆盖应用「${habit.name}」",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        pendingHabit = null
                    }) {
                        Text("覆盖应用", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.applyHabit(habit, overwrite = false) { error ->
                        Toast.makeText(
                            context,
                            error ?: "已追加应用「${habit.name}」",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    pendingHabit = null
                }) { Text("追加应用") }
            },
            dismissButton = {
                TextButton(onClick = { pendingHabit = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun DayCell(
    day: MonthDay,
    displayMode: String,
    showOvertimeShade: Boolean,
    modifier: Modifier = Modifier,
    onOpenDay: (String) -> Unit
) {
    val dateKey = TimeFormatter.formatDateKey(day.date)
    val hasRecord = day.recordCount > 0
    val shade = if (showOvertimeShade) overtimeShade(day.overtimeMinutes) else Color.Transparent
    val accessibilityLabel = buildString {
        append("${day.date.monthValue}月${day.date.dayOfMonth}日")
        if (day.isToday) append("，今天")
        if (day.regularMinutes > 0) append("，正常${TimeFormatter.formatDuration(day.regularMinutes)}")
        if (day.overtimeMinutes > 0) append("，加班${TimeFormatter.formatDuration(day.overtimeMinutes)}")
        if (day.isIncomplete) append("，缺卡")
        if (!hasRecord) append("，无记录")
    }

    Column(
        modifier = modifier
            .heightIn(min = 70.dp)
            .padding(2.dp)
            .clip(MaterialTheme.shapes.small)
            .background(shade)
            .semantics { contentDescription = accessibilityLabel }
            // 当月且非未来日期可进详情：有记录可修改/删除，无记录可补录
            .clickable(enabled = day.isCurrentMonth && !day.isFuture) { onOpenDay(dateKey) }
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // 日期数字：今日用橙色描边圆环，右上角红点为缺卡提示
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(28.dp)
                    .clip(CircleShape)
                    .then(
                        if (day.isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.tertiary, CircleShape)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day.date.dayOfMonth.toString(),
                    fontSize = 14.sp,
                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        day.isToday -> MaterialTheme.colorScheme.tertiary
                        !day.isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        day.isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            if (day.isIncomplete) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 4.dp, top = 2.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                )
            }
        }

        // 时长标签：按显示模式展示具体时长（h）。有打卡必有数值——
        // 若当天只有加班、又处于「上班时长」模式，则回退显示加班时长。
        val workText = TimeFormatter.formatHours(day.regularMinutes)
        val overtimeText = TimeFormatter.formatHours(day.overtimeMinutes)
        if (hasRecord) {
            when (displayMode) {
                CalendarDisplayMode.ALL.value -> {
                    if (workText.isNotEmpty()) DurationTag(workText, isOvertime = false)
                    if (overtimeText.isNotEmpty()) DurationTag(overtimeText, isOvertime = true)
                }
                CalendarDisplayMode.OVERTIME.value -> {
                    if (overtimeText.isNotEmpty()) DurationTag(overtimeText, isOvertime = true)
                    else if (workText.isNotEmpty()) DurationTag(workText, isOvertime = false)
                }
                else -> { // WORK_TIME
                    if (workText.isNotEmpty()) DurationTag(workText, isOvertime = false)
                    else if (overtimeText.isNotEmpty()) DurationTag(overtimeText, isOvertime = true)
                }
            }
        }
    }
}

/** 加班底色四档：浅→深表示加班时长，关闭开关时返回透明 */
@Composable
private fun overtimeShade(minutes: Int): Color {
    val base = MaterialTheme.colorScheme.tertiary
    val alpha = when {
        minutes <= 0 -> 0f
        minutes <= 30 -> 0.06f
        minutes <= 60 -> 0.10f
        minutes <= 120 -> 0.14f
        else -> 0.18f
    }
    return base.copy(alpha = alpha)
}

@Composable
private fun MonthMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(
            text = label,
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 时长标签：上班用工时蓝、加班用加班橙，圆角小块样式展示每日工时 */
@Composable
private fun DurationTag(text: String, isOvertime: Boolean) {
    val container = if (isOvertime) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
    val content = if (isOvertime) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSecondary
    Box(
        modifier = Modifier
            .padding(top = 1.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(container)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = content, maxLines = 1)
    }
}
