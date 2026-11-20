/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/camera/ConfirmScreen.kt
 * @Description: 识别结果确认页，展示原图与四类时间候选，用户核对/修正后入库（OCR 绝不直接写库）
 */
package com.worktime.pro.ui.camera

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.worktime.pro.domain.model.Confidence
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.domain.ocr.OcrCandidate
import com.worktime.pro.ui.ViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.LocalTime

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ConfirmScreen(
    factory: ViewModelFactory,
    onSaved: () -> Unit,
    onRetake: () -> Unit
) {
    val viewModel: CameraViewModel = viewModel(factory = factory)
    val state by viewModel.confirmState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

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
    var date by remember { mutableStateOf(state.recognizedDate) }

    // 识别完成后，把候选时间灌入可编辑状态供用户核对修正
    LaunchedEffect(state.candidates) {
        if (state.candidates.isNotEmpty()) {
            workStart = state.candidates.firstOrNull { it.type == PunchType.WORK_START }?.time ?: workStart
            workEnd = state.candidates.firstOrNull { it.type == PunchType.WORK_END }?.time ?: workEnd
            otStart = state.candidates.firstOrNull { it.type == PunchType.OT_START }?.time ?: otStart
            otEnd = state.candidates.firstOrNull { it.type == PunchType.OT_END }?.time ?: otEnd
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("识别结果确认", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "请核对识别结果，修正后保存。OCR 结果不会自动入库。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        // 原图缩略图
        state.imagePath?.let { path ->
            Card(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                Image(
                    painter = rememberAsyncImagePainter(path),
                    contentDescription = "考勤机照片",
                    modifier = Modifier.fillMaxSize()
                )
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
                Text("日期：$date")
                TimeField("上班", workStart) { workStart = it }
                TimeField("下班", workEnd) { workEnd = it }
                TimeField("加班到", otStart) { otStart = it }
                TimeField("加班退", otEnd) { otEnd = it }
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
                        workStart?.let { add(OcrCandidate(PunchType.WORK_START, it, Confidence.HIGH, "用户确认")) }
                        workEnd?.let { add(OcrCandidate(PunchType.WORK_END, it, Confidence.HIGH, "用户确认")) }
                        otStart?.let { add(OcrCandidate(PunchType.OT_START, it, Confidence.HIGH, "用户确认")) }
                        otEnd?.let { add(OcrCandidate(PunchType.OT_END, it, Confidence.HIGH, "用户确认")) }
                    }
                    viewModel.confirmCandidates(candidates, date, onSaved)
                },
                modifier = Modifier.weight(1f),
                enabled = workStart != null || workEnd != null || otStart != null || otEnd != null
            ) {
                Text("确认保存")
            }
        }
    }
}

/** 时间输入行：点击弹出 Material3 时间选择器，支持用户修正 OCR 结果或补填未识别时间 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(label: String, time: LocalTime?, onTimeChange: (LocalTime?) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        androidx.compose.material3.TextButton(onClick = { showPicker = true }) {
            Text(
                text = time?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "点击设置时间",
                color = if (time != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (showPicker) {
        val initialHour = time?.hour ?: 9
        val initialMinute = time?.minute ?: 0
        val pickerState = androidx.compose.material3.rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
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
