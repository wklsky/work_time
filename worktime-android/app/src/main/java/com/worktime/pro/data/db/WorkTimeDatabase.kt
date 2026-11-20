/**
 * @Author: wj
 * @FilePath: worktime-android/app/src/main/java/com/worktime/pro/data/db/WorkTimeDatabase.kt
 * @Description: Room 数据库持有类，首次创建时初始化默认工作规则
 */
package com.worktime.pro.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.worktime.pro.util.Constants

@Database(
    entities = [PunchEventEntity::class, WorkSessionEntity::class, WorkRuleEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class WorkTimeDatabase : RoomDatabase() {
    abstract fun punchEventDao(): PunchEventDao
    abstract fun workSessionDao(): WorkSessionDao
    abstract fun workRuleDao(): WorkRuleDao

    companion object {
        @Volatile
        private var INSTANCE: WorkTimeDatabase? = null

        fun get(context: Context): WorkTimeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }

        private fun build(context: Context): WorkTimeDatabase =
            Room.databaseBuilder(context, WorkTimeDatabase::class.java, Constants.DB_NAME)
                .addCallback(object : Callback() {
                    /**
                     * 首次安装写入默认规则，避免引擎因无规则而静默跳过计算（旧版曾有此缺陷）。
                     * 工作日默认周一至周五，用户可在设置中调整。
                     */
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        db.execSQL(
                            """
                            INSERT INTO work_rule
                            (ruleName, startTime, endTime, restStart, restEnd, bufferMinutes, dayCutTime, workDays, isDefault, updateTime)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                            """.trimIndent(),
                            arrayOf(
                                "默认规则",
                                Constants.DEFAULT_START_TIME,
                                Constants.DEFAULT_END_TIME,
                                Constants.DEFAULT_REST_START,
                                Constants.DEFAULT_REST_END,
                                Constants.DEFAULT_BUFFER_MINUTES,
                                Constants.DEFAULT_DAY_CUT_TIME,
                                Constants.DEFAULT_WORK_DAYS,
                                System.currentTimeMillis()
                            )
                        )
                    }
                })
                .build()
    }
}
