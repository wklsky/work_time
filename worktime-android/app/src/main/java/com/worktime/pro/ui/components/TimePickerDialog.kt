/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/components/TimePickerDialog.kt
 * @Description: 通用时间选择弹窗「校时」：24 小时制双圈表盘（外 1–12 / 内 13–24）+ 可拖动时针/分针
 *               + 大号等宽读数，选完小时自动进入分钟盘；统一今日页/手动录入/习惯打卡/工作规则的选时体验。
 */
package com.worktime.pro.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.worktime.pro.data.datastore.AppSettings
import com.worktime.pro.data.datastore.ClockDialStyle
import com.worktime.pro.ui.LocalSettings
import com.worktime.pro.util.TimeFormatter
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val HOURS_IN_DAY = 24
private const val DEGREES_PER_HOUR = 360f / HOURS_IN_DAY
private const val DEGREES_PER_MINUTE = 360f / 60f

/** 一圈 12 个落点（24 小时制内外圈共用），每格 30° */
private const val DEGREES_PER_DIAL_HOUR = 360f / 12f

/** 内外圈数字半径（相对表盘半径），以及区分内外圈的触点分界 */
private const val OUTER_LABEL_RATIO = 0.86f
private const val INNER_LABEL_RATIO = 0.60f
private const val RING_SPLIT_RATIO = 0.73f

/** 指针长度：时针短、分针长，与真实钟表一致 */
private const val HOUR_HAND_RATIO = 0.56f
private const val MINUTE_HAND_RATIO = 0.90f

/** 24 小时制双圈：时针长度随圈层变化，指向外圈（1–12）时更长、内圈（13–24）时更短 */
private const val OUTER_HOUR_HAND_RATIO = 0.76f
private const val INNER_HOUR_HAND_RATIO = 0.52f

/** 表盘最大边长：再大就会挤压读数与确认按钮 */
private val CLOCK_DIAL_MAX_SIZE = 284.dp

/** 当前正在调节的指针 */
private enum class ClockHand { HOUR, MINUTE }

/**
 * 时间选择弹窗。
 *
 * @param title 业务标题，如「打卡时刻」「上班时间」「日切时间」
 * @param subtitle 读数下方的上下文说明；为空时使用操作提示
 */
