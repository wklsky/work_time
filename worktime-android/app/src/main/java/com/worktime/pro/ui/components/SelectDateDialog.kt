/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/components/SelectDateDialog.kt
 * @Description: 通用日期选择弹窗，统一 M3 DatePicker 的 UTC 毫秒转换与确认/取消按钮，供各页面复用
 */
package com.worktime.pro.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import com.worktime.pro.util.TimeFormatter
import java.time.LocalDate

/**
 * 选择单个日期。
 *
 * @param initialDate 弹窗打开时选中的日期
 * @param onConfirm 点「确定」后回调选中的日期；调用方自行决定如何落地与关闭
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectDateDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = TimeFormatter.localDateToUtcMillis(initialDate)
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onConfirm(TimeFormatter.utcMillisToLocalDate(it)) }
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    ) {
        DatePicker(state = state)
    }
}
