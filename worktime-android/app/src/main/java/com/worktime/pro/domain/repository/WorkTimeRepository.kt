/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/repository/WorkTimeRepository.kt
 * @Description: 工时数据仓库，封装打卡事件写入与"事件聚合为工时段"的重算逻辑，是数据层与引擎的桥梁
 */
package com.worktime.pro.domain.repository

import com.worktime.pro.data.db.PunchEventDao
import com.worktime.pro.data.db.PunchEventEntity
import com.worktime.pro.data.db.WorkRuleDao
import com.worktime.pro.data.db.WorkSessionDao
import com.worktime.pro.data.db.WorkSessionEntity
import com.worktime.pro.domain.engine.WorkTimeCalculator
import com.worktime.pro.domain.engine.WorkRuleConfig
import com.worktime.pro.domain.model.Confidence
import com.worktime.pro.domain.model.DaySummary
import com.worktime.pro.domain.model.PunchSource
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.util.Constants
import com.worktime.pro.util.TimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.LocalTime

/** 新增打卡事件的参数（确认页提交/手动录入均走此结构） */
data class PunchInput(
    val workDate: String,
    val punchType: PunchType,
    val punchTime: LocalDateTime,
    val source: PunchSource,
    val imagePath: String? = null,
    val ocrRawText: String? = null,
    val confidence: Confidence? = null,
    val remark: String? = null
)

