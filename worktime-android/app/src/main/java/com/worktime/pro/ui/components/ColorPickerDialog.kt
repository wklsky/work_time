/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/components/ColorPickerDialog.kt
 * @Description: 零依赖 HSV 取色器，饱和度/明度面板 + 色相滑条 + Hex 输入
 */
package com.worktime.pro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerDialog(
    title: String,
    initialColor: Color,
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
    val color = Color.hsv(hue, saturation, value)

    LaunchedEffect(hue, saturation, value) {
        hex = color.toArgb().toHex()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color)
                )

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
                            .border(2.dp, Color.White, CircleShape)
                    )
                }

                Text("色相", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)

                OutlinedTextField(
                    value = hex,
                    onValueChange = { text ->
                        hex = text
                        val raw = text.trim().removePrefix("#")
                        if (raw.length == 6) {
                            raw.toLongOrNull(16)?.let { parsed ->
                                val arr = FloatArray(3)
                                android.graphics.Color.colorToHSV(
                                    (parsed.toInt() or 0xFF000000.toInt()),
                                    arr
                                )
                                hue = arr[0]
                                saturation = arr[1]
                                value = arr[2]
                            }
                        }
                    },
                    label = { Text("Hex") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
