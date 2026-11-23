/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/rules/WorkRulesScreen.kt
 * @Description: 工作规则设置页，配置班次、午休、加班缓冲、日切与工作日，并可按新规则重算历史
 */
package com.worktime.pro.ui.rules

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worktime.pro.data.datastore.AppSettings
import com.worktime.pro.ui.LocalSettings
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.ui.components.TimePickerDialog
import com.worktime.pro.util.TimeFormatter
import java.time.LocalTime
import kotlin.math.roundToInt

private enum class RuleTimeField { START, END, REST_START, REST_END, DAY_CUT }

private val WEEKDAY_OPTIONS = listOf(
    1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
    5 to "周五", 6 to "周六", 7 to "周日"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkRulesScreen(
    factory: ViewModelFactory,
    onBack: () -> Unit
) {
    val viewModel: WorkRulesViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val settings by LocalSettings.current.settings.collectAsState(initial = AppSettings())
    val use24Hour = TimeFormatter.use24Hour(context, settings.timeFormat)

    var picking by remember { mutableStateOf<RuleTimeField?>(null) }
    var confirmRecalculate by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column {
                Text("工作规则", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "决定工时与加班的计算基准",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("班次时间", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TimeRow("上班时间", state.startTime, use24Hour) { picking = RuleTimeField.START }
                TimeRow("下班时间", state.endTime, use24Hour) { picking = RuleTimeField.END }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("午休")
                        Text(
                            "午休时段从工作时长中扣除",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = state.restEnabled, onCheckedChange = viewModel::setRestEnabled)
                }
                if (state.restEnabled) {
                    TimeRow("午休开始", state.restStart, use24Hour) { picking = RuleTimeField.REST_START }
                    TimeRow("午休结束", state.restEnd, use24Hour) { picking = RuleTimeField.REST_END }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("加班与日切", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row {
                    Text("加班缓冲", modifier = Modifier.weight(1f))
                    Text(
                        "${state.bufferMinutes} 分钟",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = state.bufferMinutes.toFloat(),
                    onValueChange = { viewModel.setBufferMinutes((it / 5f).roundToInt() * 5) },
                    valueRange = 0f..120f,
                    steps = 23
                )
                Text(
                    "下班后缓冲时间内不计加班",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TimeRow("日切时间", state.dayCutTime, use24Hour) { picking = RuleTimeField.DAY_CUT }
                Text(
                    "凌晨打卡在日切时间之前时，归入前一天",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("工作日", fontWeight = FontWeight.Medium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    WEEKDAY_OPTIONS.forEach { (day, label) ->
                        FilterChip(
                            selected = day in state.workDays,
                            onClick = { viewModel.toggleWorkDay(day) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("计算预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                PreviewRow("在岗时长", TimeFormatter.formatDuration(state.spanMinutes))
                PreviewRow("午休扣除", TimeFormatter.formatDuration(state.restMinutes))
                PreviewRow("标准工时", TimeFormatter.formatDuration(state.standardMinutes))
                PreviewRow(
                    "加班起算",
                    "${TimeFormatter.formatDuration(state.standardMinutes + state.bufferMinutes)}（标准 + 缓冲）"
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "保存后只影响之后的新记录；如需历史记录同步，请使用「保存并重算全部记录」。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { viewModel.save(recalculate = false) { } },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isSaving
        ) {
            Text(if (state.isSaving) "保存中…" else "保存")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { confirmRecalculate = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isSaving
        ) {
            Text("保存并重算全部记录")
        }
    }

    picking?.let { field ->
        val initial = when (field) {
            RuleTimeField.START -> state.startTime
            RuleTimeField.END -> state.endTime
            RuleTimeField.REST_START -> state.restStart
            RuleTimeField.REST_END -> state.restEnd
            RuleTimeField.DAY_CUT -> state.dayCutTime
        }
        val title = when (field) {
            RuleTimeField.START -> "上班时间"
            RuleTimeField.END -> "下班时间"
            RuleTimeField.REST_START -> "午休开始"
            RuleTimeField.REST_END -> "午休结束"
            RuleTimeField.DAY_CUT -> "日切时间"
        }
        TimePickerDialog(
            title = title,
            initialTime = initial,
            onDismiss = { picking = null },
            onConfirm = { time ->
                when (field) {
                    RuleTimeField.START -> viewModel.setStartTime(time)
                    RuleTimeField.END -> viewModel.setEndTime(time)
                    RuleTimeField.REST_START -> viewModel.setRestStart(time)
                    RuleTimeField.REST_END -> viewModel.setRestEnd(time)
                    RuleTimeField.DAY_CUT -> viewModel.setDayCutTime(time)
                }
                picking = null
            }
        )
    }

    if (confirmRecalculate) {
        AlertDialog(
            onDismissRequest = { confirmRecalculate = false },
            title = { Text("重算全部记录") },
            text = {
                Text("将使用当前规则重算所有历史打卡的工时段，记录较多时可能需要几秒。此操作会覆盖已有计算结果，是否继续？")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRecalculate = false
                    viewModel.save(recalculate = true) { }
                }) { Text("开始重算") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRecalculate = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun TimeRow(
    label: String,
    time: LocalTime,
    use24Hour: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(
            TimeFormatter.formatTime(time, use24Hour),
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
