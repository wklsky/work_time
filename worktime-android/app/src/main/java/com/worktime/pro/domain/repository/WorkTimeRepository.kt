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
import com.worktime.pro.domain.engine.PunchMoment
import com.worktime.pro.domain.engine.PunchPairing
import com.worktime.pro.domain.engine.PunchTimelineValidator
import com.worktime.pro.domain.engine.WorkTimeCalculator
import com.worktime.pro.domain.engine.WorkRuleConfig
import com.worktime.pro.domain.model.PunchSource
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.domain.model.SessionType
import com.worktime.pro.util.Constants
import com.worktime.pro.util.TimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.LocalTime

/** 新增打卡事件的参数（手动录入/快速打卡/习惯打卡均走此结构） */
data class PunchInput(
    val workDate: String,
    val punchType: PunchType,
    val punchTime: LocalDateTime,
    val source: PunchSource,
    val remark: String? = null
)

class WorkTimeRepository(
    private val punchDao: PunchEventDao,
    private val sessionDao: WorkSessionDao,
    private val ruleDao: WorkRuleDao,
    private val calculator: WorkTimeCalculator = WorkTimeCalculator()
) {
    /** 串行化“校验 + 写入 + 重算”，避免双击/并发写入绕过时间线校验 */
    private val writeMutex = Mutex()

    fun observeEvents(date: String): Flow<List<PunchEventEntity>> = punchDao.getByDate(date)

    fun observeSessions(date: String): Flow<List<WorkSessionEntity>> = sessionDao.getByDate(date)

    /** 月历一次聚合查询，一次覆盖整月正常/加班分钟 */
    fun observeDayAggregates(start: String, end: String): Flow<List<DayAggregate>> =
        sessionDao.observeDayAggregates(start, end)

    /** 月历缺卡判定所需的四类打卡计数 */
    fun observeDayPunchCounts(start: String, end: String): Flow<List<DayPunchCounts>> =
        punchDao.observeDayPunchCounts(start, end)

    /**
     * 新增一条打卡并立即重算当日工时段。
     * 业务规则：一天可以有多段上下班/加班段，但上班与加班、同类段之间不可重叠；
     * 校验不通过时返回失败，不写入任何数据。
     */
    suspend fun addPunch(input: PunchInput): Result<Long> = writeMutex.withLock {
        runCatching {
            validateDay(input.workDate, listOf(input.toMoment()))
                ?.let { throw IllegalArgumentException(it) }
            val id = insertEvent(input)
            recalculate(input.workDate)
            id
        }
    }

    /**
     * 批量追加打卡并按日期各重算一次。
     * 业务说明：先校验所有日期，再统一写入，避免部分数据已落库。
     */
    suspend fun addPunches(inputs: List<PunchInput>): Result<Unit> = writeMutex.withLock {
        runCatching {
            if (inputs.isEmpty()) return@runCatching
            val byDate = inputs.groupBy { it.workDate }
            byDate.forEach { (date, dayInputs) ->
                validateDay(date, dayInputs.map { it.toMoment() })
                    ?.let { throw IllegalArgumentException(it) }
            }
            inputs.forEach { insertEvent(it) }
            byDate.keys.forEach { recalculate(it) }
        }
    }

    /** 修改已有打卡时间；同样走时间线校验，避免编辑后与其他段重叠 */
    suspend fun updatePunch(event: PunchEventEntity): Result<Unit> = writeMutex.withLock {
        runCatching {
            val type = PunchType.fromName(event.punchType)
                ?: throw IllegalArgumentException("未知打卡类型")
            validateDay(
                date = event.workDate,
                newMoments = listOf(PunchMoment(type, event.punchTime)),
                excludeEventId = event.id
            )?.let { throw IllegalArgumentException(it) }
            punchDao.update(event.copy(updateTime = System.currentTimeMillis()))
            recalculate(event.workDate)
        }
    }

    private suspend fun validateDay(
        date: String,
        newMoments: List<PunchMoment>,
        excludeEventId: Long? = null
    ): String? {
        val existing = punchDao.getByDateOnce(date)
            .filter { excludeEventId == null || it.id != excludeEventId }
            .mapNotNull { event ->
                val type = PunchType.fromName(event.punchType)
                    ?: return@mapNotNull null
                PunchMoment(type, event.punchTime)
            }
        return PunchTimelineValidator.validate(existing + newMoments)
    }

    private fun PunchInput.toMoment() = PunchMoment(
        type = punchType,
        time = punchTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    private suspend fun insertEvent(input: PunchInput): Long {
        val now = System.currentTimeMillis()
        return punchDao.insert(
            PunchEventEntity(
                workDate = input.workDate,
                punchType = input.punchType.name,
                punchTime = input.punchTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                source = input.source.name,
                remark = input.remark,
                createTime = now,
                updateTime = now
            )
        )
    }

    suspend fun deletePunch(event: PunchEventEntity) = writeMutex.withLock {
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
    suspend fun recalculateAll(): Int = writeMutex.withLock {
        val dates = punchDao.getDatesInRange("0000-01-01", "9999-12-31")
        dates.forEach { recalculate(it) }
        dates.size
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
            val result = calculator.calculate(TimeFormatter.millisToLocalDateTime(start.punchTime), TimeFormatter.millisToLocalDateTime(end.punchTime), rule)
            sessionDao.insert(
                WorkSessionEntity(
                    workDate = date,
                    sessionType = SessionType.REGULAR.name,
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
                TimeFormatter.millisToLocalDateTime(otStart.punchTime), TimeFormatter.millisToLocalDateTime(otEnd.punchTime), rule
            )
            sessionDao.insert(
                WorkSessionEntity(
                    workDate = date,
                    sessionType = SessionType.OVERTIME.name,
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
