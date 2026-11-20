/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/components/ColorPickerDialog.kt
 * @Description: 零依赖 HSV 取色器，含渐变色调条、Hex 输入、常用色、对比度提示与单色恢复
 */
package com.worktime.pro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worktime.pro.util.ColorContrast
import com.worktime.pro.util.ContrastLevel
import java.util.Locale

private val HUE_COLORS = listOf(
    Color(0xFFFF0000),
    Color(0xFFFFFF00),
    Color(0xFF00FF00),
    Color(0xFF00FFFF),
    Color(0xFF0000FF),
    Color(0xFFFF00FF),
    Color(0xFFFF0000)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    title: String,
    initialColor: Color,
    defaultColor: Color? = null,
    contrastAgainst: Color? = null,
    contrastLabel: String = "与背景对比度",
    swatches: List<Color> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit
) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var hex by remember { mutableStateOf(initialColor.toArgb().toHex()) }
    var hexFocused by remember { mutableStateOf(false) }
    val color = Color.hsv(hue, saturation, value)

    fun applyColor(target: Color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(target.toArgb(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        hex = target.toArgb().toHex()
    }

    // 只在不编辑 Hex 时同步文本，避免输入被滑条覆盖
    LaunchedEffect(hue, saturation, value, hexFocused) {
        if (!hexFocused) {
            hex = color.toArgb().toHex()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(color)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(12.dp)
                            )
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(hex, fontWeight = FontWeight.Bold)
                        if (contrastAgainst != null) {
                            val ratio = ColorContrast.ratio(color, contrastAgainst)
                            val level = ColorContrast.level(ratio)
                            Text(
                                "$contrastLabel ${"%.1f".format(Locale.US, ratio)}:1",
                                style = MaterialTheme.typography.bodySmall,
                                color = when (level) {
                                    ContrastLevel.GOOD -> MaterialTheme.colorScheme.secondary
                                    ContrastLevel.WARN -> MaterialTheme.colorScheme.tertiary
                                    ContrastLevel.POOR -> MaterialTheme.colorScheme.error
                                }
                            )
                        } else {
                            Text(
                                "拖动面板或输入 Hex 调整颜色",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            fun update(x: Float, y: Float) {
                                saturation = (x / size.width).coerceIn(0f, 1f)
                                value = 1f - (y / size.height).coerceIn(0f, 1f)
                            }
                            detectDragGestures(
                                onDragStart = { offset -> update(offset.x, offset.y) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    update(change.position.x, change.position.y)
                                }
                            )
                        }
                ) {
                    Box(Modifier.fillMaxSize().background(Color.hsv(hue, 1f, 1f)))
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(listOf(Color.White, Color.Transparent))
                        )
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                        )
                    )
                    Box(
                        modifier = Modifier.offset(
                            x = maxWidth * saturation - 7.dp,
                            y = maxHeight * (1f - value) - 7.dp
                        )
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(2.dp, Color.White, CircleShape)
                            .border(1.dp, Color.Black.copy(alpha = 0.35f), CircleShape)
                    )
                }

                Text(
                    "色相",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Brush.horizontalGradient(HUE_COLORS))
                    )
                    Slider(
                        value = hue,
                        onValueChange = { hue = it },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent,
                            thumbColor = color,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        )
                    )
                }

                OutlinedTextField(
                    value = hex,
                    onValueChange = { text ->
                        hex = text
                        val raw = text.trim().removePrefix("#")
                        if (raw.length == 6) {
                            raw.toLongOrNull(16)?.let { parsed ->
                                applyColor(Color(parsed.toInt() or 0xFF000000.toInt()))
                            }
                        }
                    },
                    label = { Text("Hex") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { hexFocused = it.isFocused }
                )

                if (swatches.isNotEmpty()) {
                    Text(
                        "常用颜色",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        swatches.distinct().take(14).forEach { swatch ->
                            val selected = swatch.toArgb() == color.toArgb()
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                        shape = CircleShape
                                    )
                                    .clickable { applyColor(swatch) }
                            )
                        }
                    }
                }

                if (defaultColor != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { applyColor(defaultColor) }) {
                            Text("恢复默认")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(Color.hsv(hue, saturation, value)) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun Int.toHex(): String = String.format(Locale.US, "#%06X", this and 0xFFFFFF)
