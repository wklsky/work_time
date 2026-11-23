/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/components/TimePickerDialog.kt
 * @Description: 通用时间选择弹窗「校时」：12 小时制单圈表盘 + 上午/下午切换 + 可拖动时针/分针
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
import androidx.compose.foundation.layout.width
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

private const val DEGREES_PER_MINUTE = 360f / 60f

/** 一圈 12 个落点，每格 30° */
private const val DEGREES_PER_DIAL_HOUR = 360f / 12f

/** 小时数字半径（相对表盘半径） */
private const val OUTER_LABEL_RATIO = 0.86f

/** 指针长度：时针短、分针长，与真实钟表一致 */
private const val HOUR_HAND_RATIO = 0.56f
private const val MINUTE_HAND_RATIO = 0.90f

/** 表盘最大边长：再大就会挤压读数与确认按钮 */
private val CLOCK_DIAL_MAX_SIZE = 284.dp

/**
 * 确认按钮底部安全间距。
 * Dialog 在 edge-to-edge 窗口里 navigationBarsPadding 常拿不到导航栏 insets（退化为 0），
 * 仅靠内容 padding 会让按钮贴住手势条被遮挡或误触，这里用固定间距兜底把按钮整体上移。
 */
private val CONFIRM_BUTTON_BOTTOM_INSET = 56.dp

/**
 * 表盘之外的固定内容高度（标题、读数、提示、时/分开关、按钮、各段间距及底部安全间距）。
 * 业务背景：该估值用于反推表盘可用高度，估值偏低会让表盘撑满弹层、把确认按钮挤进滚动区底部；
 * 按实际固定块约 334dp 取 336dp，保证常见屏高下全部内容首屏可见且按钮不贴屏幕边缘。
 */
