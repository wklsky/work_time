/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/domain/backup/BackupManager.kt
 * @Description: 本地数据备份与恢复，导出全部数据为 JSON 文件，替代云端同步
 */
package com.worktime.pro.domain.backup

import android.content.Context
import com.worktime.pro.data.datastore.AppSettings
import com.worktime.pro.data.datastore.SettingsDataStore
import com.worktime.pro.data.db.PunchEventDao
import com.worktime.pro.data.db.WorkRuleDao
import com.worktime.pro.data.db.WorkSessionDao
import com.worktime.pro.data.db.toDayTotals
import com.worktime.pro.util.Constants
import com.worktime.pro.domain.model.PunchType
import com.worktime.pro.util.TimeFormatter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId

class BackupManager(
    private val context: Context,
    private val punchDao: PunchEventDao,
    private val sessionDao: WorkSessionDao,
    private val ruleDao: WorkRuleDao,
    private val settingsDataStore: SettingsDataStore
) {
    /**
     * 导出全部数据为 JSON 文件。
     * 业务规则：单机版无云端兜底，备份是数据安全的唯一保障，导出必须包含全部三类表数据。
     */
    suspend fun export(settings: AppSettings): Result<File> = try {
        val root = JSONObject().apply {
            put("version", Constants.BACKUP_VERSION)
            put("exportTime", System.currentTimeMillis())
            put("punchEvents", JSONArray(punchDao.getAll().map { JSONObject(it.toJsonMap()) }))
            put("workSessions", JSONArray(sessionDao.getAll().map { JSONObject(it.toJsonMap()) }))
            put("workRules", JSONArray(ruleDao.getAll().map { JSONObject(it.toJsonMap()) }))
            put("settings", JSONObject().apply {
                put("calendarDisplayMode", settings.calendarDisplayMode)
                put("timeFormat", settings.timeFormat)
                put("showOvertimeShade", settings.showOvertimeShade)
                put("defaultPunchType", settings.defaultPunchType)
                put("themeMode", settings.themeMode)
                put("habits", settings.habits)
                put("customThemeEnabled", settings.customThemeEnabled)
                put("customLightColors", settings.customLightColors)
                put("customDarkColors", settings.customDarkColors)
                put("fontScale", settings.fontScale.toDouble())
                put("clockDialStyle", settings.clockDialStyle)
                put("reminderEnabled", settings.reminderEnabled)
                put("reminderType", settings.reminderType)
                put("reminderMinutes", settings.reminderMinutes)
                put("reminderRingEnabled", settings.reminderRingEnabled)
            })
        }
        val dir = File(context.filesDir, Constants.BACKUP_DIR).apply { mkdirs() }
        val file = File(dir, "工时备份_${System.currentTimeMillis()}.json")
        file.writeText(root.toString(), Charsets.UTF_8)
        Result.success(file)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * 校验备份文件版本。
     * 业务规则：版本不匹配时拒绝导入，避免结构差异导致数据错乱。
     */
    fun validate(file: File): Result<String> = try {
        val json = JSONObject(file.readText(Charsets.UTF_8))
        val version = json.optString("version")
        if (version == Constants.BACKUP_VERSION) Result.success(version)
        else Result.failure(IllegalArgumentException("备份版本不兼容：$version"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** 备份摘要：导入确认弹窗展示导出时间与记录条数，让用户确认选对了文件 */
    data class Summary(val exportTime: Long, val eventCount: Int, val sessionCount: Int)

    fun readSummary(file: File): Result<Summary> = try {
        val json = JSONObject(file.readText(Charsets.UTF_8))
        if (json.optString("version") != Constants.BACKUP_VERSION) {
            throw IllegalArgumentException("备份版本不兼容")
        }
        Result.success(
            Summary(
                exportTime = json.optLong("exportTime"),
                eventCount = json.optJSONArray("punchEvents")?.length() ?: 0,
                sessionCount = json.optJSONArray("workSessions")?.length() ?: 0
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * 导出可读的工时明细 CSV：按日期汇总四类打卡时刻与工时，供用户用表格软件打开观看。
     * 业务说明：JSON 备份用于换机恢复，但普通用户无法直接阅读；CSV 满足"导出数据观看"诉求。
     * 中文用 UTF-8 BOM 开头，避免 Excel 打开乱码。
     */
    suspend fun exportCsv(): Result<String> = try {
        val events = punchDao.getAll()
        val sessions = sessionDao.getAll()
        val eventsByDate = events.groupBy { it.workDate }
        val sessionsByDate = sessions.groupBy { it.workDate }
        val allDates = (eventsByDate.keys + sessionsByDate.keys).sorted()

        val sb = StringBuilder()
        sb.append('\uFEFF') // UTF-8 BOM for Excel
        sb.append("日期,上班,下班,加班到,加班退,工作时长(小时),加班时长(小时),工时明细\n")
        allDates.forEach { date ->
            val dayEvents = eventsByDate[date].orEmpty()
            // 多段打卡用 / 连接，避免导出时只保留第一段
            fun allTimes(type: String) = dayEvents
                .filter { it.punchType == type }
                .joinToString("/") {
                    Instant.ofEpochMilli(it.punchTime)
                        .atZone(ZoneId.systemDefault()).toLocalTime().toString()
                }
            val daySessions = sessionsByDate[date].orEmpty()
            val totals = daySessions.toDayTotals()
            val regular = totals.regularMinutes
            val overtime = totals.overtimeMinutes
            val formula = daySessions.joinToString("; ") { it.formula }
            sb.append(listOf(
                date,
                allTimes(PunchType.WORK_START.name),
                allTimes(PunchType.WORK_END.name),
                allTimes(PunchType.OT_START.name),
                allTimes(PunchType.OT_END.name),
                if (regular > 0) TimeFormatter.formatHours(regular).removeSuffix("h") else "0",
                if (overtime > 0) TimeFormatter.formatHours(overtime).removeSuffix("h") else "0",
                formula
            ).joinToString(",") { csvEscape(it) }).append('\n')
        }
        Result.success(sb.toString())
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** CSV 字段转义：含逗号/引号/换行的值用双引号包裹，内部引号翻倍 */
    private fun csvEscape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value

    /**
     * 从备份文件恢复数据：先清空三张表再批量写回。
     * 业务要求：导入是覆盖操作，调用方（UI）必须先二次确认；此处只负责数据落地。
     */
    suspend fun restore(file: File): Result<Unit> = try {
        val json = JSONObject(file.readText(Charsets.UTF_8))
        if (json.optString("version") != Constants.BACKUP_VERSION) {
            throw IllegalArgumentException("备份版本不兼容")
        }
        val events = json.optJSONArray("punchEvents").orEmptyObjects().map { it.toPunchEvent() }
        val sessions = json.optJSONArray("workSessions").orEmptyObjects().map { it.toWorkSession() }
        val rules = json.optJSONArray("workRules").orEmptyObjects().map { it.toWorkRule() }

        punchDao.deleteAll()
        sessionDao.deleteAll()
        ruleDao.deleteAll()
        punchDao.insertAll(events)
        sessionDao.insertAll(sessions)
        ruleDao.insertAll(rules)
        // 恢复全部设置，保证换机/重装后行为一致；备份不含照片
        json.optJSONObject("settings")?.let { saved ->
            saved.optString("themeMode").takeIf { it.isNotBlank() }?.let {
                settingsDataStore.setThemeMode(it)
            }
            saved.optString("habits").let { settingsDataStore.setHabits(it) }
            saved.optString("calendarDisplayMode").takeIf { it.isNotBlank() }?.let {
                settingsDataStore.setCalendarDisplayMode(it)
            }
            saved.optString("timeFormat").takeIf { it.isNotBlank() }?.let {
                settingsDataStore.setTimeFormat(it)
            }
            saved.optString("defaultPunchType").takeIf { it.isNotBlank() }?.let {
                settingsDataStore.setDefaultPunchType(it)
            }
            if (saved.has("showOvertimeShade")) {
                settingsDataStore.setOvertimeShade(saved.optBoolean("showOvertimeShade"))
            }
            if (saved.has("customThemeEnabled")) {
                settingsDataStore.setCustomThemeEnabled(saved.optBoolean("customThemeEnabled"))
            }
            saved.optString("customLightColors").takeIf { it.isNotBlank() }?.let {
                settingsDataStore.setCustomLightColors(it)
            }
            saved.optString("customDarkColors").takeIf { it.isNotBlank() }?.let {
                settingsDataStore.setCustomDarkColors(it)
            }
            if (saved.has("fontScale")) {
                settingsDataStore.setFontScale(saved.optDouble("fontScale", 1.0).toFloat())
            }
            saved.optString("clockDialStyle").takeIf { it.isNotBlank() }?.let {
                settingsDataStore.setClockDialStyle(it)
            }
            if (saved.has("reminderEnabled")) {
                settingsDataStore.setReminderEnabled(saved.optBoolean("reminderEnabled"))
            }
            saved.optString("reminderType").takeIf { it.isNotBlank() }?.let {
                settingsDataStore.setReminderType(it)
            }
            if (saved.has("reminderMinutes")) {
                settingsDataStore.setReminderMinutes(saved.optInt("reminderMinutes", 420))
            }
            if (saved.has("reminderRingEnabled")) {
                settingsDataStore.setReminderRingEnabled(saved.optBoolean("reminderRingEnabled", true))
            }
            // 旧备份里的提醒方式与自定义铃声字段不再恢复：当前版本固定跟随系统闹钟，
            // 避免导入历史设置后把工时提醒变回「仅震动」或指向已失效的本地音频

            // 换机/恢复后重新允许当天提醒
            settingsDataStore.setLastReminderKey("")
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun JSONArray?.orEmptyObjects(): List<JSONObject> =
        (0 until (this?.length() ?: 0)).mapNotNull { this?.optJSONObject(it) }

    private fun JSONObject.toPunchEvent() = com.worktime.pro.data.db.PunchEventEntity(
        id = optLong("id"), workDate = optString("workDate"), punchType = optString("punchType"),
        punchTime = optLong("punchTime"),
        // 兼容旧备份里残留的 OCR 来源，统一改写为手动录入
        source = optString("source").let { if (it == "OCR") "MANUAL" else it },
        sessionId = if (isNull("sessionId")) null else optLong("sessionId"),
        remark = if (isNull("remark")) null else optString("remark"),
        createTime = optLong("createTime"), updateTime = optLong("updateTime")
    )

    private fun JSONObject.toWorkSession() = com.worktime.pro.data.db.WorkSessionEntity(
        id = optLong("id"), workDate = optString("workDate"), sessionType = optString("sessionType"),
        startTime = optLong("startTime"), endTime = optLong("endTime"),
        workMinutes = optInt("workMinutes"), overtimeMinutes = optInt("overtimeMinutes"),
        restMinutes = optInt("restMinutes"), crossDay = optBoolean("crossDay"),
        formula = optString("formula"),
        startEventId = if (isNull("startEventId")) null else optLong("startEventId"),
        endEventId = if (isNull("endEventId")) null else optLong("endEventId"),
        createTime = optLong("createTime"), updateTime = optLong("updateTime")
    )

    private fun JSONObject.toWorkRule() = com.worktime.pro.data.db.WorkRuleEntity(
        id = optLong("id"), ruleName = optString("ruleName"),
        startTime = optString("startTime"), endTime = optString("endTime"),
        restStart = if (isNull("restStart")) null else optString("restStart"),
        restEnd = if (isNull("restEnd")) null else optString("restEnd"),
        bufferMinutes = optInt("bufferMinutes"), dayCutTime = optString("dayCutTime"),
        workDays = parseWorkDays(optJSONArray("workDays")),
        isDefault = optBoolean("isDefault"), updateTime = optLong("updateTime")
    )

    private fun parseWorkDays(array: JSONArray?): List<Int> =
        (0 until (array?.length() ?: 0)).mapNotNull { array?.optInt(it) }

    // 实体转 Map 供 JSON 序列化，使用扩展集中处理避免散落在各实体类
    private fun Any.toJsonMap(): Map<String, Any?> = when (this) {
        is com.worktime.pro.data.db.PunchEventEntity -> mapOf(
            "id" to id, "workDate" to workDate, "punchType" to punchType,
            "punchTime" to punchTime, "source" to source, "sessionId" to sessionId,
            "remark" to remark,
            "createTime" to createTime, "updateTime" to updateTime
        )
        is com.worktime.pro.data.db.WorkSessionEntity -> mapOf(
            "id" to id, "workDate" to workDate, "sessionType" to sessionType,
            "startTime" to startTime, "endTime" to endTime,
            "workMinutes" to workMinutes, "overtimeMinutes" to overtimeMinutes,
            "restMinutes" to restMinutes, "crossDay" to crossDay, "formula" to formula,
            "startEventId" to startEventId, "endEventId" to endEventId,
            "createTime" to createTime, "updateTime" to updateTime
        )
        is com.worktime.pro.data.db.WorkRuleEntity -> mapOf(
            "id" to id, "ruleName" to ruleName, "startTime" to startTime,
            "endTime" to endTime, "restStart" to restStart, "restEnd" to restEnd,
            "bufferMinutes" to bufferMinutes, "dayCutTime" to dayCutTime,
            "workDays" to workDays, "isDefault" to isDefault, "updateTime" to updateTime
        )
        else -> emptyMap()
    }
}
