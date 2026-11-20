/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/data/db/Daos.kt
 * @Description: Room DAO 定义，封装打卡事件、工时段与规则的本地查询与写入
 */
package com.worktime.pro.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PunchEventDao {
    /** 查询某日全部打卡事件（时间升序），供日详情与重算使用 */
    @Query("SELECT * FROM punch_event WHERE workDate = :date ORDER BY punchTime ASC")
    fun getByDate(date: String): Flow<List<PunchEventEntity>>

    /** 一次性读取某日事件，供重算逻辑同步调用 */
    @Query("SELECT * FROM punch_event WHERE workDate = :date ORDER BY punchTime ASC")
    suspend fun getByDateOnce(date: String): List<PunchEventEntity>

    @Query("SELECT * FROM punch_event WHERE workDate BETWEEN :start AND :end ORDER BY workDate, punchTime")
    suspend fun getByRange(start: String, end: String): List<PunchEventEntity>

    @Query("SELECT * FROM punch_event WHERE workDate = :date AND punchType = :type ORDER BY punchTime ASC")
    suspend fun getByDateAndType(date: String, type: String): List<PunchEventEntity>

    @Query("SELECT DISTINCT workDate FROM punch_event WHERE workDate BETWEEN :start AND :end ORDER BY workDate")
    suspend fun getDatesInRange(start: String, end: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: PunchEventEntity): Long

    @Update
    suspend fun update(event: PunchEventEntity)

    @Delete
    suspend fun delete(event: PunchEventEntity)

    @Query("DELETE FROM punch_event WHERE workDate = :date")
    suspend fun deleteByDate(date: String)

    @Query("SELECT * FROM punch_event")
    suspend fun getAll(): List<PunchEventEntity>

    @Query("DELETE FROM punch_event")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<PunchEventEntity>)
}

@Dao
interface WorkSessionDao {
    @Query("SELECT * FROM work_session WHERE workDate = :date ORDER BY startTime ASC")
    fun getByDate(date: String): Flow<List<WorkSessionEntity>>

    @Query("SELECT * FROM work_session WHERE workDate = :date ORDER BY startTime ASC")
    suspend fun getByDateOnce(date: String): List<WorkSessionEntity>

    @Query("SELECT * FROM work_session WHERE workDate BETWEEN :start AND :end ORDER BY workDate, startTime")
    suspend fun getByRange(start: String, end: String): List<WorkSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: WorkSessionEntity): Long

    @Query("DELETE FROM work_session WHERE workDate = :date")
    suspend fun deleteByDate(date: String)

    @Query("SELECT * FROM work_session")
    suspend fun getAll(): List<WorkSessionEntity>

    @Query("DELETE FROM work_session")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<WorkSessionEntity>)
}

@Dao
interface WorkRuleDao {
    @Query("SELECT * FROM work_rule WHERE isDefault = 1 LIMIT 1")
    fun getDefault(): Flow<WorkRuleEntity?>

    @Query("SELECT * FROM work_rule WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultOnce(): WorkRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: WorkRuleEntity)

    @Update
    suspend fun update(rule: WorkRuleEntity)

    @Query("SELECT * FROM work_rule")
    suspend fun getAll(): List<WorkRuleEntity>

    @Query("DELETE FROM work_rule")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<WorkRuleEntity>)
}
