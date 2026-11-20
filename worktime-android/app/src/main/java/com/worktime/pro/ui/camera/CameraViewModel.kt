/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/camera/CameraViewModel.kt
 * @Description: 拍照识别状态管理，协调 ML Kit 识别、字段抽取与候选结果确认入库
 */
package com.worktime.pro.ui.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.worktime.pro.data.datastore.SettingsDataStore
import com.worktime.pro.domain.model.Confidence
import com.worktime.pro.domain.ocr.OcrCandidate
import com.worktime.pro.domain.ocr.OcrFieldExtractor
import com.worktime.pro.domain.repository.PunchInput
import com.worktime.pro.domain.repository.WorkTimeRepository
import com.worktime.pro.util.Constants
import com.worktime.pro.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class ConfirmState(
    val candidates: List<OcrCandidate> = emptyList(),
    val imagePath: String? = null,
    val recognizedDate: LocalDate = LocalDate.now(),
    val isRecognizing: Boolean = false,
    val error: String? = null
)

class CameraViewModel(
    private val repository: WorkTimeRepository,
    private val settings: SettingsDataStore
) : ViewModel() {
    private val extractor = OcrFieldExtractor()
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    private val _confirmState = MutableStateFlow(ConfirmState())
    val confirmState: StateFlow<ConfirmState> = _confirmState.asStateFlow()

    /** 拍照页捕获的 bitmap 暂存在共享 ViewModel，确认页据此触发识别 */
    var pendingBitmap: Bitmap? = null
        private set

    fun setPendingBitmap(bitmap: Bitmap) {
        pendingBitmap = bitmap
    }

    /**
     * 识别拍照得到的 Bitmap。
     * 业务要求：全程离线（bundled 模型），识别失败必须给出错误态，不静默回退。
     */
    fun recognize(bitmap: Bitmap, context: Context) {
        viewModelScope.launch {
            _confirmState.value = _confirmState.value.copy(isRecognizing = true, error = null)
            try {
                // 读取用户自定义状态映射，空串时回退到默认编码
                val mappingRaw = settings.settings.first().statusMapping
                val mapping = if (mappingRaw.isBlank()) {
                    com.worktime.pro.domain.ocr.StatusMapping.DEFAULT
                } else {
                    com.worktime.pro.domain.ocr.StatusMapping.deserialize(mappingRaw)
                }
                val candidates = withContext(Dispatchers.Default) {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val visionText = recognizer.process(image).await()
                    extractor.extract(visionText, statusMapping = mapping)
                }
                val path = saveImage(bitmap, context)
                _confirmState.value = _confirmState.value.copy(
                    candidates = candidates,
                    imagePath = path,
                    isRecognizing = false
                )
            } catch (e: Exception) {
                // 识别异常必须暴露给用户，避免误以为识别成功而写入错误数据
                _confirmState.value = _confirmState.value.copy(
                    isRecognizing = false,
                    error = "识别失败：${e.message ?: "未知错误"}"
                )
            }
        }
    }

    fun clearError() {
        _confirmState.value = _confirmState.value.copy(error = null)
    }

    /** 用户确认后，将候选写入打卡事件并触发重算 */
    fun confirmCandidates(candidates: List<OcrCandidate>, date: LocalDate, onDone: () -> Unit) {
        viewModelScope.launch {
            val state = _confirmState.value
            for (candidate in candidates) {
                val time = candidate.time ?: continue
                // 优先用屏幕识别出的日期（MM-DD 补全年份），确认页修改后的日期作为回退
                val effectiveDate = candidate.date ?: date
                val workDate = TimeFormatter.formatDateKey(effectiveDate)
                repository.addPunch(
                    PunchInput(
                        workDate = workDate,
                        punchType = candidate.type ?: continue,
                        punchTime = LocalDateTime.of(effectiveDate, time),
                        source = com.worktime.pro.domain.model.PunchSource.OCR,
                        imagePath = state.imagePath,
                        ocrRawText = candidate.rawText,
                        confidence = candidate.confidence
                    )
                )
            }
            _confirmState.value = ConfirmState()
            onDone()
        }
    }

    /** 保存凭证图到私有目录，压缩以控制存储占用 */
    private fun saveImage(bitmap: Bitmap, context: Context): String {
        val dir = File(context.filesDir, Constants.EVIDENCE_DIR).apply { mkdirs() }
        val file = File(dir, "evidence_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, EVIDENCE_QUALITY, out)
        }
        return file.absolutePath
    }

    companion object {
        private const val EVIDENCE_QUALITY = 70
    }
}
