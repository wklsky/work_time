/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/home/HomeScreen.kt
 * @Description: 首页月历界面，展示本月日历、每日下方上班/加班时间显示与拍照/手动录入入口
 */
package com.worktime.pro.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worktime.pro.data.datastore.CalendarDisplayMode
import com.worktime.pro.domain.model.DaySummary
import com.worktime.pro.ui.LocalRepository
import com.worktime.pro.ui.LocalSettings
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.util.TimeFormatter

private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")
private const val GRID_CELLS = 42

@Composable
fun HomeScreen(
    factory: ViewModelFactory,
    onOpenDay: (String) -> Unit,
    onOpenCamera: () -> Unit,
    onOpenManual: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSummary: () -> Unit
) {
    val viewModel: HomeViewModel = viewModel(factory = factory)
    val days by viewModel.days.collectAsState()
    val displayMode by viewModel.calendarDisplayMode.collectAsState()
    val showShade by viewModel.showOvertimeShade.collectAsState()
    val month by viewModel.displayedMonth.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 顶部栏：设置入口
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("工时助手", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row {
                IconButton(onClick = onOpenSummary) {
                    Icon(Icons.Filled.BarChart, contentDescription = "统计")
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置")
                }
            }
        }

        // 月份切换
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            IconButton(onClick = { viewModel.changeMonth(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
            }
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

        // 月历网格 6×7
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            for (row in 0 until GRID_CELLS / 7) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val index = row * 7 + col
                        if (index < days.size) {
                            DayCell(
                                day = days[index],
                                displayMode = displayMode,
                                showOvertimeShade = showShade,
                                modifier = Modifier.weight(1f),
                                onOpenDay = onOpenDay
                            )
                        }
                    }
                }
            }
        }

        // 底部操作栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.material3.Button(
                onClick = onOpenCamera,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("拍照识别", modifier = Modifier.padding(start = 6.dp))
            }
            androidx.compose.material3.OutlinedButton(
                onClick = onOpenManual,
                modifier = Modifier.weight(1f)
            ) {
                Text("手动录入")
            }
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
    val repository = LocalRepository.current
    val dateKey = TimeFormatter.formatDateKey(day.date)
    val summary by repository.observeDaySummary(dateKey)
        .collectAsState(initial = DaySummary(dateKey))

    val hasRecord = summary.recordCount > 0
    val overtimeMinutes = summary.overtimeMinutes
    // 加班色阶：根据加班时长显示不同深度的底色，仅对当月日期生效
    val cellBackground = when {
        !showOvertimeShade || overtimeMinutes == 0 -> Color.Transparent
        overtimeMinutes < 60 -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
        overtimeMinutes < 180 -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.24f)
        else -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.38f)
    }

    Column(
        modifier = modifier
            .aspectRatio(0.72f)
            .padding(2.dp)
            .clip(MaterialTheme.shapes.small)
            .background(cellBackground)
            .clickable(enabled = hasRecord) { onOpenDay(dateKey) }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 日期数字
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (day.isToday) MaterialTheme.colorScheme.primary else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                fontSize = 14.sp,
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    day.isToday -> MaterialTheme.colorScheme.onPrimary
                    !day.isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }

        // 记录圆点
        if (hasRecord) {
            Row(modifier = Modifier.padding(top = 2.dp)) {
                repeat(summary.recordCount.coerceAtMost(4)) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .padding(horizontal = 1.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        // 每日下方时间显示：全局统一模式（用户决策）
        val timeText = when (displayMode) {
            CalendarDisplayMode.OVERTIME.value -> {
                if (summary.firstOtStart != null || summary.lastOtEnd != null) {
                    "加班 ${summary.firstOtStart ?: "?"}–${summary.lastOtEnd ?: "?"}"
                } else "—"
            }
            else -> {
                if (summary.firstWorkStart != null || summary.lastWorkEnd != null) {
                    "上班 ${summary.firstWorkStart ?: "?"}–${summary.lastWorkEnd ?: "?"}"
                } else "—"
            }
        }
        Text(
            text = timeText,
            fontSize = 8.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            color = if (hasRecord) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
    }
}
