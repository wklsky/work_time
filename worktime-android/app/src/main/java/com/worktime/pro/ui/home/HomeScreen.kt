/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/home/HomeScreen.kt
 * @Description: 首页月历界面，「工时台账」签名元素——每日工时堆叠条（工时蓝/加班橙）与今日橙色圆环
 */
package com.worktime.pro.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EventNote
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worktime.pro.data.datastore.CalendarDisplayMode
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.ui.components.AppWindowSize
import com.worktime.pro.ui.components.EmptyState
import com.worktime.pro.ui.components.YearMonthPicker
import com.worktime.pro.ui.components.rememberAppWindowSize
import com.worktime.pro.ui.motion.CountUpText
import com.worktime.pro.ui.motion.Motion
import com.worktime.pro.ui.motion.motionDuration
import com.worktime.pro.util.TimeFormatter
import java.time.YearMonth

private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 日历格子最小高度：刚好容纳日期圆与最多两个时长标签，避免 6 行网格超出可视区 */
private val DAY_CELL_MIN_HEIGHT = 54.dp
private val DAY_CELL_OUTER_PADDING = 1.dp
private val DAY_CELL_INNER_PADDING = 2.dp
private val DAY_NUMBER_BOX_SIZE = 24.dp
private val DAY_NUMBER_FONT_SIZE = 13.sp

/** 宽屏双栏时左侧信息栏宽度：留给右侧日历足够的 7 列空间 */
private val HOME_SIDE_PANEL_WIDTH = 220.dp

@Composable
fun HomeScreen(
    factory: ViewModelFactory,
    onOpenDay: (String) -> Unit,
    onOpenManual: () -> Unit
) {
    val viewModel: HomeViewModel = viewModel(factory = factory)
    val days by viewModel.days.collectAsState()
    val displayMode by viewModel.calendarDisplayMode.collectAsState()
    val month by viewModel.displayedMonth.collectAsState()
    val showOvertimeShade by viewModel.showOvertimeShade.collectAsState()
    var monthDrag by remember { mutableFloatStateOf(0f) }
    // 年月快速跳转弹层开关
    var showMonthPicker by remember { mutableStateOf(false) }
    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }

    val monthDays = days.filter { it.isCurrentMonth }
    val monthRegular = monthDays.sumOf { it.regularMinutes }
    val monthOvertime = monthDays.sumOf { it.overtimeMinutes }
    val attendanceDays = monthDays.count { it.recordCount > 0 }
    val incompleteDays = monthDays.filter { it.isIncomplete }
    // 切月滑动时长：transitionSpec 是非组合上下文，先折算好再传入
    val monthDuration = motionDuration(Motion.BASE)
    val showEmptyState = attendanceDays == 0 && month == YearMonth.now()

    // 左右滑动切月的手势统一构造，单栏/双栏两种布局复用
    val swipeModifier = Modifier.pointerInput(month) {
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

    // 宽屏（平板/横屏/折叠展开）用「左信息栏 + 右日历」双栏，让日历独占整屏高度；
    // 竖屏手机用「固定头尾 + 自适应日历」单栏。两种布局复用同一套日历网格与格子自适应逻辑。
    val useSideLayout = rememberAppWindowSize() != AppWindowSize.COMPACT
    if (useSideLayout) {
        Row(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .width(HOME_SIDE_PANEL_WIDTH)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeHeader(
                    month = month,
                    monthDuration = monthDuration,
                    attendanceDays = attendanceDays,
                    monthRegular = monthRegular,
                    monthOvertime = monthOvertime,
                    showEmptyState = showEmptyState,
                    compact = true,
                    onMonthChange = viewModel::changeMonth,
                    onGoToToday = viewModel::goToToday,
                    onOpenMonthPicker = { showMonthPicker = true },
                    onOpenManual = onOpenManual
                )
                CalendarFooter(incompleteDays = incompleteDays, onOpenDay = onOpenDay)
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                WeekdayHeader()
                CalendarGrid(
                    modifier = Modifier.weight(1f),
                    days = days,
                    displayMode = displayMode,
                    showOvertimeShade = showOvertimeShade,
                    swipeModifier = swipeModifier,
                    onOpenDay = onOpenDay
                )
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            HomeHeader(
                month = month,
                monthDuration = monthDuration,
                attendanceDays = attendanceDays,
                monthRegular = monthRegular,
                monthOvertime = monthOvertime,
                showEmptyState = showEmptyState,
                onMonthChange = viewModel::changeMonth,
                onGoToToday = viewModel::goToToday,
                onOpenMonthPicker = { showMonthPicker = true },
                onOpenManual = onOpenManual
            )
            WeekdayHeader()
            CalendarGrid(
                modifier = Modifier.weight(1f),
                days = days,
                displayMode = displayMode,
                showOvertimeShade = showOvertimeShade,
                swipeModifier = swipeModifier,
                onOpenDay = onOpenDay
            )
            CalendarFooter(incompleteDays = incompleteDays, onOpenDay = onOpenDay)
        }
    }

    if (showMonthPicker) {
        YearMonthPicker(
            initial = month,
            onDismiss = { showMonthPicker = false },
            onConfirm = { picked ->
                viewModel.goToMonth(picked)
                showMonthPicker = false
            }
        )
    }
}

