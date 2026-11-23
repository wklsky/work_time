/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/manual/ManualEntryScreen.kt
 * @Description: 手动录入界面：打卡类型分格、日期快捷选择与「打卡时刻」大读数，
 *               点击读数进入统一的「校时」时间带选择器。
 */
package com.worktime.pro.ui.manual

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worktime.pro.data.datastore.AppSettings
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.ui.LocalSettings
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.ui.components.SelectDateDialog
import com.worktime.pro.ui.components.TimePickerDialog
import com.worktime.pro.util.TimeFormatter
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
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

    val today = remember { LocalDate.now() }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    "手动录入",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "补录真实的打卡时刻，工时自动重算",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        SectionLabel("打卡类型")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PunchType.entries.forEach { type ->
                TypeTile(
                    label = type.label,
                    selected = selectedType == type,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedType = type }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        SectionLabel("日期")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(0L to "今天", 1L to "昨天", 2L to "前天").forEach { (daysAgo, label) ->
                val quickDate = today.minusDays(daysAgo)
                FilterChip(
                    selected = date == quickDate,
                    onClick = { date = quickDate },
                    label = { Text(label) }
                )
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showDatePicker = true }) {
                Icon(
                    Icons.Filled.DateRange,
                    contentDescription = "选择其他日期",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // 打卡时刻：整页唯一的大读数，点一下即可重新校准
        val heroShape = MaterialTheme.shapes.large
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(heroShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, heroShape)
                .clickable { showTimePicker = true }
                .padding(18.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "打卡时刻",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "点击调整",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    TimeFormatter.formatTime(time, use24Hour),
                    style = MaterialTheme.typography.displayMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-1).sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${TimeFormatter.formatDate(date)} · ${TimeFormatter.weekdayCN(date)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "同一天可多次录入，工时会按打卡时间自动配对；跨天记录自动按次日处理。",
            style = MaterialTheme.typography.bodySmall,
            color = muted
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.save(date, selectedType, time) { error ->
                    if (error != null) {
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    } else {
                        onSaved()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            // 按钮直接说出会产生什么记录，避免「保存」这类空泛动词
            Text(
                "记录 ${selectedType.label} ${TimeFormatter.formatTime(time, use24Hour)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            title = "打卡时刻",
            subtitle = "${TimeFormatter.formatDate(date)} · ${TimeFormatter.weekdayCN(date)}",
            initialTime = time,
            use24Hour = use24Hour,
            onDismiss = { showTimePicker = false },
            onConfirm = { picked ->
                time = picked
                showTimePicker = false
            }
        )
    }

    if (showDatePicker) {
        SelectDateDialog(
            initialDate = date,
            onDismiss = { showDatePicker = false },
            onConfirm = { picked ->
                date = picked
                showDatePicker = false
            }
        )
    }
}

/** 小节标题：字距拉开的眉标，和大读数形成层级，不额外占视觉重量 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 2.sp
    )
}

/**
 * 打卡类型分格：四类时间点等宽排开，选中即填墨。
 * 用 selectable 而不是 clickable，保留单选语义，读屏会读出「已选中」。
 */
@Composable
private fun TypeTile(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1
        )
    }
}
