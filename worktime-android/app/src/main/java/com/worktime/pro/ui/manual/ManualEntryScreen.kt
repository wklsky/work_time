/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/manual/ManualEntryScreen.kt
 * @Description: 手动录入界面，滑动时间选择器选时、按用户设置显示 12/24 小时制
 */
package com.worktime.pro.ui.manual

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worktime.pro.data.datastore.AppSettings
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.ui.LocalSettings
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.util.TimeFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryScreen(
    factory: ViewModelFactory,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val viewModel: ManualViewModel = viewModel(factory = factory)
    val context = LocalContext.current
    val settings by LocalSettings.current.settings.collectAsState(initial = AppSettings())
    val use24Hour = TimeFormatter.use24Hour(context, settings.timeFormat)

    var selectedType by remember { mutableStateOf(PunchType.WORK_START) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("手动录入", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))

        Text("选择打卡类型", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PunchType.entries.forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    label = { Text(text = type.label) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "日期：${TimeFormatter.formatDate(date)} ${TimeFormatter.weekdayCN(date)}",
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "修改",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 时间行：点击弹出滑动时间选择器，按用户设置的 12/24 制显示
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showTimePicker = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("时间", modifier = Modifier.weight(1f))
                    Text(
                        TimeFormatter.formatTime(time, use24Hour),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "点击时间可滑动选择",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    "提示：同一天可多次录入，工时会按打卡时间自动配对成多段；跨天记录（如下班早于上班）自动按次日处理。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { viewModel.save(date, selectedType, time, onSaved) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存")
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = use24Hour
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val hour = pickerState.hour
                    val minute = pickerState.minute
                    if (hour in 0..23 && minute in 0..59) {
                        time = LocalTime.of(hour, minute)
                    } else {
                        Toast.makeText(context, "时间无效", Toast.LENGTH_SHORT).show()
                    }
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
            text = { TimePicker(state = pickerState) }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
