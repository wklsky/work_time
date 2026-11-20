/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/data/db/Entities.kt
 * @Description: Room 实体定义，采用点段分离模型：punch_event 存原始打卡凭证，work_session 存计算结果
 */
package com.worktime.pro.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 打卡事件（原始凭证）：一条记录代表一次时间点，是工时计算的唯一数据源 */
@Entity(
    tableName = "punch_event",
    foreignKeys = [ForeignKey(
        entity = WorkSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [
        Index(value = ["workDate"]),
        Index(value = ["workDate", "punchType"]),
        Index(value = ["sessionId"])
    ]
)
data class PunchEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workDate: String,
    val punchType: String,
    val punchTime: Long,
    val source: String,
    val sessionId: Long? = null,
    val imagePath: String? = null,
    val ocrRawText: String? = null,
    val confidence: String? = null,
    val remark: String? = null,
    val createTime: Long,
    val updateTime: Long
)

/** 工时段（计算结果）：由事件聚合计算生成，是派生数据，事件变更后必须重算 */
@Entity(
    tableName = "work_session",
    indices = [
        Index(value = ["workDate"]),
        Index(value = ["workDate", "sessionType"])
    ]
)
data class WorkSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workDate: String,
    val sessionType: String,
    val startTime: Long,
    val endTime: Long,
    val workMinutes: Int,
    val overtimeMinutes: Int,
    val restMinutes: Int,
    val crossDay: Boolean,
    val formula: String,
    val startEventId: Long? = null,
    val endEventId: Long? = null,
    val createTime: Long,
    val updateTime: Long
)

/** 工作规则：单用户一条默认规则，修改不回溯历史记录 */
@Entity(tableName = "work_rule")
data class WorkRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleName: String = "默认规则",
    val startTime: String,
    val endTime: String,
    val restStart: String?,
    val restEnd: String?,
    val bufferMinutes: Int,
    val dayCutTime: String,
    val workDays: List<Int>,
    val isDefault: Boolean = true,
    val updateTime: Long
)