/** 顶部标题、本月结论、月份切换与无记录引导，单栏/双栏共用；compact 用于窄侧栏的紧凑排版 */
@Composable
private fun HomeHeader(
    month: YearMonth,
    monthDuration: Int,
    attendanceDays: Int,
    monthRegular: Int,
    monthOvertime: Int,
    showEmptyState: Boolean,
    compact: Boolean = false,
    onMonthChange: (Int) -> Unit,
    onGoToToday: () -> Unit,
    onOpenMonthPicker: () -> Unit,
    onOpenManual: () -> Unit
) {
    // 单栏需要页面左右留白；双栏左栏自身已带 16dp 内边距，不再叠加
    val horizontalInset = if (compact) 0.dp else 16.dp
    val metrics: @Composable () -> Unit = {
        MonthMetric(
            label = "出勤",
            target = attendanceDays,
            color = MaterialTheme.colorScheme.secondary
        ) { "${it}天" }
        MonthMetric(
            label = "正常",
            target = monthRegular,
            color = MaterialTheme.colorScheme.secondary
        ) { TimeFormatter.formatHours(it).ifBlank { "0h" } }
        MonthMetric(
            label = "加班",
            target = monthOvertime,
            color = MaterialTheme.colorScheme.tertiary
        ) { TimeFormatter.formatHours(it).ifBlank { "0h" } }
    }

    // 顶部标题：设置与统计入口已移至「我的」页；本月结论内联在标题右侧，省掉独立摘要卡片
    if (compact) {
        // 侧栏宽度不足以让标题与三项指标同行，改为上下两行，避免数值被折行
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                "工时助手",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) { metrics() }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = horizontalInset, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "工时助手",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { metrics() }
        }
    }

    // 月份切换：月份用大号等宽数字，作为台账的时间刻度；侧栏收窄箭头与字号，保证单行显示
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalInset),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val arrowModifier = if (compact) Modifier.size(36.dp) else Modifier
        IconButton(onClick = { onMonthChange(-1) }, modifier = arrowModifier) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
        }
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val forward = targetState > initialState
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(monthDuration)) { it / 4 * dir } + fadeIn(tween(monthDuration))) togetherWith
                    (slideOutHorizontally(tween(monthDuration)) { -it / 4 * dir } + fadeOut(tween(monthDuration)))
            },
            label = "monthSwitch"
        ) { m ->
            Text(
                text = "${m.year}年${m.monthValue}月",
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .padding(horizontal = if (compact) 4.dp else 24.dp, vertical = 4.dp)
                    .clickable(onClick = onOpenMonthPicker)
            )
        }
        IconButton(onClick = { onMonthChange(1) }, modifier = arrowModifier) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
        }
        if (month != YearMonth.now()) {
            TextButton(onClick = onGoToToday) { Text("今天") }
        }
    }

    if (showEmptyState) {
        EmptyState(
            icon = Icons.Filled.EventNote,
            title = "本月还没有记录",
            description = "从手动录入开始，记录第一条工时。",
            actionLabel = "手动录入",
            onAction = onOpenManual
        )
    }
}

/** 星期表头 */
@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
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
}

/**
 * 月历网格：按当月实际周数渲染（5 或 6 行），并用 weight 占满父级剩余空间。
 * 大屏格子随可用高度放大；小屏/横屏/大字体下维持最小可读高度且区域内可滚动，
 * 保证任意窗口尺寸下日历都能完整显示与操作。
 */
@Composable
private fun CalendarGrid(
    days: List<MonthDay>,
    displayMode: String,
    showOvertimeShade: Boolean,
    swipeModifier: Modifier,
    onOpenDay: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .then(swipeModifier)
    ) {
        val rows = maxOf(1, (days.size + 6) / 7)
        // 字体缩放会撑高格内文字，最小高度同步放大，避免时长标签被裁掉
        val minCellHeight = DAY_CELL_MIN_HEIGHT * LocalDensity.current.fontScale
        val cellHeight = maxOf(maxHeight / rows.toFloat(), minCellHeight)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth().height(cellHeight)) {
                    for (col in 0 until 7) {
                        val index = row * 7 + col
                        if (index < days.size) {
                            DayCell(
                                day = days[index],
                                displayMode = displayMode,
                                showOvertimeShade = showOvertimeShade,
                                modifier = Modifier.weight(1f).height(cellHeight),
                                onOpenDay = onOpenDay
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 日历下方的图例与缺卡提醒，单栏/双栏共用 */
@Composable
private fun CalendarFooter(
    incompleteDays: List<MonthDay>,
    onOpenDay: (String) -> Unit
) {
    // 图例：颜色语义不靠猜
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
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

    // 高度由外层按可用空间统一给定，格子内容垂直居中：大屏格子变高时视觉不空、小屏也不挤
    Column(
        modifier = modifier
            .padding(DAY_CELL_OUTER_PADDING)
            .clip(MaterialTheme.shapes.small)
            .background(shade)
            .semantics { contentDescription = accessibilityLabel }
            // 当月且非未来日期可进详情：有记录可修改/删除，无记录可补录
            .clickable(enabled = day.isCurrentMonth && !day.isFuture) { onOpenDay(dateKey) }
            .padding(vertical = DAY_CELL_INNER_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 日期数字：今日用橙色描边圆环，右上角红点为缺卡提示
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(DAY_NUMBER_BOX_SIZE)
                    .clip(CircleShape)
                    .then(
                        if (day.isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.tertiary, CircleShape)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day.date.dayOfMonth.toString(),
                    fontSize = DAY_NUMBER_FONT_SIZE,
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
    target: Int,
    color: Color,
    modifier: Modifier = Modifier,
    format: (Int) -> String
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CountUpText(
            target = target,
            format = format,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold
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
