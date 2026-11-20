/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/repository/WorkTimeRepository.kt
 * @Description: 工时数据仓库，封装打卡事件写入与"事件聚合为工时段"的重算逻辑，是数据层与引擎的桥梁
 */
package com.worktime.pro.domain.repository

import com.worktime.pro.data.db.DayAggregate
import com.worktime.pro.data.db.DayPunchCounts
import com.worktime.pro.data.db.PunchEventDao
import com.worktime.pro.data.db.PunchEventEntity
import com.worktime.pro.data.db.WorkRuleDao
import com.worktime.pro.data.db.WorkSessionDao
import com.worktime.pro.data.db.WorkSessionEntity
import com.worktime.pro.data.db.toDayTotals
import com.worktime.pro.domain.engine.PunchPairing
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
            val totals = sessions.toDayTotals()
            DaySummary(
                date = date,
                regularMinutes = totals.regularMinutes,
                overtimeMinutes = totals.overtimeMinutes,
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

    /** 月历一次聚合查询，替代逐格 observeDaySummary */
    fun observeDayAggregates(start: String, end: String): Flow<List<DayAggregate>> =
        sessionDao.observeDayAggregates(start, end)

    /** 月历缺卡判定所需的四类打卡计数 */
    fun observeDayPunchCounts(start: String, end: String): Flow<List<DayPunchCounts>> =
        punchDao.observeDayPunchCounts(start, end)

    /**
     * 新增一条打卡并立即重算当日工时段。
     * 业务规则：一天可以有多段上下班/加班段，同类型打卡按时间追加；
     * 编辑已有记录必须走 updatePunch，避免误改其他段。
     */
    suspend fun addPunch(input: PunchInput): Long {
        val id = insertEvent(input)
        recalculate(input.workDate)
        return id
    }

    /**
     * 批量追加打卡并按日期各重算一次。
     * 业务说明：习惯打卡含多条固定时间，逐条 addPunch 会重复重算；
     * OCR 识别也可能跨日期，因此按 workDate 分组重算。
     */
    suspend fun addPunches(inputs: List<PunchInput>) {
        if (inputs.isEmpty()) return
        inputs.forEach { insertEvent(it) }
        inputs.map { it.workDate }.distinct().forEach { recalculate(it) }
    }

    private suspend fun insertEvent(input: PunchInput): Long {
        val now = System.currentTimeMillis()
        return punchDao.insert(
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
    }

    suspend fun updatePunch(event: PunchEventEntity) {
        punchDao.update(event.copy(updateTime = System.currentTimeMillis()))
        recalculate(event.workDate)
    }

    suspend fun deletePunch(event: PunchEventEntity) {
        punchDao.delete(event)
        recalculate(event.workDate)
    }

    /** 查询日期区间内的工时段，供统计汇总与报表导出使用 */
    suspend fun getSessionsInRange(start: String, end: String): List<WorkSessionEntity> =
        sessionDao.getByRange(start, end)

    /** 查询区间内的打卡事件，供报表导出展示四类原始时间 */
    suspend fun getEventsInRange(start: String, end: String): List<PunchEventEntity> =
        punchDao.getByRange(start, end)

    /** 全量工时段，供「我的」页累计统计 */
    suspend fun getAllSessions(): List<WorkSessionEntity> = sessionDao.getAll()

    /** 当前生效的默认工作规则 */
    fun observeWorkRule(): Flow<com.worktime.pro.data.db.WorkRuleEntity?> = ruleDao.getDefault()

    suspend fun getWorkRule(): com.worktime.pro.data.db.WorkRuleEntity? = ruleDao.getDefaultOnce()

    /**
     * 保存默认工作规则。
     * 业务规则：修改规则默认不回溯历史记录，只有之后新写入或手动重算的日期才使用新规则。
     */
    suspend fun saveWorkRule(rule: com.worktime.pro.data.db.WorkRuleEntity) {
        val existing = ruleDao.getDefaultOnce()
        val now = System.currentTimeMillis()
        val toSave = rule.copy(
            id = existing?.id ?: 0,
            isDefault = true,
            updateTime = now
        )
        if (existing == null) ruleDao.insert(toSave) else ruleDao.update(toSave)
    }

    /** 按当前规则重算全部有打卡记录的日期，返回重算天数 */
    suspend fun recalculateAll(): Int {
        val dates = punchDao.getDatesInRange("0000-01-01", "9999-12-31")
        dates.forEach { recalculate(it) }
        return dates.size
    }

    /**
     * 重算某日工时段：先清空旧结果，再按类型配对事件成段并计算。
     * 业务规则：同一天可有多段，WORK_START/WORK_END 按时间配对成多个 REGULAR 段；
     * OT_START/OT_END 配对成多个 OVERTIME 段，独立加班段直接采用段时长，不再按标准工时推导。
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

        // 正常工作段：为每个开始事件匹配其后最近的未用结束事件
        for ((start, end) in PunchPairing.pair(starts, ends) { it.punchTime }) {
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
        for ((otStart, otEnd) in PunchPairing.pair(otStarts, otEnds) { it.punchTime }) {
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
            // 标准工时 = 在岗时长 - 午休，与 PRD「正常工作已扣除午休」的口径一致
            val start = LocalTime.parse(startTime)
            val end = LocalTime.parse(endTime)
            val rest = if (restStart != null && restEnd != null) {
                val rs = LocalTime.parse(restStart)
                val re = LocalTime.parse(restEnd)
                if (re.isAfter(rs)) java.time.Duration.between(rs, re).toMinutes().toInt() else 0
            } else 0
            val net = java.time.Duration.between(start, end).toMinutes().toInt() - rest
            if (net > 0) net else Constants.STANDARD_MINUTES_FALLBACK
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
