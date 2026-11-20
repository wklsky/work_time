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
import com.worktime.pro.util.Constants
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
                put("defaultInputMode", settings.defaultInputMode)
                put("ocrAutoFill", settings.ocrAutoFill)
                put("backupIncludePhotos", settings.backupIncludePhotos)
                put("statusMapping", settings.statusMapping)
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
        // 恢复用户自定义状态映射，保证换机/重装后识别规则一致
        json.optJSONObject("settings")?.optString("statusMapping")?.let {
            settingsDataStore.setStatusMapping(it)
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun JSONArray?.orEmptyObjects(): List<JSONObject> =
        (0 until (this?.length() ?: 0)).mapNotNull { this?.optJSONObject(it) }

    private fun JSONObject.toPunchEvent() = com.worktime.pro.data.db.PunchEventEntity(
        id = optLong("id"), workDate = optString("workDate"), punchType = optString("punchType"),
        punchTime = optLong("punchTime"), source = optString("source"),
        sessionId = if (isNull("sessionId")) null else optLong("sessionId"),
        imagePath = if (isNull("imagePath")) null else optString("imagePath"),
        ocrRawText = if (isNull("ocrRawText")) null else optString("ocrRawText"),
        confidence = if (isNull("confidence")) null else optString("confidence"),
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
            "imagePath" to imagePath, "ocrRawText" to ocrRawText,
            "confidence" to confidence, "remark" to remark,
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