class WorkTimeRepository(
    private val punchDao: PunchEventDao,
    private val sessionDao: WorkSessionDao,
    private val ruleDao: WorkRuleDao,
    private val calculator: WorkTimeCalculator = WorkTimeCalculator()
) {
    fun observeEvents(date: String): Flow<List<PunchEventEntity>> = punchDao.getByDate(date)

    fun observeSessions(date: String): Flow<List<WorkSessionEntity>> = sessionDao.getByDate(date)

    fun observeDaySummary(date: String): Flow<DaySummary> =
        sessionDao.getByDate(date).map { sessions ->
            val events = punchDao.getByDateOnce(date)
            DaySummary(
                date = date,
                regularMinutes = sessions.filter { it.sessionType == "REGULAR" }.sumOf { it.workMinutes },
                overtimeMinutes = sessions.sumOf { it.overtimeMinutes },
                firstWorkStart = events.firstOrNull { it.punchType == PunchType.WORK_START.name }
                    ?.let { TimeFormatter.formatTime(fromMillis(it.punchTime).toLocalTime()) },
                lastWorkEnd = events.lastOrNull { it.punchType == PunchType.WORK_END.name }
                    ?.let { TimeFormatter.formatTime(fromMillis(it.punchTime).toLocalTime()) },
                firstOtStart = events.firstOrNull { it.punchType == PunchType.OT_START.name }
                    ?.let { TimeFormatter.formatTime(fromMillis(it.punchTime).toLocalTime()) },
                lastOtEnd = events.lastOrNull { it.punchType == PunchType.OT_END.name }
                    ?.let { TimeFormatter.formatTime(fromMillis(it.punchTime).toLocalTime()) },
                recordCount = events.size
            )
        }

    /** 新增打卡事件并立即重算当日工时段 */
    suspend fun addPunch(input: PunchInput): Long {
        val now = System.currentTimeMillis()
        val id = punchDao.insert(
            PunchEventEntity(
                workDate = input.workDate,
                punchType = input.punchType.name,
                punchTime = input.punchTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                source = input.source.name,
                imagePath = input.imagePath,
                ocrRawText = input.ocrRawText,
                confidence = input.confidence?.name,
                remark = input.remark,
                createTime = now,
                updateTime = now
            )
        )
        recalculate(input.workDate)
        return id
    }

    suspend fun updatePunch(event: PunchEventEntity) {
        punchDao.update(event.copy(updateTime = System.currentTimeMillis()))
        recalculate(event.workDate)
    }

    suspend fun deletePunch(event: PunchEventEntity) {
        punchDao.delete(event)
        recalculate(event.workDate)
    }

    /**
     * 重算某日工时段：先清空旧结果，再按类型配对事件成段并计算。
     * 业务规则：WORK_START/WORK_END 按时间顺序交替配对成 REGULAR 段；
     * OT_START/OT_END 配对成 OVERTIME 段，独立加班段直接采用段时长，不再按标准工时推导。
     */
    suspend fun recalculate(date: String) {
        val events = punchDao.getByDateOnce(date)
        val rule = ruleDao.getDefaultOnce()?.toConfig() ?: defaultConfig()

        sessionDao.deleteByDate(date)
        if (events.isEmpty()) return

        val byType = events.groupBy { it.punchType }.mapValues { entry ->
            entry.value.sortedBy { it.punchTime }
        }
        val starts = byType[PunchType.WORK_START.name].orEmpty()
        val ends = byType[PunchType.WORK_END.name].orEmpty()
        val otStarts = byType[PunchType.OT_START.name].orEmpty()
        val otEnds = byType[PunchType.OT_END.name].orEmpty()

        val now = System.currentTimeMillis()

        // 正常工作段：按起始事件逐个配对最近的结束事件
        for ((index, start) in starts.withIndex()) {
            val end = ends.getOrNull(index) ?: continue
            if (end.punchTime <= start.punchTime) continue
            val result = calculator.calculate(fromMillis(start.punchTime), fromMillis(end.punchTime), rule)
            sessionDao.insert(
                WorkSessionEntity(
                    workDate = date,
                    sessionType = "REGULAR",
                    startTime = start.punchTime,
                    endTime = end.punchTime,
                    workMinutes = result.workMinutes,
                    overtimeMinutes = result.overtimeMinutes,
                    restMinutes = result.restMinutes,
                    crossDay = result.crossDay,
                    formula = result.formula,
                    startEventId = start.id,
                    endEventId = end.id,
                    createTime = now,
                    updateTime = now
                )
            )
        }

        // 独立加班段
        for ((index, otStart) in otStarts.withIndex()) {
            val otEnd = otEnds.getOrNull(index) ?: continue
            if (otEnd.punchTime <= otStart.punchTime) continue
            val result = calculator.calculateOvertimeSegment(
                fromMillis(otStart.punchTime), fromMillis(otEnd.punchTime), rule
            )
            sessionDao.insert(
                WorkSessionEntity(
                    workDate = date,
                    sessionType = "OVERTIME",
                    startTime = otStart.punchTime,
                    endTime = otEnd.punchTime,
                    workMinutes = result.workMinutes,
                    overtimeMinutes = result.overtimeMinutes,
                    restMinutes = 0,
                    crossDay = result.crossDay,
                    formula = result.formula,
                    startEventId = otStart.id,
                    endEventId = otEnd.id,
                    createTime = now,
                    updateTime = now
                )
            )
        }
    }

    private fun fromMillis(millis: Long): LocalDateTime =
        java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()

    private fun com.worktime.pro.data.db.WorkRuleEntity.toConfig() = WorkRuleConfig(
        startTime = LocalTime.parse(startTime),
        endTime = LocalTime.parse(endTime),
        restStart = restStart?.let { LocalTime.parse(it) },
        restEnd = restEnd?.let { LocalTime.parse(it) },
        bufferMinutes = bufferMinutes,
        standardMinutes = run {
            val derived = java.time.Duration.between(LocalTime.parse(startTime), LocalTime.parse(endTime)).toMinutes().toInt()
            if (derived > 0) derived else Constants.STANDARD_MINUTES_FALLBACK
        },
        dayCutTime = LocalTime.parse(dayCutTime),
        workDays = workDays
    )

    private fun defaultConfig() = WorkRuleConfig(
        startTime = LocalTime.parse(Constants.DEFAULT_START_TIME),
        endTime = LocalTime.parse(Constants.DEFAULT_END_TIME),
        restStart = LocalTime.parse(Constants.DEFAULT_REST_START),
        restEnd = LocalTime.parse(Constants.DEFAULT_REST_END),
        bufferMinutes = Constants.DEFAULT_BUFFER_MINUTES,
        standardMinutes = Constants.STANDARD_MINUTES_FALLBACK,
        dayCutTime = LocalTime.parse(Constants.DEFAULT_DAY_CUT_TIME),
        workDays = Constants.DEFAULT_WORK_DAYS.split(",").map { it.toInt() }
    )
}
