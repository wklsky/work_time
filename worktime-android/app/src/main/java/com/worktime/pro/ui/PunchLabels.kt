/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/ui/PunchLabels.kt
 * @Description: 打卡实体的展示标签扩展，统一把 punchType/source 字符串转成中文标签，避免各页面重复反查
 */
package com.worktime.pro.ui

import com.worktime.pro.data.db.PunchEventEntity
import com.worktime.pro.domain.model.PunchSource
import com.worktime.pro.domain.model.PunchType

/** 打卡类型的中文标签；无法识别时回退到原始字符串 */
fun PunchEventEntity.typeLabel(): String =
    PunchType.fromName(punchType)?.label ?: punchType

/** 打卡来源的中文标签；无法识别时回退到原始字符串 */
fun PunchEventEntity.sourceLabel(): String =
    PunchSource.fromName(source)?.label ?: source
