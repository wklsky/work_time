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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worktime.pro.data.db.PunchEventEntity
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

@Composable
fun SummaryScreen(
    factory: ViewModelFactory,
    onBack: () -> Unit
) {
    val viewModel: SummaryViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val repository = LocalRepository.current
    var message by remember { mutableStateOf<String?>(null) }

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

        if (state.rangeTruncated) {
            Text("提示：自定义区间已截断至上限 ${com.worktime.pro.util.Constants.MAX_CUSTOM_RANGE_DAYS} 天",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        state.result?.let { result ->
            Spacer(Modifier.height(12.dp))
            // 指标卡片
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricRow("出勤天数", "${result.attendanceDays} 天")
                    MetricRow("工作时长", TimeFormatter.formatDuration(result.totalWorkMinutes))
                    MetricRow("加班时长", TimeFormatter.formatDuration(result.totalOvertimeMinutes))
                    MetricRow("日均工时", TimeFormatter.formatDuration(result.averageWorkMinutes))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("每日工时趋势", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            WorkChart(result.daily.map { it.workMinutes to it.overtimeMinutes })

            Spacer(Modifier.height(16.dp))
            // 导出按钮：CSV 与 Excel
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    exportReport(
                        result, repository, context, ExportFormat.CSV,
                        onMessage = { message = it }
                    )
                }, modifier = Modifier.weight(1f)) { Text("导出 CSV") }
                Button(onClick = {
                    exportReport(
                        result, repository, context, ExportFormat.EXCEL,
                        onMessage = { message = it }
                    )
                }, modifier = Modifier.weight(1f)) { Text("导出 Excel") }
            }
        }

        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** 导出报表并调用系统分享，所有 IO 在后台线程执行 */
private fun exportReport(
    result: com.worktime.pro.domain.model.SummaryResult,
    repository: com.worktime.pro.domain.repository.WorkTimeRepository,
    context: android.content.Context,
    format: ExportFormat,
    onMessage: (String) -> Unit
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val events: List<PunchEventEntity> = repository.getEventsInRange(result.startDate, result.endDate)
            val file = ReportExporter(context).export(result, events, format).getOrThrow()
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            withContext(Dispatchers.Main) {
                onMessage("报表已生成：${file.name}")
                context.startActivity(Intent.createChooser(intent, "分享报表"))
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onMessage("导出失败：${e.message ?: "未知错误"}") }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

/** 简易柱状图：按每日工时最大值归一化绘制，最多展示 31 根柱子 */
@Composable
private fun WorkChart(data: List<Pair<Int, Int>>) {
    val display = data.takeLast(31)
    val maxValue = (display.maxOfOrNull { it.first + it.second } ?: 1).coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        display.forEach { (work, overtime) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(((work + overtime).toFloat() / maxValue * 140).dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
                if (display.size <= 10) {
                    Text("${work + overtime}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
