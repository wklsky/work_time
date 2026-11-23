/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/habit/HabitScreen.kt
 * @Description: 习惯打卡管理界面，自定义名称并配置固定打卡时间，供今日页一键应用
 */
package com.worktime.pro.ui.habit

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worktime.pro.data.datastore.AppSettings
import com.worktime.pro.data.datastore.PunchHabit
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.ui.LocalSettings
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.ui.components.EmptyState
import com.worktime.pro.ui.components.TimePickerDialog
import com.worktime.pro.util.TimeFormatter
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val STORAGE_TIME = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HabitScreen(
    factory: ViewModelFactory,
    onBack: () -> Unit
) {
    val viewModel: HabitViewModel = viewModel(factory = factory)
    val habits by viewModel.habits.collectAsState()
    val context = LocalContext.current
    val settings by LocalSettings.current.settings.collectAsState(initial = AppSettings())
    val use24Hour = TimeFormatter.use24Hour(context, settings.timeFormat)

    // 正在编辑的习惯：null 表示列表态，非 null 表示编辑态（新增时传一个空习惯）
    var editing by remember { mutableStateOf<PunchHabit?>(null) }

    if (editing == null) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text("习惯打卡", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "保存一组固定打卡时间，命名后在今日页一键写入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { editing = PunchHabit(name = "", times = emptyMap()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("新增习惯", modifier = Modifier.padding(start = 6.dp))
            }

            Spacer(Modifier.height(16.dp))
            if (habits.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Schedule,
                    title = "暂无习惯",
                    description = "点击上方「新增习惯」保存一组固定打卡时间。",
                    iconSize = 48.dp,
                    glyphSize = 22.dp
                )
            } else {
                habits.forEach { habit ->
                    HabitCard(
                        habit = habit,
                        use24Hour = use24Hour,
                        onEdit = { editing = habit },
                        onDelete = {
                            viewModel.deleteHabit(habit.id)
                            Toast.makeText(context, "已删除「${habit.name}」", Toast.LENGTH_SHORT).show()
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    } else {
        HabitEditor(
            habit = editing!!,
            use24Hour = use24Hour,
            onSave = { saved ->
                viewModel.saveHabit(saved) {
                    Toast.makeText(context, "已保存「${saved.name}」", Toast.LENGTH_SHORT).show()
                }
                editing = null
            },
            onCancel = { editing = null }
        )
    }
}

@Composable
private fun HabitCard(
    habit: PunchHabit,
    use24Hour: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(habit.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                val summary = PunchType.entries
                    .mapNotNull { type -> habit.times[type.name]?.let { time -> "${type.label} $time" } }
                    .joinToString("  ")
                Text(
                    summary.ifBlank { "未配置时间" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "编辑") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "删除") }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HabitEditor(
    habit: PunchHabit,
    use24Hour: Boolean,
    onSave: (PunchHabit) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(habit.name) }
    // 每类打卡的启用状态与时间，独立于 habit 编辑，保存时才汇总为 times
    val enabled = remember {
        mutableStateMapOf<PunchType, Boolean>().apply {
            PunchType.entries.forEach { put(it, habit.times.containsKey(it.name)) }
        }
    }
    val times = remember {
        mutableStateMapOf<PunchType, LocalTime>().apply {
            PunchType.entries.forEach { type ->
                habit.times[type.name]?.let { put(type, TimeFormatter.parseTime(it)) }
                    ?: put(type, LocalTime.of(9, 0))
            }
        }
    }
    var pickingType by remember { mutableStateOf<PunchType?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "取消")
            }
            Text(
                if (habit.name.isBlank()) "新增习惯" else "编辑习惯",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("习惯名称") },
            placeholder = { Text("如：正常班、加班日") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        Text(
            "勾选需要的时间项，点击时间可滑动调整",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PunchType.entries.forEach { type ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = enabled[type] == true,
                            onCheckedChange = { enabled[type] = it }
                        )
                        Text(type.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Text(
                            TimeFormatter.formatTime(times[type] ?: LocalTime.of(9, 0), use24Hour),
                            modifier = Modifier.clickable { pickingType = type },
                            color = if (enabled[type] == true) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isBlank()) {
                        Toast.makeText(context, "请输入习惯名称", Toast.LENGTH_SHORT).show()
                    } else {
                        val savedTimes = PunchType.entries
                            .filter { enabled[it] == true }
                            .associate { it.name to (times[it] ?: LocalTime.of(9, 0)).format(STORAGE_TIME) }
                        onSave(habit.copy(name = trimmed, times = savedTimes))
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("保存") }
        }
    }

    if (pickingType != null) {
        val type = pickingType!!
        TimePickerDialog(
            title = "打卡时刻",
            subtitle = "${type.label} · 保存后可在今日页一键写入",
            initialTime = times[type] ?: LocalTime.of(9, 0),
            onDismiss = { pickingType = null },
            onConfirm = { picked ->
                times[type] = picked
                pickingType = null
            }
        )
    }
}
