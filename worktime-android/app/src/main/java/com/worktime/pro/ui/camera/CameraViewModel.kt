/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/camera/CameraViewModel.kt
 * @Description: 拍照识别状态管理，协调 ML Kit 识别、字段抽取、凭证保存与相册发布
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
import com.worktime.pro.domain.evidence.EvidenceStore
import com.worktime.pro.domain.evidence.GalleryPublisher
import com.worktime.pro.domain.model.PunchSource
import com.worktime.pro.domain.ocr.OcrCandidate
import com.worktime.pro.domain.ocr.OcrFieldExtractor
import com.worktime.pro.domain.repository.PunchInput
import com.worktime.pro.domain.repository.WorkTimeRepository
import com.worktime.pro.util.TimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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
                val scaled = withContext(Dispatchers.Default) { downscale(bitmap) }
                val candidates = withContext(Dispatchers.Default) {
                    val image = InputImage.fromBitmap(scaled, 0)
                    val visionText = recognizer.process(image).await()
                    extractor.extract(visionText, statusMapping = mapping)
                }
                val path = withContext(Dispatchers.IO) {
                    EvidenceStore.save(context, scaled, EVIDENCE_QUALITY)
                }
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

    /**
     * 用户确认后批量写入打卡，并只重算涉及日期一次。
     * 相册发布失败不影响入库；相册开关关闭时跳过。
     */
    fun confirmCandidates(
        candidates: List<OcrCandidate>,
        date: LocalDate,
        context: Context,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val state = _confirmState.value
            val inputs = candidates.mapNotNull { candidate ->
                val time = candidate.time ?: return@mapNotNull null
                val type = candidate.type ?: return@mapNotNull null
                // 优先用屏幕识别出的日期（MM-DD 补全年份），确认页修改后的日期作为回退
                val effectiveDate = candidate.date ?: date
                PunchInput(
                    workDate = TimeFormatter.formatDateKey(effectiveDate),
                    punchType = type,
                    punchTime = LocalDateTime.of(effectiveDate, time),
                    source = PunchSource.OCR,
                    imagePath = state.imagePath,
                    ocrRawText = candidate.rawText,
                    confidence = candidate.confidence
                )
            }
            inputs.groupBy { it.workDate }.values.forEach { repository.addPunches(it) }

            // 确认保存后才写相册，避免用户重拍时产生垃圾照片
            val galleryEnabled = runCatching {
                settings.settings.first().gallerySaveEnabled
            }.getOrDefault(false)
            if (galleryEnabled) {
                EvidenceStore.resolve(context, state.imagePath)?.let { file ->
                    val displayName = "工时助手_${date.format(NAME_DATE)}_${System.currentTimeMillis()}.jpg"
                    runCatching { GalleryPublisher.publish(context, file, displayName) }
                }
            }

            pendingBitmap = null
            _confirmState.value = ConfirmState()
            onDone()
        }
    }

    /** 长边压缩到 1600px，降低 ML Kit 内存与耗时，同时控制凭证体积 */
    private fun downscale(source: Bitmap, maxSide: Int = 1600): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxSide) return source
        val ratio = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    override fun onCleared() {
        super.onCleared()
        recognizer.close()
        pendingBitmap = null
    }

    companion object {
        private const val EVIDENCE_QUALITY = 70
        private val NAME_DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US)
    }
}
