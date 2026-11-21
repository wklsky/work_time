/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/camera/ConfirmScreen.kt
 * @Description: 识别结果确认页，按段编辑多段打卡，用户核对/修正后入库（OCR 绝不直接写库）
 */
package com.worktime.pro.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.worktime.pro.domain.evidence.EvidenceStore
import com.worktime.pro.domain.model.Confidence
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.domain.ocr.OcrCandidate
import com.worktime.pro.ui.ViewModelFactory
import com.worktime.pro.ui.components.TimePickerDialog
import com.worktime.pro.util.TimeFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private data class ConfirmEntry(
    val id: Int,
    val type: PunchType?,
    val time: LocalTime?,
    val confidence: Confidence?,
    val rawText: String
)

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
    val use24Hour = TimeFormatter.use24Hour(context, settings.timeFormat)

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
    var hasTriggeredRecognition by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.pendingBitmap?.let { bitmap ->
            if (!hasTriggeredRecognition && viewModel.confirmState.value.candidates.isEmpty()) {
                hasTriggeredRecognition = true
                viewModel.recognize(bitmap, context)
            }
        }
    }

    var date by remember { mutableStateOf(LocalDate.now()) }
    var entries by remember { mutableStateOf<List<ConfirmEntry>>(emptyList()) }
    var nextEntryId by remember { mutableStateOf(1) }
    var editingEntryId by remember { mutableStateOf<Int?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var assigningCandidate by remember { mutableStateOf<OcrCandidate?>(null) }

    fun addEntry(type: PunchType?, time: LocalTime?, confidence: Confidence?, rawText: String) {
        entries = entries + ConfirmEntry(nextEntryId, type, time, confidence, rawText)
        nextEntryId += 1
    }

    fun updateEntry(id: Int, transform: (ConfirmEntry) -> ConfirmEntry) {
        entries = entries.map { if (it.id == id) transform(it) else it }
    }

    fun removeEntry(id: Int) {
        entries = entries.filterNot { it.id == id }
    }

    fun addCandidate(candidate: OcrCandidate) {
        val type = candidate.type
        if (type == null) {
            assigningCandidate = candidate
        } else {
            addEntry(type, candidate.time, candidate.confidence, candidate.rawText)
            candidate.date?.let { date = it }
        }
    }

    // 自动填充开启时，所有候选（含同类型多段）都进入列表；关闭时保留空列表由用户添加
    LaunchedEffect(state.candidates, settings.ocrAutoFill) {
        if (state.candidates.isEmpty()) return@LaunchedEffect
        state.candidates.firstNotNullOfOrNull { it.date }?.let { date = it }
        if (settings.ocrAutoFill) {
            var id = nextEntryId
            entries = state.candidates.map { candidate ->
                ConfirmEntry(
                    id = id++,
                    type = candidate.type,
                    time = candidate.time,
                    confidence = candidate.confidence,
                    rawText = candidate.rawText
                )
            }
            nextEntryId = id
        } else {
            entries = emptyList()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("识别结果确认", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "请核对识别结果；同一天可以有多段，确认后才会入库。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        // 原图缩略图：相对路径经 EvidenceStore 解析，缺失时给出占位
        state.imagePath?.let { path ->
            val evidence = remember(path) { EvidenceStore.resolve(context, path) }
            Card(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                if (evidence != null) {
                    Image(
                        painter = rememberAsyncImagePainter(
                            model = coil.request.ImageRequest.Builder(context)
                                .data(evidence)
                                .crossfade(true)
                                .memoryCacheKey(path)
                                .diskCacheKey(path)
                                .size(coil.size.Size.ORIGINAL) // 使用原始尺寸避免重复缩放
                                .build()
                        ),
                        contentDescription = "考勤机照片",
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
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
            Button(onClick = viewModel::clearError) { Text("关闭") }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("日期", modifier = Modifier.weight(1f))
                    Text(
                        TimeFormatter.formatDate(date),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "  修改",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    "打卡时间（可多段）",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (entries.isEmpty()) {
                    Text(
                        "还没有打卡时间。点击下方「添加一段」，或从识别结果添加。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    entries.forEachIndexed { index, entry ->
                        EntryRow(
                            index = index,
                            entry = entry,
                            use24Hour = use24Hour,
                            onTypeChange = { type ->
                                updateEntry(entry.id) { it.copy(type = type) }
                            },
                            onTimeClick = { editingEntryId = entry.id },
                            onRemove = { removeEntry(entry.id) }
                        )
                        if (index != entries.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        addEntry(PunchType.WORK_START, null, null, "用户添加")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("添加一段", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }

        if (!settings.ocrAutoFill) {
            Spacer(Modifier.height(8.dp))
            Text(
                "自动填充已关闭：请在下方识别结果中点「添加」，或手动设置每一段。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.candidates.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("识别结果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // 识别质量提示
            val lowConfidenceCount = state.candidates.count { it.confidence == Confidence.LOW }
            val mediumConfidenceCount = state.candidates.count { it.confidence == Confidence.MEDIUM }
            if (lowConfidenceCount > 0 || mediumConfidenceCount > 1) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "识别置信度较低，建议重新拍摄或仔细核对",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Text(
                if (settings.ocrAutoFill) {
                    "已自动填入上方列表；可逐段修改或删除。"
                } else {
                    "点击「添加」把识别结果加入上方列表；类型未知的需先指定。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                            showAdd = !settings.ocrAutoFill,
                            onAdd = { addCandidate(candidate) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = {
                viewModel.resetState()
                onRetake()
            }, modifier = Modifier.weight(1f)) {
                Text("重新拍摄")
            }
            Button(
                onClick = {
                    if (entries.isEmpty()) {
                        Toast.makeText(context, "至少添加一段打卡时间", Toast.LENGTH_SHORT).show()
                    } else if (entries.any { it.type == null || it.time == null }) {
                        Toast.makeText(
                            context,
                            "请补全每一段的类型和时间，或删除空行",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val candidates = entries.mapNotNull { entry ->
                            val type = entry.type ?: return@mapNotNull null
                            val time = entry.time ?: return@mapNotNull null
                            OcrCandidate(
                                type = type,
                                date = date,
                                time = time,
                                confidence = entry.confidence ?: Confidence.HIGH,
                                rawText = entry.rawText.ifBlank { "用户确认" }
                            )
                        }
                        viewModel.confirmCandidates(candidates, date, context, onSaved)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = entries.isNotEmpty()
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

    val editingEntry = editingEntryId?.let { id -> entries.firstOrNull { it.id == id } }
    if (editingEntry != null) {
        TimePickerDialog(
            title = "设置打卡时间",
            initialTime = editingEntry.time ?: LocalTime.now(),
            use24Hour = use24Hour,
            onDismiss = { editingEntryId = null },
            onConfirm = { time ->
                updateEntry(editingEntry.id) { it.copy(time = time) }
                editingEntryId = null
            }
        )
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
                                addEntry(type, candidate.time, candidate.confidence, candidate.rawText)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryRow(
    index: Int,
    entry: ConfirmEntry,
    use24Hour: Boolean,
    onTypeChange: (PunchType) -> Unit,
    onTimeClick: () -> Unit,
    onRemove: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "第 ${index + 1} 段",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            entry.confidence?.let { ConfidenceBadge(it) }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "删除这一段")
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PunchType.entries.forEach { type ->
                FilterChip(
                    selected = entry.type == type,
                    onClick = { onTypeChange(type) },
                    label = { Text(type.label) }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onTimeClick) {
                Text(
                    text = entry.time?.let { TimeFormatter.formatTime(it, use24Hour) }
                        ?: "设置时间",
                    color = if (entry.time != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(Modifier.weight(1f))
            if (entry.rawText.isNotBlank()) {
                Text(
                    text = entry.rawText.take(20),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: OcrCandidate,
    use24Hour: Boolean,
    showAdd: Boolean,
    onAdd: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(candidate.type?.label ?: "类型未知", fontWeight = FontWeight.Medium)
            Text(
                text = buildString {
                    append(
                        candidate.time?.let {
                            TimeFormatter.formatTime(it, use24Hour)
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
        if (showAdd) {
            TextButton(onClick = onAdd, enabled = candidate.time != null) {
                Text(if (candidate.type == null) "指定" else "添加")
            }
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