@Composable
fun TimePickerDialog(
    title: String,
    initialTime: LocalTime,
    use24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
    subtitle: String? = null
) {
    var hour by remember { mutableIntStateOf(initialTime.hour) }
    var minute by remember { mutableIntStateOf(initialTime.minute) }
    var activeHand by remember { mutableStateOf(ClockHand.HOUR) }
    // 抬手后延迟切盘：切换会重建 pointerInput，若立刻切，本次点击的抬起事件会被分钟盘当作新的按下
    var hourPickedTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(hourPickedTick) {
        if (hourPickedTick > 0) {
            delay(160)
            activeHand = ClockHand.MINUTE
        }
    }

    // 表盘数字样式：只影响表盘刻度，读数与按钮文案保持阿拉伯数字
    val settings by LocalSettings.current.settings.collectAsState(initial = AppSettings())
    val dialStyle = remember(settings.clockDialStyle) {
        ClockDialStyle.fromValue(settings.clockDialStyle)
    }

    val selectedTime = remember(hour, minute) { LocalTime.of(hour, minute) }
    val selectedLabel = remember(selectedTime, use24Hour) {
        TimeFormatter.formatTime(selectedTime, use24Hour)
    }

    // 弹层入场：轻微上浮 + 放大 + 淡入，一次性编排，不做循环
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(durationMillis = 280, easing = FastOutSlowInEasing))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // 弹层最高占屏幕 94%，内容超出时在弹层内滚动，避免顶部标题被裁掉
            val sheetMaxHeight = maxHeight * 0.94f
            // 表盘随屏幕高度收缩，保证确认按钮尽量留在首屏
            // 在小屏或横屏时进一步减小表盘，为按钮预留空间
            val availableHeight = maxHeight * 0.94f - 200.dp // 预留标题、读数、按钮等固定高度
            val dialSize = minOf(
                CLOCK_DIAL_MAX_SIZE,
                maxWidth - 40.dp,
                availableHeight.coerceAtLeast(160.dp) // 最小 160dp
            )

            Surface(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = sheetMaxHeight)
                    .graphicsLayer {
                        val progress = appear.value
                        alpha = progress
                        val scale = 0.96f + 0.04f * progress
                        scaleX = scale
                        scaleY = scale
                        translationY = (1f - progress) * 28.dp.toPx()
                    },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 18.dp, bottom = 20.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "校时",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 3.sp
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "关闭",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TimeReadout(
                            hour = hour,
                            minute = minute,
                            use24Hour = use24Hour,
                            activeHand = activeHand,
                            onHandChange = { activeHand = it }
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            // 表盘会随时/分自动切换，提示语跟着当前盘面走，减少用户试错
                            subtitle ?: if (activeHand == ClockHand.HOUR) {
                                if (use24Hour) {
                                    "外圈 1–12 点、内圈 13–24 点，松手后自动切换到分钟"
                                } else {
                                    "拖动指针选小时，松手后自动切换到分钟"
                                }
                            } else {
                                "拖动或点按分钟盘选择分钟"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        ClockHandToggle(
                            activeHand = activeHand,
                            onHandChange = { activeHand = it }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        AnalogClock(
                            hour = hour,
                            minute = minute,
                            use24Hour = use24Hour,
                            activeHand = activeHand,
                            dialStyle = dialStyle,
                            selectedLabel = selectedLabel,
                            onHourChange = { hour = it },
                            onMinuteChange = { minute = it },
                            // 小时选定后自动进入分钟盘，与系统闹钟的「先时后分」一致
                            onHourPicked = { hourPickedTick++ },
                            modifier = Modifier.size(dialSize)
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = { onConfirm(selectedTime) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            "使用 $selectedLabel",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 大号读数：时/分两段可点按切换当前指针，当前调节段用主题色高亮。
 * 12 小时制下在小时前显示上午/下午，拖动跨过 12 点会实时切换。
 */
@Composable
private fun TimeReadout(
    hour: Int,
    minute: Int,
    use24Hour: Boolean,
    activeHand: ClockHand,
    onHandChange: (ClockHand) -> Unit
) {
    val hourText = if (use24Hour) {
        String.format(Locale.US, "%02d", hour)
    } else {
        ((hour + 11) % 12 + 1).toString()
    }
    val minuteText = String.format(Locale.US, "%02d", minute)

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!use24Hour) {
            Text(
                text = if (hour < 12) "上午" else "下午",
                modifier = Modifier.padding(end = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ReadoutSegment(
            text = hourText,
            selected = activeHand == ClockHand.HOUR,
            contentDescription = "小时 $hourText，点击切换调节小时"
        ) { onHandChange(ClockHand.HOUR) }
        Text(
            text = ":",
            style = MaterialTheme.typography.displayMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ReadoutSegment(
            text = minuteText,
            selected = activeHand == ClockHand.MINUTE,
            contentDescription = "分钟 $minuteText，点击切换调节分钟"
        ) { onHandChange(ClockHand.MINUTE) }
    }
}

@Composable
private fun ReadoutSegment(
    text: String,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    // 切换「时/分」时底色与字色淡入淡出，不再硬切
    val animatedColor by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.onSurface,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "readoutColor"
    )
    val animatedBackground by animateColorAsState(
        targetValue = if (selected) scheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "readoutBackground"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(animatedBackground)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        // 读数像机械计数器一样换字：旧数字上滑淡出，新数字上滑淡入
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                (
                    slideInVertically(animationSpec = tween(durationMillis = 200)) { h -> h / 2 } +
                        fadeIn(animationSpec = tween(durationMillis = 200))
                    ) togetherWith (
                    slideOutVertically(animationSpec = tween(durationMillis = 140)) { h -> -h / 2 } +
                        fadeOut(animationSpec = tween(durationMillis = 140))
                    ) using SizeTransform(clip = false)
            },
            label = "readoutText"
        ) { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.displayMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-1).sp,
                color = animatedColor
            )
        }
    }
}

/** 「小时 / 分钟」分段开关：明确告诉用户当前拖动的指针 */
@Composable
private fun ClockHandToggle(
    activeHand: ClockHand,
    onHandChange: (ClockHand) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp)
    ) {
        HandToggleItem("小时", activeHand == ClockHand.HOUR) { onHandChange(ClockHand.HOUR) }
        HandToggleItem("分钟", activeHand == ClockHand.MINUTE) { onHandChange(ClockHand.MINUTE) }
    }
}