private val NON_DIAL_CONTENT_HEIGHT = 336.dp

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
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
    subtitle: String? = null
) {
    // 12 小时制：小时存 1–12，配合上午/下午还原 24 小时
    val initialDialHour = ((initialTime.hour + 11) % 12) + 1
    var dialHour by remember { mutableIntStateOf(initialDialHour) }
    var minute by remember { mutableIntStateOf(initialTime.minute) }
    var isPM by remember { mutableStateOf(initialTime.hour >= 12) }
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

    val selectedTime = remember(dialHour, isPM, minute) {
        val hour = if (isPM) {
            if (dialHour == 12) 12 else dialHour + 12
        } else {
            if (dialHour == 12) 0 else dialHour
        }
        LocalTime.of(hour, minute)
    }
    val selectedLabel = remember(selectedTime) {
        TimeFormatter.formatTime(selectedTime, use24Hour = false)
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
            val availableHeight = maxHeight * 0.94f - NON_DIAL_CONTENT_HEIGHT
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
                // 内容区可滚动、确认按钮固定在弹层底部：表盘撑高内容时按钮不会跟着滚出首屏，
                // 底部安全间距把主操作整体上移，避开手势导航条
                Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                            .padding(top = 18.dp)
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
                                dialHour = dialHour,
                                minute = minute,
                                isPM = isPM,
                                activeHand = activeHand,
                                onHandChange = { activeHand = it },
                                onTogglePeriod = { isPM = !isPM }
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                // 表盘会随时/分自动切换，提示语跟着当前盘面走，减少用户试错
                                subtitle ?: if (activeHand == ClockHand.HOUR) {
                                    "拖动指针选小时，点上午/下午切换，松手后自动切到分钟"
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
                                dialHour = dialHour,
                                minute = minute,
                                activeHand = activeHand,
                                dialStyle = dialStyle,
                                selectedLabel = selectedLabel,
                                onHourChange = { dialHour = it },
                                onMinuteChange = { minute = it },
                                // 小时选定后自动进入分钟盘，与系统闹钟的「先时后分」一致
                                onHourPicked = { hourPickedTick++ },
                                modifier = Modifier.size(dialSize)
                            )
                        }

                        Spacer(Modifier.height(18.dp))
                    }

                    Button(
                        onClick = { onConfirm(selectedTime) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = CONFIRM_BUTTON_BOTTOM_INSET)
                            .height(52.dp),
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
 * 大号读数：上午/下午切换 + 时/分两段可点按切换当前指针，当前调节段用主题色高亮。
 */
@Composable
private fun TimeReadout(
    dialHour: Int,
    minute: Int,
    isPM: Boolean,
    activeHand: ClockHand,
    onHandChange: (ClockHand) -> Unit,
    onTogglePeriod: () -> Unit
) {
    val hourText = dialHour.toString()
    val minuteText = String.format(Locale.US, "%02d", minute)

    Row(verticalAlignment = Alignment.CenterVertically) {
        PeriodToggle(isPM = isPM, onToggle = onTogglePeriod)
        Spacer(Modifier.width(10.dp))
        ReadoutSegment(
            text = hourText,
            selected = activeHand == ClockHand.HOUR,
            contentDescription = "${if (isPM) "下午" else "上午"} $hourText 点，点击切换调节小时"
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

/** 上午/下午切换：两段小药丸，点击在 AM/PM 间切换 */
@Composable
private fun PeriodToggle(isPM: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(2.dp)
    ) {
        PeriodToggleItem("上午", selected = !isPM) { if (isPM) onToggle() }
        PeriodToggleItem("下午", selected = isPM) { if (!isPM) onToggle() }
    }
}

@Composable
private fun PeriodToggleItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
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
 * 12 小时圆形表盘。
 * 时针每 12 小时一圈、分针每 60 分钟一圈；按住表盘任意位置即把当前指针拖到手指角度，
 * 无需先精确抓到细线，指针会实时吸附到整点/整分。
 */
@Composable
private fun AnalogClock(
    dialHour: Int,
    minute: Int,
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
        (dialHour % 12) * DEGREES_PER_DIAL_HOUR
    } else {
        minute * DEGREES_PER_MINUTE
    }
    val smoothSweep by animateFloatAsState(
        targetValue = sweepTarget,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "smoothSweep"
    )

    // 表盘数字不随拖动变化，预先排版，避免每帧重新测量；12 小时制单圈 1–12
    val hourLayouts = remember(textMeasurer, scheme.onSurfaceVariant, dialStyle) {
        (1..12).map { h ->
            measureDialLabel(textMeasurer, dialStyle.hourText(h), scheme.onSurfaceVariant)
        }
    }
    // 分钟盘以 5 分钟为刻度：00、05 …… 55 共 12 个数字，始终用阿拉伯数字
    val minuteLayouts = remember(textMeasurer, scheme.onSurfaceVariant) {
        (0 until 60 step 5).map { m ->
            measureDialLabel(
                textMeasurer,
                String.format(Locale.US, "%02d", m),
                scheme.onSurfaceVariant
            )
        }
    }

    Canvas(
        modifier = modifier
            .pointerInput(activeHand) {
                // 触点离圆心太近时角度不稳定，忽略中心区域，避免误跳
                val deadZone = 12.dp.toPx()
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
                            // 12 个落点：正上方是 12 点
                            val slot = (angle / DEGREES_PER_DIAL_HOUR).roundToInt() % 12
                            onHourChange(if (slot == 0) 12 else slot)
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
                contentDescription = "12 小时圆形表盘，当前选择 $selectedLabel，拖动指针校准"
                customActions = listOf(
                    CustomAccessibilityAction("小时加一") {
                        onHourChange(if (dialHour == 12) 1 else dialHour + 1)
                        true
                    },
                    CustomAccessibilityAction("小时减一") {
                        onHourChange(if (dialHour == 1) 12 else dialHour - 1)
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

        // 当前选中刻度的索引（条丁样式用来高亮对应刻度）
        val selectedIndex = when (activeHand) {
            ClockHand.HOUR -> dialHour % 12
            ClockHand.MINUTE -> minute / 5
        }

        // 刻度：12 个落点，每 30° 一格；3 的倍数加粗。条丁样式画实体条，数字样式画细刻度
        for (i in 0 until 12) {
            val angle = i * DEGREES_PER_DIAL_HOUR
            val major = i % 3 == 0
            val selected = i == selectedIndex
            if (dialStyle == ClockDialStyle.INDEX) {
                // 实体条丁：3/6/9/12 加长加粗
                val outer = dialRadius - 6.dp.toPx()
                val inner = outer - (if (major) 20.dp else 12.dp).toPx()
                drawLine(
                    color = if (selected) activeColor else if (major) strongTickColor else tickColor,
                    start = pointAt(angle, inner),
                    end = pointAt(angle, outer),
                    strokeWidth = (if (major) 5.dp else 3.dp).toPx(),
                    cap = StrokeCap.Round
                )
            } else {
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
        }

        // 数字：数字/罗马样式绘制 1–12；条丁样式不绘制数字
        val outerLabelRadius = dialRadius * OUTER_LABEL_RATIO

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

        /** 当前选中的数字用主题色重绘，明确「指针停在哪一格」 */
        fun drawSelected(text: String, angle: Float, radius: Float, fontSize: TextUnit = 10.sp) {
            val layout = measureDialLabel(textMeasurer, text, activeColor, fontSize)
            drawLabel(layout, angle, radius)
        }

        if (dialStyle != ClockDialStyle.INDEX) {
            when (activeHand) {
                ClockHand.MINUTE -> {
                    // 5 分钟一格：00、05 …… 55，每格 30°
                    minuteLayouts.forEachIndexed { index, layout ->
                        drawLabel(layout, index * DEGREES_PER_MINUTE * 5f, outerLabelRadius)
                    }
                    // 高亮指针所在的那一格（最近的 5 分钟刻度）；分钟数字始终用阿拉伯数字
                    val stickMinute = minute / 5 * 5
                    drawSelected(
                        String.format(Locale.US, "%02d", stickMinute),
                        stickMinute * DEGREES_PER_MINUTE,
                        outerLabelRadius
                    )
                }

                else -> {
                    // 小时数字 1–12：1 点在 30°、12 点在正上方 0°
                    hourLayouts.forEachIndexed { index, layout ->
                        drawLabel(layout, (index + 1) % 12 * DEGREES_PER_DIAL_HOUR, outerLabelRadius)
                    }
                    // 高亮当前时针所在的那一格
                    drawSelected(
                        dialStyle.hourText(dialHour),
                        (dialHour % 12) * DEGREES_PER_DIAL_HOUR,
                        outerLabelRadius
                    )
                }
            }
        }

        // 时针吸附到整点、分针吸附到整分，保证指针位置与拖拽角度 1:1，不因分钟数提前偏移
        val hourOnDialAngle = (dialHour % 12) * DEGREES_PER_DIAL_HOUR
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

        val hourHandRatio = HOUR_HAND_RATIO

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
