/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/summary/SummaryScreen.kt
 * @Description: 统计汇总界面，五维度切换、区间翻页、指标卡片、工时柱状图与报表导出
 */
package com.worktime.pro.ui.summary

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worktime.pro.domain.export.ExportFormat
import com.worktime.pro.domain.export.ReportExporter
import com.worktime.pro.domain.model.SummaryRange
import com.worktime.pro.ui.LocalRepository
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    factory: ViewModelFactory,
    onBack: () -> Unit
) {
    val viewModel: SummaryViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val repository = LocalRepository.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var showRangePicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("统计汇总", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        // 维度 Tab
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SummaryRange.entries.forEach { range ->
                FilterChip(
                    selected = state.range == range,
                    onClick = { viewModel.setRange(range) },
                    label = { Text(range.label) }
                )
            }
        }

        // 区间翻页
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.shiftAnchor(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一区间")
            }
            val label = state.result?.let { "${it.startDate} ~ ${it.endDate}" } ?: "加载中"
            Text(label, modifier = Modifier.padding(horizontal = 12.dp))
            IconButton(onClick = { viewModel.shiftAnchor(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一区间")
            }
        }

        // 自定义区间入口：不分维度，选中后自动切到 CUSTOM
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { showRangePicker = true }) { Text("选择自定义区间") }
        }

        if (state.rangeTruncated) {
            Text("提示：自定义区间已截断至上限 ${com.worktime.pro.util.Constants.MAX_CUSTOM_RANGE_DAYS} 天",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        if (state.isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }

        state.error?.let { error ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.recompute() }) { Text("重试") }
            }
        }

        state.result?.let { result ->
            Spacer(Modifier.height(12.dp))
            // 指标卡片：2×2 大数字，先看结论再看趋势
            val netMinutes = result.totalWorkMinutes + result.totalOvertimeMinutes
            val overtimeRatio = if (netMinutes > 0) {
                (result.totalOvertimeMinutes * 100 / netMinutes)
            } else 0
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(
                            label = "正常工时",
                            value = TimeFormatter.formatDuration(result.totalWorkMinutes),
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = "加班工时",
                            value = TimeFormatter.formatDuration(result.totalOvertimeMinutes),
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(
                            label = "出勤天数",
                            value = "${result.attendanceDays} 天",
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = "加班占比",
                            value = "$overtimeRatio%",
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        "日均 ${TimeFormatter.formatDuration(result.averageWorkMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("每日工时趋势", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            WorkChart(result.daily.map { it.workMinutes to it.overtimeMinutes })

            Spacer(Modifier.height(16.dp))
            Text("导出报表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ExportFormat.CSV to "CSV",
                    ExportFormat.EXCEL to "Excel",
                    ExportFormat.PDF to "PDF"
                ).forEach { (format, label) ->
                    OutlinedButton(
                        onClick = {
                            exportReport(scope, result, repository, context, format) { message = it }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(label) }
                }
            }
        }

        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
    }

    if (showRangePicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = state.customStart
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = state.customEnd
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = pickerState.selectedStartDateMillis?.let(::millisToDate)
                    val end = pickerState.selectedEndDateMillis?.let(::millisToDate)
                    if (start != null && end != null) {
                        viewModel.applyCustomRange(start, end)
                        showRangePicker = false
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRangePicker = false }) { Text("取消") }
            }
        ) {
            DateRangePicker(
                state = pickerState,
                modifier = Modifier.height(480.dp),
                showModeToggle = false
            )
        }
    }
}

/** 导出报表并调用系统分享；协程绑定页面生命周期，IO 切后台线程 */
private fun exportReport(
    scope: CoroutineScope,
    result: com.worktime.pro.domain.model.SummaryResult,
    repository: com.worktime.pro.domain.repository.WorkTimeRepository,
    context: android.content.Context,
    format: ExportFormat,
    onMessage: (String) -> Unit
) {
    scope.launch {
        try {
            val events = withContext(Dispatchers.IO) {
                repository.getEventsInRange(result.startDate, result.endDate)
            }
            val file = withContext(Dispatchers.IO) {
                ReportExporter(context).export(result, events, format).getOrThrow()
            }
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = format.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onMessage("报表已生成：${file.name}")
            context.startActivity(Intent.createChooser(intent, "分享报表"))
        } catch (e: Exception) {
            onMessage("导出失败：${e.message ?: "未知错误"}")
        }
    }
}

/** M3 DatePicker 使用 UTC 毫秒，这里按 UTC 还原日期避免时区偏移 */
private fun millisToDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun StatTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/** 堆叠柱状图：正常工时在下、加班在上，按区间最大净投入归一化，最多展示 31 天 */
@Composable
private fun WorkChart(data: List<Pair<Int, Int>>) {
    val display = data.takeLast(31)
    val maxValue = (display.maxOfOrNull { it.first + it.second } ?: 1).coerceAtLeast(1)
    Column {
        if (display.isEmpty()) {
            Text(
                "当前区间暂无数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                display.forEach { (work, overtime) ->
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (overtime > 0) {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .height((overtime.toFloat() / maxValue * 140).dp)
                                    .background(MaterialTheme.colorScheme.tertiary)
                            )
                        }
                        if (work > 0) {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .height((work.toFloat() / maxValue * 140).dp)
                                    .background(MaterialTheme.colorScheme.secondary)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(MaterialTheme.colorScheme.secondary, "正常")
                LegendDot(MaterialTheme.colorScheme.tertiary, "加班")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            text = label,
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