@Composable
private fun HandToggleItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

/**
 * 24 小时圆形表盘。
 * 时针每 24 小时一圈、分针每 60 分钟一圈；按住表盘任意位置即把当前指针拖到手指角度，
 * 无需先精确抓到细线，指针会实时吸附到整点/整分。
 */
@Composable
private fun AnalogClock(
    hour: Int,
    minute: Int,
    use24Hour: Boolean,
    activeHand: ClockHand,
    dialStyle: ClockDialStyle,
    selectedLabel: String,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onHourPicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val dialColor = scheme.surfaceContainerHigh.copy(alpha = 0.55f)
    val activeColor = scheme.primary
    val inactiveColor = scheme.onSurfaceVariant.copy(alpha = 0.30f)
    val tickColor = scheme.outline.copy(alpha = 0.45f)
    val strongTickColor = scheme.outline.copy(alpha = 0.75f)

    // 切盘时数字层「换焦」一次：旧盘淡出、新盘落定；首帧则是从无到有的淡入
    val digitsAlpha = remember { Animatable(0f) }
    var digitsFirstFrame by remember { mutableStateOf(true) }
    LaunchedEffect(activeHand) {
        if (digitsFirstFrame) {
            digitsFirstFrame = false
            digitsAlpha.animateTo(1f, tween(durationMillis = 360, easing = FastOutSlowInEasing))
        } else {
            digitsAlpha.animateTo(0.18f, tween(durationMillis = 90, easing = FastOutSlowInEasing))
            digitsAlpha.animateTo(1f, tween(durationMillis = 220, easing = FastOutSlowInEasing))
        }
    }
    // 活动指针末端的呼吸光点，提示这里可以拖
    val pulse = rememberInfiniteTransition(label = "handPulse")
    val haloScale by pulse.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloScale"
    )
    val haloAlpha by pulse.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloAlpha"
    )
    // 圆弧平滑跟随指针，避免点按瞬间跳到新角度
    val sweepTarget = if (activeHand == ClockHand.HOUR) {
        if (use24Hour) (hour % 12) * DEGREES_PER_DIAL_HOUR else hour * DEGREES_PER_HOUR
    } else {
        minute * DEGREES_PER_MINUTE
    }
    val smoothSweep by animateFloatAsState(
        targetValue = sweepTarget,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "smoothSweep"
    )

    // 表盘数字不随拖动变化，预先排版，避免每帧重新测量
    // 24 小时制：外圈 1–12、内圈 13–24；12 小时制沿用一天一圈的 24 点标注
    val outerHourLayouts = remember(textMeasurer, scheme.onSurfaceVariant, dialStyle) {
        (1..12).map { h ->
            measureDialLabel(textMeasurer, dialStyle.hourText(h), scheme.onSurfaceVariant)
        }
    }
    val innerHourLayouts = remember(textMeasurer, scheme.onSurfaceVariant, dialStyle) {
        (13..24).map { h ->
            measureDialLabel(textMeasurer, dialStyle.hourText(h), scheme.onSurfaceVariant)
        }
    }
    val dayHourLayouts = remember(textMeasurer, scheme.onSurfaceVariant, dialStyle) {
        (0 until HOURS_IN_DAY step 2).map { h ->
            measureDialLabel(
                textMeasurer,
                dialStyle.hourText(h, pad = true),
                scheme.onSurfaceVariant
            )
        }
    }
    // 分钟盘以 5 分钟为刻度：00、05 …… 55 共 12 个数字；极简样式下不绘制分钟数字
    val minuteLayouts = remember(textMeasurer, scheme.onSurfaceVariant, dialStyle) {
        if (dialStyle == ClockDialStyle.MINIMAL) {
            emptyList()
        } else {
            (0 until 60 step 5).map { m ->
                measureDialLabel(
                    textMeasurer,
                    String.format(Locale.US, "%02d", m),
                    scheme.onSurfaceVariant
                )
            }
        }
    }

    Canvas(
        modifier = modifier
            .pointerInput(activeHand, use24Hour) {
                // 触点离圆心太近时角度不稳定，忽略中心区域，避免误跳到 00:00
                val deadZone = 12.dp.toPx()
                // 24 小时制用半径区分内外圈：内圈 13–24，外圈 1–12
                val ringSplit = minOf(size.width, size.height) / 2f * RING_SPLIT_RATIO
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    var hourTouched = false

                    fun applyPosition(position: Offset) {
                        val dx = position.x - center.x
                        val dy = position.y - center.y
                        val distanceSquared = dx * dx + dy * dy
                        if (distanceSquared < deadZone * deadZone) return
                        val angle = angleFromTop(position, size)
                        if (activeHand == ClockHand.HOUR) {
                            val slot = (angle / DEGREES_PER_DIAL_HOUR).roundToInt() % 12
                            if (use24Hour) {
                                onHourChange(
                                    if (distanceSquared < ringSplit * ringSplit) {
                                        // 内圈 13–24：slot 0 是正上方的 24 点，落库为 0 时
                                        if (slot == 0) 0 else slot + 12
                                    } else {
                                        // 外圈 1–12：slot 0 是正上方的 12 点
                                        if (slot == 0) 12 else slot
                                    }
                                )
                            } else {
                                // 12 小时制保持一天一圈：角度直接映射 0–23，跨圈即切换上午/下午
                                onHourChange(
                                    (angle / DEGREES_PER_HOUR).roundToInt() % HOURS_IN_DAY
                                )
                            }
                            hourTouched = true
                        } else {
                            onMinuteChange((angle / DEGREES_PER_MINUTE).roundToInt() % 60)
                        }
                    }

                    applyPosition(down.position)
                    down.consume()

                    var pointer = down
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (change.position != pointer.position) {
                            applyPosition(change.position)
                            change.consume()
                        }
                        pointer = change
                    }

                    // 小时选定即自动切到分钟盘，省掉手动再点一次「分钟」
                    if (hourTouched) onHourPicked()
                }
            }
            .clearAndSetSemantics {
                contentDescription = "24 小时圆形表盘，当前选择 $selectedLabel，拖动指针校准"
                customActions = listOf(
                    CustomAccessibilityAction("小时加一") {
                        onHourChange((hour + 1) % HOURS_IN_DAY)
                        true
                    },
                    CustomAccessibilityAction("小时减一") {
                        onHourChange((hour + HOURS_IN_DAY - 1) % HOURS_IN_DAY)
                        true
                    },
                    CustomAccessibilityAction("分钟加一") {
                        onMinuteChange((minute + 1) % 60)
                        true
                    },
                    CustomAccessibilityAction("分钟减一") {
                        onMinuteChange((minute + 59) % 60)
                        true
                    }
                )
            }
    ) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val dialRadius = radius - 1.dp.toPx()

        fun pointAt(angleDegrees: Float, distance: Float): Offset {
            val radians = Math.toRadians((angleDegrees - 90f).toDouble())
            return Offset(
                x = center.x + cos(radians).toFloat() * distance,
                y = center.y + sin(radians).toFloat() * distance
            )
        }

        // 表盘底与细外圈
        drawCircle(color = dialColor, radius = dialRadius, center = center)
        drawCircle(
            color = tickColor,
            radius = dialRadius,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )

        // 刻度：小时盘按整点、分钟盘按 5 分钟，两者都是每 30° 一格；3 的倍数加粗
        val tickCount = if (activeHand == ClockHand.HOUR && !use24Hour) HOURS_IN_DAY else 12
        val tickStepDegrees = if (activeHand == ClockHand.HOUR && !use24Hour) {
            DEGREES_PER_HOUR
        } else {
            DEGREES_PER_MINUTE * 5f
        }
        for (i in 0 until tickCount) {
            val angle = i * tickStepDegrees
            val major = i % 3 == 0
            val outer = dialRadius - 5.dp.toPx()
            val inner = outer - (if (major) 11.dp else 6.dp).toPx()
            drawLine(
                color = if (major) strongTickColor else tickColor,
                start = pointAt(angle, inner),
                end = pointAt(angle, outer),
                strokeWidth = if (major) 1.6.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // 数字：24 小时制小时盘为双圈（外 1–12 / 内 13–24），分钟盘每 5 分钟一个
        val outerLabelRadius = dialRadius * OUTER_LABEL_RATIO
        val innerLabelRadius = dialRadius * INNER_LABEL_RATIO

        fun drawLabel(
            layout: TextLayoutResult,
            angle: Float,
            radius: Float,
            alpha: Float = digitsAlpha.value
        ) {
            val position = pointAt(angle, radius)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x = position.x - layout.size.width / 2f,
                    y = position.y - layout.size.height / 2f
                ),
                alpha = alpha
            )
        }

        /** 当前选中的数字用主题色重绘，明确「指针停在哪一格」；空串表示不绘制（极简样式） */
        fun drawSelected(text: String, angle: Float, radius: Float, fontSize: TextUnit = 10.sp) {
            if (text.isEmpty()) return
            val layout = measureDialLabel(textMeasurer, text, activeColor, fontSize)
            drawLabel(layout, angle, radius)
        }

        when {
            activeHand == ClockHand.MINUTE -> {
                // 5 分钟一格：00、05 …… 55，每格 30°
                minuteLayouts.forEachIndexed { index, layout ->
                    drawLabel(layout, index * DEGREES_PER_MINUTE * 5f, outerLabelRadius)
                }
                // 高亮指针所在的那一格（最近的 5 分钟刻度）；分钟数字始终用阿拉伯数字
                val stickMinute = minute / 5 * 5
                val stickText = if (dialStyle == ClockDialStyle.MINIMAL) {
                    ""
                } else {
                    String.format(Locale.US, "%02d", stickMinute)
                }
                drawSelected(stickText, stickMinute * DEGREES_PER_MINUTE, outerLabelRadius)
            }

            use24Hour -> {
                // 外圈 1–12：1 点在 30°、12 点在正上方 0°
                outerHourLayouts.forEachIndexed { index, layout ->
                    drawLabel(layout, (index + 1) % 12 * DEGREES_PER_DIAL_HOUR, outerLabelRadius)
                }
                // 内圈 13–24：13 点在 30°、24 点在正上方
                innerHourLayouts.forEachIndexed { index, layout ->
                    drawLabel(layout, (index + 1) % 12 * DEGREES_PER_DIAL_HOUR, innerLabelRadius)
                }
                // 高亮当前时针所在圈层的那一格
                val dialHour = if (hour % 12 == 0) 12 else hour % 12
                val angle = (dialHour % 12) * DEGREES_PER_DIAL_HOUR
                if (hour in 1..12) {
                    drawSelected(dialStyle.hourText(dialHour), angle, outerLabelRadius)
                } else {
                    drawSelected(
                        dialStyle.hourText(if (hour == 0) 24 else hour),
                        angle,
                        innerLabelRadius
                    )
                }
            }

            else -> {
                // 12 小时制沿用一天一圈：每 2 小时一个数字
                dayHourLayouts.forEachIndexed { index, layout ->
                    drawLabel(layout, index * 2 * DEGREES_PER_HOUR, dialRadius - 30.dp.toPx())
                }
            }
        }

        // 时针吸附到整点、分针吸附到整分，保证指针位置与拖拽角度 1:1，不因分钟数提前偏移
        // 24 小时制双圈盘面的时针按 12 格走，否则会和外圈数字错位
        val hourOnDialAngle = if (use24Hour) {
            (hour % 12) * DEGREES_PER_DIAL_HOUR
        } else {
            hour * DEGREES_PER_HOUR
        }
        val minuteAngle = minute * DEGREES_PER_MINUTE

        // 当前指针扫过的淡色圆弧：用 smoothSweep 平滑跟随，点按时不再瞬跳
        drawArc(
            color = activeColor.copy(alpha = 0.16f),
            startAngle = -90f,
            sweepAngle = smoothSweep,
            useCenter = false,
            topLeft = Offset(
                center.x - dialRadius + 2.dp.toPx(),
                center.y - dialRadius + 2.dp.toPx()
            ),
            size = Size(
                dialRadius * 2 - 4.dp.toPx(),
                dialRadius * 2 - 4.dp.toPx()
            ),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        fun drawHand(angle: Float, lengthRatio: Float, width: Dp, color: Color, active: Boolean) {
            val tip = pointAt(angle, dialRadius * lengthRatio)
            drawLine(
                color = color,
                start = center,
                end = tip,
                strokeWidth = width.toPx(),
                cap = StrokeCap.Round
            )
            if (active) {
                // 呼吸光晕：像仪表指示灯，暗示「这根针可以拖」
                drawCircle(
                    color = color.copy(alpha = haloAlpha),
                    radius = width.toPx() * 2.8f * haloScale,
                    center = tip
                )
            }
            // 活动指针末端加一个圆点，提示这里可以拖动
            drawCircle(
                color = color,
                radius = (if (active) width * 0.9f else width / 2f).toPx(),
                center = tip
            )
        }

        // 时针长度随所选圈层变化：外圈 1–12 伸到外圈数字附近，内圈 13–24 只到内圈
        val hourHandRatio = when {
            !use24Hour -> HOUR_HAND_RATIO
            hour in 1..12 -> OUTER_HOUR_HAND_RATIO
            else -> INNER_HOUR_HAND_RATIO
        }

        // 非活动指针先画、活动指针压在上面；时针短、分针长，靠长短即可区分
        when (activeHand) {
            ClockHand.HOUR -> {
                drawHand(minuteAngle, MINUTE_HAND_RATIO, 3.dp, inactiveColor, active = false)
                drawHand(hourOnDialAngle, hourHandRatio, 7.dp, activeColor, active = true)
            }
            ClockHand.MINUTE -> {
                drawHand(hourOnDialAngle, hourHandRatio, 3.dp, inactiveColor, active = false)
                drawHand(minuteAngle, MINUTE_HAND_RATIO, 5.dp, activeColor, active = true)
            }
        }

        // 中心轴
        drawCircle(color = activeColor, radius = 7.dp.toPx(), center = center)
        drawCircle(color = scheme.surface, radius = 3.dp.toPx(), center = center)
    }
}

/** 表盘数字统一排版：等宽、10sp，保证内外圈与分钟盘视觉一致 */
private fun measureDialLabel(
    textMeasurer: TextMeasurer,
    text: String,
    color: Color,
    fontSize: TextUnit = 10.sp
): TextLayoutResult = textMeasurer.measure(
    text = AnnotatedString(text),
    style = TextStyle(
        color = color,
        fontSize = fontSize,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium
    )
)

/** 以正上方为 0°、顺时针递增，把触点换算成表盘角度 */
private fun angleFromTop(position: Offset, size: IntSize): Float {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val degrees = Math.toDegrees(
        atan2((position.y - cy).toDouble(), (position.x - cx).toDouble())
    ).toFloat()
    return (degrees + 90f + 360f) % 360f
}
