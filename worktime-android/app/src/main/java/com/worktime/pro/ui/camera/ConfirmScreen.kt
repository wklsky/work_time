/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/camera/ConfirmScreen.kt
 * @Description: 识别结果确认页，展示原图与四类时间候选，用户核对/修正后入库（OCR 绝不直接写库）
 */
package com.worktime.pro.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.worktime.pro.domain.evidence.EvidenceStore
import com.worktime.pro.domain.model.Confidence
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.domain.ocr.OcrCandidate
import com.worktime.pro.ui.ViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConfirmScreen(
    factory: ViewModelFactory,
    onSaved: () -> Unit,
    onRetake: () -> Unit
) {
    val viewModel: CameraViewModel = viewModel(factory = factory)
    val state by viewModel.confirmState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by com.worktime.pro.ui.LocalSettings.current.settings.collectAsState(
        initial = com.worktime.pro.data.datastore.AppSettings()
    )
    val use24Hour = com.worktime.pro.util.TimeFormatter.use24Hour(context, settings.timeFormat)

    // Android 8–9 写公共相册需要存储权限；页面打开时请求一次，拒绝也不阻塞入库
    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(settings.gallerySaveEnabled) {
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            settings.gallerySaveEnabled &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            galleryPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    // 进入页面后自动识别暂存的照片；仅触发一次，避免重组时重复识别
    LaunchedEffect(Unit) {
        viewModel.pendingBitmap?.let { bitmap ->
            if (viewModel.confirmState.value.candidates.isEmpty()) {
                viewModel.recognize(bitmap, context)
            }
        }
    }

    // 每个类型的可编辑候选状态，用户可修正 OCR 结果
    var workStart by remember { mutableStateOf<LocalTime?>(null) }
    var workEnd by remember { mutableStateOf<LocalTime?>(null) }
    var otStart by remember { mutableStateOf<LocalTime?>(null) }
    var otEnd by remember { mutableStateOf<LocalTime?>(null) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var assigningCandidate by remember { mutableStateOf<OcrCandidate?>(null) }

    fun setSlot(type: PunchType, time: LocalTime?) {
        when (type) {
            PunchType.WORK_START -> workStart = time
            PunchType.WORK_END -> workEnd = time
            PunchType.OT_START -> otStart = time
            PunchType.OT_END -> otEnd = time
        }
    }

    // 识别完成后，按设置决定是否自动填充；日期始终取首个识别日期供用户核对
    LaunchedEffect(state.candidates, settings.ocrAutoFill) {
        if (state.candidates.isNotEmpty()) {
            if (settings.ocrAutoFill) {
                workStart = state.candidates.firstOrNull { it.type == PunchType.WORK_START }?.time ?: workStart
                workEnd = state.candidates.firstOrNull { it.type == PunchType.WORK_END }?.time ?: workEnd
                otStart = state.candidates.firstOrNull { it.type == PunchType.OT_START }?.time ?: otStart
                otEnd = state.candidates.firstOrNull { it.type == PunchType.OT_END }?.time ?: otEnd
            }
            // 屏幕上的 MM-DD 已补全年份，取首个识别日期作为记录归属日
            state.candidates.firstNotNullOfOrNull { it.date }?.let { date = it }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("识别结果确认", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "请核对识别结果，修正后保存。OCR 结果不会自动入库。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        // 原图缩略图：相对路径经 EvidenceStore 解析，缺失时给出占位
        state.imagePath?.let { path ->
            val evidence = EvidenceStore.resolve(context, path)
            Card(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                if (evidence != null) {
                    Image(
                        painter = rememberAsyncImagePainter(evidence),
                        contentDescription = "考勤机照片",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "凭证图片已丢失",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (state.isRecognizing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        state.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
            Button(onClick = viewModel::clearError) { Text("重试") }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("日期", modifier = Modifier.weight(1f))
                    Text(
                        com.worktime.pro.util.TimeFormatter.formatDate(date),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "  修改",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TimeField(
                    "上班", workStart, use24Hour,
                    state.candidates.firstOrNull { it.type == PunchType.WORK_START }?.confidence
                ) { workStart = it }
                TimeField(
                    "下班", workEnd, use24Hour,
                    state.candidates.firstOrNull { it.type == PunchType.WORK_END }?.confidence
                ) { workEnd = it }
                TimeField(
                    "加班到", otStart, use24Hour,
                    state.candidates.firstOrNull { it.type == PunchType.OT_START }?.confidence
                ) { otStart = it }
                TimeField(
                    "加班退", otEnd, use24Hour,
                    state.candidates.firstOrNull { it.type == PunchType.OT_END }?.confidence
                ) { otEnd = it }
            }
        }

        if (!settings.ocrAutoFill) {
            Spacer(Modifier.height(8.dp))
            Text(
                "自动填充已关闭：可在下方识别结果点「填入」，或直接点时间手动设置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.candidates.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("识别结果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.candidates.forEach { candidate ->
                        CandidateRow(
                            candidate = candidate,
                            use24Hour = use24Hour,
                            onFill = {
                                val type = candidate.type
                                if (type != null) {
                                    candidate.time?.let { setSlot(type, it) }
                                    candidate.date?.let { date = it }
                                } else {
                                    assigningCandidate = candidate
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) {
                Text("重新拍摄")
            }
            Button(
                onClick = {
                    val candidates = buildList {
                        workStart?.let { add(OcrCandidate(PunchType.WORK_START, date, it, Confidence.HIGH, "用户确认")) }
                        workEnd?.let { add(OcrCandidate(PunchType.WORK_END, date, it, Confidence.HIGH, "用户确认")) }
                        otStart?.let { add(OcrCandidate(PunchType.OT_START, date, it, Confidence.HIGH, "用户确认")) }
                        otEnd?.let { add(OcrCandidate(PunchType.OT_END, date, it, Confidence.HIGH, "用户确认")) }
                    }
                    viewModel.confirmCandidates(candidates, date, context, onSaved)
                },
                modifier = Modifier.weight(1f),
                enabled = workStart != null || workEnd != null || otStart != null || otEnd != null
            ) {
                Text("确认保存")
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
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

    assigningCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { assigningCandidate = null },
            title = { Text("指定打卡类型") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        candidate.rawText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PunchType.entries.forEach { type ->
                            TextButton(onClick = {
                                candidate.time?.let { setSlot(type, it) }
                                candidate.date?.let { date = it }
                                assigningCandidate = null
                            }) { Text(type.label) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { assigningCandidate = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CandidateRow(
    candidate: OcrCandidate,
    use24Hour: Boolean,
    onFill: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(candidate.type?.label ?: "类型未知", fontWeight = FontWeight.Medium)
            Text(
                text = buildString {
                    append(
                        candidate.time?.let {
                            com.worktime.pro.util.TimeFormatter.formatTime(it, use24Hour)
                        } ?: "无时间"
                    )
                    if (candidate.rawText.isNotBlank()) {
                        append(" · ")
                        append(candidate.rawText.take(28))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        ConfidenceBadge(candidate.confidence)
        TextButton(onClick = onFill, enabled = candidate.time != null) {
            Text(if (candidate.type == null) "指定" else "填入")
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: Confidence) {
    val color = when (confidence) {
        Confidence.HIGH -> MaterialTheme.colorScheme.secondary
        Confidence.MEDIUM -> MaterialTheme.colorScheme.tertiary
        Confidence.LOW -> MaterialTheme.colorScheme.error
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = confidence.label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

/** 时间输入行：点击弹出 Material3 时间选择器，支持用户修正 OCR 结果或补填未识别时间 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(
    label: String,
    time: LocalTime?,
    use24Hour: Boolean,
    confidence: Confidence? = null,
    onTimeChange: (LocalTime?) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        androidx.compose.material3.TextButton(onClick = { showPicker = true }) {
            Text(
                text = time?.let {
                    com.worktime.pro.util.TimeFormatter.formatTime(it, use24Hour)
                } ?: "点击设置时间",
                color = if (time != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (confidence != null) {
            Spacer(Modifier.width(6.dp))
            ConfidenceBadge(confidence)
        }
    }
    if (showPicker) {
        val initialHour = time?.hour ?: 9
        val initialMinute = time?.minute ?: 0
        val pickerState = androidx.compose.material3.rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = use24Hour
        )
        // 当前 material3 版本无 TimePickerDialog，用 AlertDialog 承载 TimePicker
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onTimeChange(LocalTime.of(pickerState.hour, pickerState.minute))
                    showPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onTimeChange(null)
                    showPicker = false
                }) { Text("清除") }
            },
            text = { androidx.compose.material3.TimePicker(state = pickerState) }
        )
    }
}
