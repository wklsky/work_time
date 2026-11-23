/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/components/PunchStamp.kt
 * @Description: 打卡盖章确认：打卡成功后短暂浮现一枚朱红印章，模拟在台账上「盖章留痕」。
 *               印章放大压入、稍作停留后淡出，是「工时台账」的签名瞬间。
 */
package com.worktime.pro.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.worktime.pro.ui.theme.StampVermillion
import kotlinx.coroutines.delay

/**
 * @param typeLabel 打卡类型，如「上班」「加班到」
 * @param timeLabel 记录时刻，如「上午 8:05」
 */
@Composable
fun PunchStamp(
    typeLabel: String,
    timeLabel: String,
    onDismiss: () -> Unit
) {
    val scale = remember { Animatable(1.6f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(durationMillis = 140))
        scale.animateTo(1f, tween(durationMillis = 240, easing = FastOutSlowInEasing))
        delay(950)
        alpha.animateTo(0f, tween(durationMillis = 200))
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        rotationZ = -6f
                        this.alpha = alpha.value
                    }
                    .border(3.dp, StampVermillion, CircleShape)
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "工时打卡",
                    style = MaterialTheme.typography.labelSmall,
                    color = StampVermillion,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = StampVermillion
                )
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = StampVermillion
                )
            }
        }
    }
}
